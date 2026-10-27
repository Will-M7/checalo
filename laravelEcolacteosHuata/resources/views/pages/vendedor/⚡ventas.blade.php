<?php

use App\Enums\EstadoProduccion;
use App\Enums\MetodoPago;
use App\Models\Cliente;
use App\Models\Descuento;
use App\Models\Produccion;
use App\Models\Venta;
use App\Models\VentaItem;
use Flux\Flux;
use Illuminate\Support\Collection;
use Livewire\Component;
use Livewire\Attributes\Locked;
use App\Models\IntentoPago;
use App\Services\Payments\{CobrosService, VentasService, Dinero};

new class extends Component
{
    public array $carrito = [];

    public string $busquedaProducto = '';

    public ?int $clienteId = null;

    public string $clienteNombreLibre = '';

    public string $clienteDocLibre = '';

    public array $descuentosActivos = [];

    public string $metodoPago = 'efectivo';

    public ?float $efectivoRecibido = null;

    public ?int $ventaGuardadaId = null;
    #[Locked] public ?int $intentoYapeId = null;
    #[Locked] public ?string $referenciaYape = null;
    #[Locked] public string $referenciaVenta;
    #[Locked] public string $estadoYape = '';
    public string $codigoYape = '';


    public function mount(): void
    {
        $this->referenciaVenta = (string) \Illuminate\Support\Str::uuid();
        $produccionId = request()->integer('produccion');

        if ($produccionId) {
            $this->agregar($produccionId);
        }
    }

    public function with(): array
    {
        $subtotal = collect($this->carrito)->sum('subtotal');
        $cliente = $this->clienteId ? Cliente::find($this->clienteId) : null;
        $descuentosDisponibles = Descuento::where('activo', true)->orderBy('nombre')->get();
        $descuentosAplicados = $this->descuentosAplicados($descuentosDisponibles);
        $descuentoPct = (float) $descuentosAplicados->sum('porcentaje');
        $descuento = round($subtotal * $descuentoPct / 100, 2);
        $total = round($subtotal - $descuento, 2);

        $vuelto = null;
        if ($this->metodoPago === 'efectivo' && $this->efectivoRecibido !== null && $this->efectivoRecibido >= $total) {
            $vuelto = round($this->efectivoRecibido - $total, 2);
        }

        return [
            'producciones' => Produccion::query()
                ->where('estado', EstadoProduccion::Completada)
                ->with('receta.detalles.insumo')
                ->when($this->busquedaProducto, fn ($q) => $q->where(
                    fn ($q2) => $q2->where('nombre', 'like', "%{$this->busquedaProducto}%")
                        ->orWhereHas('receta', fn ($r) => $r->where('nombre', 'like', "%{$this->busquedaProducto}%"))
                ))
                ->orderByDesc('fecha')
                ->get(),
            'clientesGuardados' => Cliente::orderBy('nombres')->orderBy('apellidos')->get(),
            'clienteSeleccionado' => $cliente,
            'descuentosDisponibles' => $descuentosDisponibles,
            'metodos' => MetodoPago::options(),
            'subtotal' => $subtotal,
            'descuentoPct' => $descuentoPct,
            'descuento' => $descuento,
            'total' => $total,
            'vuelto' => $vuelto,
            'intentoActual' => $this->intentoYapeId ? IntentoPago::where('user_id', auth('web')->id())->find($this->intentoYapeId) : null,
        ];
    }

    /**
     * @param  Collection<int, Descuento>  $descuentosDisponibles
     * @return Collection<int, Descuento>
     */
    private function descuentosAplicados(?Collection $descuentosDisponibles = null): Collection
    {
        $descuentosDisponibles ??= Descuento::where('activo', true)->get();

        return $descuentosDisponibles->filter(fn (Descuento $d) => ! empty($this->descuentosActivos[$d->id]));
    }

    public function agregar(int $produccionId): void
    {
        $produccion = Produccion::with('receta')->findOrFail($produccionId);
        $nombre = $produccion->nombre ?: $produccion->receta->nombre;
        $disponible = $produccion->cantidadDisponible();

        $lineaExistente = collect($this->carrito)->firstWhere('produccion_id', $produccionId);
        $cantidadEnCarrito = $lineaExistente['cantidad'] ?? 0;

        if ($cantidadEnCarrito + 1 > $disponible) {
            Flux::toast('No hay suficiente cantidad disponible de '.$nombre.'.', variant: 'danger');

            return;
        }

        foreach ($this->carrito as $index => $linea) {
            if ($linea['produccion_id'] === $produccionId) {
                $this->carrito[$index]['cantidad']++;
                $this->carrito[$index]['subtotal'] = round($this->carrito[$index]['precio_unitario'] * $this->carrito[$index]['cantidad'], 2);

                return;
            }
        }

        $this->carrito[] = [
            'produccion_id' => $produccion->id,
            'nombre' => $nombre,
            'precio_unitario' => $produccion->receta->precioVenta(),
            'cantidad' => 1,
            'subtotal' => round($produccion->receta->precioVenta(), 2),
        ];
    }

    public function incrementar(int $index): void
    {
        $linea = $this->carrito[$index];
        $produccion = Produccion::find($linea['produccion_id']);

        if (! $produccion || $produccion->cantidadDisponible() < $linea['cantidad'] + 1) {
            Flux::toast('No hay suficiente cantidad disponible.', variant: 'danger');

            return;
        }

        $this->carrito[$index]['cantidad']++;
        $this->carrito[$index]['subtotal'] = round($this->carrito[$index]['precio_unitario'] * $this->carrito[$index]['cantidad'], 2);
    }

    public function decrementar(int $index): void
    {
        if ($this->carrito[$index]['cantidad'] <= 1) {
            $this->quitar($index);

            return;
        }

        $this->carrito[$index]['cantidad']--;
        $this->carrito[$index]['subtotal'] = round($this->carrito[$index]['precio_unitario'] * $this->carrito[$index]['cantidad'], 2);
    }

    /**
     * Recalcula el subtotal de una línea cuando la cantidad se edita manualmente,
     * respetando la cantidad disponible de la producción.
     */
    public function actualizarCantidad(int $index): void
    {
        if (! isset($this->carrito[$index])) {
            return;
        }

        $produccion = Produccion::find($this->carrito[$index]['produccion_id']);
        $cantidad = (int) $this->carrito[$index]['cantidad'];

        if ($cantidad < 1) {
            $cantidad = 1;
        }

        if ($produccion && $cantidad > $produccion->cantidadDisponible()) {
            $cantidad = (int) $produccion->cantidadDisponible();
            Flux::toast('Cantidad máxima alcanzada para '.$this->carrito[$index]['nombre'].'.', variant: 'danger');
        }

        $this->carrito[$index]['cantidad'] = $cantidad;
        $this->carrito[$index]['subtotal'] = round($this->carrito[$index]['precio_unitario'] * $cantidad, 2);
    }

    public function updated(string $property): void
    {
        if (preg_match('/^carrito\.(\d+)\.cantidad$/', $property, $coincidencia)) {
            $this->actualizarCantidad((int) $coincidencia[1]);
        }
    }

    public function quitar(int $index): void
    {
        unset($this->carrito[$index]);
        $this->carrito = array_values($this->carrito);
    }

    public function guardar(): void
    {
        $actor = auth('web')->user();
        $code = $this->codigoYape; $this->codigoYape = '';
        abort_unless($actor && $actor->activo && $actor->puedePantalla('vendedor.ventas'), 403);
        $this->validate(['metodoPago' => 'required|in:efectivo,yape,plin,tarjeta,otro', 'clienteNombreLibre' => 'nullable|string|max:80', 'clienteDocLibre' => 'nullable|string|max:15', 'clienteId' => 'nullable|integer|exists:clientes,id']);
        $service = app(CobrosService::class);
        try {
            if ($this->intentoYapeId || $this->metodoPago === 'yape') {
                if (! $this->intentoYapeId) {
                    $this->referenciaYape ??= (string) \Illuminate\Support\Str::uuid();
                    $cliente = $this->clienteId ? Cliente::findOrFail($this->clienteId) : null;
                    $payment = $service->presencial($actor, $this->carrito, array_keys(array_filter($this->descuentosActivos)), [
                        'cliente_id' => $cliente?->id, 'cliente_nombre_libre' => $cliente ? trim($cliente->nombres.' '.$cliente->apellidos) : ($this->clienteNombreLibre ?: 'Venta presencial'), 'cliente_doc_libre' => $this->clienteDocLibre ?: null,
                    ], $this->referenciaYape);
                    $this->intentoYapeId = $payment->id;
                }
                $payment = IntentoPago::where('user_id', $actor->id)->findOrFail($this->intentoYapeId);
                $payment = $code !== '' ? $service->validar($payment, $code, 'staff:'.$actor->id, (string) request()->ip()) : $service->consultar($payment);
                $this->estadoYape = $payment->estado === 'PAID' ? ($payment->venta_id ? 'Pago confirmado.' : 'Ingreso confirmado sin stock: conservado para revisión administrativa.') : ($payment->estado === 'EXPIRED' ? 'Operación vencida.' : 'Esperando pago o código correcto; las coincidencias ambiguas requieren revisión.');
                if (! $payment->venta_id) { return; }
                $sale = Venta::findOrFail($payment->venta_id);
            } else {
                $cliente = $this->clienteId ? Cliente::findOrFail($this->clienteId) : null;
                $cash = $this->efectivoRecibido === null ? null : Dinero::minor(number_format($this->efectivoRecibido, 2, '.', ''));
                $sale = app(VentasService::class)->presencialManual($actor, $this->carrito, array_keys(array_filter($this->descuentosActivos)), $this->metodoPago, ['cliente_id' => $cliente?->id, 'cliente_nombre_libre' => $cliente ? null : ($this->clienteNombreLibre ?: null), 'cliente_doc_libre' => $cliente ? null : ($this->clienteDocLibre ?: null)], $cash, $this->referenciaVenta);
            }
        } catch (\App\Services\Payments\StockInsuficiente|\Illuminate\Validation\ValidationException|\InvalidArgumentException $e) {
            $this->estadoYape = $e instanceof \Illuminate\Validation\ValidationException ? collect($e->errors())->flatten()->first() : $e->getMessage();
            Flux::toast($this->estadoYape, variant: 'danger'); return;
        }
        $this->ventaGuardadaId = $sale->id;
        $this->reset(['carrito', 'clienteId', 'clienteNombreLibre', 'clienteDocLibre', 'descuentosActivos', 'efectivoRecibido', 'busquedaProducto', 'intentoYapeId', 'referenciaYape', 'codigoYape']);
        $this->metodoPago = 'efectivo';
        $this->referenciaVenta = (string) \Illuminate\Support\Str::uuid();
        Flux::toast('Venta registrada. Boleta '.$sale->numero_boleta.' lista.', variant: 'success');
        $this->js("window.open('".route('vendedor.ventas.boleta', $sale->id)."', '_blank')");
    }
    public function cancelarYape(): void
    {
        $payment = IntentoPago::where('user_id', auth('web')->id())->findOrFail($this->intentoYapeId);
        $payment = app(CobrosService::class)->cancelar($payment);
        if (in_array($payment->estado, ['CANCELLED', 'EXPIRED'], true)) { $this->reset(['intentoYapeId', 'referenciaYape', 'codigoYape']); $this->estadoYape = 'Espera cerrada. Puedes iniciar otra operación.'; }
        else { $this->estadoYape = 'El ingreso ya fue confirmado; consulta su boleta o revisión.'; }
    }

};
?>

<div class="flex flex-col gap-6 lg:flex-row">
    <div class="flex flex-1 flex-col gap-6">
        <div class="flex items-center gap-3">
            <x-ui.titulo titulo="Punto de venta">Selecciona productos para agregarlos a la venta actual.</x-ui.titulo>
            <flux:badge color="green">{{ count($carrito) }} producto(s)</flux:badge>
        </div>

        <div class="grid gap-4 sm:grid-cols-2">
            <div class="rounded-xl border border-huata-verde-100 p-4 dark:border-zinc-700 huata-cuadro">
                <flux:heading size="sm" class="mb-2">Cliente (opcional)</flux:heading>

                <div class="flex flex-col gap-2">
                    <flux:select placeholder="Selecciona un cliente guardado..." wire:model.live="clienteId" size="sm">
                        <flux:select.option value="">Sin cliente / cliente nuevo</flux:select.option>
                        @foreach ($clientesGuardados as $c)
                            <flux:select.option value="{{ $c->id }}">{{ $c->nombres }} {{ $c->apellidos }} &middot; {{ $c->documento }}</flux:select.option>
                        @endforeach
                    </flux:select>

                    @if ($clienteSeleccionado)
                        <p class="text-xs text-zinc-500">{{ $clienteSeleccionado->compras_count }} compra(s) anteriores registradas.</p>
                    @else
                        <flux:text class="text-xs text-zinc-500">O ingresa los datos manualmente (venta sin cliente guardado):</flux:text>
                        <div class="grid grid-cols-2 gap-2">
                            <flux:input placeholder="Nombre en boleta" wire:model="clienteNombreLibre" size="sm" />
                            <flux:input placeholder="DNI/RUC" wire:model="clienteDocLibre" size="sm" />
                        </div>
                    @endif
                </div>
            </div>

            <div class="rounded-xl border border-huata-verde-100 p-4 dark:border-zinc-700 huata-cuadro">
                <flux:heading size="sm" class="mb-2">Descuento</flux:heading>

                <div class="max-h-40 overflow-y-auto">
                    <div class="flex flex-col divide-y divide-huata-verde-100 dark:divide-zinc-700">
                        @forelse ($descuentosDisponibles as $opcionDescuento)
                            <div wire:key="descuento-{{ $opcionDescuento->id }}" class="flex items-center justify-between gap-2 py-2 first:pt-0 last:pb-0">
                                <div class="min-w-0">
                                    <p class="truncate text-sm font-medium">{{ $opcionDescuento->nombre }}</p>
                                    <p class="text-xs text-zinc-500">{{ number_format((float) $opcionDescuento->porcentaje, 2) }}%</p>
                                </div>
                                <flux:switch wire:model.live="descuentosActivos.{{ $opcionDescuento->id }}" />
                            </div>
                        @empty
                            <flux:text class="py-2 text-sm text-zinc-500">No hay descuentos disponibles.</flux:text>
                        @endforelse
                    </div>
                </div>
            </div>
        </div>

        <div class="flex flex-wrap items-center justify-between gap-4">
            <flux:input
                wire:model.live.debounce.300ms="busquedaProducto"
                icon="magnifying-glass"
                placeholder="Filtrar por nombre..."
                class="max-w-sm"
            />
        </div>

        <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            @forelse ($producciones as $produccion)
                @php $disponible = $produccion->cantidadDisponible(); $cantidad = $disponible; $precioUnidad = $produccion->receta->precioVenta(); @endphp
                <div wire:key="producto-{{ $produccion->id }}" class="hh-accion !flex-col !gap-0 !p-3">
                    <div class="mb-3 flex aspect-[4/3] w-full items-center justify-center overflow-hidden rounded-lg bg-[var(--hh-superficie-2)]">
                        @if ($produccion->imagenUrl())
                            <img src="{{ $produccion->imagenUrl() }}" alt="{{ $produccion->nombre }}" class="size-full object-cover" />
                        @else
                            <flux:icon name="cube" class="size-10 text-[var(--hh-marca)] opacity-50" />
                        @endif
                    </div>

                    <div class="flex w-full items-start justify-between gap-2">
                        <p class="text-sm font-semibold leading-snug text-[var(--hh-texto)]">{{ $produccion->nombre ?? $produccion->receta->nombre }}</p>
                        <flux:badge :color="$cantidad > 5 ? 'green' : ($cantidad > 0 ? 'amber' : 'red')" size="sm" class="shrink-0">
                            {{ number_format($cantidad, 0) }}
                        </flux:badge>
                    </div>

                    <p class="mt-2 w-full text-xl font-semibold tabular-nums text-[var(--hh-texto)]">S/ {{ number_format($precioUnidad, 2) }}</p>

                    <flux:button
                        size="sm"
                        variant="primary"
                        icon="plus"
                        class="mt-3 w-full"
                        wire:click="agregar({{ $produccion->id }})"
                        :disabled="$disponible <= 0"
                    >
                        Agregar
                    </flux:button>
                </div>
            @empty
                <div class="col-span-full rounded-xl border border-huata-verde-100 p-6 text-center text-zinc-500 dark:border-zinc-700 huata-cuadro">
                    No se encontraron productos.
                </div>
            @endforelse
        </div>
    </div>

    <div class="w-full lg:max-w-sm">
        <div class="sticky top-20 flex flex-col gap-4 rounded-xl border border-huata-verde-100 p-5 dark:border-zinc-700 huata-cuadro">
            <flux:heading size="lg">Venta actual</flux:heading>

            @if (empty($carrito))
                <div class="flex flex-col items-center justify-center gap-2 py-8 text-zinc-400">
                    <flux:icon name="shopping-cart" class="size-10" />
                    <flux:text>Selecciona un producto</flux:text>
                </div>
            @else
                <div class="flex flex-col divide-y divide-huata-verde-100 dark:divide-zinc-700">
                    @foreach ($carrito as $index => $linea)
                        <div class="flex flex-col gap-2 py-3 first:pt-0 last:pb-0" wire:key="linea-{{ $index }}">
                            <div class="flex items-start justify-between gap-2">
                                <p class="min-w-0 truncate text-sm font-medium">{{ $linea['nombre'] }}</p>
                                <span class="shrink-0 text-sm font-semibold">S/ {{ number_format($linea['subtotal'], 2) }}</span>
                            </div>

                            <div class="flex items-center justify-between gap-2">
                                <p class="text-xs text-zinc-500">S/ {{ number_format($linea['precio_unitario'], 2) }} c/u</p>

                                <div class="flex items-center gap-1">
                                    <flux:button size="sm" variant="ghost" icon="minus" wire:click="decrementar({{ $index }})" />
                                    <flux:input
                                        type="number"
                                        min="1"
                                        wire:model.live="carrito.{{ $index }}.cantidad"
                                        size="xs"
                                        class="w-9 px-1 text-center [-moz-appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none [&::-webkit-outer-spin-button]:appearance-none"
                                    />
                                    <flux:button size="sm" variant="ghost" icon="plus" wire:click="incrementar({{ $index }})" />
                                    <flux:button size="sm" variant="ghost" icon="trash" wire:click="quitar({{ $index }})" />
                                </div>
                            </div>
                        </div>
                    @endforeach
                </div>
            @endif

            <div class="flex justify-between text-sm">
                <span>Subtotal</span>
                <span>S/ {{ number_format($subtotal, 2) }}</span>
            </div>
            @if ($descuento > 0)
                <div class="-mt-2 flex justify-between text-sm text-huata-verde-700">
                    <span>Descuento ({{ $descuentoPct }}%)</span>
                    <span>- S/ {{ number_format($descuento, 2) }}</span>
                </div>
            @endif
            <div class="flex items-end justify-between border-t border-[var(--hh-borde)] pt-3">
                <span class="text-sm font-semibold text-[var(--hh-texto-2)]">Total</span>
                <span class="text-3xl font-semibold tabular-nums tracking-tight text-[var(--hh-texto)]">S/ {{ number_format($total, 2) }}</span>
            </div>

            <fieldset>
                <legend class="mb-2 text-sm font-semibold text-[var(--hh-texto)]">Método de pago</legend>
                <div class="grid grid-cols-3 gap-2">
                    @foreach ($metodos as $m)
                        <label class="flex min-h-11 cursor-pointer items-center justify-center rounded-xl border border-[var(--hh-borde-fuerte)] px-2 text-center text-sm font-semibold text-[var(--hh-texto)] transition-colors hover:border-[var(--hh-marca)] has-[:checked]:border-[var(--hh-marca)] has-[:checked]:bg-[var(--hh-marca)] has-[:checked]:text-white has-[:focus-visible]:outline has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-[var(--hh-marca)]">
                            <input type="radio" class="sr-only" name="metodo-pago" value="{{ $m['value'] }}" wire:model.live="metodoPago">{{ $m['label'] }}
                        </label>
                    @endforeach
                </div>
            </fieldset>

            @if ($metodoPago === 'efectivo')
                <flux:input type="number" step="0.01" min="0" label="Efectivo recibido" wire:model.live="efectivoRecibido" />
                @if ($vuelto !== null && $vuelto > 0)
                    <div class="flex justify-between text-sm font-semibold text-huata-celeste-700">
                        <span>Vuelto</span>
                        <span>S/ {{ number_format($vuelto, 2) }}</span>
                    </div>
                @endif
            @endif

            @if($metodoPago === 'yape' || $intentoYapeId)
                <p class="text-sm" role="status">{{ $estadoYape ?: 'Primero prepara el cobro. El QR solo aparece cuando hay una intención canónica.' }}</p>
                @if($intentoActual?->external_id && $intentoActual->estado === 'PENDING')
                    <p class="font-semibold">Cobro fijado: S/ {{ number_format($intentoActual->amount_minor / 100, 2) }} · {{ $intentoActual->receptor_snapshot['nombre'] }}</p>
                    <img src="{{ asset('storage/'.$intentoActual->receptor_snapshot['qr_path']) }}" alt="QR del receptor autorizado" class="mx-auto w-44 rounded-xl">
                    <flux:input wire:model="codigoYape" label="Código Yape comunicado por el comprador" inputmode="numeric" maxlength="3" autocomplete="off" />
                @endif
                @if($intentoYapeId) <flux:button wire:click="cancelarYape">Cerrar espera de cobro</flux:button> @endif
            @endif
            <flux:button variant="primary" icon="check" wire:click="guardar" class="!min-h-12 !text-base">Cobrar y guardar boleta</flux:button>

            @if ($ventaGuardadaId)
                <flux:button as="a" :href="route('vendedor.ventas.boleta', $ventaGuardadaId)" target="_blank" variant="filled" icon="document-arrow-down">
                    Ver boleta (PDF)
                </flux:button>
            @endif
        </div>
    </div>
</div>

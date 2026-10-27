<?php

use App\Enums\EstadoProduccion;
use App\Models\Produccion;
use App\Models\Receta;
use App\Models\Turno;
use Flux\Flux;
use Livewire\Component;

new class extends Component
{
    public ?int $produccionId = null;

    /**
     * @var array<int, array{receta_id: int|null, cantidad: string}>
     */
    public array $lineas = [];

    public ?int $produccionTurnoId = null;

    public ?int $registrarId = null;

    public string $registrarNombre = '';

    public string $registrarRecetaNombre = '';

    public string $registrarCantidad = '';

    public ?int $registrarTurnoId = null;

    public ?int $eliminarProduccionId = null;

    public function mount(): void
    {
        $this->lineas = [['receta_id' => null, 'cantidad' => '']];
    }

    public function with(): array
    {
        return [
            'recetas' => Receta::orderBy('nombre')->get(),
            'turnos' => Turno::where('es_produccion', true)->orderBy('hora_inicio')->get(),
            'producciones' => Produccion::with('receta.detalles.insumo', 'turno')->orderByDesc('updated_at')->limit(20)->get(),
            'faltantesNueva' => $this->faltantesFormNueva(),
            'stockBajoNueva' => $this->stockBajoFormNueva(),
            'insumosAgotadosPorLinea' => $this->insumosAgotadosPorLinea(),
            'faltantesActualizar' => $this->faltantesFormActualizar(),
        ];
    }

    /**
     * Recetas seleccionadas en el formulario (id único, sin repetir), con sus insumos.
     *
     * @return \Illuminate\Support\Collection<int, Receta>
     */
    private function recetasSeleccionadas(): \Illuminate\Support\Collection
    {
        $recetaIds = collect($this->lineas)->pluck('receta_id')->filter()->unique();

        if ($recetaIds->isEmpty()) {
            return collect();
        }

        return Receta::whereIn('id', $recetaIds)->with('detalles.insumo.unidadMedida')->get()->keyBy('id');
    }

    /**
     * Cuánto consumiría cada insumo en total si se guardaran todas las líneas del
     * formulario tal como están, sumando entre líneas que compartan un mismo insumo (para no
     * dejar que dos líneas agoten el mismo insumo sin darse cuenta entre sí). Si se está
     * editando una producción existente, descuenta lo que esa producción ya tenía consumido.
     *
     * @return \Illuminate\Support\Collection<int, array{insumo: \App\Models\Insumo, pendiente: float}>
     */
    private function pendientePorInsumoFormulario(): \Illuminate\Support\Collection
    {
        $recetas = $this->recetasSeleccionadas();

        if ($recetas->isEmpty()) {
            return collect();
        }

        $yaConsumido = $this->produccionId
            ? \App\Models\ProduccionDetalle::where('produccion_id', $this->produccionId)
                ->selectRaw('insumo_id, SUM(cantidad_consumida) as total')
                ->groupBy('insumo_id')
                ->pluck('total', 'insumo_id')
            : collect();

        $pendientePorInsumo = collect();

        foreach ($this->lineas as $linea) {
            if (empty($linea['receta_id']) || ! is_numeric($linea['cantidad']) || (float) $linea['cantidad'] <= 0) {
                continue;
            }

            $receta = $recetas->get($linea['receta_id']);

            if (! $receta) {
                continue;
            }

            foreach ($receta->detalles as $detalleReceta) {
                if (! $detalleReceta->insumo) {
                    continue;
                }

                $actual = $pendientePorInsumo->get($detalleReceta->insumo_id, ['insumo' => $detalleReceta->insumo, 'pendiente' => 0.0]);
                $actual['pendiente'] += (float) $detalleReceta->cantidad * (float) $linea['cantidad'];
                $pendientePorInsumo->put($detalleReceta->insumo_id, $actual);
            }
        }

        return $pendientePorInsumo->map(function (array $item) use ($yaConsumido) {
            $item['pendiente'] = max(0, $item['pendiente'] - (float) ($yaConsumido[$item['insumo']->id] ?? 0));

            return $item;
        })->filter(fn (array $item) => $item['pendiente'] > 0)->values();
    }

    /**
     * Insumos sin stock suficiente para lo que se está por guardar en "Nueva producción" /
     * "Editar producción", calculado en vivo mientras se llena el formulario.
     *
     * @return \Illuminate\Support\Collection<int, array{insumo: \App\Models\Insumo, pendiente: float, faltante: float}>
     */
    private function faltantesFormNueva(): \Illuminate\Support\Collection
    {
        return $this->pendientePorInsumoFormulario()
            ->filter(fn (array $item) => $item['pendiente'] > (float) $item['insumo']->stock_actual)
            ->map(fn (array $item) => [
                'insumo' => $item['insumo'],
                'pendiente' => $item['pendiente'],
                'faltante' => $item['pendiente'] - (float) $item['insumo']->stock_actual,
            ])
            ->values();
    }

    /**
     * Insumos de las recetas seleccionadas cuyo stock está por agotarse (10 unidades o menos,
     * el mismo umbral que el panel), para avisar aunque todavía alcance para esta producción.
     *
     * @return \Illuminate\Support\Collection<int, array{insumo: \App\Models\Insumo, restante: float}>
     */
    private function stockBajoFormNueva(): \Illuminate\Support\Collection
    {
        $umbral = 10;
        $pendientePorInsumo = $this->pendientePorInsumoFormulario()->keyBy(fn (array $item) => $item['insumo']->id);

        return $this->recetasSeleccionadas()
            ->pluck('detalles')
            ->flatten()
            ->pluck('insumo')
            ->filter()
            ->unique('id')
            ->map(function (\App\Models\Insumo $insumo) use ($pendientePorInsumo) {
                $pendiente = (float) ($pendientePorInsumo[$insumo->id]['pendiente'] ?? 0);

                return ['insumo' => $insumo, 'restante' => (float) $insumo->stock_actual - $pendiente];
            })
            ->filter(fn (array $item) => $item['restante'] > 0 && $item['restante'] <= $umbral)
            ->values();
    }

    /**
     * Para cada línea del formulario, los insumos de su receta que ya están en 0: si hay
     * alguno, esa línea no puede producirse hasta reponer stock.
     *
     * @return array<int, \Illuminate\Support\Collection<int, \App\Models\Insumo>>
     */
    private function insumosAgotadosPorLinea(): array
    {
        $recetas = $this->recetasSeleccionadas();

        return collect($this->lineas)->map(function (array $linea) use ($recetas) {
            $receta = $recetas->get($linea['receta_id'] ?? null);

            if (! $receta) {
                return collect();
            }

            return collect($receta->detalles)
                ->pluck('insumo')
                ->filter()
                ->unique('id')
                ->filter(fn (\App\Models\Insumo $insumo) => (float) $insumo->stock_actual <= 0)
                ->values();
        })->all();
    }

    /**
     * Insumos sin stock suficiente para lo que se está por sumar en "Actualizar producción",
     * calculado en vivo mientras se llena el formulario.
     */
    private function faltantesFormActualizar(): \Illuminate\Support\Collection
    {
        if (! $this->registrarId || ! is_numeric($this->registrarCantidad) || (float) $this->registrarCantidad <= 0) {
            return collect();
        }

        $produccion = Produccion::find($this->registrarId);

        if (! $produccion) {
            return collect();
        }

        $cantidadFinal = (float) $produccion->cantidad_producida + (float) $this->registrarCantidad;

        return $produccion->insumosInsuficientes($cantidadFinal);
    }

    public function nuevaProduccion(): void
    {
        $this->reset(['produccionId', 'produccionTurnoId']);
        $this->lineas = [['receta_id' => null, 'cantidad' => '']];
        Flux::modal('produccion-form')->show();
    }

    public function editarProduccion(int $id): void
    {
        $p = Produccion::findOrFail($id);
        $this->produccionId = $p->id;
        $this->produccionTurnoId = $p->turno_id;
        $this->lineas = [['receta_id' => $p->receta_id, 'cantidad' => (string) $p->cantidad_producida]];
        Flux::modal('produccion-form')->show();
    }

    public function agregarLinea(): void
    {
        $this->lineas[] = ['receta_id' => null, 'cantidad' => ''];
    }

    public function quitarLinea(int $index): void
    {
        if (count($this->lineas) <= 1) {
            return;
        }

        unset($this->lineas[$index]);
        $this->lineas = array_values($this->lineas);
    }

    public function guardarProduccion(): void
    {
        $data = $this->validate([
            'lineas' => 'required|array|min:1',
            'lineas.*.receta_id' => 'required|exists:recetas,id',
            'lineas.*.cantidad' => 'required|numeric|min:0.001',
        ]);

        $faltantes = $this->faltantesFormNueva();

        if ($faltantes->isNotEmpty()) {
            Flux::toast($this->mensajeFaltantes($faltantes), variant: 'danger');

            return;
        }

        if ($this->produccionId) {
            $linea = $data['lineas'][0];
            $produccion = Produccion::findOrFail($this->produccionId);
            $produccion->update([
                'nombre' => Receta::find($linea['receta_id'])->nombre,
                'receta_id' => $linea['receta_id'],
                'cantidad_producida' => $linea['cantidad'],
                'turno_id' => $this->produccionTurnoId,
                'estado' => EstadoProduccion::Planificada,
            ]);
            $produccion->consumirInsumos();
        } else {
            foreach ($data['lineas'] as $indice => $linea) {
                $produccion = Produccion::create([
                    'fecha' => now()->toDateString(),
                    'nombre' => Receta::find($linea['receta_id'])->nombre,
                    'receta_id' => $linea['receta_id'],
                    'cantidad_producida' => $linea['cantidad'],
                    'lote' => 'L-'.now()->format('YmdHis').sprintf('%02d', $indice + 1).'-'.random_int(100, 999),
                    'turno_id' => $this->produccionTurnoId,
                    'responsable_id' => auth()->id(),
                    'estado' => EstadoProduccion::Planificada,
                ]);

                $produccion->consumirInsumos();
            }
        }

        Flux::modal('produccion-form')->close();
        Flux::toast('Producción guardada y stock de insumos descontado.', variant: 'success');
    }

    public function abrirActualizarProduccion(int $id): void
    {
        $p = Produccion::with('receta')->findOrFail($id);
        $this->registrarId = $p->id;
        $this->registrarNombre = $p->nombre ?: 'Sin nombre';
        $this->registrarRecetaNombre = $p->receta->nombre;
        $this->registrarCantidad = '';
        $this->registrarTurnoId = $p->turno_id;
        Flux::modal('actualizar-produccion-form')->show();
    }

    public function actualizarProduccion(): void
    {
        $data = $this->validate([
            'registrarCantidad' => 'required|numeric|min:0.001',
        ]);

        $produccion = Produccion::findOrFail($this->registrarId);
        $cantidadAgregada = (float) $data['registrarCantidad'];
        $cantidadFinal = (float) $produccion->cantidad_producida + $cantidadAgregada;

        $faltantes = $produccion->insumosInsuficientes($cantidadFinal);

        if ($faltantes->isNotEmpty()) {
            Flux::toast($this->mensajeFaltantes($faltantes), variant: 'danger');

            return;
        }

        $produccion->update([
            'cantidad_producida' => (float) $produccion->cantidad_producida + $cantidadAgregada,
            'turno_id' => $this->registrarTurnoId,
            'estado' => EstadoProduccion::Planificada,
        ]);

        // Descuenta solo lo pendiente respecto a lo ya consumido, para no descontar dos veces.
        $produccion->consumirInsumos();

        Flux::modal('actualizar-produccion-form')->close();
        Flux::toast('Producción actualizada y stock de insumos descontado.', variant: 'success');
    }

    public function completarProduccion(int $id): void
    {
        $produccion = Produccion::findOrFail($id);

        if ($produccion->estado === EstadoProduccion::Completada) {
            return;
        }

        $faltantes = $produccion->insumosInsuficientes((float) $produccion->cantidad_producida);

        if ($faltantes->isNotEmpty()) {
            Flux::toast($this->mensajeFaltantes($faltantes), variant: 'danger');

            return;
        }

        $produccion->completar();
        Flux::toast('Producción publicada.', variant: 'success');
    }

    public function pedirEliminarProduccion(int $id): void
    {
        $this->eliminarProduccionId = $id;
        Flux::modal('confirmar-eliminar-produccion')->show();
    }

    public function eliminarProduccion(): void
    {
        $produccion = Produccion::findOrFail($this->eliminarProduccionId);

        if ((float) $produccion->cantidad_vendida > 0) {
            Flux::modal('confirmar-eliminar-produccion')->close();
            Flux::toast('No se puede eliminar: esta producción ya tiene ventas registradas.', variant: 'danger');

            return;
        }

        $produccion->revertirConsumo();
        $produccion->delete();

        Flux::modal('confirmar-eliminar-produccion')->close();
        Flux::toast('Producción eliminada y stock de insumos restituido.', variant: 'success');
    }

    /**
     * @param  \Illuminate\Support\Collection<int, array{insumo: \App\Models\Insumo, pendiente: float, faltante: float}>  $faltantes
     */
    private function mensajeFaltantes(\Illuminate\Support\Collection $faltantes): string
    {
        $detalle = $faltantes
            ->map(fn (array $f) => "{$f['insumo']->nombre} (faltan ".number_format($f['faltante'], 2).' '.$f['insumo']->unidadMedida->simbolo.')')
            ->implode(', ');

        return 'No hay stock suficiente de insumos para producir esa cantidad: '.$detalle.'.';
    }
};
?>

<div class="flex flex-col gap-6">
    <div class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <x-ui.titulo titulo="Producción">Registra cada lote que preparas y descuenta automáticamente los insumos usados.</x-ui.titulo>
        <flux:modal.trigger name="produccion-form">
            <flux:button size="sm" variant="primary" icon="plus" wire:click="nuevaProduccion">Nueva producción</flux:button>
        </flux:modal.trigger>
    </div>

    <div class="overflow-x-auto rounded-xl border border-huata-verde-100 px-3 dark:border-zinc-700 huata-cuadro">
        <flux:table>
            <flux:table.columns class="bg-white/80 dark:bg-zinc-800/80">
                <flux:table.column>Fecha</flux:table.column>
                <flux:table.column>Turno</flux:table.column>
                <flux:table.column>Nombre</flux:table.column>
                <flux:table.column>Receta</flux:table.column>
                <flux:table.column>Cantidad</flux:table.column>
                <flux:table.column>Vendido</flux:table.column>
                <flux:table.column>Precio unidad</flux:table.column>
                <flux:table.column>Precio total</flux:table.column>
                <flux:table.column>Estado</flux:table.column>
                <flux:table.column class="w-px whitespace-nowrap">Acciones</flux:table.column>
            </flux:table.columns>
            <flux:table.rows>
                @forelse ($producciones as $produccion)
                    @php $precioUnidad = $produccion->receta->precioVenta(); $disponible = $produccion->cantidadDisponible(); @endphp
                    <flux:table.row wire:key="prod-{{ $produccion->id }}">
                        <flux:table.cell>{{ $produccion->fecha->format('d/m/Y') }}</flux:table.cell>
                        <flux:table.cell>{{ $produccion->turno?->nombre ?? '—' }}</flux:table.cell>
                        <flux:table.cell>{{ $produccion->nombre ?? '—' }}</flux:table.cell>
                        <flux:table.cell>{{ $produccion->receta->nombre }}</flux:table.cell>
                        <flux:table.cell>
                            <flux:badge :color="$disponible > 0 ? 'green' : 'zinc'" size="sm">
                                {{ number_format($disponible, 2) }}
                            </flux:badge>
                        </flux:table.cell>
                        <flux:table.cell>{{ number_format((float) $produccion->cantidad_vendida, 2) }}</flux:table.cell>
                        <flux:table.cell>S/ {{ number_format($precioUnidad, 2) }}</flux:table.cell>
                        <flux:table.cell>S/ {{ number_format($precioUnidad * $disponible, 2) }}</flux:table.cell>
                        <flux:table.cell>
                            <flux:badge :color="$produccion->estado->value === 'completada' ? 'green' : 'amber'" size="sm">
                                {{ $produccion->estado->label() }}
                            </flux:badge>
                        </flux:table.cell>
                        <flux:table.cell class="whitespace-nowrap">
                            <div class="flex flex-nowrap items-center gap-2">
                                <flux:modal.trigger name="produccion-form">
                                    <flux:button size="sm" variant="ghost" icon="pencil" wire:click="editarProduccion({{ $produccion->id }})" />
                                </flux:modal.trigger>
                                <flux:modal.trigger name="confirmar-eliminar-produccion">
                                    <flux:button size="sm" variant="ghost" icon="trash" wire:click="pedirEliminarProduccion({{ $produccion->id }})" />
                                </flux:modal.trigger>
                                <flux:modal.trigger name="actualizar-produccion-form">
                                    <flux:button size="sm" variant="ghost" icon="arrow-path" wire:click="abrirActualizarProduccion({{ $produccion->id }})" />
                                </flux:modal.trigger>
                                @if ($produccion->estado->value !== 'completada')
                                    <flux:button size="sm" variant="primary" class="shrink-0" wire:click="completarProduccion({{ $produccion->id }})">
                                        Publicar
                                    </flux:button>
                                @endif
                            </div>
                        </flux:table.cell>
                    </flux:table.row>
                @empty
                    <flux:table.row><flux:table.cell colspan="10" class="text-center text-zinc-500">Sin producciones registradas.</flux:table.cell></flux:table.row>
                @endforelse
            </flux:table.rows>
        </flux:table>
    </div>

    <flux:modal name="produccion-form" class="md:w-[30rem]">
        <form wire:submit="guardarProduccion" class="flex flex-col gap-6">
            <flux:heading size="lg">{{ $produccionId ? 'Editar producción' : 'Nueva producción' }}</flux:heading>

            <div>
                <div class="mb-2 flex items-center justify-between">
                    <flux:heading size="sm">Recetas a producir</flux:heading>
                    @unless ($produccionId)
                        <flux:button size="sm" variant="ghost" icon="plus" wire:click="agregarLinea">Agregar receta</flux:button>
                    @endunless
                </div>

                <div class="flex flex-col gap-3">
                    @foreach ($lineas as $index => $linea)
                        @php
                            $agotados = $insumosAgotadosPorLinea[$index] ?? collect();
                            $sinStock = $agotados->isNotEmpty();
                        @endphp
                        <div
                            class="flex flex-col gap-2 rounded-lg border border-huata-verde-100 bg-huata-verde-50/40 p-3 dark:border-zinc-700 dark:bg-zinc-900/40"
                            wire:key="linea-{{ $index }}"
                        >
                            <div class="flex flex-col gap-3 sm:flex-row sm:items-start">
                                <flux:select label="Receta" wire:model.live="lineas.{{ $index }}.receta_id" class="flex-1">
                                    <flux:select.option value="">Selecciona...</flux:select.option>
                                    @foreach ($recetas as $r)
                                        <flux:select.option value="{{ $r->id }}">{{ $r->nombre }}</flux:select.option>
                                    @endforeach
                                </flux:select>

                                <flux:input
                                    type="number" step="0.001" min="0"
                                    label="Cantidad"
                                    wire:model.live.debounce.400ms="lineas.{{ $index }}.cantidad"
                                    class="sm:w-28"
                                    :disabled="$sinStock"
                                />

                                @if (! $produccionId && count($lineas) > 1)
                                    <flux:button
                                        size="sm"
                                        variant="ghost"
                                        icon="trash"
                                        class="self-end sm:mt-6 sm:self-start"
                                        wire:click="quitarLinea({{ $index }})"
                                    />
                                @endif
                            </div>

                            @if ($sinStock)
                                <p class="flex items-start gap-1.5 text-xs text-huata-rojo-600 dark:text-huata-rojo-500">
                                    <flux:icon name="x-circle" class="mt-0.5 size-4 shrink-0" />
                                    Sin stock de {{ $agotados->pluck('nombre')->implode(', ') }}. No puedes producir esta receta hasta reponer stock.
                                </p>
                            @endif
                        </div>
                    @endforeach
                </div>
            </div>

            @if ($faltantesNueva->isNotEmpty())
                <div class="flex items-start gap-2 rounded-lg border border-huata-rojo-500/40 bg-huata-rojo-50 p-3 text-sm text-huata-rojo-700 dark:bg-huata-rojo-500/10 dark:text-huata-rojo-300">
                    <flux:icon name="exclamation-triangle" class="mt-0.5 size-5 shrink-0" />
                    <div>
                        <p class="font-semibold">No hay stock suficiente para producir esta cantidad:</p>
                        <ul class="mt-1 list-inside list-disc">
                            @foreach ($faltantesNueva as $f)
                                <li>{{ $f['insumo']->nombre }} — faltan {{ number_format($f['faltante'], 2) }} {{ $f['insumo']->unidadMedida->simbolo }}</li>
                            @endforeach
                        </ul>
                    </div>
                </div>
            @endif

            @if ($stockBajoNueva->isNotEmpty())
                <div class="flex items-start gap-2 rounded-lg border border-huata-amarillo-500/50 bg-huata-amarillo-400/10 p-3 text-sm text-huata-amarillo-600 dark:bg-huata-amarillo-500/10 dark:text-huata-amarillo-400">
                    <flux:icon name="exclamation-triangle" class="mt-0.5 size-5 shrink-0" />
                    <div>
                        <p class="font-semibold">Estos insumos están por agotarse:</p>
                        <ul class="mt-1 list-inside list-disc">
                            @foreach ($stockBajoNueva as $s)
                                <li>{{ $s['insumo']->nombre }} — quedarán {{ number_format($s['restante'], 2) }} {{ $s['insumo']->unidadMedida->simbolo }}</li>
                            @endforeach
                        </ul>
                    </div>
                </div>
            @endif

            <flux:select label="Turno (opcional)" wire:model="produccionTurnoId">
                <flux:select.option value="">Sin turno</flux:select.option>
                @foreach ($turnos as $t)
                    <flux:select.option value="{{ $t->id }}">{{ $t->nombre }}</flux:select.option>
                @endforeach
            </flux:select>

            <div class="flex justify-end gap-2">
                <flux:modal.close><flux:button variant="ghost">Cancelar</flux:button></flux:modal.close>
                <flux:button type="submit" variant="primary" :disabled="$faltantesNueva->isNotEmpty()">Guardar</flux:button>
            </div>
        </form>
    </flux:modal>

    <flux:modal name="actualizar-produccion-form" class="md:w-96">
        <form wire:submit="actualizarProduccion" class="flex flex-col gap-6">
            <flux:heading size="lg">Actualizar producción</flux:heading>

            <flux:input label="Nombre de la producción" value="{{ $registrarNombre }}" disabled />
            <flux:input label="Receta" value="{{ $registrarRecetaNombre }}" disabled />

            <flux:input type="number" step="0.001" min="0.001" label="Cantidad a agregar" wire:model.live.debounce.400ms="registrarCantidad" />

            @if ($faltantesActualizar->isNotEmpty())
                <div class="flex items-start gap-2 rounded-lg border border-huata-rojo-500/40 bg-huata-rojo-50 p-3 text-sm text-huata-rojo-700 dark:bg-huata-rojo-500/10 dark:text-huata-rojo-300">
                    <flux:icon name="exclamation-triangle" class="mt-0.5 size-5 shrink-0" />
                    <div>
                        <p class="font-semibold">No hay stock suficiente para agregar esta cantidad:</p>
                        <ul class="mt-1 list-inside list-disc">
                            @foreach ($faltantesActualizar as $f)
                                <li>{{ $f['insumo']->nombre }} — faltan {{ number_format($f['faltante'], 2) }} {{ $f['insumo']->unidadMedida->simbolo }}</li>
                            @endforeach
                        </ul>
                    </div>
                </div>
            @endif

            <flux:select label="Turno" wire:model="registrarTurnoId">
                <flux:select.option value="">Sin turno</flux:select.option>
                @foreach ($turnos as $t)
                    <flux:select.option value="{{ $t->id }}">{{ $t->nombre }}</flux:select.option>
                @endforeach
            </flux:select>

            <div class="flex justify-end gap-2">
                <flux:modal.close><flux:button variant="ghost">Cancelar</flux:button></flux:modal.close>
                <flux:button type="submit" variant="primary" :disabled="$faltantesActualizar->isNotEmpty()">Guardar</flux:button>
            </div>
        </form>
    </flux:modal>

    <x-confirm-modal
        name="confirmar-eliminar-produccion"
        accion="eliminarProduccion"
        titulo="¿Eliminar esta producción?"
        mensaje="Se restituirá el stock de insumos consumidos. Esta acción no se puede deshacer."
    />
</div>

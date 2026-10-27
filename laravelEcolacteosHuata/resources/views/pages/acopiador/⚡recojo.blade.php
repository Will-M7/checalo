<?php

use App\Enums\EstadoLeche;
use App\Models\PrecioLeche;
use App\Models\Proveedor;
use App\Models\Recojo;
use App\Models\Turno;
use Flux\Flux;
use Livewire\Attributes\Computed;
use Livewire\Attributes\Validate;
use Livewire\Component;

new class extends Component
{
    public ?int $proveedorId = null;

    public ?int $recojoEditId = null;

    #[Validate('required|numeric|min:0.01')]
    public string $litros = '';

    #[Validate('required|string')]
    public string $estadoLeche = 'normal';

    public string $estadoOtro = '';

    public ?int $turnoId = null;

    public function with(): array
    {
        $acopiador = auth()->user()->acopiador;

        $proveedores = $acopiador
            ? $acopiador->proveedores()->with('user')->orderBy('id')->get()
            : collect();

        // Una vez que el acopiador entrega a planta, sus recojos de hoy quedan marcados como
        // "entregado" y ya no cuentan aquí: el estado y los litros de cada proveedor se
        // restablecen para el siguiente turno/ronda de recojo.
        $recojosHoyPorProveedor = Recojo::query()
            ->where('acopiador_id', $acopiador?->id)
            ->whereDate('fecha', now()->toDateString())
            ->where('entregado', false)
            ->get()
            ->groupBy('proveedor_id');

        return [
            'acopiador' => $acopiador,
            'proveedores' => $proveedores,
            'recogidosHoyIds' => $recojosHoyPorProveedor->keys(),
            'litrosHoyPorProveedor' => $recojosHoyPorProveedor->map(fn ($recojos) => $recojos->sum('litros')),
            'ultimoRecojoPorProveedor' => $recojosHoyPorProveedor->map(fn ($recojos) => $recojos->sortByDesc('id')->first()),
            'turnos' => Turno::where('es_produccion', false)->orderBy('hora_inicio')->get(),
            'precioVigente' => (float) (PrecioLeche::vigente()?->precio_litro ?? 0),
            'estados' => EstadoLeche::options(),
        ];
    }

    #[Computed]
    public function proveedorSeleccionado(): ?Proveedor
    {
        return $this->proveedorId ? Proveedor::with('user')->find($this->proveedorId) : null;
    }

    public function seleccionar(int $id): void
    {
        $this->proveedorId = $id;
        $this->recojoEditId = null;
        $this->reset(['litros', 'estadoOtro']);
        $this->estadoLeche = 'normal';
        $this->turnoId = Turno::where('es_produccion', false)->orderBy('hora_inicio')->value('id');
        $this->resetValidation();

        Flux::modal('recojo-form')->show();
    }

    public function editar(int $recojoId): void
    {
        $recojo = Recojo::findOrFail($recojoId);

        $this->recojoEditId = $recojo->id;
        $this->proveedorId = $recojo->proveedor_id;
        $this->litros = (string) $recojo->litros;
        $this->estadoLeche = $recojo->estado_leche;
        $this->estadoOtro = $recojo->estado_otro ?? '';
        $this->turnoId = $recojo->turno_id;
        $this->resetValidation();

        Flux::modal('recojo-form')->show();
    }

    private function precioVigenteActual(): float
    {
        return (float) (PrecioLeche::vigente()?->precio_litro ?? 0);
    }

    public function guardar(): void
    {
        $proveedor = $this->proveedorSeleccionado;

        abort_if(! $proveedor, 404);
        abort_if($proveedor->bloqueado, 403, 'Este proveedor está bloqueado y no puede entregar leche.');

        $data = $this->validate([
            'litros' => 'required|numeric|min:0.01',
            'estadoLeche' => 'required|string',
            'estadoOtro' => $this->estadoLeche === 'otro' ? 'required|string|max:255' : 'nullable|string|max:255',
            'turnoId' => 'required|exists:turnos,id',
        ]);

        if ($this->recojoEditId) {
            $recojo = Recojo::findOrFail($this->recojoEditId);

            $recojo->update([
                'turno_id' => $data['turnoId'],
                'litros' => $data['litros'],
                'litros_entregados' => $data['litros'],
                'estado_leche' => $data['estadoLeche'],
                'estado_otro' => $data['estadoOtro'] ?? '',
                'total' => round((float) $data['litros'] * (float) $recojo->precio_litro, 2),
            ]);

            Flux::toast('Recojo actualizado correctamente.', variant: 'success');
        } else {
            $precio = $this->precioVigenteActual();

            Recojo::create([
                'proveedor_id' => $proveedor->id,
                'acopiador_id' => auth()->user()->acopiador->id,
                'turno_id' => $data['turnoId'],
                'fecha' => now()->toDateString(),
                'litros' => $data['litros'],
                'litros_entregados' => $data['litros'],
                'estado_leche' => $data['estadoLeche'],
                'estado_otro' => $data['estadoOtro'] ?? '',
                'precio_litro' => $precio,
                'total' => round((float) $data['litros'] * $precio, 2),
            ]);

            Flux::toast('Recojo registrado correctamente.', variant: 'success');
        }

        Flux::modal('recojo-form')->close();
        $this->reset(['proveedorId', 'recojoEditId', 'litros', 'estadoOtro']);
    }
};
?>

<div class="flex flex-col gap-6">
    <x-ui.titulo titulo="Recojo de ruta">{{ $acopiador?->ruta_nombre ?? 'Sin ruta asignada' }}</x-ui.titulo>

    <div class="overflow-x-auto rounded-xl border border-huata-verde-100 px-3 dark:border-zinc-700 huata-cuadro">
        <flux:table>
            <flux:table.columns class="bg-white/80 dark:bg-zinc-800/80">
                <flux:table.column>Nombres</flux:table.column>
                <flux:table.column>Apellidos</flux:table.column>
                <flux:table.column>DNI/RUC</flux:table.column>
                <flux:table.column>Celular</flux:table.column>
                <flux:table.column>Ubicación</flux:table.column>
                <flux:table.column>Fecha</flux:table.column>
                <flux:table.column>Litros recogidos hoy</flux:table.column>
                <flux:table.column>Estado</flux:table.column>
                <flux:table.column class="w-px whitespace-nowrap">Acciones</flux:table.column>
            </flux:table.columns>
            <flux:table.rows>
                @forelse ($proveedores as $proveedor)
                    <flux:table.row wire:key="proveedor-{{ $proveedor->id }}">
                        <flux:table.cell class="font-medium">{{ $proveedor->user->nombres }}</flux:table.cell>
                        <flux:table.cell>{{ $proveedor->user->apellidos }}</flux:table.cell>
                        <flux:table.cell>{{ $proveedor->user->documento ?? '—' }}</flux:table.cell>
                        <flux:table.cell>{{ $proveedor->user->celular ?? '—' }}</flux:table.cell>
                        <flux:table.cell class="text-lg font-bold text-huata-verde-800 dark:text-huata-verde-200">
                            {{ $proveedor->finca_ubicacion ?: 'Sin registrar' }}
                        </flux:table.cell>
                        <flux:table.cell>
                            {{ $ultimoRecojoPorProveedor->get($proveedor->id)?->created_at?->format('d/m/Y H:i') ?? now()->format('d/m/Y H:i') }}
                        </flux:table.cell>
                        <flux:table.cell>
                            {{ number_format((float) ($litrosHoyPorProveedor[$proveedor->id] ?? 0), 2) }} L
                        </flux:table.cell>
                        <flux:table.cell>
                            @if ($proveedor->bloqueado)
                                <flux:badge color="red" size="sm">Bloqueado por calidad</flux:badge>
                            @elseif ($recogidosHoyIds->contains($proveedor->id))
                                <flux:badge color="green" size="sm">Ya recogido hoy</flux:badge>
                            @else
                                <flux:badge color="amber" size="sm">Falta recoger</flux:badge>
                            @endif
                        </flux:table.cell>
                        <flux:table.cell>
                            <div class="flex gap-2">
                                <flux:button
                                    size="sm"
                                    variant="primary"
                                    :disabled="$proveedor->bloqueado"
                                    wire:click="seleccionar({{ $proveedor->id }})"
                                >
                                    Registrar recojo
                                </flux:button>
                                @if ($ultimoRecojoPorProveedor->has($proveedor->id))
                                    <flux:button
                                        size="sm"
                                        variant="ghost"
                                        icon="pencil"
                                        wire:click="editar({{ $ultimoRecojoPorProveedor[$proveedor->id]->id }})"
                                    />
                                @endif
                            </div>
                        </flux:table.cell>
                    </flux:table.row>
                @empty
                    <flux:table.row>
                        <flux:table.cell colspan="9" class="text-center text-zinc-500">No tienes proveedores asignados todavía.</flux:table.cell>
                    </flux:table.row>
                @endforelse
            </flux:table.rows>
        </flux:table>
    </div>

    <flux:modal name="recojo-form" class="md:w-96">
        @if ($this->proveedorSeleccionado)
            @if ($this->proveedorSeleccionado->bloqueado)
                <div class="flex flex-col gap-3">
                    <flux:heading size="lg">Proveedor bloqueado</flux:heading>
                    <flux:text>
                        {{ $this->proveedorSeleccionado->user->nombreCompleto() }} tiene {{ $this->proveedorSeleccionado->alteraciones }}
                        alteraciones de calidad registradas y está bloqueado para entregar leche. Comunícate con planta
                        para desbloquearlo.
                    </flux:text>
                    <div class="flex justify-end">
                        <flux:modal.close><flux:button variant="primary">Entendido</flux:button></flux:modal.close>
                    </div>
                </div>
            @else
                <form wire:submit="guardar" class="flex flex-col gap-6">
                    <div>
                        <flux:heading size="lg">{{ $recojoEditId ? 'Editar recojo' : 'Registrar recojo' }}</flux:heading>
                        <flux:subheading>{{ $this->proveedorSeleccionado->user->nombreCompleto() }}</flux:subheading>
                    </div>

                    <flux:input label="Litros" type="number" step="0.01" min="0" wire:model.live="litros" />

                    @if ($turnos->count() > 1)
                        <flux:select label="Turno" wire:model="turnoId">
                            @foreach ($turnos as $turno)
                                <flux:select.option value="{{ $turno->id }}">{{ $turno->nombre }} ({{ $turno->hora_inicio }} - {{ $turno->hora_fin }})</flux:select.option>
                            @endforeach
                        </flux:select>
                    @endif

                    <flux:select label="Estado de la leche" wire:model.live="estadoLeche">
                        @foreach ($estados as $estado)
                            <flux:select.option value="{{ $estado['value'] }}">{{ $estado['label'] }}</flux:select.option>
                        @endforeach
                    </flux:select>

                    @if ($estadoLeche === 'otro')
                        <flux:input label="Describe el estado" wire:model="estadoOtro" />
                    @endif

                    <div class="rounded-lg bg-huata-verde-50 p-3 text-center dark:bg-huata-verde-900/30">
                        <flux:text>Litros que estás registrando</flux:text>
                        <p class="text-2xl font-bold text-huata-verde-700">{{ number_format(is_numeric($litros) ? (float) $litros : 0, 2) }} L</p>
                    </div>

                    <div class="flex justify-end gap-2">
                        <flux:modal.close><flux:button variant="ghost">Cancelar</flux:button></flux:modal.close>
                        <flux:button type="submit" variant="primary">Guardar</flux:button>
                    </div>
                </form>
            @endif
        @endif
    </flux:modal>
</div>

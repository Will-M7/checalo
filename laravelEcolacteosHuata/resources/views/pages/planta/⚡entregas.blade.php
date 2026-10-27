<?php

use App\Models\Acopiador;
use App\Models\EntregaPlanta;
use App\Models\Recojo;
use App\Models\ResultadoLaboratorio;
use Flux\Flux;
use Livewire\Component;

new class extends Component
{
    public string $fecha = '';

    public ?int $recojoEditId = null;

    public string $recojoEditLitros = '';

    public ?int $entregaEditId = null;

    public string $entregaEditLitros = '';

    public ?int $acopiadorDetalleId = null;

    public function mount(): void
    {
        $this->fecha = now()->toDateString();
    }

    public function with(): array
    {
        return [
            'acopiadores' => Acopiador::with(['user', 'proveedores.user'])->orderBy('id')->get(),
            'recojosPorProveedor' => Recojo::with('turno')
                ->whereDate('fecha', $this->fecha)
                ->get()
                ->groupBy('proveedor_id'),
            'entregasPorAcopiador' => EntregaPlanta::query()
                ->whereDate('fecha', $this->fecha)
                ->get()
                ->keyBy('acopiador_id'),
            // Qué acopiadores ya tienen al menos un resultado de laboratorio registrado ese día:
            // recién ahí el botón "Enviar laboratorio" deja de estar pendiente.
            'acopiadoresConLaboratorioHoy' => ResultadoLaboratorio::whereDate('fecha', $this->fecha)
                ->with('proveedor')
                ->get()
                ->pluck('proveedor.acopiador_id')
                ->unique(),
        ];
    }

    public function verDetalleAcopiador(int $id): void
    {
        $this->acopiadorDetalleId = $id;
        Flux::modal('detalle-acopiador')->show();
    }

    public function editarRecojo(int $id): void
    {
        $recojo = Recojo::findOrFail($id);
        $this->recojoEditId = $recojo->id;
        $this->recojoEditLitros = (string) $recojo->litros;
        $this->resetValidation();
        Flux::modal('recojo-litros-form')->show();
    }

    public function guardarRecojoLitros(): void
    {
        $data = $this->validate([
            'recojoEditLitros' => 'required|numeric|min:0',
        ]);

        $recojo = Recojo::findOrFail($this->recojoEditId);
        $litros = (float) $data['recojoEditLitros'];

        $recojo->update([
            'litros' => $litros,
            'total' => round($litros * (float) $recojo->precio_litro, 2),
        ]);

        $this->recalcularEntrega($recojo->acopiador_id, $recojo->fecha->toDateString());

        Flux::modal('recojo-litros-form')->close();
        Flux::toast('Litros del proveedor actualizados.', variant: 'success');
    }

    public function editarEntrega(int $id): void
    {
        $entrega = EntregaPlanta::findOrFail($id);
        $this->entregaEditId = $entrega->id;
        $this->entregaEditLitros = (string) $entrega->litros_total;
        $this->resetValidation();
        Flux::modal('entrega-litros-form')->show();
    }

    public function guardarEntregaLitros(): void
    {
        $data = $this->validate([
            'entregaEditLitros' => 'required|numeric|min:0',
        ]);

        EntregaPlanta::findOrFail($this->entregaEditId)->update([
            'litros_total' => (float) $data['entregaEditLitros'],
        ]);

        Flux::modal('entrega-litros-form')->close();
        Flux::toast('Litros entregados por el acopiador actualizados.', variant: 'success');
    }

    /**
     * Recalcula el total entregado por el acopiador ese día a partir de sus recojos vigentes,
     * para que la corrección de un litraje se refleje también en la entrega ya registrada.
     */
    protected function recalcularEntrega(int $acopiadorId, string $fecha): void
    {
        $entrega = EntregaPlanta::where('acopiador_id', $acopiadorId)->whereDate('fecha', $fecha)->first();

        if ($entrega) {
            $entrega->update([
                'litros_total' => Recojo::where('acopiador_id', $acopiadorId)->whereDate('fecha', $fecha)->sum('litros'),
            ]);
        }
    }
};
?>

<div class="flex flex-col gap-6">
    <div class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <x-ui.titulo titulo="Entregas de acopiadores">Leche recibida de cada proveedor, agrupada automáticamente por acopiador.</x-ui.titulo>

        <flux:input type="date" wire:model.live="fecha" class="max-w-xs" />
    </div>

    <div class="max-h-[36rem] overflow-x-auto overflow-y-auto rounded-xl border border-huata-verde-100 px-3 dark:border-zinc-700 huata-cuadro">
        <flux:table>
            <flux:table.columns sticky class="bg-white/80 dark:bg-zinc-800/80">
                <flux:table.column>Acopiador</flux:table.column>
                <flux:table.column>Entregado a planta</flux:table.column>
                <flux:table.column>Laboratorio</flux:table.column>
                <flux:table.column>Proveedores</flux:table.column>
                <flux:table.column>Recojos de hoy</flux:table.column>
                <flux:table.column class="w-px whitespace-nowrap">Acciones</flux:table.column>
            </flux:table.columns>
            <flux:table.rows>
                @forelse ($acopiadores as $acopiador)
                    @php
                        $entrega = $entregasPorAcopiador->get($acopiador->id);
                        $enviadoLaboratorio = $acopiadoresConLaboratorioHoy->contains($acopiador->id);
                        $recojosAcopiador = $acopiador->proveedores->sum(fn ($p) => $recojosPorProveedor->get($p->id, collect())->count());
                    @endphp
                    <flux:table.row wire:key="acopiador-{{ $acopiador->id }}">
                        <flux:table.cell class="font-medium">{{ $acopiador->user->nombreCompleto() }}</flux:table.cell>
                        <flux:table.cell>
                            @if ($entrega)
                                <flux:badge color="green" size="sm">{{ number_format((float) $entrega->litros_total, 2) }} L</flux:badge>
                            @else
                                <flux:badge color="zinc" size="sm">Sin entrega</flux:badge>
                            @endif
                        </flux:table.cell>
                        <flux:table.cell>
                            <flux:badge :color="$enviadoLaboratorio ? 'green' : 'zinc'" size="sm">
                                Laboratorio: {{ $enviadoLaboratorio ? 'Sí' : 'Pendiente' }}
                            </flux:badge>
                        </flux:table.cell>
                        <flux:table.cell>{{ $acopiador->proveedores->count() }}</flux:table.cell>
                        <flux:table.cell>{{ $recojosAcopiador }}</flux:table.cell>
                        <flux:table.cell class="whitespace-nowrap">
                            <div class="flex flex-nowrap items-center gap-2">
                                <flux:modal.trigger name="detalle-acopiador">
                                    <flux:button size="sm" variant="primary" icon="table-cells" wire:click="verDetalleAcopiador({{ $acopiador->id }})">
                                        Ver detalle
                                    </flux:button>
                                </flux:modal.trigger>
                                @if ($entrega)
                                    <flux:button size="sm" variant="ghost" icon="pencil" wire:click="editarEntrega({{ $entrega->id }})" />
                                @endif
                            </div>
                        </flux:table.cell>
                    </flux:table.row>
                @empty
                    <flux:table.row>
                        <flux:table.cell colspan="6" class="text-center text-zinc-500">No hay acopiadores registrados.</flux:table.cell>
                    </flux:table.row>
                @endforelse
            </flux:table.rows>
        </flux:table>
    </div>

    <flux:modal name="detalle-acopiador" class="md:w-[52rem]">
        @php $acopiadorDetalle = $acopiadores->firstWhere('id', $acopiadorDetalleId); @endphp
        <div class="flex flex-col gap-5">
            <flux:heading size="lg">{{ $acopiadorDetalle?->user?->nombreCompleto() }}</flux:heading>

            <div class="max-h-96 overflow-x-auto overflow-y-auto rounded-xl border border-huata-verde-100 px-3 dark:border-zinc-700 huata-cuadro">
                <flux:table>
                    <flux:table.columns sticky class="bg-white/80 dark:bg-zinc-800/80">
                        <flux:table.column>Proveedor</flux:table.column>
                        <flux:table.column>Fecha</flux:table.column>
                        <flux:table.column>Turno</flux:table.column>
                        <flux:table.column>Litros</flux:table.column>
                        <flux:table.column>Estado de la leche</flux:table.column>
                        <flux:table.column>Total</flux:table.column>
                        <flux:table.column>Entregado a planta</flux:table.column>
                        <flux:table.column class="w-px whitespace-nowrap">Acciones</flux:table.column>
                    </flux:table.columns>
                    <flux:table.rows>
                        @forelse ($acopiadorDetalle?->proveedores ?? [] as $proveedor)
                            @php $recojos = $recojosPorProveedor->get($proveedor->id, collect()); @endphp
                            @forelse ($recojos as $recojo)
                                <flux:table.row wire:key="recojo-{{ $recojo->id }}">
                                    <flux:table.cell class="font-medium">{{ $proveedor->user->nombreCompleto() }}</flux:table.cell>
                                    <flux:table.cell>{{ $recojo->created_at->format('d/m/Y H:i') }}</flux:table.cell>
                                    <flux:table.cell>{{ $recojo->turno->nombre }}</flux:table.cell>
                                    <flux:table.cell>{{ number_format((float) $recojo->litros, 2) }} L</flux:table.cell>
                                    <flux:table.cell>
                                        <flux:badge :color="$recojo->estado_leche === 'normal' ? 'green' : 'amber'" size="sm">
                                            {{ ucfirst($recojo->estado_otro ?: $recojo->estado_leche) }}
                                        </flux:badge>
                                    </flux:table.cell>
                                    <flux:table.cell>S/ {{ number_format((float) $recojo->total, 2) }}</flux:table.cell>
                                    <flux:table.cell>
                                        <flux:badge :color="$recojo->entregado ? 'green' : 'zinc'" size="sm">
                                            {{ $recojo->entregado ? 'Sí' : 'Pendiente' }}
                                        </flux:badge>
                                    </flux:table.cell>
                                    <flux:table.cell>
                                        <flux:button size="sm" variant="ghost" icon="pencil" wire:click="editarRecojo({{ $recojo->id }})" />
                                    </flux:table.cell>
                                </flux:table.row>
                            @empty
                                <flux:table.row wire:key="sin-recojo-{{ $proveedor->id }}">
                                    <flux:table.cell class="font-medium text-zinc-500">{{ $proveedor->user->nombreCompleto() }}</flux:table.cell>
                                    <flux:table.cell colspan="7" class="text-zinc-400">Sin recojo registrado en esta fecha.</flux:table.cell>
                                </flux:table.row>
                            @endforelse
                        @empty
                            <flux:table.row>
                                <flux:table.cell colspan="8" class="text-center text-zinc-500">Este acopiador no tiene proveedores asignados.</flux:table.cell>
                            </flux:table.row>
                        @endforelse
                    </flux:table.rows>
                </flux:table>
            </div>

            <div class="flex justify-end">
                <flux:modal.close>
                    <flux:button variant="ghost">Cerrar</flux:button>
                </flux:modal.close>
            </div>
        </div>
    </flux:modal>

    <flux:modal name="recojo-litros-form" class="md:w-80">
        <form wire:submit="guardarRecojoLitros" class="flex flex-col gap-6">
            <div>
                <flux:heading size="lg">Corregir litros del proveedor</flux:heading>
                <flux:subheading>Verifica que la cantidad de leche que llegó coincida con lo registrado.</flux:subheading>
            </div>
            <flux:input type="number" step="0.01" min="0" label="Litros" wire:model="recojoEditLitros" />
            <div class="flex justify-end gap-2">
                <flux:modal.close><flux:button variant="ghost">Cancelar</flux:button></flux:modal.close>
                <flux:button type="submit" variant="primary">Guardar</flux:button>
            </div>
        </form>
    </flux:modal>

    <flux:modal name="entrega-litros-form" class="md:w-80">
        <form wire:submit="guardarEntregaLitros" class="flex flex-col gap-6">
            <div>
                <flux:heading size="lg">Corregir litros del acopiador</flux:heading>
                <flux:subheading>Verifica el total entregado a planta por el acopiador.</flux:subheading>
            </div>
            <flux:input type="number" step="0.01" min="0" label="Litros totales" wire:model="entregaEditLitros" />
            <div class="flex justify-end gap-2">
                <flux:modal.close><flux:button variant="ghost">Cancelar</flux:button></flux:modal.close>
                <flux:button type="submit" variant="primary">Guardar</flux:button>
            </div>
        </form>
    </flux:modal>
</div>

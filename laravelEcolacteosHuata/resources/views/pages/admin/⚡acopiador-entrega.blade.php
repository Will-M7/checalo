<?php

use App\Models\Acopiador;
use App\Models\EntregaPlanta;
use Livewire\Component;

new class extends Component
{
    public string $fecha = '';

    public function mount(): void
    {
        $this->fecha = now()->toDateString();
    }

    public function with(): array
    {
        return [
            'acopiadores' => Acopiador::with('user')->orderBy('id')->get(),
            'entregasPorAcopiador' => EntregaPlanta::whereDate('fecha', $this->fecha)->get()->keyBy('acopiador_id'),
        ];
    }
};
?>

<div class="flex flex-col gap-6">
    <div class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <x-ui.titulo titulo="Entrega a planta">Cierre diario de cada acopiador, individualmente.</x-ui.titulo>
        <flux:input type="date" wire:model.live="fecha" class="max-w-xs" />
    </div>

    <div class="max-h-[32rem] overflow-x-auto overflow-y-auto rounded-xl border border-huata-verde-100 dark:border-zinc-700">
        <flux:table>
            <flux:table.columns sticky class="bg-white dark:bg-zinc-800">
                <flux:table.column>Acopiador</flux:table.column>
                <flux:table.column>Ruta</flux:table.column>
                <flux:table.column>Guía</flux:table.column>
                <flux:table.column>Litros entregados</flux:table.column>
                <flux:table.column>No recogidos</flux:table.column>
                <flux:table.column>Estado</flux:table.column>
            </flux:table.columns>
            <flux:table.rows>
                @forelse ($acopiadores as $acopiador)
                    @php $entrega = $entregasPorAcopiador->get($acopiador->id); @endphp
                    <flux:table.row wire:key="entrega-acopiador-{{ $acopiador->id }}">
                        <flux:table.cell class="font-medium">{{ $acopiador->user->nombreCompleto() }}</flux:table.cell>
                        <flux:table.cell>{{ $acopiador->ruta_nombre }}</flux:table.cell>
                        <flux:table.cell>{{ $entrega?->guia ?? '—' }}</flux:table.cell>
                        <flux:table.cell>{{ $entrega ? number_format((float) $entrega->litros_total, 2).' L' : '—' }}</flux:table.cell>
                        <flux:table.cell>{{ $entrega?->proveedores_no_recogidos ?? '—' }}</flux:table.cell>
                        <flux:table.cell>
                            @if ($entrega)
                                <flux:badge color="green" size="sm">Entregado</flux:badge>
                            @else
                                <flux:badge color="zinc" size="sm">Sin entrega registrada</flux:badge>
                            @endif
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
</div>

<?php

use App\Models\Proveedor;
use App\Models\Recojo;
use Livewire\Component;

new class extends Component
{
    public function with(): array
    {
        $desde = now()->subDays(6)->startOfDay();
        $hasta = now()->endOfDay();

        return [
            'desde' => $desde,
            'hasta' => $hasta,
            'proveedores' => Proveedor::with('user')->orderBy('id')->get(),
            'recojosPorProveedor' => Recojo::with('turno')
                ->whereBetween('fecha', [$desde->toDateString(), $hasta->toDateString()])
                ->orderByDesc('fecha')
                ->get()
                ->groupBy('proveedor_id'),
        ];
    }
};
?>

<div class="flex flex-col gap-6">
    <x-ui.titulo titulo="Entregas de proveedores">Historial de cada proveedor, individualmente &middot; {{ $desde->format('d/m/Y') }} — {{ $hasta->format('d/m/Y') }}</x-ui.titulo>

    @forelse ($proveedores as $proveedor)
        @php $recojos = $recojosPorProveedor->get($proveedor->id, collect()); @endphp
        <div class="rounded-xl border border-huata-verde-100 dark:border-zinc-700 huata-cuadro">
            <div class="flex flex-wrap items-center justify-between gap-3 border-b border-huata-verde-100 px-4 py-3 dark:border-zinc-700">
                <div>
                    <flux:heading size="lg">{{ $proveedor->user->nombreCompleto() }}</flux:heading>
                    <flux:text class="text-sm text-zinc-500">{{ $proveedor->finca_ubicacion ?: 'Sin ubicación registrada' }}</flux:text>
                </div>
                <flux:badge color="green" size="sm">{{ number_format((float) $recojos->sum('litros'), 2) }} L en el período</flux:badge>
            </div>

            <div class="max-h-72 overflow-x-auto overflow-y-auto">
                <flux:table>
                    <flux:table.columns sticky class="bg-white dark:bg-zinc-800">
                        <flux:table.column>Fecha</flux:table.column>
                        <flux:table.column>Turno</flux:table.column>
                        <flux:table.column>Litros</flux:table.column>
                        <flux:table.column>Total</flux:table.column>
                        <flux:table.column>Pago</flux:table.column>
                    </flux:table.columns>
                    <flux:table.rows>
                        @forelse ($recojos as $recojo)
                            <flux:table.row wire:key="prov-entrega-{{ $recojo->id }}">
                                <flux:table.cell>{{ $recojo->fecha->format('d/m/Y') }}</flux:table.cell>
                                <flux:table.cell>{{ $recojo->turno?->nombre ?? '—' }}</flux:table.cell>
                                <flux:table.cell>{{ number_format((float) $recojo->litros, 2) }} L</flux:table.cell>
                                <flux:table.cell>S/ {{ number_format((float) $recojo->total, 2) }}</flux:table.cell>
                                <flux:table.cell>
                                    <flux:badge :color="$recojo->pagado ? 'green' : 'amber'" size="sm">
                                        {{ $recojo->pagado ? 'Cobrado' : 'Pendiente' }}
                                    </flux:badge>
                                </flux:table.cell>
                            </flux:table.row>
                        @empty
                            <flux:table.row>
                                <flux:table.cell colspan="5" class="text-center text-zinc-500">Sin entregas en este período.</flux:table.cell>
                            </flux:table.row>
                        @endforelse
                    </flux:table.rows>
                </flux:table>
            </div>
        </div>
    @empty
        <div class="rounded-xl border border-huata-verde-100 p-6 text-center text-zinc-500 dark:border-zinc-700 huata-cuadro">
            No hay proveedores registrados.
        </div>
    @endforelse
</div>

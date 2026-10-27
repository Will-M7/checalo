<?php

use App\Models\Proveedor;
use App\Models\ResultadoLaboratorio;
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
            'resultadosPorProveedor' => ResultadoLaboratorio::with('turno')
                ->whereBetween('fecha', [$desde->toDateString(), $hasta->toDateString()])
                ->orderByDesc('fecha')
                ->get()
                ->groupBy('proveedor_id'),
        ];
    }
};
?>

<div class="flex flex-col gap-6">
    <x-ui.titulo titulo="Laboratorio">Calidad de la leche de cada proveedor, individualmente &middot; {{ $desde->format('d/m/Y') }} — {{ $hasta->format('d/m/Y') }}</x-ui.titulo>

    @forelse ($proveedores as $proveedor)
        @php $resultados = $resultadosPorProveedor->get($proveedor->id, collect()); @endphp
        <div class="rounded-xl border border-huata-verde-100 dark:border-zinc-700 huata-cuadro">
            <div class="flex flex-wrap items-center justify-between gap-3 border-b border-huata-verde-100 px-4 py-3 dark:border-zinc-700">
                <div>
                    <flux:heading size="lg">{{ $proveedor->user->nombreCompleto() }}</flux:heading>
                    <flux:text class="text-sm text-zinc-500">{{ $proveedor->finca_ubicacion ?: 'Sin ubicación registrada' }}</flux:text>
                </div>
                @php $noConformes = $resultados->where('conforme', false)->count(); @endphp
                <flux:badge :color="$noConformes > 0 ? 'red' : 'green'" size="sm">
                    {{ $noConformes > 0 ? $noConformes.' no conforme(s)' : 'Todo conforme' }}
                </flux:badge>
            </div>

            <div class="max-h-72 overflow-x-auto overflow-y-auto">
                <flux:table>
                    <flux:table.columns sticky class="bg-white dark:bg-zinc-800">
                        <flux:table.column>Fecha</flux:table.column>
                        <flux:table.column>Turno</flux:table.column>
                        <flux:table.column>Litros</flux:table.column>
                        <flux:table.column>Grasa</flux:table.column>
                        <flux:table.column>Densidad</flux:table.column>
                        <flux:table.column>pH</flux:table.column>
                        <flux:table.column>Resultado</flux:table.column>
                        <flux:table.column>Conforme</flux:table.column>
                    </flux:table.columns>
                    <flux:table.rows>
                        @forelse ($resultados as $resultado)
                            <flux:table.row wire:key="lab-{{ $resultado->id }}">
                                <flux:table.cell>{{ $resultado->fecha->format('d/m/Y') }}</flux:table.cell>
                                <flux:table.cell>{{ $resultado->turno?->nombre ?? '—' }}</flux:table.cell>
                                <flux:table.cell>{{ number_format((float) $resultado->litros, 2) }}</flux:table.cell>
                                <flux:table.cell>{{ $resultado->grasa !== null ? number_format((float) $resultado->grasa, 2) : '—' }}</flux:table.cell>
                                <flux:table.cell>{{ $resultado->densidad !== null ? number_format((float) $resultado->densidad, 3) : '—' }}</flux:table.cell>
                                <flux:table.cell>{{ $resultado->ph !== null ? number_format((float) $resultado->ph, 2) : '—' }}</flux:table.cell>
                                <flux:table.cell>{{ ucfirst($resultado->resultado) }}</flux:table.cell>
                                <flux:table.cell>
                                    <flux:badge :color="$resultado->conforme ? 'green' : 'red'" size="sm">
                                        {{ $resultado->conforme ? 'Conforme' : 'No conforme' }}
                                    </flux:badge>
                                </flux:table.cell>
                            </flux:table.row>
                        @empty
                            <flux:table.row>
                                <flux:table.cell colspan="8" class="text-center text-zinc-500">Sin resultados en este período.</flux:table.cell>
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

<?php

use App\Enums\EstadoProduccion;
use App\Models\Produccion;
use Livewire\Component;

new class extends Component
{
    public string $busqueda = '';

    public function with(): array
    {
        $producciones = Produccion::query()
            ->with('receta.detalles.insumo')
            ->where('estado', EstadoProduccion::Completada)
            ->when($this->busqueda, fn ($q) => $q->whereHas('receta', fn ($r) => $r->where('nombre', 'like', "%{$this->busqueda}%")))
            ->orderByDesc('fecha')
            ->get();

        return [
            'producciones' => $producciones,
        ];
    }
};
?>

<div class="flex flex-col gap-6">
    <div class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <x-ui.titulo titulo="Productos">Todo lo que ya se terminó de producir, listo para vender.</x-ui.titulo>
        <flux:input
            wire:model.live.debounce.300ms="busqueda"
            icon="magnifying-glass"
            placeholder="Buscar por receta..."
            class="max-w-sm"
        />
    </div>

    <div class="overflow-x-auto rounded-xl border border-huata-verde-100 px-3 dark:border-zinc-700 huata-cuadro">
        <flux:table>
            <flux:table.columns class="bg-white/80 dark:bg-zinc-800/80">
                <flux:table.column>Nombre</flux:table.column>
                <flux:table.column>Cantidad</flux:table.column>
                <flux:table.column>Vendido</flux:table.column>
                <flux:table.column>Precio unidad</flux:table.column>
                <flux:table.column>Precio total</flux:table.column>
                <flux:table.column class="w-px whitespace-nowrap">Estado</flux:table.column>
            </flux:table.columns>
            <flux:table.rows>
                @forelse ($producciones as $produccion)
                    @php $precioUnidad = $produccion->receta->precioVenta(); $disponible = $produccion->cantidadDisponible(); @endphp
                    <flux:table.row wire:key="prod-{{ $produccion->id }}">
                        <flux:table.cell class="font-medium">{{ $produccion->nombre ?? $produccion->receta->nombre }}</flux:table.cell>
                        <flux:table.cell>
                            <flux:badge :color="$disponible > 0 ? 'green' : 'zinc'" size="sm">
                                {{ number_format($disponible, 2) }}
                            </flux:badge>
                        </flux:table.cell>
                        <flux:table.cell>{{ number_format((float) $produccion->cantidad_vendida, 2) }}</flux:table.cell>
                        <flux:table.cell>S/ {{ number_format($precioUnidad, 2) }}</flux:table.cell>
                        <flux:table.cell>S/ {{ number_format($precioUnidad * $disponible, 2) }}</flux:table.cell>
                        <flux:table.cell>
                            <flux:badge color="green" size="sm">{{ $produccion->estado->label() }}</flux:badge>
                        </flux:table.cell>
                    </flux:table.row>
                @empty
                    <flux:table.row><flux:table.cell colspan="6" class="text-center text-zinc-500">No se encontraron producciones.</flux:table.cell></flux:table.row>
                @endforelse
            </flux:table.rows>
        </flux:table>
    </div>
</div>

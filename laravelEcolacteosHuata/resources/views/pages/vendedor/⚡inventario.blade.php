<?php

use App\Enums\EstadoProduccion;
use App\Models\Produccion;
use Livewire\Component;

new class extends Component
{
    use \App\Livewire\Concerns\FiltrosListado;
    public string $busqueda = '';

    public function with(): array
    {
        $producciones = Produccion::query()
                ->when($this->filtro('stock') === 'disponible', fn ($q) => $q->whereRaw('(cantidad_producida - cantidad_vendida) > 0'))
                ->when($this->filtro('stock') === 'bajo', fn ($q) => $q->whereRaw('(cantidad_producida - cantidad_vendida) > 0 AND (cantidad_producida - cantidad_vendida) <= 5'))
                ->when($this->filtro('stock') === 'agotado', fn ($q) => $q->whereRaw('(cantidad_producida - cantidad_vendida) <= 0'))
                ->when($this->filtro('desde'), fn ($q, $v) => $q->whereDate('fecha', '>=', $v))
                ->when($this->filtro('hasta'), fn ($q, $v) => $q->whereDate('fecha', '<=', $v))
                
            ->where('estado', EstadoProduccion::Completada)
            ->with('receta.detalles.insumo')
            ->when($this->busqueda, fn ($q) => $q->where(
                fn ($q2) => $q2->where('nombre', 'like', "%{$this->busqueda}%")
                    ->orWhereHas('receta', fn ($r) => $r->where('nombre', 'like', "%{$this->busqueda}%"))
            ))
            ->orderByDesc('created_at')->orderByDesc('id')
            ->get();

        $filas = $producciones->map(fn (Produccion $p) => [
            'id' => $p->id,
            'nombre' => $p->nombre ?? $p->receta->nombre,
            'precioUnidad' => $p->receta->precioVenta(),
            'disponible' => $p->cantidadDisponible(),
        ]);

        return [
            'filas' => $filas,
            'valorTotal' => $filas->sum(fn (array $f) => $f['disponible'] * $f['precioUnidad']),
            'unidadesTotal' => $filas->sum('disponible'),
            'sinStock' => $filas->where('disponible', '<=', 0)->count(),
        ];
    }
};
?>

<div class="flex flex-col gap-6">
    <x-ui.titulo titulo="Inventario">Cantidad disponible de lo producido, lista para la venta.</x-ui.titulo>

    <div class="grid gap-4 sm:grid-cols-3">
        <div class="rounded-xl border border-huata-verde-100 p-5 dark:border-zinc-700 huata-cuadro">
            <flux:text>Valor del inventario</flux:text>
            <p class="mt-1 text-2xl font-bold text-huata-verde-700">S/ {{ number_format((float) $valorTotal, 2) }}</p>
        </div>
        <div class="rounded-xl border border-huata-verde-100 p-5 dark:border-zinc-700 huata-cuadro">
            <flux:text>Unidades disponibles</flux:text>
            <p class="mt-1 text-2xl font-bold text-huata-verde-700">{{ number_format((float) $unidadesTotal, 2) }}</p>
        </div>
        <div class="rounded-xl border border-huata-verde-100 p-5 dark:border-zinc-700 huata-cuadro">
            <flux:text>Sin stock</flux:text>
            <p class="mt-1 text-2xl font-bold {{ $sinStock > 0 ? 'text-huata-rojo-600' : 'text-huata-verde-700' }}">{{ $sinStock }}</p>
        </div>
    </div>

    <div class="flex flex-wrap items-center gap-3"><flux:input
        wire:model.live.debounce.300ms="busqueda"
        icon="magnifying-glass"
        placeholder="Buscar por receta..."
        class="max-w-sm"
    /><x-ui.filtros :activos="$filtros"><flux:select label="Disponibilidad" wire:model.live="filtros.stock"><flux:select.option value="">Todos</flux:select.option><flux:select.option value="disponible">Con stock</flux:select.option><flux:select.option value="bajo">Cantidad baja (hasta 5)</flux:select.option><flux:select.option value="agotado">Sin stock</flux:select.option></flux:select><flux:input label="Desde" type="date" wire:model.live="filtros.desde" /><flux:input label="Hasta" type="date" wire:model.live="filtros.hasta" /></x-ui.filtros></div>

    <div class="overflow-x-auto rounded-xl border border-huata-verde-100 px-3 dark:border-zinc-700 huata-cuadro">
        <flux:table>
            <flux:table.columns class="bg-white/80 dark:bg-zinc-800/80">
                <flux:table.column>Producto</flux:table.column>
                <flux:table.column>Precio unidad</flux:table.column>
                <flux:table.column>Disponible</flux:table.column>
                <flux:table.column>Valor en stock</flux:table.column>
                <flux:table.column class="w-px whitespace-nowrap">Estado</flux:table.column>
            </flux:table.columns>
            <flux:table.rows>
                @forelse ($filas as $fila)
                    <flux:table.row wire:key="inv-{{ $fila['id'] }}">
                        <flux:table.cell class="font-medium">{{ $fila['nombre'] }}</flux:table.cell>
                        <flux:table.cell>S/ {{ number_format($fila['precioUnidad'], 2) }}</flux:table.cell>
                        <flux:table.cell>{{ number_format($fila['disponible'], 2) }}</flux:table.cell>
                        <flux:table.cell>S/ {{ number_format($fila['disponible'] * $fila['precioUnidad'], 2) }}</flux:table.cell>
                        <flux:table.cell>
                            @if ($fila['disponible'] <= 0)
                                <flux:badge color="red" size="sm">Sin stock</flux:badge>
                            @elseif ($fila['disponible'] <= 5)
                                <flux:badge color="amber" size="sm">Stock bajo</flux:badge>
                            @else
                                <flux:badge color="green" size="sm">Disponible</flux:badge>
                            @endif
                        </flux:table.cell>
                    </flux:table.row>
                @empty
                    <flux:table.row><flux:table.cell colspan="5" class="text-center text-zinc-500">No se encontraron productos.</flux:table.cell></flux:table.row>
                @endforelse
            </flux:table.rows>
        </flux:table>
    </div>
</div>

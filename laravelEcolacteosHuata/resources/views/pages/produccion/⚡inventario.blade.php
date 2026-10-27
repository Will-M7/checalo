<?php

use App\Models\CompraDetalle;
use App\Models\Insumo;
use Livewire\Component;

new class extends Component
{
    public string $busqueda = '';

    public string $filtroEstado = '';

    /**
     * 0 = sin stock, 1 = stock bajo, 2 = disponible. Se usa para mostrar primero los insumos
     * más urgentes de reponer, en vez de un simple orden alfabético.
     */
    private function prioridad(Insumo $insumo): int
    {
        return match (true) {
            (float) $insumo->stock_actual <= 0 => 0,
            (float) $insumo->stock_actual <= 10 => 1,
            default => 2,
        };
    }

    private function estado(Insumo $insumo): string
    {
        return match ($this->prioridad($insumo)) {
            0 => 'sin_stock',
            1 => 'stock_bajo',
            default => 'disponible',
        };
    }

    public function filtrarPor(string $estado): void
    {
        $this->filtroEstado = $this->filtroEstado === $estado ? '' : $estado;
    }

    /**
     * Última compra registrada de cada insumo (fecha y precio pagado), para comparar de un
     * vistazo contra el costo unitario promedio que se usa en las recetas.
     *
     * @return \Illuminate\Support\Collection<int, CompraDetalle>
     */
    private function ultimasCompras(): \Illuminate\Support\Collection
    {
        return CompraDetalle::with('compra')
            ->orderByDesc('id')
            ->get()
            ->unique('insumo_id')
            ->keyBy('insumo_id');
    }

    public function with(): array
    {
        // Se asegura de que el insumo de leche siempre exista, aunque nunca se haya enviado
        // ningún resultado de laboratorio a producción todavía.
        Insumo::deLeche();

        $insumos = Insumo::query()
            ->with('unidadMedida')
            ->when($this->busqueda, fn ($q) => $q->where('nombre', 'like', "%{$this->busqueda}%"))
            ->get()
            ->sortBy([
                fn (Insumo $a, Insumo $b) => $this->prioridad($a) <=> $this->prioridad($b),
                fn (Insumo $a, Insumo $b) => $b->es_leche <=> $a->es_leche,
                fn (Insumo $a, Insumo $b) => $a->nombre <=> $b->nombre,
            ])
            ->values();

        $sinStock = $insumos->filter(fn (Insumo $i) => $this->estado($i) === 'sin_stock')->count();
        $stockBajo = $insumos->filter(fn (Insumo $i) => $this->estado($i) === 'stock_bajo')->count();

        return [
            'insumos' => $this->filtroEstado
                ? $insumos->filter(fn (Insumo $i) => $this->estado($i) === $this->filtroEstado)->values()
                : $insumos,
            'valorTotal' => $insumos->sum(fn (Insumo $i) => (float) $i->stock_actual * (float) $i->costo_unitario),
            'sinStock' => $sinStock,
            'stockBajo' => $stockBajo,
            'ultimasCompras' => $this->ultimasCompras(),
        ];
    }
};
?>

<div class="flex flex-col gap-6">
    <div class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <x-ui.titulo titulo="Inventario">Stock disponible de insumos para producción, con los más urgentes de reponer primero.</x-ui.titulo>
        <flux:button size="sm" variant="ghost" icon="credit-card" :href="route('produccion.compras')" wire:navigate>
            Historial de compras
        </flux:button>
    </div>

    <div class="grid gap-4 sm:grid-cols-3">
        <div class="rounded-xl border border-huata-verde-100 p-5 dark:border-zinc-700 huata-cuadro">
            <flux:text>Valor del inventario</flux:text>
            <p class="mt-1 text-2xl font-bold text-huata-verde-700">S/ {{ number_format((float) $valorTotal, 2) }}</p>
        </div>
        <button
            type="button"
            wire:click="filtrarPor('stock_bajo')"
            class="rounded-xl border p-5 text-left transition-colors {{ $filtroEstado === 'stock_bajo' ? 'border-huata-amarillo-500 bg-huata-amarillo-400/10' : 'border-huata-verde-100 dark:border-zinc-700 dark:bg-zinc-800' }} huata-cuadro"
        >
            <flux:text>Insumos con stock bajo</flux:text>
            <p class="mt-1 text-2xl font-bold {{ $stockBajo > 0 ? 'text-huata-amarillo-600' : 'text-huata-verde-700' }}">{{ $stockBajo }}</p>
        </button>
        <button
            type="button"
            wire:click="filtrarPor('sin_stock')"
            class="rounded-xl border p-5 text-left transition-colors {{ $filtroEstado === 'sin_stock' ? 'border-huata-rojo-500 bg-huata-rojo-500/10' : 'border-huata-verde-100 dark:border-zinc-700 dark:bg-zinc-800' }} huata-cuadro"
        >
            <flux:text>Insumos sin stock</flux:text>
            <p class="mt-1 text-2xl font-bold {{ $sinStock > 0 ? 'text-huata-rojo-600' : 'text-huata-verde-700' }}">{{ $sinStock }}</p>
        </button>
    </div>

    <div class="flex flex-wrap items-center gap-3">
        <flux:input
            wire:model.live.debounce.300ms="busqueda"
            icon="magnifying-glass"
            placeholder="Buscar insumo..."
            class="max-w-sm"
        />
        @if ($filtroEstado)
            <flux:button size="sm" variant="ghost" icon="x-mark" wire:click="filtrarPor('')">
                Quitar filtro
            </flux:button>
        @endif
    </div>

    <div class="overflow-x-auto rounded-xl border border-huata-verde-100 px-3 dark:border-zinc-700 huata-cuadro">
        <flux:table>
            <flux:table.columns class="bg-white/80 dark:bg-zinc-800/80">
                <flux:table.column>Insumo</flux:table.column>
                <flux:table.column>Unidad</flux:table.column>
                <flux:table.column>Costo unitario</flux:table.column>
                <flux:table.column>Stock actual</flux:table.column>
                <flux:table.column>Valor en stock</flux:table.column>
                <flux:table.column>Última compra</flux:table.column>
                <flux:table.column class="w-px whitespace-nowrap">Estado</flux:table.column>
            </flux:table.columns>
            <flux:table.rows>
                @forelse ($insumos as $insumo)
                    @php $ultimaCompra = $ultimasCompras->get($insumo->id); @endphp
                    <flux:table.row
                        wire:key="inv-{{ $insumo->id }}"
                        class="{{ $insumo->stock_actual <= 0 ? 'bg-huata-rojo-500/5' : '' }}"
                    >
                        <flux:table.cell class="font-medium">
                            {{ $insumo->nombre }}
                            @if ($insumo->es_leche)
                                <flux:badge color="blue" size="sm" class="ml-1">Automático (litraje que llegó a planta)</flux:badge>
                            @endif
                        </flux:table.cell>
                        <flux:table.cell>{{ $insumo->unidadMedida->simbolo }}</flux:table.cell>
                        <flux:table.cell>S/ {{ number_format((float) $insumo->costo_unitario, 4) }}</flux:table.cell>
                        <flux:table.cell>{{ number_format((float) $insumo->stock_actual, 2) }} {{ $insumo->unidadMedida->simbolo }}</flux:table.cell>
                        <flux:table.cell>S/ {{ number_format((float) $insumo->stock_actual * (float) $insumo->costo_unitario, 2) }}</flux:table.cell>
                        <flux:table.cell>
                            @if ($ultimaCompra)
                                <flux:link :href="route('produccion.compras', ['insumo' => $insumo->id])" wire:navigate class="whitespace-nowrap">
                                    {{ $ultimaCompra->compra->created_at->format('d/m/Y') }} — S/ {{ number_format((float) $ultimaCompra->costo_unitario, 4) }}
                                </flux:link>
                            @else
                                <span class="text-zinc-400">Sin compras registradas</span>
                            @endif
                        </flux:table.cell>
                        <flux:table.cell>
                            @if ($insumo->stock_actual <= 0)
                                <flux:badge color="red" size="sm">Sin stock</flux:badge>
                            @elseif ($insumo->stock_actual <= 10)
                                <flux:badge color="amber" size="sm">Stock bajo</flux:badge>
                            @else
                                <flux:badge color="green" size="sm">Disponible</flux:badge>
                            @endif
                        </flux:table.cell>
                    </flux:table.row>
                @empty
                    <flux:table.row><flux:table.cell colspan="7" class="text-center text-zinc-500">No se encontraron insumos.</flux:table.cell></flux:table.row>
                @endforelse
            </flux:table.rows>
        </flux:table>
    </div>
</div>

<?php

use App\Enums\MetodoPago;
use App\Models\Venta;
use Flux\Flux;
use Illuminate\Database\Eloquent\Builder;
use Livewire\Component;
use Livewire\WithPagination;

new class extends Component
{
    use \App\Livewire\Concerns\FiltrosListado;
    use WithPagination;

    public string $desde = '';

    public string $hasta = '';

    public string $busqueda = '';

    public string $metodoPago = '';

    public function limpiarFiltros(): void
    {
        $this->filtros = []; $this->busqueda = ''; $this->metodoPago = '';
        $this->mount(); $this->resetPage();
    }

    public function mount(): void
    {
        $this->desde = now()->subDays(30)->toDateString();
        $this->hasta = now()->toDateString();
    }

    public function updatingDesde(): void
    {
        $this->resetPage();
    }

    public function updatingHasta(): void
    {
        $this->resetPage();
    }

    public function updatingBusqueda(): void
    {
        $this->resetPage();
    }

    public function updatingMetodoPago(): void
    {
        $this->resetPage();
    }

    /**
     * @return Builder<Venta>
     */
    private function filtroVentas(): Builder
    {
        return Venta::query()->when($this->filtro('vendedor'), fn ($q, $v) => $q->where('vendedor_id', $v))
            ->whereBetween('fecha', [$this->desde, $this->hasta])
            ->when($this->metodoPago, fn ($q) => $q->where('metodo_pago', $this->metodoPago))
            ->when($this->busqueda, fn ($q) => $q->where(function ($q) {
                $q->where('numero_boleta', 'like', "%{$this->busqueda}%")
                    ->orWhere('cliente_nombre_libre', 'like', "%{$this->busqueda}%")
                    ->orWhereHas('cliente', fn ($c) => $c->where('nombres', 'like', "%{$this->busqueda}%")
                        ->orWhere('apellidos', 'like', "%{$this->busqueda}%"));
            }));
    }

    public function with(): array
    {
        $query = $this->filtroVentas()->with('cliente', 'vendedor')->orderByDesc('created_at')->orderByDesc('id');

        return [
            'ventas' => $query->paginate(15),
            'totalPeriodo' => (clone $query)->reorder()->sum('total'),
            'boletasPeriodo' => (clone $query)->reorder()->count(),
            'metodos' => MetodoPago::options(),
            'vendedoresFiltro' => \App\Models\User::whereIn('id', Venta::select('vendedor_id'))->orderBy('nombres')->get(),
        ];
    }

    /**
     * Conserva pagos externos y saldo; elimina del historial únicamente las ventas que coinciden con los filtros actuales
     * (rango de fechas, método de pago y búsqueda). No repone el stock de los productos vendidos.
     */
    public function limpiarHistorial(): void
    {
        $eliminadas = $this->filtroVentas()->sinTrazabilidadFinanciera()->delete();

        if ($eliminadas === 0) {
            Flux::toast('No hay ventas eliminables con los filtros actuales. Se conserva la trazabilidad de pagos externos y saldo.', variant: 'warning');

            return;
        }

        $this->resetPage();

        Flux::modal('confirmar-limpiar-historial')->close();
        Flux::toast("Se eliminaron {$eliminadas} boleta(s) del historial.", variant: 'success');
    }
};
?>

<div class="flex flex-col gap-6">
    <div class="flex flex-wrap items-start justify-between gap-4">
        <x-ui.titulo titulo="Historial de ventas">Consulta las boletas emitidas y descarga su PDF.</x-ui.titulo>

        <flux:modal.trigger name="confirmar-limpiar-historial">
            <flux:button size="sm" variant="ghost" icon="trash" :disabled="$boletasPeriodo === 0">
                Limpiar historial
            </flux:button>
        </flux:modal.trigger>
    </div>

    <div class="flex flex-wrap items-end gap-4 rounded-xl border border-huata-verde-100 p-4 dark:border-zinc-700 huata-cuadro">
        <flux:input type="date" label="Desde" wire:model.live="desde" class="max-w-40" />
        <flux:input type="date" label="Hasta" wire:model.live="hasta" class="max-w-40" />
        <flux:select label="Método de pago" wire:model.live="metodoPago" class="max-w-xs">
            <flux:select.option value="">Todos</flux:select.option>
            @foreach ($metodos as $m)
                <flux:select.option value="{{ $m['value'] }}">{{ $m['label'] }}</flux:select.option>
            @endforeach
        </flux:select>
        <div class="flex flex-wrap items-center gap-3"><flux:input
            wire:model.live.debounce.300ms="busqueda"
            icon="magnifying-glass"
            placeholder="Boleta o cliente..."
            class="max-w-xs"
        /><x-ui.filtros :activos="$filtros"><flux:select label="Vendedor" wire:model.live="filtros.vendedor"><flux:select.option value="">Todos</flux:select.option>@foreach($vendedoresFiltro as $v)<flux:select.option :value="$v->id">{{ $v->nombreCompleto() }}</flux:select.option>@endforeach</flux:select></x-ui.filtros></div>
    </div>

    <div class="grid gap-3 sm:grid-cols-2">
        <div class="rounded-xl border border-huata-verde-100 p-4 dark:border-zinc-700 huata-cuadro">
            <flux:text>Total vendido en el período</flux:text>
            <p class="mt-1 text-2xl font-bold text-huata-verde-700">S/ {{ number_format((float) $totalPeriodo, 2) }}</p>
        </div>
        <div class="rounded-xl border border-huata-verde-100 p-4 dark:border-zinc-700 huata-cuadro">
            <flux:text>Boletas emitidas</flux:text>
            <p class="mt-1 text-2xl font-bold text-huata-verde-700">{{ $boletasPeriodo }}</p>
        </div>
    </div>

    <div class="overflow-x-auto rounded-xl border border-huata-verde-100 px-3 dark:border-zinc-700 huata-cuadro">
        <flux:table :paginate="$ventas">
            <flux:table.columns class="bg-white/80 dark:bg-zinc-800/80">
                <flux:table.column>Boleta</flux:table.column>
                <flux:table.column>Fecha</flux:table.column>
                <flux:table.column>Cliente</flux:table.column>
                <flux:table.column>Vendedor</flux:table.column>
                <flux:table.column>Método</flux:table.column>
                <flux:table.column>Total</flux:table.column>
                <flux:table.column class="w-px whitespace-nowrap">Acciones</flux:table.column>
            </flux:table.columns>
            <flux:table.rows>
                @forelse ($ventas as $venta)
                    <flux:table.row wire:key="venta-{{ $venta->id }}">
                        <flux:table.cell class="font-medium">{{ $venta->numero_boleta }}</flux:table.cell>
                        <flux:table.cell>{{ $venta->fecha->format('d/m/Y') }}</flux:table.cell>
                        <flux:table.cell>
                            {{ $venta->cliente ? "{$venta->cliente->nombres} {$venta->cliente->apellidos}" : ($venta->cliente_nombre_libre ?: 'Venta anónima') }}
                        </flux:table.cell>
                        <flux:table.cell>{{ $venta->vendedor?->nombreCompleto() ?? '—' }}</flux:table.cell>
                        <flux:table.cell>{{ \App\Enums\MetodoPago::from($venta->metodo_pago)->label() }}</flux:table.cell>
                        <flux:table.cell>S/ {{ number_format((float) $venta->total, 2) }}</flux:table.cell>
                        <flux:table.cell>
                            <flux:button
                                as="a"
                                target="_blank"
                                :href="route('vendedor.ventas.boleta', $venta->id)"
                                size="sm"
                                variant="ghost"
                                icon="document-arrow-down"
                            />
                        </flux:table.cell>
                    </flux:table.row>
                @empty
                    <flux:table.row><flux:table.cell colspan="7" class="text-center text-zinc-500">Sin ventas en este período.</flux:table.cell></flux:table.row>
                @endforelse
            </flux:table.rows>
        </flux:table>
    </div>

    <x-confirm-modal
        name="confirmar-limpiar-historial"
        accion="limpiarHistorial"
        titulo="¿Eliminar historial del período?"
        :mensaje="'Esto eliminará permanentemente '.$boletasPeriodo.' boleta(s) del '.\Illuminate\Support\Carbon::parse($desde)->format('d/m/Y').' al '.\Illuminate\Support\Carbon::parse($hasta)->format('d/m/Y').' (según los filtros actuales). Esta acción no se puede deshacer.'"
        textoConfirmar="Eliminar historial"
    />
</div>

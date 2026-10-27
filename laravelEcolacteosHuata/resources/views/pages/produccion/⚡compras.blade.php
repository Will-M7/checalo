<?php

use App\Models\CompraDetalle;
use App\Models\Insumo;
use Illuminate\Database\Eloquent\Builder;
use Livewire\Attributes\Url;
use Livewire\Component;
use Livewire\WithPagination;

new class extends Component
{
    use WithPagination;

    public string $desde = '';

    public string $hasta = '';

    #[Url(as: 'insumo')]
    public string $insumoId = '';

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

    public function updatingInsumoId(): void
    {
        $this->resetPage();
    }

    public function filtrarHoy(): void
    {
        $this->desde = now()->toDateString();
        $this->hasta = now()->toDateString();
        $this->resetPage();
    }

    public function filtrarAyer(): void
    {
        $this->desde = now()->subDay()->toDateString();
        $this->hasta = now()->subDay()->toDateString();
        $this->resetPage();
    }

    public function filtrarEstaSemana(): void
    {
        $this->desde = now()->startOfWeek()->toDateString();
        $this->hasta = now()->toDateString();
        $this->resetPage();
    }

    public function filtrarSemanaPasada(): void
    {
        $this->desde = now()->subWeek()->startOfWeek()->toDateString();
        $this->hasta = now()->subWeek()->endOfWeek()->toDateString();
        $this->resetPage();
    }

    public function filtrarEsteMes(): void
    {
        $this->desde = now()->startOfMonth()->toDateString();
        $this->hasta = now()->toDateString();
        $this->resetPage();
    }

    public function filtrarMesPasado(): void
    {
        $this->desde = now()->subMonthNoOverflow()->startOfMonth()->toDateString();
        $this->hasta = now()->subMonthNoOverflow()->endOfMonth()->toDateString();
        $this->resetPage();
    }

    /**
     * @return Builder<CompraDetalle>
     */
    private function filtroCompras(): Builder
    {
        return CompraDetalle::query()
            ->whereHas('compra', fn ($q) => $q->whereDate('fecha', '>=', $this->desde)->whereDate('fecha', '<=', $this->hasta))
            ->when($this->insumoId, fn ($q) => $q->where('insumo_id', $this->insumoId));
    }

    /**
     * Compara el precio de esta compra contra la compra inmediatamente anterior del mismo
     * insumo (sin importar el filtro de fechas actual), para poder ver si subió o bajó.
     *
     * @return array{anterior: float, diferencia: float, porcentaje: float}|null
     */
    private function variacionPrecio(CompraDetalle $detalle): ?array
    {
        $anterior = CompraDetalle::where('insumo_id', $detalle->insumo_id)
            ->where('id', '<', $detalle->id)
            ->orderByDesc('id')
            ->value('costo_unitario');

        if ($anterior === null) {
            return null;
        }

        $anterior = (float) $anterior;
        $actual = (float) $detalle->costo_unitario;
        $diferencia = $actual - $anterior;

        return [
            'anterior' => $anterior,
            'diferencia' => $diferencia,
            'porcentaje' => $anterior > 0 ? ($diferencia / $anterior) * 100 : 0,
        ];
    }

    public function with(): array
    {
        $query = $this->filtroCompras()->with('compra.registradoPor', 'insumo.unidadMedida')->orderByDesc('id');

        $compras = $query->paginate(15);

        return [
            'compras' => $compras,
            'variaciones' => collect($compras->items())->mapWithKeys(fn (CompraDetalle $d) => [$d->id => $this->variacionPrecio($d)]),
            'totalPeriodo' => (clone $query)->reorder()->sum('subtotal'),
            'comprasPeriodo' => (clone $query)->reorder()->count(),
            'insumos' => Insumo::orderBy('nombre')->get(),
        ];
    }
};
?>

<div class="flex flex-col gap-6">
    <x-ui.titulo titulo="Compras de insumos">Historial de ingresos de stock: cuándo, cuánto y a qué precio, para comparar precios entre compras.</x-ui.titulo>

    <div class="flex flex-wrap gap-2">
        <flux:button size="sm" variant="ghost" wire:click="filtrarHoy">Hoy</flux:button>
        <flux:button size="sm" variant="ghost" wire:click="filtrarAyer">Ayer</flux:button>
        <flux:button size="sm" variant="ghost" wire:click="filtrarEstaSemana">Esta semana</flux:button>
        <flux:button size="sm" variant="ghost" wire:click="filtrarSemanaPasada">Semana pasada</flux:button>
        <flux:button size="sm" variant="ghost" wire:click="filtrarEsteMes">Este mes</flux:button>
        <flux:button size="sm" variant="ghost" wire:click="filtrarMesPasado">Mes pasado</flux:button>
    </div>

    <div class="flex flex-wrap items-end gap-4 rounded-xl border border-huata-verde-100 p-4 dark:border-zinc-700 huata-cuadro">
        <flux:input type="date" label="Desde" wire:model.live="desde" class="max-w-40" />
        <flux:input type="date" label="Hasta" wire:model.live="hasta" class="max-w-40" />
        <flux:select label="Insumo" wire:model.live="insumoId" class="max-w-xs">
            <flux:select.option value="">Todos los insumos</flux:select.option>
            @foreach ($insumos as $ins)
                <flux:select.option value="{{ $ins->id }}">{{ $ins->nombre }}</flux:select.option>
            @endforeach
        </flux:select>
    </div>

    <div class="grid gap-3 sm:grid-cols-2">
        <div class="rounded-xl border border-huata-verde-100 p-4 dark:border-zinc-700 huata-cuadro">
            <flux:text>Total gastado en el período</flux:text>
            <p class="mt-1 text-2xl font-bold text-huata-verde-700">S/ {{ number_format((float) $totalPeriodo, 2) }}</p>
        </div>
        <div class="rounded-xl border border-huata-verde-100 p-4 dark:border-zinc-700 huata-cuadro">
            <flux:text>Compras registradas</flux:text>
            <p class="mt-1 text-2xl font-bold text-huata-verde-700">{{ $comprasPeriodo }}</p>
        </div>
    </div>

    <div class="overflow-x-auto rounded-xl border border-huata-verde-100 px-3 dark:border-zinc-700 huata-cuadro">
        <flux:table :paginate="$compras">
            <flux:table.columns class="bg-white/80 dark:bg-zinc-800/80">
                <flux:table.column>Insumo</flux:table.column>
                <flux:table.column>Fecha y hora</flux:table.column>
                <flux:table.column>Cantidad ingresada</flux:table.column>
                <flux:table.column>Precio unitario</flux:table.column>
                <flux:table.column>Variación vs. compra anterior</flux:table.column>
                <flux:table.column>Subtotal</flux:table.column>
                <flux:table.column>Registrado por</flux:table.column>
            </flux:table.columns>
            <flux:table.rows>
                @forelse ($compras as $detalle)
                    @php $variacion = $variaciones[$detalle->id] ?? null; @endphp
                    <flux:table.row wire:key="compra-{{ $detalle->id }}">
                        <flux:table.cell class="font-medium">{{ $detalle->insumo->nombre }}</flux:table.cell>
                        <flux:table.cell>{{ $detalle->compra->created_at->format('d/m/Y H:i') }}</flux:table.cell>
                        <flux:table.cell>{{ number_format((float) $detalle->cantidad, 2) }} {{ $detalle->insumo->unidadMedida->simbolo }}</flux:table.cell>
                        <flux:table.cell>S/ {{ number_format((float) $detalle->costo_unitario, 4) }}</flux:table.cell>
                        <flux:table.cell>
                            @if ($variacion === null)
                                <flux:badge color="zinc" size="sm">Primera compra registrada</flux:badge>
                            @elseif ($variacion['diferencia'] > 0)
                                <flux:badge color="red" size="sm" icon="arrow-trending-up">
                                    +S/ {{ number_format($variacion['diferencia'], 4) }} ({{ number_format($variacion['porcentaje'], 1) }}%)
                                </flux:badge>
                            @elseif ($variacion['diferencia'] < 0)
                                <flux:badge color="green" size="sm" icon="arrow-trending-down">
                                    S/ {{ number_format($variacion['diferencia'], 4) }} ({{ number_format($variacion['porcentaje'], 1) }}%)
                                </flux:badge>
                            @else
                                <flux:badge color="zinc" size="sm">Sin cambio</flux:badge>
                            @endif
                        </flux:table.cell>
                        <flux:table.cell>S/ {{ number_format((float) $detalle->subtotal, 2) }}</flux:table.cell>
                        <flux:table.cell>{{ $detalle->compra->registradoPor?->nombreCompleto() ?? '—' }}</flux:table.cell>
                    </flux:table.row>
                @empty
                    <flux:table.row><flux:table.cell colspan="7" class="text-center text-zinc-500">Sin compras registradas en este período.</flux:table.cell></flux:table.row>
                @endforelse
            </flux:table.rows>
        </flux:table>
    </div>
</div>

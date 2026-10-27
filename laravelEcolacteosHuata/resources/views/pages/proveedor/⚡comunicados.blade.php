<?php

use App\Enums\Rol;
use App\Enums\TipoComunicado;
use App\Models\Comunicado;
use Livewire\Component;

new class extends Component
{
    use \App\Livewire\Concerns\FiltrosListado;
    public string $busqueda = '';
    public string $filtroTipo = '';

    public function with(): array
    {
        return [
            'tipos' => TipoComunicado::options(),
            'comunicados' => Comunicado::query()->when($this->filtro('desde'), fn ($q, $v) => $q->whereDate('fecha', '>=', $v))->when($this->filtro('hasta'), fn ($q, $v) => $q->whereDate('fecha', '<=', $v))->when($this->busqueda !== '', fn ($q) => $q->where(fn ($t) => $t->where('titulo', 'like', '%'.$this->busqueda.'%')->orWhere('descripcion', 'like', '%'.$this->busqueda.'%')))
                ->paraRol(Rol::Proveedor)
                ->when($this->filtroTipo, fn ($q) => $q->where('tipo', $this->filtroTipo))
                ->orderByDesc('fecha')
                ->get(),
        ];
    }
};
?>

<div class="flex flex-col gap-6">
    <x-ui.titulo titulo="Comunicados y asesoramiento">Avisos publicados por la planta.</x-ui.titulo>
    <div class="flex flex-wrap items-center gap-3"><flux:input wire:model.live.debounce.300ms="busqueda" icon="magnifying-glass" placeholder="Buscar comunicado..." class="max-w-sm" /><x-ui.filtros :activos="$filtros"><flux:input label="Desde" type="date" wire:model.live="filtros.desde" /><flux:input label="Hasta" type="date" wire:model.live="filtros.hasta" /></x-ui.filtros></div>


    <flux:select label="Filtrar por tipo" wire:model.live="filtroTipo" class="max-w-xs">
        <flux:select.option value="">Todos</flux:select.option>
        @foreach ($tipos as $tipo)
            <flux:select.option value="{{ $tipo['value'] }}">{{ $tipo['label'] }}</flux:select.option>
        @endforeach
    </flux:select>

    <div class="flex flex-col gap-4">
        @forelse ($comunicados as $comunicado)
            <div class="rounded-xl border border-huata-verde-100 p-4 dark:border-zinc-700 huata-cuadro">
                <div class="flex flex-wrap items-center justify-between gap-2">
                    <flux:badge :color="$comunicado->tipo->value === 'asesoramiento' ? 'blue' : 'green'" size="sm">
                        {{ $comunicado->tipo->label() }}
                    </flux:badge>
                    <flux:text class="text-sm text-zinc-500">{{ $comunicado->fecha->format('d/m/Y') }}</flux:text>
                </div>
                <flux:heading size="lg" class="mt-2">{{ $comunicado->titulo }}</flux:heading>
                <flux:text class="mt-1 whitespace-pre-line text-zinc-600 dark:text-zinc-300">{{ $comunicado->descripcion }}</flux:text>
            </div>
        @empty
            <div class="rounded-xl border border-huata-verde-100 p-6 text-center text-zinc-500 dark:border-zinc-700 huata-cuadro">
                {{ $busqueda !== '' || count($filtros) ? 'No se encontraron comunicados con estos filtros.' : 'No hay comunicados publicados todavía.' }}
            </div>
        @endforelse
    </div>
</div>

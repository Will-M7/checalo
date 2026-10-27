@props(['activos' => []])
@php $seleccionados = array_filter($activos, fn($v) => is_scalar($v) && (string) $v !== ''); @endphp
<div x-data="{ abierto: false }" class="relative shrink-0">
    <flux:button icon="adjustments-horizontal" x-on:click="abierto = !abierto" x-bind:aria-expanded="abierto">Filtrar @if(count($seleccionados)) ({{ count($seleccionados) }}) @endif</flux:button>
    <div x-show="abierto" x-cloak x-on:click.outside="abierto = false" x-on:keydown.escape.window="abierto = false" class="huata-cuadro absolute right-0 z-30 mt-2 flex max-h-[70vh] w-72 max-w-[85vw] flex-col gap-3 overflow-y-auto rounded-xl border border-huata-verde-100 p-4 shadow-lg dark:border-zinc-700" aria-label="Filtros del listado">
        {{ $slot }}
        <flux:button size="sm" wire:click="limpiarFiltros">Limpiar filtros</flux:button>
    </div>
</div>

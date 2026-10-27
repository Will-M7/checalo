@props([
    'titulo',
])

{{-- Título de pantalla de un módulo (el contenido del slot es el subtítulo). Mantiene todas las pantallas con la misma jerarquía. --}}
<div {{ $attributes->class('hh-entra min-w-0') }}>
    <h1 class="text-balance font-display text-2xl font-bold tracking-tight text-[var(--hh-texto)] sm:text-3xl">{{ $titulo }}</h1>
    @if ($slot->isNotEmpty())
        <p class="mt-1 max-w-3xl text-pretty text-sm text-[var(--hh-texto-2)] sm:text-base">{{ $slot }}</p>
    @endif
</div>

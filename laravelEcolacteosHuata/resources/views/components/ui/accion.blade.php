@props([
    'href',
    'titulo',
    'descripcion' => null,
    'icono' => 'squares-2x2',
    'indice' => 0,
])

{{-- Tarjeta de acceso rápido a un módulo (navegación con wire:navigate). --}}
<a href="{{ $href }}" wire:navigate {{ $attributes->class('hh-accion hh-entra') }} style="--i: {{ $indice }}">
    <span class="hh-icono-suave"><flux:icon :icon="$icono" class="size-5" /></span>
    <span class="min-w-0">
        <span class="block text-sm font-semibold text-[var(--hh-texto)]">{{ $titulo }}</span>
        @if ($descripcion)
            <span class="mt-0.5 block text-pretty text-xs leading-snug text-[var(--hh-texto-2)]">{{ $descripcion }}</span>
        @endif
    </span>
    <flux:icon icon="arrow-right" class="hh-flecha mt-0.5 size-4 shrink-0" />
</a>

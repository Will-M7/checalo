@props([
    'titulo',
    'descripcion' => null,
    'icono' => 'inbox',
])

{{-- Estado vacío con icono, título y una explicación breve. --}}
<div {{ $attributes->class('hh-vacio') }}>
    <span class="hh-icono-suave"><flux:icon :icon="$icono" class="size-6" /></span>
    <p class="text-sm font-bold text-[var(--hh-texto)]">{{ $titulo }}</p>
    @if ($descripcion)
        <p class="max-w-xs text-pretty text-xs">{{ $descripcion }}</p>
    @endif
    {{ $slot }}
</div>

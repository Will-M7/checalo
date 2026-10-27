@props([
    'titulo' => null,
    'descripcion' => null,
    'indice' => 0,
    'relleno' => true,
])

{{-- Contenedor de sección con encabezado opcional (para gráficos, listas y tablas). Las acciones del encabezado van en el slot `acciones`. --}}
<section {{ $attributes->class('hh-panel hh-entra') }} style="--i: {{ $indice }}">
    @if ($titulo)
        <div class="flex items-start justify-between gap-3 border-b border-[var(--hh-borde)] px-5 py-4">
            <div class="min-w-0">
                <h2 class="text-base font-semibold tracking-tight text-[var(--hh-texto)]">{{ $titulo }}</h2>
                @if ($descripcion)
                    <p class="mt-0.5 text-xs text-[var(--hh-texto-2)]">{{ $descripcion }}</p>
                @endif
            </div>
            @isset($acciones)
                <div class="flex shrink-0 items-center gap-2">{{ $acciones }}</div>
            @endisset
        </div>
    @endif

    <div @class(['p-5' => $relleno, 'overflow-hidden rounded-b-[var(--hh-radio)]' => ! $relleno])>{{ $slot }}</div>
</section>

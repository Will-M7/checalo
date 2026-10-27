@props([
    'titulo',
    'subtitulo' => null,
    'etiqueta' => null,
    'fecha' => true,
    'banner' => true,
])

{{-- Encabezado de página. Con `banner` (por defecto) es el banner de bienvenida del panel: degradado azul corporativo,
     rol, fecha y título. Sin banner es una cabecera sobria. Las acciones van en el slot. --}}
@php
    $fechaTexto = ucfirst(now(config('app.zona_horaria_local'))->locale('es')->isoFormat('dddd D [de] MMMM [de] YYYY'));
@endphp

@if ($banner)
    <header {{ $attributes->class('hh-banner hh-entra') }}>
        <div class="relative z-10 flex flex-col gap-5 px-6 py-7 sm:flex-row sm:items-end sm:justify-between sm:px-8 sm:py-8">
            <div class="min-w-0">
                <p class="mb-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs font-semibold text-white/80">
                    @if ($etiqueta)
                        <span class="rounded-full bg-white/15 px-2.5 py-1 font-bold uppercase tracking-[0.14em] text-white">{{ $etiqueta }}</span>
                    @endif
                    @if ($fecha)
                        <span>{{ $fechaTexto }}</span>
                    @endif
                </p>
                <h1 class="text-balance font-display text-3xl font-bold tracking-tight sm:text-4xl">{{ $titulo }}</h1>
                @if ($subtitulo)
                    <p class="mt-2 max-w-2xl text-pretty text-sm text-white/85 sm:text-base">{{ $subtitulo }}</p>
                @endif
            </div>

            @if ($slot->isNotEmpty())
                <div class="flex shrink-0 flex-wrap items-center gap-2 [&_[data-flux-button]]:!border-white/30 [&_[data-flux-button]]:!bg-white/95 [&_[data-flux-button]]:!text-[#173d72] [&_[data-flux-button]]:hover:!bg-white">{{ $slot }}</div>
            @endif
        </div>
        <div class="hh-banner-franja relative z-10" aria-hidden="true"></div>
    </header>
@else
    <header {{ $attributes->class('hh-entra flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between') }}>
        <div class="min-w-0">
            @if ($etiqueta || $fecha)
                <p class="mb-1.5 flex flex-wrap items-center gap-x-3 text-xs font-medium text-[var(--hh-texto-2)]">
                    @if ($etiqueta)
                        <span class="font-bold uppercase tracking-[0.14em] text-[var(--hh-marca)]">{{ $etiqueta }}</span>
                    @endif
                    @if ($fecha)
                        <span>{{ $fechaTexto }}</span>
                    @endif
                </p>
            @endif
            <h1 class="text-balance font-display text-2xl font-bold tracking-tight text-[var(--hh-texto)] sm:text-3xl">{{ $titulo }}</h1>
            @if ($subtitulo)
                <p class="mt-1 max-w-2xl text-pretty text-sm text-[var(--hh-texto-2)] sm:text-base">{{ $subtitulo }}</p>
            @endif
        </div>

        @if ($slot->isNotEmpty())
            <div class="flex shrink-0 flex-wrap items-center gap-2">{{ $slot }}</div>
        @endif
    </header>
@endif

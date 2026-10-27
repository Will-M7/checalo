@props([
    'etiqueta',
    'valor',
    'icono' => 'chart-bar',
    'color' => '#2864b4',
    'detalle' => null,
    'progreso' => null,
    'indice' => 0,
])

{{-- Indicador (KPI): icono en caja de color, cifra grande, etiqueta, detalle opcional y barra fina de progreso.
     `progreso` (0-100) solo debe recibir proporciones reales. --}}
<div {{ $attributes->class('hh-kpi hh-entra p-5') }} style="--hh-kpi-color: {{ $color }}; --i: {{ $indice }}">
    <div class="flex items-start justify-between gap-3">
        <span class="hh-kpi-icono"><flux:icon :icon="$icono" class="size-5" /></span>
        @if ($detalle)
            <span class="max-w-[60%] text-end text-xs font-medium leading-snug text-[var(--hh-texto-2)]">{{ $detalle }}</span>
        @endif
    </div>

    <p class="mt-4 font-display text-4xl font-bold leading-none tracking-tight tabular-nums text-[var(--hh-texto)]">{{ $valor }}</p>
    <p class="mt-2 text-sm font-medium text-[var(--hh-texto-2)]">{{ $etiqueta }}</p>

    @if ($progreso !== null)
        <div class="hh-barra mt-3" style="--hh-barra-color: {{ $color }}" role="img" aria-label="{{ $etiqueta }}: {{ round($progreso) }} por ciento">
            <span style="--hh-ancho: {{ max(0, min(100, $progreso)) }}%"></span>
        </div>
    @endif
</div>

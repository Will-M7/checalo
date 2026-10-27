@props(['proveedor'])

<div class="flex flex-col gap-3 rounded-xl border border-huata-verde-100 p-4 dark:border-zinc-700 huata-cuadro">
    <div class="flex items-start gap-3">
        <flux:icon name="map-pin" class="mt-1 size-7 shrink-0 text-huata-verde-600" />
        <div>
            <p class="text-xl font-bold leading-snug text-huata-verde-950 dark:text-white">
                {{ $proveedor->finca_ubicacion ?: 'Ubicación no registrada' }}
            </p>
            @if ($proveedor->finca_referencia)
                <p class="text-base text-zinc-600 dark:text-zinc-300">{{ $proveedor->finca_referencia }}</p>
            @endif
            @if ($proveedor->latitud && $proveedor->longitud)
                <a
                    href="https://www.openstreetmap.org/?mlat={{ $proveedor->latitud }}&mlon={{ $proveedor->longitud }}#map=17/{{ $proveedor->latitud }}/{{ $proveedor->longitud }}"
                    target="_blank"
                    class="mt-1 inline-block text-sm text-huata-celeste-700 underline"
                >
                    Ver en el mapa ({{ $proveedor->latitud }}, {{ $proveedor->longitud }})
                </a>
            @endif
        </div>
    </div>

    @if ($proveedor->latitud && $proveedor->longitud)
        <x-mapa-ubicacion :lat="$proveedor->latitud" :lng="$proveedor->longitud" :editable="false" height="180px" />
    @endif
</div>

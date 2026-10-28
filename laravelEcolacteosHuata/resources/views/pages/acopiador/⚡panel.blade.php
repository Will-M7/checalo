<?php

use App\Enums\Rol;
use App\Models\EntregaPlanta;
use App\Models\Recojo;
use Livewire\Component;

new class extends Component
{
    public function with(): array
    {
        $acopiador = auth()->user()->acopiador;
        $fecha = now(config('app.zona_horaria_local'))->toDateString();

        $recojosHoy = Recojo::query()
            ->where('acopiador_id', $acopiador?->id)
            ->whereDate('fecha', $fecha);

        $proveedoresAsignados = $acopiador?->proveedores()->count() ?? 0;
        $proveedoresRecogidosHoy = (clone $recojosHoy)->distinct('proveedor_id')->count('proveedor_id');

        return [
            'acopiador' => $acopiador,
            'litrosHoy' => (clone $recojosHoy)->sum('litros'),
            'totalProveedores' => $proveedoresAsignados,
            'proveedoresRecogidosHoy' => $proveedoresRecogidosHoy,
            'proveedoresPendientes' => max($proveedoresAsignados - $proveedoresRecogidosHoy, 0),
            'entregadoPlantaHoy' => (float) EntregaPlanta::query()->where('acopiador_id', $acopiador?->id)->whereDate('fecha', $fecha)->sum('litros_total'),
            'ultimosRecojos' => (clone $recojosHoy)->with('proveedor.user')->latest('created_at')->latest('id')->limit(5)->get(),
        ];
    }
};
?>

<div class="flex flex-col gap-8">
    <x-ui.page-header
        titulo="Panel del acopiador"
        :subtitulo="$acopiador?->ruta_nombre ? 'Ruta asignada: '.$acopiador->ruta_nombre : 'Aún no tienes una ruta asignada.'"
        :etiqueta="Rol::Acopiador->etiquetaPanel()"
    >
        @if(auth('web')->user()->puedePantalla(\App\Authorization\Pantallas::deRuta('acopiador.recojo') ?? 'acopiador.recojo'))
        <flux:button as="a" :href="route('acopiador.recojo')" wire:navigate variant="primary" icon="truck" size="sm">Registrar recojo</flux:button>
        @endif
    </x-ui.page-header>

    <section aria-label="Resumen del día" class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <x-ui.kpi etiqueta="Litros recogidos hoy" :valor="number_format((float) $litrosHoy, 2).' L'" icono="beaker" :indice="0" />
        <x-ui.kpi
            etiqueta="Proveedores asignados"
            :valor="$totalProveedores"
            icono="users"
            :detalle="$proveedoresRecogidosHoy.' recogidos hoy'"
            :progreso="$totalProveedores > 0 ? $proveedoresRecogidosHoy / $totalProveedores * 100 : null"
            :indice="1"
        />
        <x-ui.kpi
            etiqueta="Faltan por recoger hoy"
            :valor="$proveedoresPendientes"
            icono="clock"
            :color="$proveedoresPendientes > 0 ? '#c93445' : '#2864b4'"
            :detalle="$proveedoresPendientes > 0 ? 'Revisa tu ruta' : ($totalProveedores > 0 ? 'Ruta completa' : null)"
            :indice="2"
        />
        <x-ui.kpi etiqueta="Entregado a planta hoy" :valor="number_format($entregadoPlantaHoy, 2).' L'" icono="building-office-2" color="#23865b" :indice="3" />
    </section>

    <div class="grid gap-6 xl:grid-cols-5">
        <x-ui.panel titulo="Recojos de hoy" descripcion="Los últimos registrados en tu ruta." :indice="4" class="xl:col-span-3">
            @if ($ultimosRecojos->isEmpty())
                <x-ui.vacio titulo="Todavía no registras recojos hoy" descripcion="Cuando recojas leche de un proveedor aparecerá aquí." icono="truck">
        @if(auth('web')->user()->puedePantalla(\App\Authorization\Pantallas::deRuta('acopiador.recojo') ?? 'acopiador.recojo'))
                    <flux:button as="a" :href="route('acopiador.recojo')" wire:navigate size="sm" variant="primary" class="mt-2">Ir a recojo de ruta</flux:button>
        @endif
                </x-ui.vacio>
            @else
                <ul class="flex flex-col divide-y divide-[var(--hh-borde)]">
                    @foreach ($ultimosRecojos as $recojo)
                        <li class="flex items-center gap-3 py-3 first:pt-0 last:pb-0">
                            <span class="grid size-9 shrink-0 place-items-center rounded-lg border border-[var(--hh-borde)] text-[var(--hh-marca)]"><flux:icon icon="beaker" class="size-4" /></span>
                            <div class="min-w-0 flex-1">
                                <p class="truncate text-sm font-semibold text-[var(--hh-texto)]">{{ $recojo->proveedor?->user?->nombreCompleto() ?? 'Proveedor' }}</p>
                                <p class="text-xs text-[var(--hh-texto-2)]">{{ $recojo->entregado ? 'Entregado a planta' : 'Pendiente de entregar a planta' }}</p>
                            </div>
                            <span class="text-sm font-semibold tabular-nums text-[var(--hh-texto)]">{{ number_format((float) $recojo->litros, 2) }} L</span>
                        </li>
                    @endforeach
                </ul>
            @endif
        </x-ui.panel>

        <x-ui.panel titulo="Tu ruta" descripcion="Mapa de recojo con tus proveedores." :indice="5" class="xl:col-span-2">
            <div class="flex flex-col items-start gap-4">
                <span class="grid size-12 place-items-center rounded-xl border border-[var(--hh-borde)] text-[var(--hh-marca)]"><flux:icon icon="map" class="size-6" /></span>
                <div>
                    <p class="text-sm font-semibold text-[var(--hh-texto)]">{{ $acopiador?->ruta_nombre ?? 'Sin ruta asignada' }}</p>
                    <p class="mt-1 text-xs text-[var(--hh-texto-2)]">Mira en el mapa a quién te falta recoger y por dónde ir.</p>
                </div>
        @if(auth('web')->user()->puedePantalla(\App\Authorization\Pantallas::deRuta('acopiador.mapa-recojo') ?? 'acopiador.mapa-recojo'))
                <flux:button as="a" :href="route('acopiador.mapa-recojo')" wire:navigate icon="map-pin" size="sm">Abrir mapa de recojo</flux:button>
        @endif
            </div>
        </x-ui.panel>
    </div>

    <section aria-labelledby="accesos-titulo">
        <h2 id="accesos-titulo" class="hh-entra mb-4 text-lg font-semibold tracking-tight text-[var(--hh-texto)]" style="--i: 6">Accesos rápidos</h2>
        @php
            $accesos = [
                ['acopiador.recojo', 'Recojo de ruta', 'Registra los litros que recoges de cada proveedor.', 'truck'],
                ['acopiador.entrega', 'Entrega a planta', 'Entrega lo recogido y genera la guía.', 'building-office-2'],
                ['acopiador.mapa-recojo', 'Mapa de recojo', 'Tu ruta y tus proveedores en el mapa.', 'map'],
                ['acopiador.inventario', 'Inventario', 'Resumen de lo recogido y entregado.', 'archive-box'],
                ['asistencia.mis', 'Asistencia', 'Marca tu asistencia y revisa tu historial.', 'calendar-days'],
            ];
        @endphp
        <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
            @foreach ($accesos as [$ruta, $titulo, $descripcion, $icono])
                @if(auth('web')->user()->puedePantalla(\App\Authorization\Pantallas::deRuta($ruta) ?? $ruta))
                <x-ui.accion :href="route($ruta)" :titulo="$titulo" :descripcion="$descripcion" :icono="$icono" :indice="7 + $loop->index" />
                @endif
            @endforeach
        </div>
    </section>
</div>

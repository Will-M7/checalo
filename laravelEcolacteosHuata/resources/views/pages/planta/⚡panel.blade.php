<?php

use App\Enums\EstadoProduccion;
use App\Enums\Rol;
use App\Models\Acopiador;
use App\Models\Comunicado;
use App\Models\EntregaPlanta;
use App\Models\PagoProveedor;
use App\Models\Produccion;
use App\Models\Proveedor;
use App\Models\Recojo;
use App\Models\ResultadoLaboratorio;
use Livewire\Component;

new class extends Component
{
    public function with(): array
    {
        $hoy = now(config('app.zona_horaria_local'));
        $fecha = $hoy->toDateString();

        $laboratorioHoy = ResultadoLaboratorio::query()->whereDate('fecha', $fecha);

        return [
            'litrosHoy' => Recojo::query()->whereDate('fecha', $fecha)->sum('litros'),
            'totalAcopiadores' => Acopiador::query()->count(),
            'totalProveedores' => Proveedor::query()->count(),
            'totalProductos' => Produccion::query()->where('estado', EstadoProduccion::Completada)->count(),
            'entregasHoy' => EntregaPlanta::query()->whereDate('fecha', $fecha)->count(),
            'litrosEntregadosHoy' => (float) EntregaPlanta::query()->whereDate('fecha', $fecha)->sum('litros_total'),
            'analisisHoy' => (clone $laboratorioHoy)->count(),
            'noConformesHoy' => (clone $laboratorioHoy)->where('conforme', false)->count(),
            'pagadoMes' => (float) PagoProveedor::query()->whereBetween('fecha', [$hoy->copy()->startOfMonth()->toDateString(), $hoy->copy()->endOfMonth()->toDateString()])->sum('monto'),
            'ultimasEntregas' => EntregaPlanta::query()->with('acopiador.user')->latest('fecha')->latest('id')->limit(4)->get(),
            'ultimosResultados' => ResultadoLaboratorio::query()->with('proveedor.user')->latest('fecha')->latest('id')->limit(4)->get(),
            'comunicados' => Comunicado::query()->paraRol(Rol::PersonalPlanta)->latest('fecha')->latest('id')->limit(3)->get(),
        ];
    }
};
?>

<div class="flex flex-col gap-8">
    <x-ui.page-header
        titulo="Panel de planta"
        subtitulo="Acopio, calidad y trazabilidad de la leche, en un solo lugar."
        :etiqueta="Rol::PersonalPlanta->etiquetaPanel()"
    />

    <section aria-label="Resumen de acopio" class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <x-ui.kpi etiqueta="Litros recibidos hoy" :valor="number_format((float) $litrosHoy, 2).' L'" icono="beaker" :indice="0" />
        <x-ui.kpi etiqueta="Entregas de hoy" :valor="$entregasHoy" icono="truck" :detalle="number_format($litrosEntregadosHoy, 2).' L entregados'" :indice="1" />
        <x-ui.kpi
            etiqueta="Análisis de laboratorio hoy"
            :valor="$analisisHoy"
            icono="eye-dropper"
            :color="$noConformesHoy > 0 ? '#c93445' : '#2864b4'"
            :detalle="$noConformesHoy > 0 ? $noConformesHoy.' no conforme(s)' : ($analisisHoy > 0 ? 'Todos conformes' : 'Sin análisis aún')"
            :indice="2"
        />
        <x-ui.kpi etiqueta="Pagado a proveedores este mes" :valor="'S/ '.number_format($pagadoMes, 2)" icono="banknotes" color="#23865b" :indice="3" />
    </section>

    <section aria-label="Red de acopio" class="grid gap-4 sm:grid-cols-3">
        <x-ui.kpi etiqueta="Acopiadores" :valor="$totalAcopiadores" icono="user-group" :indice="4" />
        <x-ui.kpi etiqueta="Proveedores" :valor="$totalProveedores" icono="users" :indice="5" />
        <x-ui.kpi etiqueta="Productos publicados" :valor="$totalProductos" icono="cube" :indice="6" />
    </section>

    <div class="grid gap-6 xl:grid-cols-2">
        <x-ui.panel titulo="Últimas entregas de acopiadores" descripcion="Leche que llegó a la planta." :indice="7">
            @if ($ultimasEntregas->isEmpty())
                <x-ui.vacio titulo="Aún no hay entregas" descripcion="Cuando un acopiador entregue leche a la planta aparecerá aquí." icono="truck" />
            @else
                <ul class="flex flex-col divide-y divide-[var(--hh-borde)]">
                    @foreach ($ultimasEntregas as $entrega)
                        <li class="flex items-center gap-3 py-3 first:pt-0 last:pb-0">
                            <span class="grid size-9 shrink-0 place-items-center rounded-lg border border-[var(--hh-borde)] text-[var(--hh-marca)]"><flux:icon icon="truck" class="size-4" /></span>
                            <div class="min-w-0 flex-1">
                                <p class="truncate text-sm font-semibold text-[var(--hh-texto)]">{{ $entrega->acopiador?->user?->nombreCompleto() ?? 'Acopiador' }}</p>
                                <p class="text-xs text-[var(--hh-texto-2)]">{{ $entrega->fecha->format('d/m/Y') }}@if ($entrega->guia) · Guía {{ $entrega->guia }}@endif</p>
                            </div>
                            <span class="text-sm font-semibold tabular-nums text-[var(--hh-texto)]">{{ number_format((float) $entrega->litros_total, 2) }} L</span>
                        </li>
                    @endforeach
                </ul>
            @endif
        </x-ui.panel>

        <x-ui.panel titulo="Resultados de laboratorio" descripcion="Calidad de la leche de cada proveedor." :indice="8">
            @if ($ultimosResultados->isEmpty())
                <x-ui.vacio titulo="Aún no hay resultados" descripcion="Los análisis registrados aparecerán aquí." icono="beaker" />
            @else
                <ul class="flex flex-col divide-y divide-[var(--hh-borde)]">
                    @foreach ($ultimosResultados as $resultado)
                        <li class="flex items-center gap-3 py-3 first:pt-0 last:pb-0">
                            <div class="min-w-0 flex-1">
                                <p class="truncate text-sm font-semibold text-[var(--hh-texto)]">{{ $resultado->proveedor?->user?->nombreCompleto() ?? 'Proveedor' }}</p>
                                <p class="text-xs text-[var(--hh-texto-2)]">{{ $resultado->fecha->format('d/m/Y') }}</p>
                            </div>
                            <flux:badge :color="$resultado->conforme ? 'green' : 'red'" size="sm">{{ $resultado->conforme ? 'Conforme' : 'No conforme' }}</flux:badge>
                        </li>
                    @endforeach
                </ul>
            @endif
        </x-ui.panel>
    </div>

    <x-ui.panel titulo="Comunicados recientes" :indice="9">
        @if ($comunicados->isEmpty())
            <x-ui.vacio titulo="Sin comunicados" descripcion="Los avisos para el personal de planta aparecerán aquí." icono="megaphone" />
        @else
            <ul class="flex flex-col divide-y divide-[var(--hh-borde)]">
                @foreach ($comunicados as $comunicado)
                    <li class="py-3 first:pt-0 last:pb-0">
                        <p class="text-sm font-semibold text-[var(--hh-texto)]">{{ $comunicado->titulo }}</p>
                        <p class="text-xs text-[var(--hh-texto-2)]">{{ $comunicado->fecha->format('d/m/Y') }}</p>
                    </li>
                @endforeach
            </ul>
        @endif
    </x-ui.panel>

    <section aria-labelledby="accesos-titulo">
        <h2 id="accesos-titulo" class="hh-entra mb-4 text-lg font-semibold tracking-tight text-[var(--hh-texto)]" style="--i: 10">Accesos rápidos</h2>
        @php
            $accesos = [
                ['planta.laboratorio', 'Laboratorio', 'Registra y consulta los análisis de calidad.', 'beaker'],
                ['planta.entregas', 'Entregas de acopiadores', 'Revisa lo que llega a la planta cada turno.', 'truck'],
                ['planta.pagos', 'Pago a proveedores', 'Calcula y registra los pagos por periodo.', 'banknotes'],
                ['planta.rutas', 'Rutas', 'Mapa y recorridos de acopio.', 'map'],
                ['asistencia.mis', 'Asistencia', 'Revisa tus marcas y justifica faltas.', 'calendar-days'],
                ['planta.comunicados', 'Comunicados', 'Avisos de la empresa para tu rol.', 'megaphone'],
            ];
        @endphp
        <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
            @foreach ($accesos as [$ruta, $titulo, $descripcion, $icono])
                @if(auth('web')->user()->puedePantalla(\App\Authorization\Pantallas::deRuta($ruta) ?? $ruta))
                <x-ui.accion :href="route($ruta)" :titulo="$titulo" :descripcion="$descripcion" :icono="$icono" :indice="11 + $loop->index" />
                @endif
            @endforeach
        </div>
    </section>
</div>

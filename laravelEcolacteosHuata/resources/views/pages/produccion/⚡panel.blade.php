<?php

use App\Enums\EstadoProduccion;
use App\Enums\Rol;
use App\Models\Insumo;
use App\Models\Produccion;
use App\Models\Receta;
use Livewire\Component;

new class extends Component
{
    public function with(): array
    {
        $fecha = now(config('app.zona_horaria_local'))->toDateString();

        return [
            'totalInsumos' => Insumo::query()->count(),
            'totalRecetas' => Receta::query()->count(),
            'produccionHoy' => Produccion::query()->whereDate('fecha', $fecha)->sum('cantidad_producida'),
            'produccionesPendientes' => Produccion::query()->where('estado', '!=', EstadoProduccion::Completada->value)->count(),
            'productosPublicados' => Produccion::catalogoPublico()->count(),
            'insumosStockBajo' => Insumo::query()->with('unidadMedida')->where('stock_actual', '<=', 10)->orderBy('stock_actual')->get(),
            'ultimasProducciones' => Produccion::query()->with('receta')->latest('fecha')->latest('id')->limit(5)->get(),
        ];
    }
};
?>

<div class="flex flex-col gap-8">
    <x-ui.page-header
        titulo="Panel de producción"
        :subtitulo="'Bienvenido, '.auth()->user()->nombreCompleto().'. Controla recetas, insumos y lo que sale de la planta.'"
        :etiqueta="Rol::Produccion->etiquetaPanel()"
    >
        @if(auth('web')->user()->puedePantalla(\App\Authorization\Pantallas::deRuta('produccion.registros') ?? 'produccion.registros'))
        <flux:button as="a" :href="route('produccion.registros')" wire:navigate variant="primary" icon="plus" size="sm">Registrar producción</flux:button>
        @endif
    </x-ui.page-header>

    <section aria-label="Resumen de producción" class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <x-ui.kpi etiqueta="Producción de hoy" :valor="number_format((float) $produccionHoy, 2)" icono="cube" color="#23865b" detalle="unidades producidas" :indice="0" />
        <x-ui.kpi etiqueta="Producciones pendientes" :valor="$produccionesPendientes" icono="clock" detalle="planificadas o en proceso" :indice="1" />
        <x-ui.kpi etiqueta="Recetas registradas" :valor="$totalRecetas" icono="clipboard-document-list" :detalle="$productosPublicados.' producto(s) publicado(s) con stock'" :indice="2" />
        <x-ui.kpi
            etiqueta="Insumos con stock bajo"
            :valor="$insumosStockBajo->count()"
            icono="exclamation-triangle"
            :color="$insumosStockBajo->isNotEmpty() ? '#c93445' : '#2864b4'"
            :detalle="'de '.$totalInsumos.' insumos'"
            :indice="3"
        />
    </section>

    <div class="grid gap-6 xl:grid-cols-5">
        <x-ui.panel titulo="Stock bajo de insumos" descripcion="Insumos con 10 o menos unidades: conviene reponerlos pronto." :indice="4" class="xl:col-span-2">
            @if ($insumosStockBajo->isEmpty())
                <x-ui.vacio titulo="Todo el stock está al día" descripcion="Ningún insumo está en el nivel mínimo." icono="check-circle" />
            @else
                <ul class="flex flex-col gap-4">
                    @foreach ($insumosStockBajo as $insumo)
                        <li>
                            <div class="mb-1.5 flex items-baseline justify-between gap-3 text-sm">
                                <span class="truncate font-medium text-[var(--hh-texto)]">{{ $insumo->nombre }}</span>
                                <span class="shrink-0 font-semibold tabular-nums text-[var(--hh-peligro)]">{{ number_format((float) $insumo->stock_actual, 2) }} {{ $insumo->unidadMedida->simbolo }}</span>
                            </div>
                            <div class="hh-barra" style="--hh-barra-color: #c93445" role="img" aria-label="{{ $insumo->nombre }}: {{ number_format((float) $insumo->stock_actual, 2) }} {{ $insumo->unidadMedida->simbolo }}">
                                <span style="--hh-ancho: {{ max(3, min(100, (float) $insumo->stock_actual * 10)) }}%; --i: {{ $loop->index }}"></span>
                            </div>
                        </li>
                    @endforeach
                </ul>
            @endif
        </x-ui.panel>

        <x-ui.panel titulo="Últimas producciones" :indice="5" :relleno="false" class="xl:col-span-3">
            <div class="max-h-80 overflow-auto">
                <flux:table>
                    <flux:table.columns sticky>
                        <flux:table.column>Fecha</flux:table.column>
                        <flux:table.column>Receta</flux:table.column>
                        <flux:table.column>Lote</flux:table.column>
                        <flux:table.column>Cantidad</flux:table.column>
                        <flux:table.column>Estado</flux:table.column>
                    </flux:table.columns>
                    <flux:table.rows>
                        @forelse ($ultimasProducciones as $produccion)
                            <flux:table.row wire:key="up-{{ $produccion->id }}">
                                <flux:table.cell>{{ $produccion->fecha->format('d/m/Y') }}</flux:table.cell>
                                <flux:table.cell>{{ $produccion->nombre ?? $produccion->receta->nombre }}</flux:table.cell>
                                <flux:table.cell>{{ $produccion->lote }}</flux:table.cell>
                                <flux:table.cell class="tabular-nums">{{ number_format((float) $produccion->cantidad_producida, 2) }}</flux:table.cell>
                                <flux:table.cell>
                                    <flux:badge :color="$produccion->estado->value === 'completada' ? 'green' : 'amber'" size="sm">
                                        {{ $produccion->estado->label() }}
                                    </flux:badge>
                                </flux:table.cell>
                            </flux:table.row>
                        @empty
                            <flux:table.row><flux:table.cell colspan="5" class="text-center text-zinc-500">Todavía no hay producciones registradas.</flux:table.cell></flux:table.row>
                        @endforelse
                    </flux:table.rows>
                </flux:table>
            </div>
        </x-ui.panel>
    </div>

    <section aria-labelledby="accesos-titulo">
        <h2 id="accesos-titulo" class="hh-entra mb-4 text-lg font-semibold tracking-tight text-[var(--hh-texto)]" style="--i: 6">Accesos rápidos</h2>
        @php
            $accesos = [
                ['produccion.registros', 'Producción', 'Registra lotes y publícalos para la venta.', 'cube'],
                ['produccion.recetas', 'Recetas', 'Ingredientes, costo y precio de venta.', 'clipboard-document-list'],
                ['produccion.insumos', 'Insumos', 'Stock y costo de cada insumo.', 'archive-box'],
                ['produccion.unidades', 'Unidades de medida', 'Kilos, litros y demás unidades.', 'scale'],
                ['produccion.productos', 'Productos', 'Lo publicado y disponible para vender.', 'shopping-bag'],
                ['produccion.compras', 'Compras', 'Registra las compras de insumos.', 'credit-card'],
                ['produccion.inventario', 'Inventario', 'Existencias de insumos y productos.', 'clipboard-document-check'],
                ['asistencia.mis', 'Asistencia', 'Tus marcas y justificaciones.', 'calendar-days'],
            ];
        @endphp
        <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            @foreach ($accesos as [$ruta, $titulo, $descripcion, $icono])
                @if(auth('web')->user()->puedePantalla(\App\Authorization\Pantallas::deRuta($ruta) ?? $ruta))
                <x-ui.accion :href="route($ruta)" :titulo="$titulo" :descripcion="$descripcion" :icono="$icono" :indice="7 + $loop->index" />
                @endif
            @endforeach
        </div>
    </section>
</div>

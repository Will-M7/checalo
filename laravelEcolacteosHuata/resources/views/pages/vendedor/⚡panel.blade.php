<?php

use App\Enums\EstadoPedidoWeb;
use App\Enums\EstadoProduccion;
use App\Enums\Rol;
use App\Models\PedidoWeb;
use App\Models\Produccion;
use App\Models\Venta;
use Livewire\Component;

new class extends Component
{
    public function with(): array
    {
        $fecha = now(config('app.zona_horaria_local'))->toDateString();
        $ventasHoy = Venta::query()->whereDate('fecha', $fecha)->get();

        $disponibles = Produccion::query()
            ->where('estado', EstadoProduccion::Completada)
            ->with('receta.detalles.insumo')
            ->get()
            ->map(fn (Produccion $p) => [
                'nombre' => $p->nombre ?? $p->receta->nombre,
                'disponible' => $p->cantidadDisponible(),
                'valor' => $p->cantidadDisponible() * $p->receta->precioVenta(),
            ]);

        return [
            'totalVentasHoy' => $ventasHoy->sum('total'),
            'boletasHoy' => $ventasHoy->count(),
            'produccionHoy' => Produccion::query()->whereDate('fecha', $fecha)->sum('cantidad_producida'),
            'valorInventario' => $disponibles->sum('valor'),
            'productosStockBajo' => $disponibles->filter(fn (array $d) => $d['disponible'] <= 5)->sortBy('disponible')->values(),
            'pedidosPendientes' => PedidoWeb::query()->where('estado', EstadoPedidoWeb::Pendiente)->count(),
            'ultimasVentas' => Venta::query()->with('cliente')->latest('id')->limit(5)->get(),
        ];
    }
};
?>

<div class="flex flex-col gap-8">
    @if(auth('web')->user()->puedePantalla('vendedor.pedidos-web'))<livewire:disponibilidad-web />@endif
    <x-ui.page-header
        titulo="Panel de ventas"
        :subtitulo="'Bienvenido, '.auth()->user()->nombreCompleto().'. Vende, factura y atiende los pedidos de la web.'"
        :etiqueta="Rol::Vendedor->etiquetaPanel()"
    >
        @if(auth('web')->user()->puedePantalla(\App\Authorization\Pantallas::deRuta('vendedor.ventas') ?? 'vendedor.ventas'))
        <flux:button as="a" :href="route('vendedor.ventas')" wire:navigate variant="primary" icon="shopping-cart" size="sm">Nueva venta</flux:button>
        @endif
    </x-ui.page-header>

    <section aria-label="Resumen de ventas" class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <x-ui.kpi etiqueta="Ventas de hoy" :valor="'S/ '.number_format((float) $totalVentasHoy, 2)" icono="banknotes" color="#23865b" :detalle="$boletasHoy.' boleta(s)'" :indice="0" />
        @if(auth('web')->user()->puedePantalla('vendedor.pedidos-web'))
        <a href="{{ route('vendedor.pedidos-web') }}" wire:navigate class="block rounded-[var(--hh-radio)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--hh-marca)]">
            <x-ui.kpi
                etiqueta="Compras web pendientes"
                :valor="$pedidosPendientes"
                icono="inbox-arrow-down"
                :color="$pedidosPendientes > 0 ? '#c93445' : '#2864b4'"
                :detalle="$pedidosPendientes > 0 ? 'Confirma y genera la boleta' : 'Al día'"
                :indice="1"
            />
        </a>
        @endif
        <x-ui.kpi etiqueta="Valor del inventario" :valor="'S/ '.number_format((float) $valorInventario, 2)" icono="archive-box" detalle="al precio de venta" :indice="2" />
        <x-ui.kpi
            etiqueta="Productos con stock bajo"
            :valor="$productosStockBajo->count()"
            icono="exclamation-triangle"
            :color="$productosStockBajo->isNotEmpty() ? '#c93445' : '#2864b4'"
            detalle="stock de 5 unidades o menos"
            :indice="3"
        />
    </section>

    <div class="grid gap-6 xl:grid-cols-5">
        <x-ui.panel titulo="Últimas ventas" :indice="4" :relleno="false" class="xl:col-span-3">
            <div class="max-h-80 overflow-auto">
                <flux:table>
                    <flux:table.columns sticky>
                        <flux:table.column>Boleta</flux:table.column>
                        <flux:table.column>Cliente</flux:table.column>
                        <flux:table.column>Total</flux:table.column>
                        <flux:table.column>Fecha</flux:table.column>
                    </flux:table.columns>
                    <flux:table.rows>
                        @forelse ($ultimasVentas as $venta)
                            <flux:table.row wire:key="uv-{{ $venta->id }}">
                                <flux:table.cell class="whitespace-nowrap font-medium">{{ $venta->numero_boleta }}</flux:table.cell>
                                <flux:table.cell>{{ $venta->cliente?->nombres.' '.$venta->cliente?->apellidos ?: ($venta->cliente_nombre_libre ?: 'Venta anónima') }}</flux:table.cell>
                                <flux:table.cell class="whitespace-nowrap tabular-nums">S/ {{ number_format((float) $venta->total, 2) }}</flux:table.cell>
                                <flux:table.cell class="whitespace-nowrap">{{ $venta->fecha->format('d/m/Y') }}</flux:table.cell>
                            </flux:table.row>
                        @empty
                            <flux:table.row><flux:table.cell colspan="4" class="text-center text-zinc-500">Todavía no hay ventas registradas.</flux:table.cell></flux:table.row>
                        @endforelse
                    </flux:table.rows>
                </flux:table>
            </div>
        </x-ui.panel>

        <x-ui.panel titulo="Stock bajo" descripcion="Productos que se están agotando." :indice="5" class="xl:col-span-2">
            @if ($productosStockBajo->isEmpty())
                <x-ui.vacio titulo="Stock al día" descripcion="Ningún producto está por agotarse." icono="check-circle" />
            @else
                <ul class="flex flex-col gap-4">
                    @foreach ($productosStockBajo as $item)
                        <li>
                            <div class="mb-1.5 flex items-baseline justify-between gap-3 text-sm">
                                <span class="truncate font-medium text-[var(--hh-texto)]">{{ $item['nombre'] }}</span>
                                <span class="shrink-0 font-semibold tabular-nums text-[var(--hh-peligro)]">{{ number_format($item['disponible'], 2) }}</span>
                            </div>
                            <div class="hh-barra" style="--hh-barra-color: #c93445" role="img" aria-label="{{ $item['nombre'] }}: {{ number_format($item['disponible'], 2) }}">
                                <span style="--hh-ancho: {{ max(3, min(100, $item['disponible'] * 20)) }}%; --i: {{ $loop->index }}"></span>
                            </div>
                        </li>
                    @endforeach
                </ul>
            @endif
        </x-ui.panel>
    </div>

    <section aria-labelledby="accesos-titulo">
        <h2 id="accesos-titulo" class="hh-entra mb-4 text-lg font-semibold tracking-tight text-[var(--hh-texto)]" style="--i: 6">Accesos rápidos</h2>
        @php
            $accesos = [
                ['vendedor.ventas', 'Venta', 'Punto de venta: carrito, descuentos y boleta.', 'shopping-cart'],
                ['vendedor.pedidos-web', 'Compras por web', 'Pedidos de clientes que pagaron en línea.', 'inbox-arrow-down'],
                ['vendedor.produccion', 'Productos', 'Lo que hay disponible para vender.', 'cube'],
                ['vendedor.historial', 'Historial de ventas', 'Consulta y reimprime boletas.', 'clock'],
                ['vendedor.clientes', 'Clientes', 'Clientes frecuentes y sus compras.', 'users'],
                ['vendedor.inventario', 'Inventario', 'Existencias y valor al precio de venta.', 'archive-box'],
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

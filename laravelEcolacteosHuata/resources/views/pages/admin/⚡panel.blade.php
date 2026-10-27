<?php

use App\Enums\Rol;
use App\Models\Comunicado;
use App\Models\PedidoWeb;
use App\Models\Recojo;
use App\Models\User;
use App\Models\Venta;
use Livewire\Component;

new class extends Component
{
    public function with(): array
    {
        $hoy = now(config('app.zona_horaria_local'))->toDateString();
        $totalUsuarios = User::query()->count();
        $usuariosActivos = User::query()->where('activo', true)->count();

        $porRol = collect(Rol::cases())->map(fn (Rol $rol) => [
            'label' => $rol->label(),
            'total' => User::query()->where('rol', $rol)->count(),
        ]);

        return [
            'totalUsuarios' => $totalUsuarios,
            'usuariosActivos' => $usuariosActivos,
            'porRol' => $porRol,
            'mayorPorRol' => max(1, (int) $porRol->max('total')),
            'recojosHoy' => (float) Recojo::query()->whereDate('fecha', $hoy)->sum('litros'),
            'ventasHoy' => (float) Venta::query()->whereDate('fecha', $hoy)->sum('total'),
            'pedidosPendientes' => PedidoWeb::query()->where('estado', 'pendiente')->count(),
            'ultimasVentas' => Venta::query()->latest('id')->limit(4)->get(['id', 'numero_boleta', 'cliente_nombre_libre', 'cliente_id', 'total', 'fecha']),
            'comunicados' => Comunicado::query()->latest('fecha')->latest('id')->limit(3)->get(['id', 'titulo', 'fecha']),
        ];
    }
};
?>

<div class="flex flex-col gap-8">
    <x-ui.page-header
        titulo="Panel de administración"
        subtitulo="Supervisa y gestiona las operaciones de Ecolácteos Huata."
        :etiqueta="Rol::Administrador->etiquetaPanel()"
    >
        <flux:button as="a" :href="route('home')" target="_blank" icon="arrow-top-right-on-square" size="sm">Ver sitio web</flux:button>
    </x-ui.page-header>

    {{-- Indicadores principales --}}
    <section aria-label="Indicadores de usuarios" class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <x-ui.kpi etiqueta="Usuarios totales" :valor="$totalUsuarios" icono="users" color="#2864b4" :indice="0" />
        <x-ui.kpi
            etiqueta="Usuarios activos"
            :valor="$usuariosActivos"
            icono="user-circle"
            color="#23865b"
            :detalle="$totalUsuarios - $usuariosActivos > 0 ? ($totalUsuarios - $usuariosActivos).' inactivos' : 'Todos activos'"
            :progreso="$totalUsuarios > 0 ? $usuariosActivos / $totalUsuarios * 100 : null"
            :indice="1"
        />
        @foreach ($porRol->take(2) as $item)
            <x-ui.kpi
                :etiqueta="$item['label']"
                :valor="$item['total']"
                :icono="$loop->first ? 'shield-check' : 'building-office-2'"
                :color="$loop->first ? '#173d72' : '#f2c230'"
                :indice="2 + $loop->index"
            />
        @endforeach
    </section>

    {{-- Operación de hoy: datos reales del día --}}
    <section aria-label="Operación de hoy" class="grid gap-4 sm:grid-cols-3">
        <x-ui.kpi etiqueta="Litros recogidos hoy" :valor="number_format($recojosHoy, 0).' L'" icono="beaker" color="#2864b4" :indice="4" />
        <x-ui.kpi etiqueta="Ventas de hoy" :valor="'S/ '.number_format($ventasHoy, 2)" icono="banknotes" color="#2864b4" :indice="5" />
        @if(auth('web')->user()->puedePantalla('vendedor.pedidos-web'))
        <a href="{{ route('vendedor.pedidos-web') }}" wire:navigate class="block rounded-[var(--hh-radio)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--hh-marca)]">
            <x-ui.kpi etiqueta="Compras web pendientes" :valor="$pedidosPendientes" icono="inbox-arrow-down" :color="$pedidosPendientes > 0 ? '#c93445' : '#64748b'" :detalle="$pedidosPendientes > 0 ? 'Requieren atención' : 'Al día'" :indice="6" />
        </a>
        @endif
    </section>

    <div class="grid gap-6 xl:grid-cols-5">
        {{-- Distribución de usuarios por rol (conteos reales) --}}
        <x-ui.panel titulo="Usuarios por rol" descripcion="Distribución del equipo según su rol principal." :indice="7" class="xl:col-span-3">
            <ul class="flex flex-col gap-4">
                @foreach ($porRol as $item)
                    <li class="grid grid-cols-[minmax(0,12rem)_1fr_2rem] items-center gap-3 text-sm">
                        <span class="truncate font-medium text-[var(--hh-texto)]">{{ $item['label'] }}</span>
                        <div class="hh-barra" role="img" aria-label="{{ $item['label'] }}: {{ $item['total'] }}">
                            <span style="--hh-ancho: {{ $item['total'] / $mayorPorRol * 100 }}%; --i: {{ $loop->index }}"></span>
                        </div>
                        <span class="text-end font-bold tabular-nums text-[var(--hh-texto)]">{{ $item['total'] }}</span>
                    </li>
                @endforeach
            </ul>
        </x-ui.panel>

        {{-- Actividad reciente: ventas y comunicados reales --}}
        <x-ui.panel titulo="Actividad reciente" descripcion="Últimos movimientos registrados." :indice="8" class="xl:col-span-2">
            @if ($ultimasVentas->isEmpty() && $comunicados->isEmpty())
                <x-ui.vacio titulo="Aún no hay actividad" descripcion="Las ventas y los comunicados aparecerán aquí a medida que se registren." icono="clock" />
            @else
                <ul class="flex flex-col divide-y divide-[var(--hh-borde)]">
                    @foreach ($ultimasVentas as $venta)
                        <li class="flex items-center gap-3 py-3 first:pt-0">
                            <span class="grid size-9 shrink-0 place-items-center rounded-lg border border-[var(--hh-borde)] text-[var(--hh-marca)]"><flux:icon icon="receipt-percent" class="size-4" /></span>
                            <div class="min-w-0 flex-1">
                                <p class="truncate text-sm font-semibold text-[var(--hh-texto)]">Venta {{ $venta->numero_boleta }}</p>
                                <p class="text-xs text-[var(--hh-texto-2)]">{{ $venta->fecha->format('d/m/Y') }}</p>
                            </div>
                            <span class="text-sm font-bold tabular-nums text-[var(--hh-texto)]">S/ {{ number_format((float) $venta->total, 2) }}</span>
                        </li>
                    @endforeach
                    @foreach ($comunicados as $comunicado)
                        <li class="flex items-center gap-3 py-3 last:pb-0">
                            <span class="grid size-9 shrink-0 place-items-center rounded-lg border border-[var(--hh-borde)] text-[var(--hh-marca)]"><flux:icon icon="megaphone" class="size-4" /></span>
                            <div class="min-w-0 flex-1">
                                <p class="truncate text-sm font-semibold text-[var(--hh-texto)]">{{ $comunicado->titulo }}</p>
                                <p class="text-xs text-[var(--hh-texto-2)]">Comunicado · {{ $comunicado->fecha->format('d/m/Y') }}</p>
                            </div>
                        </li>
                    @endforeach
                </ul>
            @endif
        </x-ui.panel>
    </div>

    {{-- Centro de accesos rápidos --}}
    <section aria-labelledby="accesos-titulo">
        <h2 id="accesos-titulo" class="hh-entra mb-4 text-lg font-semibold tracking-tight text-[var(--hh-texto)]" style="--i: 9">Accesos rápidos</h2>
        @php
            $accesos = [
                ['admin.usuarios', 'Usuarios', 'Crea, edita y desactiva las cuentas del equipo.', 'users'],
                ['admin.asignar-roles', 'Asignar roles', 'Define el rol principal y los accesos de cada persona.', 'identification'],
                ['admin.descuentos', 'Descuentos', 'Administra los descuentos que se aplican en las ventas.', 'tag'],
                ['admin.precios', 'Precios de la leche', 'Actualiza el precio por litro que se paga a proveedores.', 'currency-dollar'],
                ['planta.rutas', 'Rutas', 'Revisa los recorridos de acopio y sus proveedores.', 'map-pin'],
                ['admin.turnos', 'Turnos', 'Configura los turnos de rutas y de producción.', 'clock'],
                ['admin.empresa', 'Empresa', 'Datos de la empresa que aparecen en boletas y reportes.', 'building-office'],
                ['asistencia.tomar', 'Asistencia', 'Toma la asistencia y revisa justificaciones.', 'calendar-days'],
                ['planta.comunicados', 'Comunicados', 'Publica avisos y asesoramiento para cada rol.', 'megaphone'],
                ['vendedor.pedidos-web', 'Compras por web', 'Confirma pedidos de clientes y genera su boleta.', 'inbox-arrow-down'],
            ];
        @endphp
        <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
            @foreach ($accesos as [$ruta, $titulo, $descripcion, $icono])
                @if(auth('web')->user()->puedePantalla(\App\Authorization\Pantallas::deRuta($ruta) ?? $ruta))
                <x-ui.accion :href="route($ruta)" :titulo="$titulo" :descripcion="$descripcion" :icono="$icono" :indice="10 + $loop->index" />
                @endif
            @endforeach
        </div>
    </section>
</div>

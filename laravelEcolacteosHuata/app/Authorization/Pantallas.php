<?php

namespace App\Authorization;

use App\Enums\Rol;

/** Catálogo de código: una asignación habilita la pantalla y sus operaciones ordinarias. */
final class Pantallas
{
    public const CRITICAS = ['admin.usuarios', 'admin.asignar-roles', 'admin.roles'];

    public static function todas(): array
    {
        $items = [];
        foreach (Rol::cases() as $rol) {
            foreach ($rol->navegacion() as $item) {
                $items[$item['route']] = [...$item, 'android' => self::android()[$item['route']] ?? null];
            }
        }
        $items['admin.roles'] = ['route' => 'admin.roles', 'label' => 'Roles y funciones', 'icon' => 'shield-check', 'android' => self::android()['admin.roles']];

        foreach ($items as &$item) {
            $item['area'] = match (explode('.', $item['route'])[0]) {
                'admin' => 'Administración', 'planta' => 'Planta', 'vendedor' => 'Ventas', 'produccion' => 'Producción', 'acopiador' => 'Acopio', 'proveedor' => 'Proveedor', default => 'Asistencia'
            };
        }

        return $items;
    }

    /** Nombre de Screen nativa; null indica función exclusivamente web. No se generan WebViews. */
    public static function android(): array
    {
        return [
            'admin.panel' => 'AdminDashboard', 'admin.usuarios' => 'AdminUsuarios', 'admin.roles' => 'AdminRoles',
            'admin.asignar-roles' => 'AdminAsignarRoles', 'admin.descuentos' => 'AdminDescuentos',
            'admin.precios' => 'AdminPrecios', 'admin.turnos' => 'AdminTurnos', 'admin.empresa' => 'AdminEmpresa',
            'planta.panel' => 'PlantaDashboard', 'planta.laboratorio' => 'PlantaLaboratorio',
            'planta.entregas' => 'PlantaRecepcion', 'planta.pagos' => 'PlantaPagos', 'planta.rutas' => 'PlantaRutas',
            'planta.comunicados' => 'PlantaComunicados', 'acopiador.panel' => 'AcopiadorDashboard',
            'acopiador.recojo' => 'AcopiadorRecojo', 'acopiador.mapa-recojo' => 'AcopiadorMapaRecojo',
            'acopiador.entrega' => 'AcopiadorEntrega', 'acopiador.inventario' => 'AcopiadorInventario',
            'proveedor.panel' => 'ProveedorDashboard', 'proveedor.entregas' => 'ProveedorEntregas',
            'proveedor.laboratorio' => 'ProveedorLaboratorio', 'proveedor.comunicados' => 'ProveedorComunicados',
            'vendedor.panel' => 'VendedorDashboard', 'vendedor.ventas' => 'VendedorVenta',
            'vendedor.produccion' => 'VendedorProductos', 'vendedor.historial' => 'VendedorHistorialVentas',
            'vendedor.clientes' => 'VendedorClientes', 'vendedor.inventario' => 'VendedorInventario',
            'produccion.panel' => 'ProduccionDashboard', 'produccion.registros' => 'ProduccionRegistros',
            'produccion.recetas' => 'ProduccionRecetas', 'produccion.insumos' => 'ProduccionInsumos',
            'produccion.unidades' => 'ProduccionUnidades', 'produccion.productos' => 'ProduccionProductos',
            'produccion.compras' => 'ProduccionCompras', 'produccion.inventario' => 'ProduccionInventario',
            'asistencia.tomar' => 'TomarAsistencia', 'asistencia.mis' => 'MiAsistencia',
        ];
    }

    public static function deRuta(?string $ruta): ?string
    {
        if (isset(self::todas()[$ruta])) {
            return $ruta;
        }

        return match ($ruta) {
            'admin.acopiador-recojo' => 'admin.usuarios', 'admin.acopiador-entrega' => 'admin.usuarios',
            'admin.proveedor-entregas', 'admin.proveedor-laboratorio' => 'admin.usuarios',
            'vendedor.ventas.boleta' => 'vendedor.historial', 'planta.laboratorio.pdf' => 'planta.laboratorio',
            'acopiador.inventario.pdf' => 'acopiador.inventario',
            default => null,
        };
    }

    public static function paraRuta(?string $ruta): array
    {
        if ($ruta === 'vendedor.ventas.boleta') {
            return ['vendedor.historial', 'vendedor.ventas', 'vendedor.pedidos-web'];
        }
        $id = self::deRuta($ruta);

        return $id ? [$id] : [];
    }

    public static function deComponente(string $nombre): ?string
    {
        if (! str_starts_with($nombre, 'pages::')) {
            return null;
        }

        return self::deRuta(substr($nombre, 7));
    }

    /** Matriz de escrituras móviles: dependencias de lectura no conceden escritura. */
    public static function escritura(string $tabla): array
    {
        return match ($tabla) {
            'empresa' => ['admin.empresa'], 'precio_leche' => ['admin.precios'], 'descuento' => ['admin.descuentos'],
            'usuario', 'acopiador', 'proveedor' => ['admin.usuarios'],
            'turno', 'turno_produccion' => ['admin.turnos'], 'ruta' => ['planta.rutas'],
            'cliente' => ['vendedor.clientes'], 'unidad_medida' => ['produccion.unidades'],
            'insumo' => ['produccion.insumos'], 'receta', 'receta_detalle' => ['produccion.recetas'],
            'comunicado' => ['planta.comunicados'], 'recojo' => ['acopiador.recojo', 'admin.usuarios'],
            'resultado_laboratorio' => ['planta.laboratorio'], 'entrega_planta' => ['acopiador.entrega', 'admin.usuarios'],
            'pago_proveedor' => ['planta.pagos', 'acopiador.recojo'],
            'compra', 'compra_detalle' => ['produccion.compras'], 'produccion' => ['produccion.registros'],
            'venta' => ['vendedor.ventas', 'vendedor.historial'],
            'horario_asistencia', 'dia_asistencia_acopiador' => ['asistencia.tomar'],
            'asistencia', 'justificacion' => ['asistencia.tomar', 'asistencia.mis'],
            default => [],
        };
    }

    /** Accesos efectivos anteriores (incluye rutas directas, además de navegación). Solo importación. */
    public static function heredadas(Rol $rol): array
    {
        $prefijos = match ($rol) {
            Rol::Administrador => ['admin.', 'planta.', 'vendedor.', 'produccion.'],
            default => [$rol->routePrefix().'.'],
        };
        $ids = array_keys(array_filter(self::todas(), fn ($p) => collect($prefijos)->contains(fn ($prefix) => str_starts_with($p['route'], $prefix))));
        $ids[] = 'asistencia.mis';
        if ($rol === Rol::Administrador) {
            $ids[] = 'asistencia.tomar';
        }

        return array_values(array_unique($ids));
    }
}

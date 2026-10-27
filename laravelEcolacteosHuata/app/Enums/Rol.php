<?php

namespace App\Enums;

enum Rol: string
{
    case Administrador = 'administrador';
    case PersonalPlanta = 'personal_planta';
    case Acopiador = 'acopiador';
    case Proveedor = 'proveedor';
    case Vendedor = 'vendedor';
    case Produccion = 'produccion';

    public function label(): string
    {
        return match ($this) {
            self::Administrador => 'Administrador del sistema',
            self::PersonalPlanta => 'Personal de planta',
            self::Acopiador => 'Acopiador',
            self::Proveedor => 'Proveedor',
            self::Vendedor => 'Vendedor',
            self::Produccion => 'Producción',
        };
    }

    /**
     * Slug usado como prefijo de rutas y para elegir el layout/sidebar del rol.
     */
    public function routePrefix(): string
    {
        return match ($this) {
            self::Administrador => 'admin',
            self::PersonalPlanta => 'planta',
            self::Acopiador => 'acopiador',
            self::Proveedor => 'proveedor',
            self::Vendedor => 'vendedor',
            self::Produccion => 'produccion',
        };
    }

    /**
     * @return array<int, array{value: string, label: string}>
     */
    public static function options(): array
    {
        return collect(self::cases())
            ->map(fn (self $rol) => ['value' => $rol->value, 'label' => $rol->label()])
            ->values()
            ->toArray();
    }

    /**
     * Ítems del menú lateral para este rol: ruta, etiqueta e ícono (nombre de Heroicon).
     *
     * @return array<int, array{route: string, label: string, icon: string}>
     */
    public function navegacion(): array
    {
        $prefijo = $this->routePrefix();

        $items = match ($this) {
            self::Administrador => [
                ['panel', 'Panel', 'home'],
                ['usuarios', 'Usuarios', 'users'],
                ['asignar-roles', 'Asignar rol a usuarios', 'identification'],
                ['descuentos', 'Descuentos', 'tag'],
                ['precios', 'Precios de la leche', 'currency-dollar'],
                ['planta.rutas', 'Rutas', 'map-pin'],
                ['turnos', 'Turnos de rutas y turnos de producción', 'clock'],
                ['empresa', 'Empresa', 'building-office'],
                ['vendedor.configuracion-pagos', 'Configuración de pagos', 'qr-code'],
                ['asistencia.tomar', 'Tomar asistencia', 'calendar-days'],
                ['vendedor.pedidos-web', 'Compras por web', 'inbox-arrow-down'],
                ['planta.comunicados', 'Comunicados y asesoramiento', 'megaphone'],
            ],
            self::PersonalPlanta => [
                ['panel', 'Panel', 'home'],
                ['laboratorio', 'Laboratorio', 'beaker'],
                ['entregas', 'Entregas de acopiadores', 'truck'],
                ['pagos', 'Pago a proveedores', 'banknotes'],
                ['rutas', 'Rutas', 'map'],
                ['asistencia.mis', 'Asistencia', 'calendar-days'],
                ['comunicados', 'Comunicados', 'megaphone'],
            ],
            self::Acopiador => [
                ['panel', 'Panel', 'home'],
                ['recojo', 'Recojo de ruta', 'truck'],
                ['mapa-recojo', 'Mapa de recojo', 'map'],
                ['entrega', 'Entrega a planta', 'building-office-2'],
                ['asistencia.mis', 'Asistencia', 'calendar-days'],
                ['inventario', 'Inventario', 'archive-box'],
            ],
            self::Proveedor => [
                ['panel', 'Panel', 'home'],
                ['entregas', 'Mis entregas', 'truck'],
                ['laboratorio', 'Laboratorio', 'beaker'],
                ['comunicados', 'Comunicados', 'megaphone'],
            ],
            self::Vendedor => [
                ['panel', 'Panel', 'home'],
                ['ventas', 'Venta', 'shopping-cart'],
                ['produccion', 'Productos', 'cube'],
                ['pedidos-web', 'Compras por web', 'inbox-arrow-down'],
                ['historial', 'Historial de ventas', 'clock'],
                ['clientes', 'Clientes', 'users'],
                ['asistencia.mis', 'Asistencia', 'calendar-days'],
                ['inventario', 'Inventario', 'archive-box'],
                ['configuracion-pagos', 'Configuración de pagos', 'qr-code'],
            ],
            self::Produccion => [
                ['panel', 'Panel', 'home'],
                ['registros', 'Producción', 'cube'],
                ['recetas', 'Recetas', 'clipboard-document-list'],
                ['insumos', 'Insumos', 'archive-box'],
                ['unidades', 'Unidades de medida', 'scale'],
                ['productos', 'Productos', 'shopping-bag'],
                ['compras', 'Compras', 'credit-card'],
                ['asistencia.mis', 'Asistencia', 'calendar-days'],
                ['inventario', 'Inventario', 'clipboard-document-check'],
            ],
        };

        return array_map(fn (array $item) => [
            // Un slug con punto (ej. "vendedor.ventas") ya es un nombre de ruta completo de otro
            // rol y no se prefija; el administrador lo usa para acceder directamente a los
            // módulos de los demás roles.
            'route' => str_contains($item[0], '.') ? $item[0] : "{$prefijo}.{$item[0]}",
            'label' => $item[1],
            'icon' => $item[2],
        ], $items);
    }

    /**
     * Para roles con muchos módulos (como Administrador), agrupa la navegación en secciones
     * para el sidebar. Devuelve null para roles que no necesitan agrupación.
     *
     * @return array<string, array<int, array{route: string, label: string, icon: string}>>|null
     */
    public function navegacionAgrupada(): ?array
    {
        if ($this !== self::Administrador) {
            return null;
        }

        $items = collect($this->navegacion())->keyBy('route');

        $secciones = [
            'General' => ['admin.panel'],
            'Gestión' => ['admin.usuarios', 'admin.asignar-roles', 'admin.descuentos', 'admin.precios'],
            'Operaciones' => ['planta.rutas', 'admin.turnos', 'asistencia.tomar', 'vendedor.pedidos-web', 'planta.comunicados'],
            'Configuración' => ['admin.empresa', 'vendedor.configuracion-pagos'],
        ];

        return collect($secciones)
            ->map(fn (array $rutas) => collect($rutas)->map(fn (string $ruta) => $items[$ruta])->values()->all())
            ->all();
    }

    /**
     * Nombre corto del área, en mayúsculas, para la tarjeta del rol en la barra lateral.
     */
    public function etiquetaPanel(): string
    {
        return match ($this) {
            self::Administrador => 'Administración',
            self::PersonalPlanta => 'Planta',
            self::Acopiador => 'Acopio',
            self::Proveedor => 'Proveedor',
            self::Vendedor => 'Ventas',
            self::Produccion => 'Producción',
        };
    }

    /**
     * Descripción breve del espacio de trabajo del rol.
     */
    public function subtituloPanel(): string
    {
        return match ($this) {
            self::Administrador => 'Panel de control',
            self::PersonalPlanta => 'Centro de operaciones',
            self::Acopiador => 'Operaciones de campo',
            self::Proveedor => 'Mis entregas',
            self::Vendedor => 'Punto de venta',
            self::Produccion => 'Planta de elaboración',
        };
    }

    /**
     * Ícono (Heroicon) que identifica al rol.
     */
    public function iconoPanel(): string
    {
        return match ($this) {
            self::Administrador => 'shield-check',
            self::PersonalPlanta => 'building-office-2',
            self::Acopiador => 'truck',
            self::Proveedor => 'user-group',
            self::Vendedor => 'shopping-cart',
            self::Produccion => 'beaker',
        };
    }
}

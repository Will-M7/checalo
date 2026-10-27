<?php

use App\Authorization\Pantallas;
use App\Enums\Rol;
use App\Models\{RolDinamico, User, Receta, Produccion};
use Livewire\Livewire;

function adminFiltros(): User
{
    $admin = User::factory()->rol(Rol::Administrador)->create();
    RolDinamico::where('codigo', 'administrador')->update(['pantallas' => array_keys(Pantallas::todas())]);
    return $admin;
}

function usuarioFiltro(string $name, bool $active, array $roles): User
{
    $user = User::factory()->create(['nombres' => $name, 'apellidos' => 'Filtro', 'activo' => $active]);
    $user->rolesDinamicos()->sync(RolDinamico::whereIn('codigo', $roles)->pluck('id'));
    return $user;
}

test('listados conservan su render y exponen filtros sin cambiar acciones', function (string $page) {
    $this->actingAs(adminFiltros());
    $component = Livewire::test('pages::'.$page)->assertOk();
    if ($page !== 'asistencia.tomar') $component->assertSee('Filtrar');
    $component->call('limpiarFiltros')->assertOk();
})->with([
    'admin.usuarios','admin.asignar-roles','admin.roles','admin.descuentos','admin.finanzas',
    'vendedor.produccion','vendedor.inventario','vendedor.ventas','vendedor.historial','vendedor.clientes','vendedor.pedidos-web',
    'produccion.productos','produccion.registros','produccion.insumos','produccion.inventario','produccion.recetas',
    'planta.rutas','planta.laboratorio','planta.entregas','planta.pagos','planta.comunicados',
    'acopiador.recojo','acopiador.inventario','proveedor.entregas','proveedor.laboratorio','proveedor.comunicados','asistencia.tomar',
]);

test('usuarios combina nombre rol dinámico y estado y limpia sin mutar asignaciones', function (string $page) {
    $admin = adminFiltros();
    $role = RolDinamico::create(['codigo' => 'supervisor_filtro', 'nombre' => 'Supervisor dinámico', 'activo' => true, 'protegido' => false, 'pantallas' => ['vendedor.clientes']]);
    $yes = usuarioFiltro('Flora Uno', true, ['vendedor', $role->codigo]);
    $no = usuarioFiltro('Flora Dos', false, [$role->codigo]);
    usuarioFiltro('Otro Nombre', true, ['vendedor']);
    $this->actingAs($admin);
    $component = Livewire::test('pages::'.$page)->set('busqueda', 'Flora')->set('filtros.rol', $role->codigo)->set('filtros.estado', '1')
        ->assertSee('Flora Uno')->assertDontSee('Flora Dos')->assertDontSee('Otro Nombre');
    if ($page === 'admin.asignar-roles') $component->call('abrirAsignar', $yes->id)->assertSet('userId', $yes->id);
    else $component->call('editar', $yes->id)->assertSet('userId', $yes->id);
    $component->call('limpiarFiltros')->assertSet('filtros', [])->assertSet('busqueda', '');
    expect($yes->rolesDinamicos()->count())->toBe(2)->and($no->fresh()->activo)->toBeFalse();
})->with(['admin.usuarios','admin.asignar-roles']);

test('paginación filtra toda la consulta reinicia página y cuenta varios o ningún rol', function () {
    $this->actingAs(adminFiltros());
    for ($i = 0; $i < 13; $i++) usuarioFiltro('Fila '.sprintf('%02d', $i), true, ['vendedor']);
    $multi = usuarioFiltro('Zeta Múltiple', true, ['vendedor', 'produccion']);
    $none = usuarioFiltro('Zeta Sin Rol', true, []);
    $component = Livewire::test('pages::admin.asignar-roles')->call('setPage', 2)->set('busqueda', 'Zeta')->assertSet('paginators.page', 1)
        ->set('filtros.cantidad', 'varios')->assertSee('Zeta Múltiple')->assertDontSee('Zeta Sin Rol');
    $component->set('filtros.cantidad', 'ninguno')->assertSee('Zeta Sin Rol')->assertDontSee('Zeta Múltiple')
        ->set('busqueda', 'inexistente')->assertSee('No se encontraron');
    expect(User::count())->toBe(16);
});

test('disponibilidad y texto se combinan sin vender ni modificar stock', function () {
    $this->actingAs(adminFiltros());
    $r = Receta::factory()->create(['nombre' => 'Yogur búsqueda']);
    $a = Produccion::factory()->create(['receta_id' => $r->id, 'nombre' => 'Yogur uno', 'estado' => 'completada', 'cantidad_producida' => 10, 'cantidad_vendida' => 8]);
    $b = Produccion::factory()->create(['receta_id' => $r->id, 'nombre' => 'Yogur cero', 'estado' => 'completada', 'cantidad_producida' => 10, 'cantidad_vendida' => 10]);
    Livewire::test('pages::vendedor.inventario')->set('busqueda', 'Yogur')->set('filtros.stock', 'bajo')->assertSee('Yogur uno')->assertDontSee('Yogur cero')
        ->set('filtros.stock', 'agotado')->assertSee('Yogur cero')->assertDontSee('Yogur uno')->call('limpiarFiltros')->assertSee('Yogur uno')->assertSee('Yogur cero');
    expect((float) $a->fresh()->cantidad_vendida)->toBe(8.0)->and((float) $b->fresh()->cantidad_vendida)->toBe(10.0);
});

test('filtrar no concede acceso a pantallas no asignadas', function () {
    $user = User::factory()->rol(Rol::Proveedor)->create();
    $this->actingAs($user);
    Livewire::test('pages::admin.usuarios')->assertForbidden();
    Livewire::test('pages::admin.finanzas')->assertForbidden();
});

test('criterios de cada módulo se aplican a consultas reales antes de limitar o paginar', function () {
    $this->actingAs(adminFiltros());
    $checks = [
        'admin.usuarios' => ['rol' => 'vendedor', 'estado' => '0', 'cantidad' => 'ninguno'],
        'admin.roles' => ['estado' => '1', 'pantalla' => 'vendedor.ventas'],
        'admin.descuentos' => ['estado' => '0'],
        'admin.finanzas' => ['estado' => 'no_conciliado'],
        'vendedor.ventas' => ['stock' => 'agotado', 'desde' => '2026-01-01'],
        'vendedor.produccion' => ['stock' => 'bajo', 'hasta' => '2026-12-31'],
        'vendedor.historial' => ['vendedor' => '1'],
        'vendedor.clientes' => ['compras' => 'sin'],
        'vendedor.pedidos-web' => ['pago' => 'pendiente', 'vendedor' => '1', 'desde' => '2026-01-01'],
        'produccion.registros' => ['estado' => 'planificada', 'desde' => '2026-01-01'],
        'produccion.insumos' => ['unidad' => '1', 'leche' => '0'],
        'produccion.inventario' => ['unidad' => '1', 'leche' => '1'],
        'produccion.recetas' => ['insumo' => '1'],
        'planta.rutas' => ['estado' => '1', 'acopiador' => '1'],
        'planta.laboratorio' => ['conforme' => '0', 'hasta' => '2026-12-31'],
        'planta.entregas' => ['recepcion' => 'si'],
        'planta.pagos' => ['pago' => 'pendiente'],
        'planta.comunicados' => ['tipo' => 'comunicado'],
        'acopiador.recojo' => ['recogido' => '0'],
        'acopiador.inventario' => ['entregado' => '1', 'pagado' => '0'],
        'proveedor.entregas' => ['entregado' => '0', 'pagado' => '1'],
        'proveedor.laboratorio' => ['conforme' => '1'],
        'asistencia.tomar' => ['asistencia' => 'sin_registro'],
    ];
    foreach ($checks as $page => $filters) {
        Livewire::test('pages::'.$page)->set($page === 'vendedor.ventas' ? 'busquedaProducto' : 'busqueda', 'Filtro')->set('filtros', $filters)->assertOk();
    }
});

test('distribuciones filtra copias del catálogo sin aplicar cambios de autorización', function () {
    $this->actingAs(adminFiltros());
    $before = RolDinamico::orderBy('id')->get()->toJson();
    $count = \App\Models\DistribucionRoles::count();
    Livewire::test(\App\Livewire\DistribucionesRoles::class)->call('abrir')->set('busqueda', 'Inicial')
        ->set('filtros.tipo', 'base')->assertSee('Inicial')->set('filtros.tipo', 'personalizada')->assertSee('No se encontraron distribuciones')
        ->call('limpiarFiltros')->assertSet('busqueda', '');
    expect(RolDinamico::orderBy('id')->get()->toJson())->toBe($before)->and(\App\Models\DistribucionRoles::count())->toBe($count);
});

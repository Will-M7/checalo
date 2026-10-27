<?php

use App\Authorization\Distribuciones;
use App\Enums\Rol;
use App\Enums\TeamRole;
use App\Models\DistribucionRoles;
use App\Models\Team;
use App\Models\User;

function usuarioConRol(Rol $rol): User
{
    $user = User::factory()->rol($rol)->create();
    if ($rol === Rol::Administrador) {
        // Esta prueba comprueba operaciones de la distribución histórica, no el nuevo valor Inicial.
        Distribuciones::confirmar($user, Distribuciones::previsualizar($user, 'aplicar', DistribucionRoles::where('codigo', 'en-desarrollo')->value('id'))['token'], true);
    }
    $team = Team::principal();

    $team->members()->attach($user, ['role' => TeamRole::Owner->value]);
    $user->switchTeam($team);

    return $user->fresh();
}

test('cada rol puede entrar a su propio panel', function (Rol $rol, string $routeName) {
    $user = usuarioConRol($rol);

    $response = $this->actingAs($user)->get(route($routeName));

    $response->assertOk();
})->with([
    [Rol::Administrador, 'admin.panel'],
    [Rol::PersonalPlanta, 'planta.panel'],
    [Rol::Acopiador, 'acopiador.panel'],
    [Rol::Proveedor, 'proveedor.panel'],
    [Rol::Vendedor, 'vendedor.panel'],
    [Rol::Produccion, 'produccion.panel'],
]);

test('un proveedor no puede entrar al panel de administrador', function () {
    $user = usuarioConRol(Rol::Proveedor);

    $response = $this->actingAs($user)->get(route('admin.panel'));

    $response->assertForbidden();
});

test('un acopiador no puede entrar a las pantallas de planta', function () {
    $user = usuarioConRol(Rol::Acopiador);

    $response = $this->actingAs($user)->get(route('planta.entregas'));

    $response->assertForbidden();
});

test('el administrador no puede entrar a las pantallas del acopiador', function () {
    $user = usuarioConRol(Rol::Administrador);

    $response = $this->actingAs($user)->get(route('acopiador.recojo'));

    $response->assertForbidden();
});

test('el administrador puede entrar a los módulos de producción, planta y vendedor', function (string $routeName) {
    $user = usuarioConRol(Rol::Administrador);

    $response = $this->actingAs($user)->get(route($routeName));

    $response->assertOk();
})->with([
    'produccion.insumos',
    'produccion.recetas',
    'produccion.registros',
    'produccion.productos',
    'produccion.inventario',
    'planta.laboratorio',
    'planta.entregas',
    'planta.pagos',
    'planta.rutas',
    'planta.comunicados',
    'vendedor.ventas',
    'vendedor.historial',
    'vendedor.produccion',
    'vendedor.clientes',
    'admin.asignar-roles',
    'admin.turnos',
    'admin.acopiador-recojo',
    'admin.acopiador-entrega',
    'admin.proveedor-entregas',
    'admin.proveedor-laboratorio',
]);

test('login redirige a cada usuario a su panel según su rol', function () {
    $user = usuarioConRol(Rol::Acopiador);

    $response = $this->post(route('login.store'), [
        'usuario' => $user->usuario,
        'password' => 'password',
    ]);

    $response->assertSessionHasNoErrors();
    $this->assertAuthenticated();

    $this->get(route('dashboard'))->assertRedirect(route('acopiador.panel'));
});

<?php

use App\Enums\Rol;
use App\Enums\TeamRole;
use App\Models\Team;
use App\Models\User;

function usuarioDeRol(Rol $rol): User
{
    $usuario = User::factory()->rol($rol)->create();
    $team = Team::principal();
    $team->members()->attach($usuario, ['role' => TeamRole::Owner->value]);
    $usuario->switchTeam($team);

    return $usuario->fresh();
}

test('cada rol ve su panel rediseñado, con el sistema visual y sus accesos rápidos', function (Rol $rol, string $titulo, string $acceso) {
    $usuario = usuarioDeRol($rol);

    $this->actingAs($usuario)
        ->get(route($rol->routePrefix().'.panel'))
        ->assertOk()
        ->assertSee($titulo)
        ->assertSee('Accesos rápidos')
        ->assertSee($acceso)
        ->assertSee('hh-kpi', escape: false)
        ->assertSee($rol->etiquetaPanel());
})->with([
    'personal de planta' => [Rol::PersonalPlanta, 'Panel de planta', 'Pago a proveedores'],
    'acopiador' => [Rol::Acopiador, 'Panel del acopiador', 'Recojo de ruta'],
    'proveedor' => [Rol::Proveedor, 'Panel del proveedor', 'Mis entregas'],
    'producción' => [Rol::Produccion, 'Panel de producción', 'Recetas'],
    'vendedor' => [Rol::Vendedor, 'Panel de ventas', 'Compras por web'],
]);

test('el menú lateral muestra la tarjeta del rol y los módulos de cada rol', function (Rol $rol) {
    $usuario = usuarioDeRol($rol);

    $respuesta = $this->actingAs($usuario)->get(route($rol->routePrefix().'.panel'))->assertOk();

    foreach ($usuario->navegacionDeRol($rol) as $item) {
        $respuesta->assertSee($item['label']);
    }

    $respuesta->assertSee($rol->subtituloPanel());
})->with([Rol::PersonalPlanta, Rol::Acopiador, Rol::Proveedor, Rol::Produccion, Rol::Vendedor]);

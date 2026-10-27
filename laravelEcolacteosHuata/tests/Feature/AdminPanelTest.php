<?php

use App\Enums\Rol;
use App\Enums\TeamRole;
use App\Models\PedidoWeb;
use App\Models\Team;
use App\Models\User;
use App\Models\Venta;

function adminConEquipo(): User
{
    $admin = User::factory()->rol(Rol::Administrador)->create();
    $team = Team::principal();
    $team->members()->attach($admin, ['role' => TeamRole::Owner->value]);
    $admin->switchTeam($team);

    return $admin->fresh();
}

test('el panel del administrador muestra indicadores, usuarios por rol y accesos rápidos con datos reales', function () {
    // Primero los demás usuarios: la URL usa el equipo del último usuario creado.
    User::factory()->rol(Rol::Acopiador)->count(2)->create();
    User::factory()->rol(Rol::Proveedor)->create(['activo' => false]);
    $admin = adminConEquipo();

    $this->actingAs($admin)
        ->get(route('admin.panel'))
        ->assertOk()
        ->assertSee('Panel de administración')
        ->assertSee('Usuarios totales')
        ->assertSee('Usuarios por rol')
        ->assertSee('Accesos rápidos')
        ->assertSee('Compras por web')
        ->assertSee('1 inactivos')
        ->assertSee('Buscar módulos');
});

test('el panel cuenta las compras web pendientes y las ventas del día', function () {
    $admin = adminConEquipo();
    PedidoWeb::factory()->conItem()->count(2)->create();
    Venta::factory()->create(['fecha' => now(config('app.zona_horaria_local'))->toDateString(), 'total' => 40]);

    $this->actingAs($admin)
        ->get(route('admin.panel'))
        ->assertOk()
        ->assertSee('Compras web pendientes')
        ->assertSee('Requieren atención')
        ->assertSee('S/ 40.00');
});

test('quienes no son administradores no entran al panel del administrador', function () {
    $vendedor = User::factory()->rol(Rol::Vendedor)->create();
    $team = Team::principal();
    $team->members()->attach($vendedor, ['role' => TeamRole::Owner->value]);
    $vendedor->switchTeam($team);

    $this->actingAs($vendedor)->get(route('admin.panel'))->assertForbidden();
});

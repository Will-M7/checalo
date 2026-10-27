<?php

use App\Enums\EstadoProduccion;
use App\Enums\Rol;
use App\Enums\TeamRole;
use App\Models\Produccion;
use App\Models\Receta;
use App\Models\Team;
use App\Models\User;
use App\Models\Venta;
use App\Models\VentaItem;
use Livewire\Livewire;

test('abrir el punto de venta con una producción en la URL la agrega automáticamente al carrito', function () {
    $vendedor = User::factory()->rol(Rol::Vendedor)->create();
    $team = Team::principal();
    $team->members()->attach($vendedor, ['role' => TeamRole::Owner->value]);
    $vendedor->switchTeam($team);

    $receta = Receta::factory()->create();
    $produccion = Produccion::factory()->create([
        'nombre' => 'Queso fresco de prueba',
        'receta_id' => $receta->id,
        'cantidad_producida' => 10,
        'estado' => EstadoProduccion::Completada,
    ]);

    $response = $this->actingAs($vendedor)
        ->get(route('vendedor.ventas', ['produccion' => $produccion->id]));

    $response->assertOk();
    $response->assertSee('Queso fresco de prueba');
    $response->assertSee('1 producto(s)', escape: false);
});

test('al vender una producción se descuenta la cantidad disponible en los desaborados de Producción', function () {
    $produccionUser = User::factory()->rol(Rol::Produccion)->create();
    $team = Team::principal();
    $team->members()->attach($produccionUser, ['role' => TeamRole::Owner->value]);
    $produccionUser->switchTeam($team);

    $receta = Receta::factory()->create();
    $produccion = Produccion::factory()->create([
        'nombre' => 'Yogurt de prueba',
        'receta_id' => $receta->id,
        'cantidad_producida' => 10,
        'cantidad_vendida' => 0,
        'estado' => EstadoProduccion::Completada,
    ]);

    $venta = Venta::factory()->create();
    VentaItem::create([
        'venta_id' => $venta->id,
        'produccion_id' => $produccion->id,
        'nombre' => $produccion->nombre,
        'precio_unitario' => 10,
        'cantidad' => 4,
        'subtotal' => 40,
    ]);

    expect($produccion->fresh()->cantidad_vendida)->toEqual(4.0)
        ->and($produccion->fresh()->cantidadDisponible())->toEqual(6.0);

    $this->actingAs($produccionUser);

    Livewire::test('pages::produccion.registros')
        ->assertSee('6.00')
        ->assertSee('4.00');

    Livewire::test('pages::produccion.productos')
        ->assertSee('6.00')
        ->assertSee('4.00');
});

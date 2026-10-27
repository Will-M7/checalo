<?php

use App\Enums\Rol;
use App\Enums\TeamRole;
use App\Models\Compra;
use App\Models\Insumo;
use App\Models\Team;
use App\Models\UnidadMedida;
use App\Models\User;
use Livewire\Livewire;

test('registrar una compra desde insumos crea un registro en el historial de compras', function () {
    $usuario = User::factory()->rol(Rol::Produccion)->create();
    $unidad = UnidadMedida::factory()->create();
    $insumo = Insumo::factory()->create(['nombre' => 'Cuajo', 'unidad_medida_id' => $unidad->id, 'stock_actual' => 10, 'costo_unitario' => 1]);

    $this->actingAs($usuario);

    Livewire::test('pages::produccion.insumos')
        ->call('abrirCompra', $insumo->id)
        ->set('compraCantidad', '20')
        ->set('compraCosto', '2.5')
        ->call('registrarCompra')
        ->assertHasNoErrors();

    $detalle = Compra::latest('id')->first()->detalles()->firstOrFail();

    expect($detalle->insumo_id)->toBe($insumo->id)
        ->and((float) $detalle->cantidad)->toEqual(20.0)
        ->and((float) $detalle->costo_unitario)->toEqual(2.5)
        ->and((float) $detalle->subtotal)->toEqual(50.0);

    Livewire::test('pages::produccion.compras')
        ->assertSee('Cuajo')
        ->assertSee('50.00');
});

test('el historial de compras solo muestra compras dentro del rango de fechas filtrado', function () {
    $usuario = User::factory()->rol(Rol::Produccion)->create();
    $insumoDentro = Insumo::factory()->create(['nombre' => 'Sal industrial']);
    $insumoFuera = Insumo::factory()->create(['nombre' => 'Cera de abeja']);

    $dentro = Compra::factory()->create(['fecha' => now()->subDays(2)->toDateString()]);
    $dentro->detalles()->create(['insumo_id' => $insumoDentro->id, 'cantidad' => 5, 'costo_unitario' => 1, 'subtotal' => 5]);

    // Fuera del rango por defecto (últimos 30 días).
    $fuera = Compra::factory()->create(['fecha' => now()->subDays(60)->toDateString()]);
    $fuera->detalles()->create(['insumo_id' => $insumoFuera->id, 'cantidad' => 5, 'costo_unitario' => 1, 'subtotal' => 999]);

    $this->actingAs($usuario);

    // Ambos insumos aparecen en el selector de filtro (lista todos los insumos), así que la
    // aserción negativa se hace sobre el subtotal de la fila, no sobre el nombre del insumo.
    Livewire::test('pages::produccion.compras')
        ->assertSee('S/ 5.00')
        ->assertDontSee('S/ 999.00');
});

test('el historial de compras filtra por insumo y se puede llegar con el filtro ya aplicado por url', function () {
    $usuario = User::factory()->rol(Rol::Produccion)->create();
    $team = Team::principal();
    $team->members()->attach($usuario, ['role' => TeamRole::Owner->value]);
    $usuario->switchTeam($team);

    $insumoA = Insumo::factory()->create(['nombre' => 'Leche fresca']);
    $insumoB = Insumo::factory()->create(['nombre' => 'Cultivo láctico']);

    $compra = Compra::factory()->create(['fecha' => now()->toDateString()]);
    $compra->detalles()->create(['insumo_id' => $insumoA->id, 'cantidad' => 10, 'costo_unitario' => 1.8, 'subtotal' => 18]);
    $compra->detalles()->create(['insumo_id' => $insumoB->id, 'cantidad' => 2, 'costo_unitario' => 0.5, 'subtotal' => 777]);

    $this->actingAs($usuario);

    // Ambos insumos aparecen en el selector de filtro (lista todos los insumos), así que la
    // aserción negativa se hace sobre el subtotal de la fila, no sobre el nombre del insumo.
    // Se llega a la página con el filtro de insumo ya aplicado por query string (por ejemplo,
    // desde el enlace "Última compra" del Inventario).
    $this->get(route('produccion.compras', ['insumo' => $insumoA->id]))
        ->assertSee('S/ 18.00')
        ->assertDontSee('S/ 777.00');
});

test('el historial de compras muestra si el precio subió o bajó respecto a la compra anterior del mismo insumo', function () {
    $usuario = User::factory()->rol(Rol::Produccion)->create();
    $insumo = Insumo::factory()->create(['nombre' => 'Cuajo']);

    $primera = Compra::factory()->create(['fecha' => now()->subDays(10)->toDateString()]);
    $primera->detalles()->create(['insumo_id' => $insumo->id, 'cantidad' => 5, 'costo_unitario' => 2, 'subtotal' => 10]);

    $segunda = Compra::factory()->create(['fecha' => now()->toDateString()]);
    $segunda->detalles()->create(['insumo_id' => $insumo->id, 'cantidad' => 5, 'costo_unitario' => 3, 'subtotal' => 15]);

    $this->actingAs($usuario);

    Livewire::test('pages::produccion.compras')
        ->assertSee('+S/ 1.0000')
        ->assertSee('50.0%');
});

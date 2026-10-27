<?php

use App\Enums\EstadoProduccion;
use App\Enums\Rol;
use App\Models\Insumo;
use App\Models\Produccion;
use App\Models\Receta;
use App\Models\UnidadMedida;
use App\Models\User;
use Livewire\Livewire;

test('no se puede registrar una nueva producción si no hay stock suficiente de insumos', function () {
    $usuario = User::factory()->rol(Rol::Produccion)->create();
    $unidad = UnidadMedida::factory()->create();
    $insumo = Insumo::factory()->create(['nombre' => 'Leche fresca', 'unidad_medida_id' => $unidad->id, 'stock_actual' => 5]);
    $receta = Receta::factory()->create();
    $receta->detalles()->create(['insumo_id' => $insumo->id, 'cantidad' => 10, 'unidad_medida_id' => $unidad->id]);

    $this->actingAs($usuario);

    // Requiere 10 * 2 = 20 de leche, pero solo hay 5.
    Livewire::test('pages::produccion.registros')
        ->call('nuevaProduccion')
        ->set('lineas.0.receta_id', $receta->id)
        ->set('lineas.0.cantidad', '2')
        ->call('guardarProduccion');

    expect(Produccion::where('receta_id', $receta->id)->exists())->toBeFalse()
        ->and((float) $insumo->fresh()->stock_actual)->toEqual(5.0);
});

test('el formulario de nueva producción muestra una advertencia y bloquea guardar cuando falta stock', function () {
    $usuario = User::factory()->rol(Rol::Produccion)->create();
    $unidad = UnidadMedida::factory()->create();
    $insumo = Insumo::factory()->create(['nombre' => 'Leche fresca', 'unidad_medida_id' => $unidad->id, 'stock_actual' => 5]);
    $receta = Receta::factory()->create();
    $receta->detalles()->create(['insumo_id' => $insumo->id, 'cantidad' => 10, 'unidad_medida_id' => $unidad->id]);

    $this->actingAs($usuario);

    Livewire::test('pages::produccion.registros')
        ->call('nuevaProduccion')
        ->set('lineas.0.receta_id', $receta->id)
        ->set('lineas.0.cantidad', '2')
        ->assertSee('No hay stock suficiente')
        ->assertSee('Leche fresca');
});

test('no se puede sumar cantidad a una producción si no alcanza el stock de insumos', function () {
    $usuario = User::factory()->rol(Rol::Produccion)->create();
    $unidad = UnidadMedida::factory()->create();
    $insumo = Insumo::factory()->create(['unidad_medida_id' => $unidad->id, 'stock_actual' => 12]);
    $receta = Receta::factory()->create();
    $receta->detalles()->create(['insumo_id' => $insumo->id, 'cantidad' => 10, 'unidad_medida_id' => $unidad->id]);

    $this->actingAs($usuario);

    // Producción inicial de 1 (consume 10, quedan 2 de stock).
    Livewire::test('pages::produccion.registros')
        ->call('nuevaProduccion')
        ->set('lineas.0.receta_id', $receta->id)
        ->set('lineas.0.cantidad', '1')
        ->call('guardarProduccion');

    expect((float) $insumo->fresh()->stock_actual)->toEqual(2.0);

    $produccion = Produccion::where('receta_id', $receta->id)->firstOrFail();

    // Sumar 1 más requeriría otras 10, pero solo quedan 2.
    Livewire::test('pages::produccion.registros')
        ->call('abrirActualizarProduccion', $produccion->id)
        ->set('registrarCantidad', '1')
        ->call('actualizarProduccion');

    expect((float) $produccion->fresh()->cantidad_producida)->toEqual(1.0)
        ->and((float) $insumo->fresh()->stock_actual)->toEqual(2.0);
});

test('no se puede publicar una producción sin stock suficiente de insumos', function () {
    $usuario = User::factory()->rol(Rol::Produccion)->create();
    $unidad = UnidadMedida::factory()->create();
    $insumo = Insumo::factory()->create(['unidad_medida_id' => $unidad->id, 'stock_actual' => 0]);
    $receta = Receta::factory()->create();
    $receta->detalles()->create(['insumo_id' => $insumo->id, 'cantidad' => 10, 'unidad_medida_id' => $unidad->id]);

    // Creada directamente con la factory, sin pasar por "Nueva producción", así que todavía
    // no se descontó nada.
    $produccion = Produccion::factory()->create([
        'receta_id' => $receta->id,
        'cantidad_producida' => 1,
        'estado' => EstadoProduccion::Planificada,
    ]);

    $this->actingAs($usuario);

    Livewire::test('pages::produccion.registros')
        ->call('completarProduccion', $produccion->id);

    expect($produccion->fresh()->estado)->toBe(EstadoProduccion::Planificada);
});

test('el insumo nunca queda con stock negativo', function () {
    $insumo = Insumo::factory()->create(['stock_actual' => 5]);

    $insumo->consumir(20);

    expect((float) $insumo->fresh()->stock_actual)->toEqual(0.0);
});

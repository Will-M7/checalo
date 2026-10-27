<?php

use App\Enums\EstadoProduccion;
use App\Enums\Rol;
use App\Models\Insumo;
use App\Models\Produccion;
use App\Models\Receta;
use App\Models\UnidadMedida;
use App\Models\User;
use App\Models\Venta;
use App\Models\VentaItem;
use Livewire\Livewire;

test('el inventario del vendedor muestra la cantidad disponible de producción, no el catálogo de productos', function () {
    $vendedor = User::factory()->rol(Rol::Vendedor)->create();
    $unidad = UnidadMedida::factory()->create();
    $insumo = Insumo::factory()->create(['unidad_medida_id' => $unidad->id, 'costo_unitario' => 2, 'stock_actual' => 1000]);

    $receta = Receta::factory()->create(['nombre' => 'Queso fresco']);
    $receta->detalles()->create(['insumo_id' => $insumo->id, 'cantidad' => 5, 'unidad_medida_id' => $unidad->id]);

    $produccion = Produccion::factory()->create(['receta_id' => $receta->id, 'cantidad_producida' => 10, 'estado' => EstadoProduccion::Completada]);

    $venta = Venta::factory()->create();
    VentaItem::create([
        'venta_id' => $venta->id,
        'produccion_id' => $produccion->id,
        'nombre' => 'Queso fresco',
        'precio_unitario' => 10,
        'cantidad' => 3,
        'subtotal' => 30,
    ]);

    $this->actingAs($vendedor);

    Livewire::test('pages::vendedor.inventario')
        ->assertSee('Queso fresco')
        ->assertSee('S/ 10.00')
        ->assertSee('7.00');

    expect((float) $produccion->fresh()->cantidad_vendida)->toEqual(3.0);
});

test('el panel del vendedor calcula el valor del inventario y el stock bajo desde producción', function () {
    $vendedor = User::factory()->rol(Rol::Vendedor)->create();
    $unidad = UnidadMedida::factory()->create();
    $insumo = Insumo::factory()->create(['unidad_medida_id' => $unidad->id, 'costo_unitario' => 2, 'stock_actual' => 1000]);

    $receta = Receta::factory()->create(['nombre' => 'Yogurt natural']);
    $receta->detalles()->create(['insumo_id' => $insumo->id, 'cantidad' => 1, 'unidad_medida_id' => $unidad->id]);

    Produccion::factory()->create(['receta_id' => $receta->id, 'cantidad_producida' => 3, 'estado' => EstadoProduccion::Completada]);

    $this->actingAs($vendedor);

    Livewire::test('pages::vendedor.panel')
        ->assertSee('S/ 6.00')
        ->assertSee('Yogurt natural');
});

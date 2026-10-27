<?php

use App\Enums\EstadoProduccion;
use App\Models\Insumo;
use App\Models\Produccion;
use App\Models\Receta;
use App\Models\RecetaDetalle;
use App\Models\UnidadMedida;

test('registrar una compra aumenta el stock y recalcula el costo promedio ponderado', function () {
    $insumo = Insumo::factory()->create(['stock_actual' => 10, 'costo_unitario' => 2]);

    // Stock: 10 a 2.00 (valor 20) + 10 a 4.00 (valor 40) = 20 unidades, valor 60 -> costo promedio 3.00
    $insumo->registrarCompra(10, 4);
    $insumo->refresh();

    expect((float) $insumo->stock_actual)->toBe(20.0)
        ->and((float) $insumo->costo_unitario)->toBe(3.0);
});

test('consumir insumo descuenta del stock', function () {
    $insumo = Insumo::factory()->create(['stock_actual' => 50]);

    $insumo->consumir(12.5);
    $insumo->refresh();

    expect((float) $insumo->stock_actual)->toBe(37.5);
});

test('completar una producción descuenta el stock de insumos multiplicado por la cantidad producida', function () {
    $kg = UnidadMedida::factory()->create();
    $receta = Receta::factory()->create();

    $leche = Insumo::factory()->create(['stock_actual' => 100]);

    RecetaDetalle::factory()->create([
        'receta_id' => $receta->id, 'insumo_id' => $leche->id, 'cantidad' => 10, 'unidad_medida_id' => $kg->id,
    ]);

    // Producir 3 unidades debe consumir 3 * 10 = 30 de leche.
    $produccion = Produccion::factory()->create([
        'receta_id' => $receta->id,
        'cantidad_producida' => 3,
    ]);

    $produccion->completar();

    expect((float) $leche->fresh()->stock_actual)->toBe(70.0)
        ->and($produccion->fresh()->estado)->toBe(EstadoProduccion::Completada);
});

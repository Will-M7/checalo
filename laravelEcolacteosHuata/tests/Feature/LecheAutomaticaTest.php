<?php

use App\Models\Acopiador;
use App\Models\EntregaPlanta;
use App\Models\Insumo;
use App\Models\Proveedor;
use App\Models\Recojo;
use App\Models\Turno;

test('registrar un recojo ya no afecta el stock del insumo de leche', function () {
    $insumoLeche = Insumo::factory()->create(['es_leche' => true, 'stock_actual' => 100, 'costo_unitario' => 1.5]);
    $acopiador = Acopiador::factory()->create();
    $turno = Turno::factory()->create();
    $proveedor = Proveedor::factory()->create(['acopiador_id' => $acopiador->id]);

    Recojo::create([
        'proveedor_id' => $proveedor->id,
        'acopiador_id' => $acopiador->id,
        'turno_id' => $turno->id,
        'fecha' => now()->toDateString(),
        'litros' => 50,
        'estado_leche' => 'normal',
        'precio_litro' => 1.8,
        'total' => 90,
    ]);

    // El recojo solo registra la entrega del proveedor al acopiador; el stock se suma
    // recién cuando planta envía la leche (ya evaluada como conforme) a producción.
    expect((float) $insumoLeche->fresh()->stock_actual)->toBe(100.0);
});

test('corregir el litraje de un recojo ya registrado no afecta el stock de leche', function () {
    $insumoLeche = Insumo::factory()->create(['es_leche' => true, 'stock_actual' => 0, 'costo_unitario' => 1.5]);
    $acopiador = Acopiador::factory()->create();
    $turno = Turno::factory()->create();
    $proveedor = Proveedor::factory()->create(['acopiador_id' => $acopiador->id]);

    $recojo = Recojo::create([
        'proveedor_id' => $proveedor->id,
        'acopiador_id' => $acopiador->id,
        'turno_id' => $turno->id,
        'fecha' => now()->toDateString(),
        'litros' => 20,
        'estado_leche' => 'normal',
        'precio_litro' => 1.8,
        'total' => 36,
    ]);

    $recojo->update(['litros' => 25]);

    expect((float) $insumoLeche->fresh()->stock_actual)->toBe(0.0);
});

test('registrar una entrega a planta no afecta el stock de leche', function () {
    $insumoLeche = Insumo::factory()->create(['es_leche' => true, 'stock_actual' => 100, 'costo_unitario' => 1.5]);
    $acopiador = Acopiador::factory()->create();
    $turno = Turno::factory()->create();

    EntregaPlanta::create([
        'acopiador_id' => $acopiador->id,
        'turno_id' => $turno->id,
        'fecha' => now()->toDateString(),
        'litros_total' => 50,
        'guia' => 'G-TEST-001',
    ]);

    expect((float) $insumoLeche->fresh()->stock_actual)->toBe(100.0);
});

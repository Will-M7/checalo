<?php

use App\Enums\EstadoProduccion;
use App\Enums\Rol;
use App\Models\Produccion;
use App\Models\User;
use App\Models\Venta;
use App\Models\VentaItem;
use Livewire\Livewire;

test('limpiar historial elimina solo las ventas dentro del rango de fechas filtrado', function () {
    $vendedor = User::factory()->rol(Rol::Vendedor)->create();
    $produccion = Produccion::factory()->create(['cantidad_producida' => 100, 'estado' => EstadoProduccion::Completada]);

    $ventaDentro = Venta::factory()->create(['fecha' => now()->subDays(5)->toDateString()]);
    VentaItem::factory()->create(['venta_id' => $ventaDentro->id, 'produccion_id' => $produccion->id]);

    $ventaFuera = Venta::factory()->create(['fecha' => now()->subDays(60)->toDateString()]);
    VentaItem::factory()->create(['venta_id' => $ventaFuera->id, 'produccion_id' => $produccion->id]);

    $this->actingAs($vendedor);

    Livewire::test('pages::vendedor.historial')
        ->call('limpiarHistorial');

    expect(Venta::find($ventaDentro->id))->toBeNull()
        ->and(VentaItem::where('venta_id', $ventaDentro->id)->exists())->toBeFalse()
        ->and(Venta::find($ventaFuera->id))->not->toBeNull();
});

test('limpiar historial sin ventas en el período no elimina nada', function () {
    $vendedor = User::factory()->rol(Rol::Vendedor)->create();
    $ventaFuera = Venta::factory()->create(['fecha' => now()->subDays(60)->toDateString()]);

    $this->actingAs($vendedor);

    Livewire::test('pages::vendedor.historial')
        ->call('limpiarHistorial');

    expect(Venta::find($ventaFuera->id))->not->toBeNull();
});

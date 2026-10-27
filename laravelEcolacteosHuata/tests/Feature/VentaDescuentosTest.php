<?php

use App\Enums\EstadoProduccion;
use App\Enums\Rol;
use App\Models\Cliente;
use App\Models\Descuento;
use App\Models\Insumo;
use App\Models\Produccion;
use App\Models\Receta;
use App\Models\UnidadMedida;
use App\Models\User;
use App\Models\Venta;
use Livewire\Livewire;

function crearProduccionVendible(float $precioUnidad, float $cantidadProducida): Produccion
{
    $unidad = UnidadMedida::factory()->create();
    $insumo = Insumo::factory()->create(['unidad_medida_id' => $unidad->id, 'costo_unitario' => $precioUnidad, 'stock_actual' => 1000]);
    $receta = Receta::factory()->create();
    $receta->detalles()->create(['insumo_id' => $insumo->id, 'cantidad' => 1, 'unidad_medida_id' => $unidad->id]);

    return Produccion::factory()->create([
        'receta_id' => $receta->id,
        'cantidad_producida' => $cantidadProducida,
        'estado' => EstadoProduccion::Completada,
    ]);
}

test('activar un descuento en el punto de venta reduce el total y queda registrado en la boleta', function () {
    $vendedor = User::factory()->rol(Rol::Vendedor)->create();
    $produccion = crearProduccionVendible(100, 10);
    $descuento = Descuento::factory()->create(['nombre' => 'Descuento por volumen', 'porcentaje' => 10, 'activo' => true]);

    $this->actingAs($vendedor);

    Livewire::test('pages::vendedor.ventas')
        ->call('agregar', $produccion->id)
        ->set("descuentosActivos.{$descuento->id}", true)
        ->set('metodoPago', 'efectivo')
        ->set('efectivoRecibido', 100)
        ->call('guardar')
        ->assertHasNoErrors();

    $venta = Venta::latest('id')->first();

    expect((float) $venta->subtotal)->toEqual(100.0)
        ->and((float) $venta->descuento)->toEqual(10.0)
        ->and((float) $venta->total)->toEqual(90.0)
        ->and($venta->descuento_tipo)->toBe('Descuento por volumen');
});

test('un descuento inactivo no aparece disponible en el punto de venta', function () {
    $vendedor = User::factory()->rol(Rol::Vendedor)->create();
    Descuento::factory()->create(['nombre' => 'Oculto', 'activo' => false]);

    $this->actingAs($vendedor);

    Livewire::test('pages::vendedor.ventas')
        ->assertDontSee('Oculto');
});

test('editar manualmente la cantidad en el carrito recalcula el subtotal y respeta la cantidad disponible', function () {
    $vendedor = User::factory()->rol(Rol::Vendedor)->create();
    $produccion = crearProduccionVendible(20, 5);

    $this->actingAs($vendedor);

    Livewire::test('pages::vendedor.ventas')
        ->call('agregar', $produccion->id)
        ->set('carrito.0.cantidad', 3)
        ->assertSet('carrito.0.subtotal', 60.0)
        ->set('carrito.0.cantidad', 50)
        ->assertSet('carrito.0.cantidad', 5)
        ->assertSet('carrito.0.subtotal', 100.0);
});

test('seleccionar un cliente guardado en el punto de venta lo deja asignado a la venta', function () {
    $vendedor = User::factory()->rol(Rol::Vendedor)->create();
    $cliente = Cliente::factory()->create(['nombres' => 'Rosa', 'apellidos' => 'Mamani', 'documento' => '70399900']);

    $this->actingAs($vendedor);

    Livewire::test('pages::vendedor.ventas')
        ->assertSee('Rosa Mamani')
        ->set('clienteId', $cliente->id)
        ->assertSee($cliente->documento);
});

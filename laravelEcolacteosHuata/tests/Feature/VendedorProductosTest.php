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
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\Storage;
use Livewire\Livewire;

test('el rol de vendedor ve las producciones publicadas con nombre, cantidad, precio unidad y estado', function () {
    $vendedor = User::factory()->rol(Rol::Vendedor)->create();
    $unidad = UnidadMedida::factory()->create();
    $insumo = Insumo::factory()->create(['unidad_medida_id' => $unidad->id, 'costo_unitario' => 2, 'stock_actual' => 1000]);

    $recetaQueso = Receta::factory()->create(['nombre' => 'Queso fresco']);
    $recetaQueso->detalles()->create(['insumo_id' => $insumo->id, 'cantidad' => 5, 'unidad_medida_id' => $unidad->id]);

    $recetaYogurt = Receta::factory()->create(['nombre' => 'Yogurt natural']);
    $recetaYogurt->detalles()->create(['insumo_id' => $insumo->id, 'cantidad' => 5, 'unidad_medida_id' => $unidad->id]);

    Produccion::factory()->create(['nombre' => 'Queso del lote 1', 'receta_id' => $recetaQueso->id, 'cantidad_producida' => 20, 'estado' => EstadoProduccion::Completada]);
    Produccion::factory()->create(['nombre' => 'Yogurt del lote 1', 'receta_id' => $recetaYogurt->id, 'cantidad_producida' => 2, 'estado' => EstadoProduccion::Completada]);
    Produccion::factory()->create(['nombre' => 'Sin publicar', 'receta_id' => $recetaQueso->id, 'cantidad_producida' => 8, 'estado' => EstadoProduccion::Planificada]);

    $this->actingAs($vendedor);

    Livewire::test('pages::vendedor.produccion')
        ->assertSee('Queso del lote 1')
        ->assertSee('S/ 10.00')
        ->assertSee('Disponible')
        ->assertSee('Yogurt del lote 1')
        ->assertSee('Cantidad baja')
        ->assertDontSee('Sin publicar');
});

test('la cantidad mostrada en productos y punto de venta se descuenta con cada venta', function () {
    $vendedor = User::factory()->rol(Rol::Vendedor)->create();
    $unidad = UnidadMedida::factory()->create();
    $insumo = Insumo::factory()->create(['unidad_medida_id' => $unidad->id, 'costo_unitario' => 2, 'stock_actual' => 1000]);

    $receta = Receta::factory()->create(['nombre' => 'Queso fresco']);
    $receta->detalles()->create(['insumo_id' => $insumo->id, 'cantidad' => 5, 'unidad_medida_id' => $unidad->id]);

    $produccion = Produccion::factory()->create(['receta_id' => $receta->id, 'cantidad_producida' => 20, 'estado' => EstadoProduccion::Completada]);

    $venta = Venta::factory()->create();
    VentaItem::create([
        'venta_id' => $venta->id,
        'produccion_id' => $produccion->id,
        'nombre' => 'Queso fresco',
        'precio_unitario' => 10,
        'cantidad' => 8,
        'subtotal' => 80,
    ]);

    expect($produccion->fresh()->cantidadDisponible())->toEqual(12.0);

    $this->actingAs($vendedor);

    // Ambas pantallas muestran la cantidad disponible tras la venta (20 - 8 = 12), no la producida original.
    Livewire::test('pages::vendedor.produccion')->assertSee('12.00');
    Livewire::test('pages::vendedor.ventas')->assertSee('12');
});

test('el rol de vendedor puede subir una foto para una producción publicada', function () {
    Storage::fake('public');

    $vendedor = User::factory()->rol(Rol::Vendedor)->create();
    $receta = Receta::factory()->create();
    $produccion = Produccion::factory()->create(['receta_id' => $receta->id, 'estado' => EstadoProduccion::Completada]);

    $this->actingAs($vendedor);

    Livewire::test('pages::vendedor.produccion')
        ->call('abrirFoto', $produccion->id)
        ->set('foto', UploadedFile::fake()->image('foto.jpg'))
        ->call('guardarFoto')
        ->assertHasNoErrors();

    $produccion->refresh();

    expect($produccion->imagen)->not->toBeNull();
    Storage::disk('public')->assertExists($produccion->imagen);
});

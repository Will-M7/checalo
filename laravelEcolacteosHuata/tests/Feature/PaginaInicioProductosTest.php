<?php

use App\Enums\EstadoProduccion;
use App\Enums\Rol;
use App\Models\Empresa;
use App\Models\Insumo;
use App\Models\Produccion;
use App\Models\Receta;
use App\Models\UnidadMedida;
use App\Models\User;
use Livewire\Livewire;

/**
 * Receta con un único insumo cuyo costo total es 20 (10 × S/ 2).
 */
function recetaConCosto(string $nombre, ?float $precioVenta = null): Receta
{
    $unidad = UnidadMedida::factory()->create();
    $insumo = Insumo::factory()->create(['unidad_medida_id' => $unidad->id, 'costo_unitario' => 2]);
    $receta = Receta::factory()->create(['nombre' => $nombre, 'precio_venta' => $precioVenta]);
    $receta->detalles()->create([
        'insumo_id' => $insumo->id,
        'cantidad' => 10,
        'unidad_medida_id' => $unidad->id,
    ]);

    return $receta;
}

test('la portada muestra los productos publicados con stock y su precio', function () {
    $receta = recetaConCosto('Queso fresco');
    Produccion::factory()->create([
        'nombre' => 'Queso fresco de hoy',
        'receta_id' => $receta->id,
        'cantidad_producida' => 10,
        'cantidad_vendida' => 4,
        'estado' => EstadoProduccion::Completada,
    ]);

    $this->get(route('home'))
        ->assertOk()
        ->assertSee('Queso fresco de hoy')
        ->assertSee('6 unidades disponibles')
        ->assertSee('S/ 20.00')
        ->assertSee('Tu carrito');
});

test('usa el precio de venta de la receta cuando existe y 1 unidad va en singular', function () {
    $receta = recetaConCosto('Yogurt', precioVenta: 7.5);
    Produccion::factory()->create([
        'nombre' => 'Yogurt natural',
        'receta_id' => $receta->id,
        'cantidad_producida' => 3,
        'cantidad_vendida' => 2,
        'estado' => EstadoProduccion::Completada,
    ]);

    $this->get(route('home'))
        ->assertSee('Yogurt natural')
        ->assertSee('S/ 7.50')
        ->assertSee('1 unidad disponible')
        ->assertDontSee('S/ 20.00');
});

test('no muestra producciones pendientes ni agotadas y muestra el mensaje vacío', function () {
    $receta = recetaConCosto('Mantequilla');
    Produccion::factory()->create([
        'nombre' => 'Mantequilla pendiente',
        'receta_id' => $receta->id,
        'cantidad_producida' => 10,
        'estado' => EstadoProduccion::Planificada,
    ]);
    Produccion::factory()->create([
        'nombre' => 'Mantequilla agotada',
        'receta_id' => $receta->id,
        'cantidad_producida' => 5,
        'cantidad_vendida' => 5,
        'estado' => EstadoProduccion::Completada,
    ]);

    expect(Produccion::catalogoPublico())->toBeEmpty();

    $this->get(route('home'))
        ->assertOk()
        ->assertDontSee('Mantequilla pendiente')
        ->assertDontSee('Mantequilla agotada')
        ->assertSee('Pronto publicaremos aquí nuestros productos frescos')
        ->assertDontSee('id="carrito"', escape: false);
});

test('el WhatsApp usa el teléfono de la empresa con el prefijo 51', function () {
    Empresa::actual()->update(['telefono' => '951 234 567']);

    $receta = recetaConCosto('Queso');
    Produccion::factory()->create([
        'receta_id' => $receta->id,
        'cantidad_producida' => 2,
        'estado' => EstadoProduccion::Completada,
    ]);

    $this->get(route('home'))
        ->assertOk()
        ->assertSee('whatsapp: "51951234567"', escape: false)
        ->assertSee('Confirmar pedido');

    expect(Empresa::actual()->whatsapp())->toBe('51951234567');
});

test('sin teléfono se puede comprar igual, pero sin aviso por WhatsApp', function () {
    Empresa::actual()->update(['telefono' => '']);

    $receta = recetaConCosto('Queso');
    Produccion::factory()->create([
        'receta_id' => $receta->id,
        'cantidad_producida' => 2,
        'estado' => EstadoProduccion::Completada,
    ]);

    expect(Empresa::actual()->whatsapp())->toBeNull();

    $this->get(route('home'))
        ->assertOk()
        ->assertSee('Confirmar pedido')
        ->assertSee('whatsapp: null', escape: false);
});

test('producción fija el precio de venta de una receta desde el formulario', function () {
    $usuario = User::factory()->rol(Rol::Produccion)->create();
    $unidad = UnidadMedida::factory()->create();
    $insumo = Insumo::factory()->create(['unidad_medida_id' => $unidad->id, 'costo_unitario' => 2]);

    $this->actingAs($usuario);

    Livewire::test('pages::produccion.recetas')
        ->call('nuevaReceta')
        ->set('recetaNombre', 'Queso paria')
        ->set('recetaPrecioVenta', '12.50')
        ->call('agregarIngrediente')
        ->set('recetaDetalles.0.insumo_id', $insumo->id)
        ->set('recetaDetalles.0.cantidad', '10')
        ->call('guardarReceta')
        ->assertHasNoErrors();

    $receta = Receta::where('nombre', 'Queso paria')->firstOrFail();

    expect((float) $receta->precio_venta)->toBe(12.5)
        ->and($receta->precioVenta())->toBe(12.5)
        ->and($receta->costoTotal())->toEqual(20.0);
});

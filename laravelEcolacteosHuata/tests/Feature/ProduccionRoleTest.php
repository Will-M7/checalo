<?php

use App\Enums\EstadoProduccion;
use App\Enums\Rol;
use App\Models\Compra;
use App\Models\Insumo;
use App\Models\Produccion;
use App\Models\Receta;
use App\Models\Turno;
use App\Models\UnidadMedida;
use App\Models\User;
use Livewire\Livewire;

test('el rol de producción puede crear un insumo y registrar una compra que aumenta su stock', function () {
    $usuario = User::factory()->rol(Rol::Produccion)->create();
    $unidad = UnidadMedida::factory()->create();

    $this->actingAs($usuario);

    Livewire::test('pages::produccion.insumos')
        ->call('nuevoInsumo')
        ->set('insumoNombre', 'Leche fresca')
        ->set('insumoUnidadMedidaId', $unidad->id)
        ->set('insumoCostoUnitario', '1.8')
        ->call('guardarInsumo')
        ->assertHasNoErrors();

    $insumo = Insumo::where('nombre', 'Leche fresca')->firstOrFail();

    Livewire::test('pages::produccion.insumos')
        ->call('abrirCompra', $insumo->id)
        ->set('compraCantidad', '50')
        ->set('compraCosto', '2')
        ->call('registrarCompra')
        ->assertHasNoErrors();

    expect((float) $insumo->fresh()->stock_actual)->toEqual(50.0);
});

test('el rol de producción puede crear una receta con ingredientes y calcula su costo', function () {
    $usuario = User::factory()->rol(Rol::Produccion)->create();
    $unidad = UnidadMedida::factory()->create();
    $insumo = Insumo::factory()->create(['unidad_medida_id' => $unidad->id, 'costo_unitario' => 2]);

    $this->actingAs($usuario);

    Livewire::test('pages::produccion.recetas')
        ->call('nuevaReceta')
        ->set('recetaNombre', 'Queso fresco artesanal')
        ->call('agregarIngrediente')
        ->set('recetaDetalles.0.insumo_id', $insumo->id)
        ->set('recetaDetalles.0.cantidad', '10')
        ->set('recetaDetalles.0.unidad_medida_id', $unidad->id)
        ->call('guardarReceta')
        ->assertHasNoErrors();

    $receta = Receta::where('nombre', 'Queso fresco artesanal')->with('detalles')->firstOrFail();

    expect($receta->detalles)->toHaveCount(1)
        ->and($receta->costoTotal())->toEqual(20.0);
});

test('el rol de producción puede registrar y completar una producción, descontando el stock de insumos', function () {
    $usuario = User::factory()->rol(Rol::Produccion)->create();
    $unidad = UnidadMedida::factory()->create();
    $insumo = Insumo::factory()->create(['unidad_medida_id' => $unidad->id, 'stock_actual' => 100]);
    $turno = Turno::factory()->create();

    $receta = Receta::factory()->create();
    $receta->detalles()->create([
        'insumo_id' => $insumo->id,
        'cantidad' => 10,
        'unidad_medida_id' => $unidad->id,
    ]);

    $this->actingAs($usuario);

    Livewire::test('pages::produccion.registros')
        ->call('nuevaProduccion')
        ->set('lineas.0.receta_id', $receta->id)
        ->set('lineas.0.cantidad', '3')
        ->set('produccionTurnoId', $turno->id)
        ->call('guardarProduccion')
        ->assertHasNoErrors();

    $produccion = Produccion::where('receta_id', $receta->id)->firstOrFail();

    $produccion->completar();

    expect($produccion->fresh()->estado)->toBe(EstadoProduccion::Completada)
        ->and((float) $insumo->fresh()->stock_actual)->toEqual(70.0);
});

test('la producción recién creada, editada o actualizada aparece primero en la tabla', function () {
    $usuario = User::factory()->rol(Rol::Produccion)->create();
    $unidad = UnidadMedida::factory()->create();
    $insumo = Insumo::factory()->create(['unidad_medida_id' => $unidad->id, 'stock_actual' => 1000]);
    $receta = Receta::factory()->create();
    $receta->detalles()->create(['insumo_id' => $insumo->id, 'cantidad' => 10, 'unidad_medida_id' => $unidad->id]);

    $antigua = Produccion::factory()->create([
        'nombre' => 'Antigua',
        'receta_id' => $receta->id,
        'fecha' => now()->toDateString(),
        'created_at' => now()->subDay(),
        'updated_at' => now()->subDay(),
    ]);
    $reciente = Produccion::factory()->create([
        'nombre' => 'Reciente',
        'receta_id' => $receta->id,
        'fecha' => now()->toDateString(),
        'created_at' => now()->subHours(2),
        'updated_at' => now()->subHours(2),
    ]);

    $this->actingAs($usuario);

    Livewire::test('pages::produccion.registros')
        ->assertSeeInOrder(['Reciente', 'Antigua']);

    $antigua->update(['nombre' => 'Antigua editada']);

    Livewire::test('pages::produccion.registros')
        ->assertSeeInOrder(['Antigua editada', 'Reciente']);
});

test('la tabla de producción muestra el precio unidad y el precio total calculados desde el costo de la receta', function () {
    $usuario = User::factory()->rol(Rol::Produccion)->create();
    $unidad = UnidadMedida::factory()->create();
    $insumo = Insumo::factory()->create(['unidad_medida_id' => $unidad->id, 'costo_unitario' => 2, 'stock_actual' => 1000]);

    $receta = Receta::factory()->create();
    $receta->detalles()->create(['insumo_id' => $insumo->id, 'cantidad' => 10, 'unidad_medida_id' => $unidad->id]);

    Produccion::factory()->create(['receta_id' => $receta->id, 'cantidad_producida' => 3]);

    $this->actingAs($usuario);

    Livewire::test('pages::produccion.registros')
        ->assertSee('S/ 20.00')
        ->assertSee('S/ 60.00');
});

test('el rol de producción puede actualizar una producción planificada para sumar cantidad y cambiar el turno', function () {
    $usuario = User::factory()->rol(Rol::Produccion)->create();
    $unidad = UnidadMedida::factory()->create();
    $insumo = Insumo::factory()->create(['unidad_medida_id' => $unidad->id, 'stock_actual' => 100]);
    $turnoOriginal = Turno::factory()->create();
    $turnoNuevo = Turno::factory()->create();

    $receta = Receta::factory()->create();
    $receta->detalles()->create(['insumo_id' => $insumo->id, 'cantidad' => 10, 'unidad_medida_id' => $unidad->id]);

    $original = Produccion::factory()->create([
        'nombre' => 'Lote original',
        'receta_id' => $receta->id,
        'cantidad_producida' => 2,
        'turno_id' => $turnoOriginal->id,
        'estado' => EstadoProduccion::Planificada,
    ]);

    $this->actingAs($usuario);

    Livewire::test('pages::produccion.registros')
        ->call('abrirActualizarProduccion', $original->id)
        ->assertSet('registrarNombre', 'Lote original')
        ->assertSet('registrarRecetaNombre', $receta->nombre)
        ->set('registrarCantidad', '3')
        ->set('registrarTurnoId', $turnoNuevo->id)
        ->call('actualizarProduccion')
        ->assertHasNoErrors();

    $original->refresh();

    // Se creó directamente con la factory (sin pasar por "Nueva producción"), así que al
    // actualizarla se descuenta de una vez todo lo que corresponde a la cantidad final (5 * 10).
    expect((float) $original->cantidad_producida)->toEqual(5.0)
        ->and($original->turno_id)->toBe($turnoNuevo->id)
        ->and($original->nombre)->toBe('Lote original')
        ->and((float) $insumo->fresh()->stock_actual)->toEqual(50.0);
});

test('el rol de producción puede actualizar una producción ya publicada, descontando solo lo que falte', function () {
    $usuario = User::factory()->rol(Rol::Produccion)->create();
    $unidad = UnidadMedida::factory()->create();
    $insumo = Insumo::factory()->create(['unidad_medida_id' => $unidad->id, 'stock_actual' => 100]);

    $receta = Receta::factory()->create();
    $receta->detalles()->create(['insumo_id' => $insumo->id, 'cantidad' => 10, 'unidad_medida_id' => $unidad->id]);

    $publicada = Produccion::factory()->create([
        'receta_id' => $receta->id,
        'cantidad_producida' => 2,
        'estado' => EstadoProduccion::Completada,
    ]);

    $this->actingAs($usuario);

    Livewire::test('pages::produccion.registros')
        ->call('abrirActualizarProduccion', $publicada->id)
        ->set('registrarCantidad', '1')
        ->call('actualizarProduccion')
        ->assertHasNoErrors();

    // Se creó directamente con la factory (sin pasar por "Nueva producción"), así que al
    // actualizarla se descuenta de una vez todo lo que corresponde a la cantidad final (3 * 10).
    expect((float) $publicada->fresh()->cantidad_producida)->toEqual(3.0)
        ->and($publicada->fresh()->estado)->toBe(EstadoProduccion::Planificada)
        ->and((float) $insumo->fresh()->stock_actual)->toEqual(70.0);
});

test('actualizar y volver a publicar una producción no descuenta el stock dos veces', function () {
    $usuario = User::factory()->rol(Rol::Produccion)->create();
    $unidad = UnidadMedida::factory()->create();
    $insumo = Insumo::factory()->create(['unidad_medida_id' => $unidad->id, 'stock_actual' => 100]);

    $receta = Receta::factory()->create();
    $receta->detalles()->create(['insumo_id' => $insumo->id, 'cantidad' => 10, 'unidad_medida_id' => $unidad->id]);

    $produccion = Produccion::factory()->create([
        'receta_id' => $receta->id,
        'cantidad_producida' => 2,
        'estado' => EstadoProduccion::Planificada,
    ]);

    $this->actingAs($usuario);

    $component = Livewire::test('pages::produccion.registros');

    // Primera publicación: descuenta 2 * 10 = 20.
    $component->call('completarProduccion', $produccion->id);
    expect((float) $insumo->fresh()->stock_actual)->toEqual(80.0);

    // Actualizar la cantidad ya descuenta lo pendiente de inmediato (1 * 10 = 10), sin esperar
    // a que se vuelva a publicar.
    $component->call('abrirActualizarProduccion', $produccion->id)
        ->set('registrarCantidad', '1')
        ->call('actualizarProduccion');
    expect((float) $insumo->fresh()->stock_actual)->toEqual(70.0);

    // Volver a publicar no debe descontar nada más: ya no queda pendiente.
    $component->call('completarProduccion', $produccion->id);

    expect($produccion->fresh()->estado)->toBe(EstadoProduccion::Completada)
        ->and((float) $insumo->fresh()->stock_actual)->toEqual(70.0);
});

test('editar o actualizar una producción ya publicada la vuelve a dejar en estado pendiente', function () {
    $usuario = User::factory()->rol(Rol::Produccion)->create();
    $unidad = UnidadMedida::factory()->create();
    $insumo = Insumo::factory()->create(['unidad_medida_id' => $unidad->id, 'stock_actual' => 1000]);
    $receta = Receta::factory()->create();
    $receta->detalles()->create(['insumo_id' => $insumo->id, 'cantidad' => 10, 'unidad_medida_id' => $unidad->id]);

    $editada = Produccion::factory()->create(['receta_id' => $receta->id, 'estado' => EstadoProduccion::Completada]);
    $actualizada = Produccion::factory()->create(['receta_id' => $receta->id, 'estado' => EstadoProduccion::Completada]);

    $this->actingAs($usuario);

    Livewire::test('pages::produccion.registros')
        ->call('editarProduccion', $editada->id)
        ->set('lineas.0.cantidad', '5')
        ->call('guardarProduccion')
        ->assertHasNoErrors();

    Livewire::test('pages::produccion.registros')
        ->call('abrirActualizarProduccion', $actualizada->id)
        ->set('registrarCantidad', '1')
        ->call('actualizarProduccion')
        ->assertHasNoErrors();

    expect($editada->fresh()->estado)->toBe(EstadoProduccion::Planificada)
        ->and($actualizada->fresh()->estado)->toBe(EstadoProduccion::Planificada);
});

test('editar y actualizar siempre aparecen en la tabla, sin importar el estado de la producción', function () {
    $usuario = User::factory()->rol(Rol::Produccion)->create();
    $unidad = UnidadMedida::factory()->create();
    $insumo = Insumo::factory()->create(['unidad_medida_id' => $unidad->id, 'stock_actual' => 100]);
    $receta = Receta::factory()->create();
    $receta->detalles()->create(['insumo_id' => $insumo->id, 'cantidad' => 10, 'unidad_medida_id' => $unidad->id]);

    Produccion::factory()->create(['receta_id' => $receta->id, 'estado' => EstadoProduccion::Completada]);
    Produccion::factory()->create(['receta_id' => $receta->id, 'estado' => EstadoProduccion::Planificada]);

    $this->actingAs($usuario);

    Livewire::test('pages::produccion.registros')
        ->assertSee('Actualizar producción');
});

test('el botón publicar solo aparece cuando el estado es pendiente, y desaparece una vez publicada', function () {
    $usuario = User::factory()->rol(Rol::Produccion)->create();
    $unidad = UnidadMedida::factory()->create();
    $insumo = Insumo::factory()->create(['unidad_medida_id' => $unidad->id, 'stock_actual' => 100]);
    $receta = Receta::factory()->create();
    $receta->detalles()->create(['insumo_id' => $insumo->id, 'cantidad' => 10, 'unidad_medida_id' => $unidad->id]);

    Produccion::factory()->create(['receta_id' => $receta->id, 'estado' => EstadoProduccion::Planificada]);

    $this->actingAs($usuario);

    Livewire::test('pages::produccion.registros')
        ->assertSee('Publicar');

    Produccion::factory()->create(['receta_id' => $receta->id, 'estado' => EstadoProduccion::Completada]);

    // Con solo una producción publicada y ninguna pendiente, el botón ya no debe aparecer.
    Produccion::query()->update(['estado' => EstadoProduccion::Completada->value]);

    Livewire::test('pages::produccion.registros')
        ->assertDontSee('Publicar');
});

test('presionar publicar en la tabla marca la producción como publicada y descuenta el stock', function () {
    $usuario = User::factory()->rol(Rol::Produccion)->create();
    $unidad = UnidadMedida::factory()->create();
    $insumo = Insumo::factory()->create(['unidad_medida_id' => $unidad->id, 'stock_actual' => 100]);
    $receta = Receta::factory()->create();
    $receta->detalles()->create(['insumo_id' => $insumo->id, 'cantidad' => 10, 'unidad_medida_id' => $unidad->id]);

    $produccion = Produccion::factory()->create([
        'receta_id' => $receta->id,
        'cantidad_producida' => 3,
        'estado' => EstadoProduccion::Planificada,
    ]);

    $this->actingAs($usuario);

    Livewire::test('pages::produccion.registros')
        ->call('completarProduccion', $produccion->id);

    expect($produccion->fresh()->estado)->toBe(EstadoProduccion::Completada)
        ->and((float) $insumo->fresh()->stock_actual)->toEqual(70.0);
});

test('el formulario de nueva producción no tiene un id de producción asignado, a diferencia de editar', function () {
    $usuario = User::factory()->rol(Rol::Produccion)->create();
    $receta = Receta::factory()->create();
    $produccion = Produccion::factory()->create(['receta_id' => $receta->id, 'estado' => EstadoProduccion::Planificada]);

    $this->actingAs($usuario);

    Livewire::test('pages::produccion.registros')
        ->call('nuevaProduccion')
        ->assertSet('produccionId', null)
        ->call('editarProduccion', $produccion->id)
        ->assertSet('produccionId', $produccion->id);
});

test('todas las recetas están disponibles en nueva producción sin importar el stock de sus insumos', function () {
    $usuario = User::factory()->rol(Rol::Produccion)->create();
    $unidad = UnidadMedida::factory()->create();

    $insumoSuficiente = Insumo::factory()->create(['stock_actual' => 100, 'unidad_medida_id' => $unidad->id]);
    $insumoEscaso = Insumo::factory()->create(['stock_actual' => 2, 'unidad_medida_id' => $unidad->id]);

    $recetaConStock = Receta::factory()->create(['nombre' => 'Receta con stock']);
    $recetaConStock->detalles()->create(['insumo_id' => $insumoSuficiente->id, 'cantidad' => 10, 'unidad_medida_id' => $unidad->id]);

    $recetaSinStock = Receta::factory()->create(['nombre' => 'Receta sin stock']);
    $recetaSinStock->detalles()->create(['insumo_id' => $insumoEscaso->id, 'cantidad' => 10, 'unidad_medida_id' => $unidad->id]);

    $this->actingAs($usuario);

    Livewire::test('pages::produccion.registros')
        ->assertSee('Receta con stock')
        ->assertSee('Receta sin stock');
});

test('la producción recién creada toma automáticamente el nombre de su receta, sin campo manual', function () {
    $usuario = User::factory()->rol(Rol::Produccion)->create();
    $unidad = UnidadMedida::factory()->create();
    Insumo::factory()->create(['unidad_medida_id' => $unidad->id, 'stock_actual' => 1000]);
    $receta = Receta::factory()->create(['nombre' => 'Queso fresco']);
    $recetaNueva = Receta::factory()->create(['nombre' => 'Yogurt natural']);
    $turno = Turno::factory()->create();

    $this->actingAs($usuario);

    Livewire::test('pages::produccion.registros')
        ->call('nuevaProduccion')
        ->set('lineas.0.receta_id', $receta->id)
        ->set('lineas.0.cantidad', '2')
        ->call('guardarProduccion')
        ->assertHasNoErrors();

    $produccion = Produccion::where('receta_id', $receta->id)->firstOrFail();
    expect($produccion->nombre)->toBe('Queso fresco');
    $loteOriginal = $produccion->lote;

    // Editar cambia la receta, la cantidad y el turno; el nombre se actualiza para reflejar
    // la nueva receta, pero el lote (identificador del registro) no cambia.
    Livewire::test('pages::produccion.registros')
        ->call('editarProduccion', $produccion->id)
        ->set('lineas.0.receta_id', $recetaNueva->id)
        ->set('lineas.0.cantidad', '5')
        ->set('produccionTurnoId', $turno->id)
        ->call('guardarProduccion')
        ->assertHasNoErrors();

    $produccion->refresh();

    expect($produccion->nombre)->toBe('Yogurt natural')
        ->and($produccion->receta_id)->toBe($recetaNueva->id)
        ->and((float) $produccion->cantidad_producida)->toEqual(5.0)
        ->and($produccion->turno_id)->toBe($turno->id)
        ->and($produccion->lote)->toBe($loteOriginal);
});

test('nueva producción permite agregar varias recetas de una vez, creando un lote por cada una', function () {
    $usuario = User::factory()->rol(Rol::Produccion)->create();
    $unidad = UnidadMedida::factory()->create();
    $insumo = Insumo::factory()->create(['unidad_medida_id' => $unidad->id, 'stock_actual' => 1000]);

    $recetaQueso = Receta::factory()->create(['nombre' => 'Queso fresco']);
    $recetaQueso->detalles()->create(['insumo_id' => $insumo->id, 'cantidad' => 5, 'unidad_medida_id' => $unidad->id]);

    $recetaYogurt = Receta::factory()->create(['nombre' => 'Yogurt natural']);
    $recetaYogurt->detalles()->create(['insumo_id' => $insumo->id, 'cantidad' => 3, 'unidad_medida_id' => $unidad->id]);

    $this->actingAs($usuario);

    Livewire::test('pages::produccion.registros')
        ->call('nuevaProduccion')
        ->set('lineas.0.receta_id', $recetaQueso->id)
        ->set('lineas.0.cantidad', '2')
        ->call('agregarLinea')
        ->set('lineas.1.receta_id', $recetaYogurt->id)
        ->set('lineas.1.cantidad', '4')
        ->call('guardarProduccion')
        ->assertHasNoErrors();

    expect(Produccion::where('receta_id', $recetaQueso->id)->exists())->toBeTrue()
        ->and(Produccion::where('receta_id', $recetaYogurt->id)->exists())->toBeTrue()
        ->and(Produccion::count())->toBe(2)
        ->and((float) $insumo->fresh()->stock_actual)->toEqual(1000 - (5 * 2) - (3 * 4));

    $lotes = Produccion::pluck('lote');
    expect($lotes->unique())->toHaveCount(2);
});

test('en nueva producción no se puede quitar la última línea, pero sí las adicionales', function () {
    $usuario = User::factory()->rol(Rol::Produccion)->create();

    $this->actingAs($usuario);

    $component = Livewire::test('pages::produccion.registros')
        ->call('nuevaProduccion')
        ->call('agregarLinea')
        ->assertCount('lineas', 2);

    $component->call('quitarLinea', 1)->assertCount('lineas', 1);
    $component->call('quitarLinea', 0)->assertCount('lineas', 1);
});

test('en nueva producción se bloquea la cantidad de una línea cuyo insumo ya está en 0', function () {
    $usuario = User::factory()->rol(Rol::Produccion)->create();
    $unidad = UnidadMedida::factory()->create();
    $insumoAgotado = Insumo::factory()->create(['nombre' => 'Cuajo', 'unidad_medida_id' => $unidad->id, 'stock_actual' => 0]);
    $receta = Receta::factory()->create();
    $receta->detalles()->create(['insumo_id' => $insumoAgotado->id, 'cantidad' => 1, 'unidad_medida_id' => $unidad->id]);

    $this->actingAs($usuario);

    Livewire::test('pages::produccion.registros')
        ->call('nuevaProduccion')
        ->set('lineas.0.receta_id', $receta->id)
        ->assertSee('Sin stock de Cuajo');
});

test('en nueva producción se avisa cuando el stock de un insumo está por agotarse', function () {
    $usuario = User::factory()->rol(Rol::Produccion)->create();
    $unidad = UnidadMedida::factory()->create();
    $insumoBajo = Insumo::factory()->create(['nombre' => 'Sal', 'unidad_medida_id' => $unidad->id, 'stock_actual' => 8]);
    $receta = Receta::factory()->create();
    $receta->detalles()->create(['insumo_id' => $insumoBajo->id, 'cantidad' => 1, 'unidad_medida_id' => $unidad->id]);

    $this->actingAs($usuario);

    Livewire::test('pages::produccion.registros')
        ->call('nuevaProduccion')
        ->set('lineas.0.receta_id', $receta->id)
        ->set('lineas.0.cantidad', '1')
        ->assertSee('están por agotarse')
        ->assertSee('Sal');
});

test('el rol de producción puede ver el inventario de insumos con su valor y estado de stock', function () {
    $usuario = User::factory()->rol(Rol::Produccion)->create();
    $unidad = UnidadMedida::factory()->create();

    Insumo::factory()->create(['nombre' => 'Sal', 'unidad_medida_id' => $unidad->id, 'costo_unitario' => 1, 'stock_actual' => 0]);
    Insumo::factory()->create(['nombre' => 'Leche fresca', 'unidad_medida_id' => $unidad->id, 'costo_unitario' => 2, 'stock_actual' => 50, 'es_leche' => true]);

    $this->actingAs($usuario);

    Livewire::test('pages::produccion.inventario')
        ->assertSee('S/ 100.00')
        ->assertSee('Sin stock')
        ->assertSee('Automático (litraje que llegó a planta)');
});

test('el inventario muestra primero los insumos sin stock y luego los de stock bajo, antes que los disponibles', function () {
    $usuario = User::factory()->rol(Rol::Produccion)->create();
    $unidad = UnidadMedida::factory()->create();

    Insumo::factory()->create(['nombre' => 'Leche fresca', 'unidad_medida_id' => $unidad->id, 'stock_actual' => 50, 'es_leche' => true]);
    Insumo::factory()->create(['nombre' => 'Cultivo láctico', 'unidad_medida_id' => $unidad->id, 'stock_actual' => 5]);
    Insumo::factory()->create(['nombre' => 'Sal', 'unidad_medida_id' => $unidad->id, 'stock_actual' => 0]);

    $this->actingAs($usuario);

    Livewire::test('pages::produccion.inventario')
        ->assertSeeInOrder(['Sal', 'Cultivo láctico', 'Leche fresca']);
});

test('los botones de estado del inventario filtran la tabla por sin stock o stock bajo', function () {
    $usuario = User::factory()->rol(Rol::Produccion)->create();
    $unidad = UnidadMedida::factory()->create();

    Insumo::factory()->create(['nombre' => 'Disponible', 'unidad_medida_id' => $unidad->id, 'stock_actual' => 100]);
    Insumo::factory()->create(['nombre' => 'Bajo', 'unidad_medida_id' => $unidad->id, 'stock_actual' => 5]);
    Insumo::factory()->create(['nombre' => 'Agotado', 'unidad_medida_id' => $unidad->id, 'stock_actual' => 0]);

    $this->actingAs($usuario);

    Livewire::test('pages::produccion.inventario')
        ->call('filtrarPor', 'sin_stock')
        ->assertSee('Agotado')
        ->assertDontSee('Bajo')
        ->assertDontSee('Disponible')
        ->call('filtrarPor', 'sin_stock')
        ->assertSee('Disponible');
});

test('el inventario muestra la fecha y el precio de la última compra de cada insumo', function () {
    $usuario = User::factory()->rol(Rol::Produccion)->create();
    $unidad = UnidadMedida::factory()->create();
    $insumo = Insumo::factory()->create(['nombre' => 'Cuajo', 'unidad_medida_id' => $unidad->id, 'stock_actual' => 100]);

    $compra = Compra::factory()->create(['fecha' => now()->subDays(3)->toDateString()]);
    $compra->detalles()->create(['insumo_id' => $insumo->id, 'cantidad' => 10, 'costo_unitario' => 3.5, 'subtotal' => 35]);

    $this->actingAs($usuario);

    Livewire::test('pages::produccion.inventario')
        ->assertSee('S/ 3.5000');
});

test('el insumo de leche aparece primero en la tabla y no se puede eliminar', function () {
    $usuario = User::factory()->rol(Rol::Produccion)->create();
    $unidad = UnidadMedida::factory()->create();

    Insumo::factory()->create(['nombre' => 'Azúcar', 'unidad_medida_id' => $unidad->id]);
    $leche = Insumo::factory()->create(['nombre' => 'Leche fresca', 'es_leche' => true, 'unidad_medida_id' => $unidad->id]);

    $this->actingAs($usuario);

    $component = Livewire::test('pages::produccion.insumos')
        ->assertSeeInOrder(['Leche fresca', 'Azúcar']);

    $component->call('pedirEliminarInsumo', $leche->id)->call('eliminarInsumo');

    expect(Insumo::find($leche->id))->not->toBeNull();
});

test('el insumo de leche se crea solo al visitar Insumos, aunque no exista todavía ni se haya enviado laboratorio a producción', function () {
    $usuario = User::factory()->rol(Rol::Produccion)->create();

    expect(Insumo::where('es_leche', true)->exists())->toBeFalse();

    $this->actingAs($usuario);

    Livewire::test('pages::produccion.insumos')
        ->assertSee('Leche fresca')
        ->assertSee('Bloqueado');

    expect(Insumo::where('es_leche', true)->count())->toBe(1);
});

test('el dashboard de productos solo muestra producciones completadas con nombre, precio unidad, precio total y estado', function () {
    $usuario = User::factory()->rol(Rol::Produccion)->create();
    $unidad = UnidadMedida::factory()->create();
    $insumo = Insumo::factory()->create(['unidad_medida_id' => $unidad->id, 'costo_unitario' => 2, 'stock_actual' => 1000]);

    $recetaQueso = Receta::factory()->create(['nombre' => 'Queso fresco']);
    $recetaQueso->detalles()->create(['insumo_id' => $insumo->id, 'cantidad' => 5, 'unidad_medida_id' => $unidad->id]);

    $recetaYogurt = Receta::factory()->create(['nombre' => 'Yogurt natural']);
    $recetaYogurt->detalles()->create(['insumo_id' => $insumo->id, 'cantidad' => 5, 'unidad_medida_id' => $unidad->id]);

    $completada = Produccion::factory()->create(['receta_id' => $recetaQueso->id, 'cantidad_producida' => 4]);
    $completada->completar();

    Produccion::factory()->create(['receta_id' => $recetaYogurt->id, 'cantidad_producida' => 3, 'estado' => EstadoProduccion::Planificada]);

    $this->actingAs($usuario);

    Livewire::test('pages::produccion.productos')
        ->assertSee('Queso fresco')
        ->assertSee('S/ 10.00')
        ->assertSee('S/ 40.00')
        ->assertSee('Publicado')
        ->assertDontSee('Yogurt natural');
});

test('el rol de producción puede editar insumo, unidad, costo unitario y stock de un insumo que no es leche', function () {
    $usuario = User::factory()->rol(Rol::Produccion)->create();
    $unidadOriginal = UnidadMedida::factory()->create();
    $unidadNueva = UnidadMedida::factory()->create();
    $insumo = Insumo::factory()->create([
        'nombre' => 'Sal',
        'unidad_medida_id' => $unidadOriginal->id,
        'costo_unitario' => 1,
        'stock_actual' => 5,
    ]);

    $this->actingAs($usuario);

    Livewire::test('pages::produccion.insumos')
        ->call('editarInsumo', $insumo->id)
        ->set('insumoNombre', 'Sal industrial')
        ->set('insumoUnidadMedidaId', $unidadNueva->id)
        ->set('insumoCostoUnitario', '3.5')
        ->set('insumoStockActual', '25')
        ->call('guardarInsumo')
        ->assertHasNoErrors();

    $insumo->refresh();

    expect($insumo->nombre)->toBe('Sal industrial')
        ->and($insumo->unidad_medida_id)->toBe($unidadNueva->id)
        ->and((float) $insumo->costo_unitario)->toEqual(3.5)
        ->and((float) $insumo->stock_actual)->toEqual(25.0);
});

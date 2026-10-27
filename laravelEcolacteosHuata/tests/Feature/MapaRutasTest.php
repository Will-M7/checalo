<?php

use App\Enums\Rol;
use App\Models\Acopiador;
use App\Models\Proveedor;
use App\Models\Recojo;
use App\Models\Ruta;
use App\Models\Turno;
use App\Models\User;
use Livewire\Livewire;

test('el personal de planta guarda el camino trazado de una ruta', function () {
    $this->actingAs(User::factory()->rol(Rol::PersonalPlanta)->create());
    $ruta = Ruta::factory()->create();

    Livewire::test('pages::planta.rutas')
        ->set('rutaMapaId', $ruta->id)
        ->call('guardarTrazoRuta', $ruta->id, [[-15.61, -69.97], [-15.62, -69.98]])
        ->assertHasNoErrors()
        ->assertDispatched('mapa-rutas-general');

    expect($ruta->fresh()->trazo)->toBe([[-15.61, -69.97], [-15.62, -69.98]]);
});

test('no se guarda un camino con coordenadas fuera de rango', function () {
    $this->actingAs(User::factory()->rol(Rol::PersonalPlanta)->create());
    $ruta = Ruta::factory()->create();

    Livewire::test('pages::planta.rutas')
        ->set('rutaMapaId', $ruta->id)
        ->call('guardarTrazoRuta', $ruta->id, [[-95, -69.97]])
        ->assertHasErrors('puntos.0.0');

    expect($ruta->fresh()->trazo)->toBeNull();
});

test('el personal de planta reordena las paradas y el camino se genera en ese orden', function () {
    $this->actingAs(User::factory()->rol(Rol::PersonalPlanta)->create());
    $ruta = Ruta::factory()->create();
    $primero = Proveedor::factory()->create(['latitud' => -15.1, 'longitud' => -69.1]);
    $segundo = Proveedor::factory()->create(['latitud' => -15.2, 'longitud' => -69.2]);
    $ruta->agregarProveedor($primero->id);
    $ruta->agregarProveedor($segundo->id);

    Livewire::test('pages::planta.rutas')
        ->set('rutaMapaId', $ruta->id)
        ->call('moverParada', $segundo->id, -1)
        ->call('trazarPorParadas');

    expect($ruta->fresh()->proveedores->pluck('id')->all())->toBe([$segundo->id, $primero->id])
        ->and($ruta->fresh()->trazo)->toBe([[-15.2, -69.2], [-15.1, -69.1]]);
});

test('planta marca la ubicación de un proveedor sin quitarlo de la ruta', function () {
    $this->actingAs(User::factory()->rol(Rol::PersonalPlanta)->create());
    $ruta = Ruta::factory()->create();
    $proveedor = Proveedor::factory()->create(['latitud' => null, 'longitud' => null]);
    $ruta->agregarProveedor($proveedor->id);

    Livewire::test('pages::planta.rutas')
        ->set('rutaMapaId', $ruta->id)
        ->call('ubicarProveedor', $proveedor->id)
        ->call('marcarUbicacion', -15.6186, -69.9716)
        ->assertSet('ubicandoProveedorId', null);

    expect((float) $proveedor->fresh()->latitud)->toBe(-15.6186)
        ->and((float) $proveedor->fresh()->longitud)->toBe(-69.9716)
        ->and($ruta->proveedores()->whereKey($proveedor->id)->exists())->toBeTrue();
});

test('asignar un proveedor desde Rutas lo agrega al final del orden de recojo', function () {
    $this->actingAs(User::factory()->rol(Rol::PersonalPlanta)->create());
    $ruta = Ruta::factory()->create();
    $existente = Proveedor::factory()->create();
    $nuevo = Proveedor::factory()->create();
    $ruta->agregarProveedor($existente->id);

    Livewire::test('pages::planta.rutas')
        ->call('abrirAsignar', $ruta->id)
        ->call('asignarProveedor', $nuevo->id);

    expect($ruta->fresh()->proveedores->pluck('id')->all())->toBe([$existente->id, $nuevo->id]);
});

test('el acopiador ve en su mapa solo sus rutas activas con las paradas recogidas hoy', function () {
    $acopiadorUser = User::factory()->rol(Rol::Acopiador)->create();
    $acopiador = Acopiador::factory()->create(['user_id' => $acopiadorUser->id]);
    $ruta = Ruta::factory()->create(['acopiador_id' => $acopiador->id, 'nombre' => 'Ruta Huata Lago']);
    Ruta::factory()->create(['acopiador_id' => $acopiador->id, 'nombre' => 'Ruta inactiva', 'activa' => false]);
    Ruta::factory()->create(['nombre' => 'Ruta de otro acopiador']);

    $recogido = Proveedor::factory()->create(['acopiador_id' => $acopiador->id]);
    $pendiente = Proveedor::factory()->create(['acopiador_id' => $acopiador->id]);
    $ruta->agregarProveedor($recogido->id);
    $ruta->agregarProveedor($pendiente->id);

    Recojo::factory()->create([
        'proveedor_id' => $recogido->id,
        'acopiador_id' => $acopiador->id,
        'turno_id' => Turno::factory()->create()->id,
        'fecha' => now()->toDateString(),
        'entregado' => false,
    ]);

    $this->actingAs($acopiadorUser);

    $componente = Livewire::test('pages::acopiador.mapa-recojo')
        ->assertSet('rutaId', $ruta->id)
        ->assertSee('Ruta Huata Lago')
        ->assertSee('1 / 2 recogidos')
        ->assertDontSee('Ruta inactiva')
        ->assertDontSee('Ruta de otro acopiador');

    $paradas = collect($componente->viewData('datosMapa')['paradas'])->keyBy('id');

    expect($paradas[$recogido->id]['recogido'])->toBeTrue()
        ->and($paradas[$pendiente->id]['recogido'])->toBeFalse();
});

test('el acopiador no puede ver la ruta de otro acopiador cambiando el id', function () {
    $acopiadorUser = User::factory()->rol(Rol::Acopiador)->create();
    Acopiador::factory()->create(['user_id' => $acopiadorUser->id]);
    $ajena = Ruta::factory()->create(['nombre' => 'Ruta ajena']);

    $this->actingAs($acopiadorUser);

    Livewire::test('pages::acopiador.mapa-recojo')
        ->set('rutaId', $ajena->id)
        ->assertViewHas('ruta', null)
        ->assertDontSee('Ruta ajena');
});

test('el panel junto al mapa lista todos los proveedores con los de la ruta primero en su orden de recojo', function () {
    $this->actingAs(User::factory()->rol(Rol::PersonalPlanta)->create());
    $ruta = Ruta::factory()->create();
    $primero = Proveedor::factory()->create();
    $segundo = Proveedor::factory()->create();
    $sinRuta = Proveedor::factory()->create();
    $ruta->agregarProveedor($primero->id);
    $ruta->agregarProveedor($segundo->id);

    $componente = Livewire::test('pages::planta.rutas')
        ->set('rutaMapaId', $ruta->id)
        ->call('moverParada', $segundo->id, -1);

    $lista = $componente->viewData('proveedoresMapa');

    expect($lista->pluck('proveedor.id')->all())->toBe([$segundo->id, $primero->id, $sinRuta->id])
        ->and($lista->pluck('parada')->all())->toBe([1, 2, null]);

    $componente->call('agregarProveedorARuta', $ruta->id, $sinRuta->id)
        ->call('quitarProveedorDeRuta', $ruta->id, $primero->id);

    expect($ruta->fresh()->proveedores->pluck('id')->all())->toBe([$segundo->id, $sinRuta->id]);
});

test('al mover la ubicación dentro del rango del camino de otra ruta el proveedor pasa a esa ruta', function () {
    $this->actingAs(User::factory()->rol(Rol::PersonalPlanta)->create());
    $anterior = Ruta::factory()->create(['trazo' => [[-15.70, -69.90], [-15.71, -69.90]]]);
    $cercana = Ruta::factory()->create(['trazo' => [[-15.60, -69.97], [-15.62, -69.97]]]);
    $proveedor = Proveedor::factory()->create(['latitud' => -15.70, 'longitud' => -69.90]);
    $anterior->agregarProveedor($proveedor->id);

    // ~200 m al este del camino de la ruta cercana.
    Livewire::test('pages::planta.rutas')
        ->call('ubicarProveedor', $proveedor->id)
        ->call('marcarUbicacion', -15.61, -69.9681)
        ->assertDispatched('mapa-rutas-general');

    expect($proveedor->rutas()->pluck('rutas.id')->all())->toBe([$cercana->id])
        ->and($proveedor->fresh()->acopiador_id)->toBe($cercana->acopiador_id);
});

test('si la nueva ubicación queda fuera del rango de todas las rutas el proveedor conserva su ruta', function () {
    $this->actingAs(User::factory()->rol(Rol::PersonalPlanta)->create());
    $suRuta = Ruta::factory()->create(['trazo' => [[-15.70, -69.90], [-15.71, -69.90]]]);
    Ruta::factory()->create(['trazo' => [[-15.60, -69.97], [-15.62, -69.97]]]);
    $proveedor = Proveedor::factory()->create();
    $suRuta->agregarProveedor($proveedor->id);

    // ~2 km del camino más cercano.
    Livewire::test('pages::planta.rutas')
        ->call('ubicarProveedor', $proveedor->id)
        ->call('marcarUbicacion', -15.61, -69.95);

    expect($proveedor->rutas()->pluck('rutas.id')->all())->toBe([$suRuta->id]);
});

test('al arrastrar el marcador de un proveedor se guarda su ubicación según el rango guardado de cada ruta', function () {
    $this->actingAs(User::factory()->rol(Rol::PersonalPlanta)->create());
    $anterior = Ruta::factory()->create(['trazo' => [[-15.70, -69.90], [-15.71, -69.90]]]);
    $angosta = Ruta::factory()->create(['trazo' => [[-15.60, -69.97], [-15.62, -69.97]], 'rango' => 100]);
    $proveedor = Proveedor::factory()->create(['latitud' => -15.70, 'longitud' => -69.90]);
    $anterior->agregarProveedor($proveedor->id);

    // ~200 m al este del camino: fuera de los 100 m de la ruta angosta.
    $componente = Livewire::test('pages::planta.rutas')
        ->call('moverUbicacionProveedor', $proveedor->id, -15.61, -69.9681)
        ->assertDispatched('mapa-rutas-general');

    expect((float) $proveedor->fresh()->latitud)->toBe(-15.61)
        ->and($proveedor->rutas()->pluck('rutas.id')->all())->toBe([$anterior->id]);

    $componente->call('guardarRangoRuta', $angosta->id, 500)
        ->call('moverUbicacionProveedor', $proveedor->id, -15.61, -69.9681);

    expect($angosta->fresh()->rango)->toBe(500)
        ->and($proveedor->rutas()->pluck('rutas.id')->all())->toBe([$angosta->id]);
});

test('no se guarda un rango de ruta fuera de los límites', function () {
    $this->actingAs(User::factory()->rol(Rol::PersonalPlanta)->create());
    $ruta = Ruta::factory()->create();

    Livewire::test('pages::planta.rutas')
        ->call('guardarRangoRuta', $ruta->id, 6000)
        ->assertHasErrors('rango');

    expect($ruta->fresh()->rango)->toBe(500);
});

test('al guardar el camino las paradas se numeran de inicio a fin y los proveedores sin ubicación quedan al final', function () {
    $this->actingAs(User::factory()->rol(Rol::PersonalPlanta)->create());
    $ruta = Ruta::factory()->create();
    $sinUbicacion = Proveedor::factory()->create(['latitud' => null, 'longitud' => null]);
    $alFinal = Proveedor::factory()->create(['latitud' => -15.69, 'longitud' => -69.90]);
    $alInicio = Proveedor::factory()->create(['latitud' => -15.61, 'longitud' => -69.90]);
    $ruta->agregarProveedor($sinUbicacion->id);
    $ruta->agregarProveedor($alFinal->id);
    $ruta->agregarProveedor($alInicio->id);

    Livewire::test('pages::planta.rutas')
        ->call('guardarTrazoRuta', $ruta->id, [[-15.60, -69.90], [-15.70, -69.90]]);

    expect($ruta->fresh()->proveedores->pluck('id')->all())->toBe([$alInicio->id, $alFinal->id, $sinUbicacion->id]);
});

test('un proveedor agregado a una ruta con camino toma su número según dónde cae en el camino', function () {
    $ruta = Ruta::factory()->create(['trazo' => [[-15.60, -69.90], [-15.70, -69.90]]]);
    $primero = Proveedor::factory()->create(['latitud' => -15.61, 'longitud' => -69.90]);
    $ultimo = Proveedor::factory()->create(['latitud' => -15.69, 'longitud' => -69.90]);
    $enMedio = Proveedor::factory()->create(['latitud' => -15.65, 'longitud' => -69.90]);

    $ruta->agregarProveedor($primero->id);
    $ruta->agregarProveedor($ultimo->id);
    $ruta->agregarProveedor($enMedio->id);

    expect($ruta->fresh()->proveedores->pluck('id')->all())->toBe([$primero->id, $enMedio->id, $ultimo->id]);
});

test('al mover la ubicación de un proveedor dentro de su ruta se renumeran las paradas', function () {
    $this->actingAs(User::factory()->rol(Rol::PersonalPlanta)->create());
    $ruta = Ruta::factory()->create(['trazo' => [[-15.60, -69.90], [-15.70, -69.90]]]);
    $primero = Proveedor::factory()->create(['latitud' => -15.61, 'longitud' => -69.90]);
    $segundo = Proveedor::factory()->create(['latitud' => -15.65, 'longitud' => -69.90]);
    $ruta->agregarProveedor($primero->id);
    $ruta->agregarProveedor($segundo->id);

    Livewire::test('pages::planta.rutas')
        ->call('moverUbicacionProveedor', $primero->id, -15.69, -69.90);

    expect($ruta->fresh()->proveedores->pluck('id')->all())->toBe([$segundo->id, $primero->id]);
});

<?php

use App\Enums\Rol;
use App\Models\Acopiador;
use App\Models\Proveedor;
use App\Models\Ruta;
use App\Models\User;
use Livewire\Livewire;

test('el personal de planta puede crear una nueva ruta eligiendo su acopiador', function () {
    $planta = User::factory()->rol(Rol::PersonalPlanta)->create();
    $this->actingAs($planta);

    $acopiador = Acopiador::factory()->create();

    Livewire::test('pages::planta.rutas')
        ->call('nuevaRuta')
        ->set('rutaFormNombre', 'Ruta Huata Lago')
        ->set('rutaFormAcopiadorId', $acopiador->id)
        ->call('guardarRuta')
        ->assertHasNoErrors();

    $ruta = Ruta::where('nombre', 'Ruta Huata Lago')->firstOrFail();

    expect($ruta->acopiador_id)->toBe($acopiador->id)
        ->and($ruta->activa)->toBeTrue();
});

test('no se puede crear una ruta sin nombre o sin acopiador', function () {
    $planta = User::factory()->rol(Rol::PersonalPlanta)->create();
    $this->actingAs($planta);

    Livewire::test('pages::planta.rutas')
        ->call('nuevaRuta')
        ->call('guardarRuta')
        ->assertHasErrors(['rutaFormNombre', 'rutaFormAcopiadorId']);
});

test('el personal de planta puede editar el nombre y el estado de una ruta existente', function () {
    $planta = User::factory()->rol(Rol::PersonalPlanta)->create();
    $this->actingAs($planta);

    $acopiador = Acopiador::factory()->create();
    $ruta = Ruta::factory()->create(['acopiador_id' => $acopiador->id, 'nombre' => 'Ruta vieja', 'activa' => true]);

    Livewire::test('pages::planta.rutas')
        ->call('editarRuta', $ruta->id)
        ->assertSet('rutaFormNombre', 'Ruta vieja')
        ->set('rutaFormNombre', 'Ruta renombrada')
        ->set('rutaFormActiva', false)
        ->call('guardarRuta')
        ->assertHasNoErrors();

    expect($ruta->fresh()->nombre)->toBe('Ruta renombrada')
        ->and($ruta->fresh()->activa)->toBeFalse();
});

test('el personal de planta puede eliminar una ruta, y sus proveedores dejan de estar asignados a ella', function () {
    $planta = User::factory()->rol(Rol::PersonalPlanta)->create();
    $this->actingAs($planta);

    $acopiador = Acopiador::factory()->create();
    $ruta = Ruta::factory()->create(['acopiador_id' => $acopiador->id]);
    $proveedor = Proveedor::factory()->create(['acopiador_id' => $acopiador->id]);
    $ruta->proveedores()->attach($proveedor->id);

    Livewire::test('pages::planta.rutas')
        ->call('pedirEliminarRuta', $ruta->id)
        ->call('eliminarRuta');

    expect(Ruta::find($ruta->id))->toBeNull();
    // El proveedor sigue existiendo, solo se quitó el vínculo con la ruta eliminada.
    expect(Proveedor::find($proveedor->id))->not->toBeNull();
});

test('un acopiador puede tener varias rutas y cada una muestra solo sus propios proveedores asignados', function () {
    $planta = User::factory()->rol(Rol::PersonalPlanta)->create();
    $this->actingAs($planta);

    $acopiador = Acopiador::factory()->create();
    $rutaCentro = Ruta::factory()->create(['acopiador_id' => $acopiador->id, 'nombre' => 'Ruta Centro']);
    $rutaLago = Ruta::factory()->create(['acopiador_id' => $acopiador->id, 'nombre' => 'Ruta Lago']);

    $proveedorCentro = Proveedor::factory()->create(['acopiador_id' => $acopiador->id]);
    $rutaCentro->proveedores()->attach($proveedorCentro->id);

    Livewire::test('pages::planta.rutas')
        ->assertSeeInOrder(['Ruta Centro', 'Ruta Lago']);
});

test('un proveedor cercano al camino se agrega a la ruta desde el mapa general', function () {
    $this->actingAs(User::factory()->rol(Rol::PersonalPlanta)->create());
    $ruta = Ruta::factory()->create();
    $proveedor = Proveedor::factory()->create(['latitud' => -15.61, 'longitud' => -69.97]);

    $componente = Livewire::test('pages::planta.rutas');

    expect(collect($componente->viewData('datosMapaGeneral')['proveedores'])->firstWhere('id', $proveedor->id)['rutaIds'])->toBe([]);

    $componente->call('agregarProveedorARuta', $ruta->id, $proveedor->id)
        ->assertDispatched('mapa-rutas-general');

    expect($ruta->proveedores()->whereKey($proveedor->id)->exists())->toBeTrue()
        ->and($proveedor->fresh()->acopiador_id)->toBe($ruta->acopiador_id);
});

test('un proveedor solo puede estar en una ruta: al asignarlo a otra sale de la anterior', function () {
    $this->actingAs(User::factory()->rol(Rol::PersonalPlanta)->create());
    $anterior = Ruta::factory()->create();
    $nueva = Ruta::factory()->create();
    $proveedor = Proveedor::factory()->create();
    $anterior->agregarProveedor($proveedor->id);

    Livewire::test('pages::planta.rutas')
        ->call('abrirAsignar', $nueva->id)
        ->call('asignarProveedor', $proveedor->id);

    expect($proveedor->rutas()->pluck('rutas.id')->all())->toBe([$nueva->id])
        ->and($anterior->proveedores()->count())->toBe(0);
});

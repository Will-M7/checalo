<?php

use App\Enums\Rol;
use App\Models\Acopiador;
use App\Models\Proveedor;
use App\Models\Ruta;
use App\Models\User;
use Livewire\Livewire;

test('el personal de planta puede asignar y quitar un proveedor de una ruta', function () {
    $planta = User::factory()->rol(Rol::PersonalPlanta)->create();
    $this->actingAs($planta);

    $acopiador = Acopiador::factory()->create();
    $ruta = Ruta::factory()->create(['acopiador_id' => $acopiador->id]);
    $proveedor = Proveedor::factory()->create(['acopiador_id' => $acopiador->id]);

    $component = Livewire::test('pages::planta.rutas')
        ->call('abrirAsignar', $ruta->id)
        ->assertSee($proveedor->user->nombres)
        ->assertSee($proveedor->finca_ubicacion);

    $component->call('asignarProveedor', $proveedor->id);

    expect($ruta->fresh()->proveedores->pluck('id'))->toContain($proveedor->id);

    $component->call('quitarProveedor', $proveedor->id);

    expect($ruta->fresh()->proveedores->pluck('id'))->not->toContain($proveedor->id);
});

test('la búsqueda de proveedores a asignar filtra por nombre y apellido', function () {
    $planta = User::factory()->rol(Rol::PersonalPlanta)->create();
    $this->actingAs($planta);

    $acopiador = Acopiador::factory()->create();
    $ruta = Ruta::factory()->create(['acopiador_id' => $acopiador->id]);

    $rosa = Proveedor::factory()->for(User::factory()->rol(Rol::Proveedor)->state(['nombres' => 'Rosa', 'apellidos' => 'Mamani']), 'user')
        ->create(['acopiador_id' => $acopiador->id]);
    $juan = Proveedor::factory()->for(User::factory()->rol(Rol::Proveedor)->state(['nombres' => 'Juan', 'apellidos' => 'Condori']), 'user')
        ->create(['acopiador_id' => $acopiador->id]);

    Livewire::test('pages::planta.rutas')
        ->call('abrirAsignar', $ruta->id)
        ->set('buscarProveedorAsignar', 'Rosa')
        ->assertViewHas('proveedoresAsignar', fn ($items) => $items->pluck('proveedor.id')->all() === [$rosa->id]);

    expect($juan)->not->toBeNull();
});

test('el botón asignar proveedores de un acopiador sin rutas crea una ruta principal automáticamente', function () {
    $planta = User::factory()->rol(Rol::PersonalPlanta)->create();
    $this->actingAs($planta);

    $acopiador = Acopiador::factory()->create(['ruta_nombre' => 'Ruta Huata Sur']);
    $proveedor = Proveedor::factory()->create(['acopiador_id' => $acopiador->id]);

    expect($acopiador->rutas()->count())->toBe(0);

    Livewire::test('pages::planta.rutas')
        ->call('abrirAsignarDeAcopiador', $acopiador->id)
        ->assertSee($proveedor->user->nombres);

    $ruta = $acopiador->rutas()->first();

    expect($ruta)->not->toBeNull()
        ->and($ruta->nombre)->toBe('Ruta Huata Sur');
});

test('un proveedor de otro acopiador sin ruta asignada aparece como candidato y se puede asignar', function () {
    $planta = User::factory()->rol(Rol::PersonalPlanta)->create();
    $this->actingAs($planta);

    $acopiadorA = Acopiador::factory()->create();
    $acopiadorB = Acopiador::factory()->create();
    $ruta = Ruta::factory()->create(['acopiador_id' => $acopiadorA->id]);

    $sinRuta = Proveedor::factory()->create(['acopiador_id' => $acopiadorB->id]);

    Livewire::test('pages::planta.rutas')
        ->call('abrirAsignar', $ruta->id)
        ->assertSee($sinRuta->user->nombres)
        ->assertSee('Sin ruta')
        ->call('asignarProveedor', $sinRuta->id);

    expect($ruta->fresh()->proveedores->pluck('id'))->toContain($sinRuta->id)
        // Al asignarlo a la ruta, pasa a pertenecer formalmente al acopiador dueño de esa ruta.
        ->and($sinRuta->fresh()->acopiador_id)->toBe($acopiadorA->id);
});

test('asignar un proveedor a una ruta hace que aparezca en el panel de Recojo del acopiador', function () {
    $planta = User::factory()->rol(Rol::PersonalPlanta)->create();
    $this->actingAs($planta);

    $acopiadorUser = User::factory()->rol(Rol::Acopiador)->create();
    $acopiador = Acopiador::factory()->create(['user_id' => $acopiadorUser->id]);
    $ruta = Ruta::factory()->create(['acopiador_id' => $acopiador->id]);

    // Proveedor recién creado, todavía sin acopiador (como si viniera de Usuarios sin asignar).
    $proveedor = Proveedor::factory()->create(['acopiador_id' => null]);

    Livewire::test('pages::planta.rutas')
        ->call('abrirAsignar', $ruta->id)
        ->call('asignarProveedor', $proveedor->id);

    expect($proveedor->fresh()->acopiador_id)->toBe($acopiador->id);

    $this->actingAs($acopiadorUser);

    Livewire::test('pages::acopiador.recojo')
        ->assertDontSee('No tienes proveedores asignados todavía.')
        ->assertSee($proveedor->user->nombres);
});

test('el botón asignar proveedores de un acopiador sin rutas permite asignar y luego quitar igual que uno con ruta', function () {
    $planta = User::factory()->rol(Rol::PersonalPlanta)->create();
    $this->actingAs($planta);

    $acopiador = Acopiador::factory()->create();
    $proveedor = Proveedor::factory()->create(['acopiador_id' => $acopiador->id]);

    expect($acopiador->rutas()->count())->toBe(0);

    $component = Livewire::test('pages::planta.rutas')
        ->call('abrirAsignarDeAcopiador', $acopiador->id)
        ->call('asignarProveedor', $proveedor->id);

    $ruta = $acopiador->rutas()->first();

    expect($ruta->fresh()->proveedores->pluck('id'))->toContain($proveedor->id);

    $component->call('quitarProveedor', $proveedor->id);

    expect($ruta->fresh()->proveedores->pluck('id'))->not->toContain($proveedor->id)
        ->and($acopiador->rutas()->count())->toBe(1);
});

test('un proveedor de otro acopiador sigue apareciendo y se puede quitar después de asignarlo', function () {
    $planta = User::factory()->rol(Rol::PersonalPlanta)->create();
    $this->actingAs($planta);

    $acopiadorA = Acopiador::factory()->create();
    $acopiadorB = Acopiador::factory()->create();
    $ruta = Ruta::factory()->create(['acopiador_id' => $acopiadorA->id]);

    $sinRuta = Proveedor::factory()->create(['acopiador_id' => $acopiadorB->id]);

    $component = Livewire::test('pages::planta.rutas')
        ->call('abrirAsignar', $ruta->id)
        ->call('asignarProveedor', $sinRuta->id);

    expect($ruta->fresh()->proveedores->pluck('id'))->toContain($sinRuta->id);

    // Tras asignarlo, debe seguir viéndose en la lista (con su botón "Quitar"), no desaparecer.
    $component->assertSee($sinRuta->user->nombres)
        ->call('quitarProveedor', $sinRuta->id);

    expect($ruta->fresh()->proveedores->pluck('id'))->not->toContain($sinRuta->id);
});

test('un proveedor de otro acopiador que ya tiene ruta no aparece como candidato', function () {
    $planta = User::factory()->rol(Rol::PersonalPlanta)->create();
    $this->actingAs($planta);

    $acopiadorA = Acopiador::factory()->create();
    $acopiadorB = Acopiador::factory()->create();
    $rutaA = Ruta::factory()->create(['acopiador_id' => $acopiadorA->id]);
    $rutaB = Ruta::factory()->create(['acopiador_id' => $acopiadorB->id]);

    $yaTieneRuta = Proveedor::factory()->create(['acopiador_id' => $acopiadorB->id]);
    $rutaB->proveedores()->attach($yaTieneRuta->id);

    Livewire::test('pages::planta.rutas')
        ->call('abrirAsignar', $rutaA->id)
        ->assertDontSee("asignarProveedor({$yaTieneRuta->id})", false);
});

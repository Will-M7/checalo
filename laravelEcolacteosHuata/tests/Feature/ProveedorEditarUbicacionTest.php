<?php

use App\Enums\Rol;
use App\Models\Acopiador;
use App\Models\Proveedor;
use App\Models\Ruta;
use App\Models\User;
use App\Notifications\ProveedorUbicacionActualizada;
use Illuminate\Support\Facades\Notification;
use Livewire\Livewire;

test('el proveedor puede editar su ubicación', function () {
    $user = User::factory()->rol(Rol::Proveedor)->create();
    $proveedor = Proveedor::factory()->create(['user_id' => $user->id, 'finca_ubicacion' => 'Ubicación vieja']);

    $this->actingAs($user);

    Livewire::test('pages::proveedor.panel')
        ->call('editarUbicacion')
        ->set('finca_ubicacion', 'Comunidad de Huata, sector nuevo')
        ->set('finca_referencia', 'A 300m de la plaza')
        ->set('latitud', -15.75)
        ->set('longitud', -70.02)
        ->call('guardarUbicacion')
        ->assertHasNoErrors();

    $proveedor->refresh();

    expect($proveedor->finca_ubicacion)->toBe('Comunidad de Huata, sector nuevo')
        ->and($proveedor->finca_referencia)->toBe('A 300m de la plaza')
        ->and((float) $proveedor->latitud)->toBe(-15.75)
        ->and((float) $proveedor->longitud)->toBe(-70.02);
});

test('editar la ubicación mantiene al proveedor en la ruta de su acopiador y mueve su punto en el camino', function () {
    $user = User::factory()->rol(Rol::Proveedor)->create();
    $acopiador = Acopiador::factory()->create();
    $proveedor = Proveedor::factory()->create(['user_id' => $user->id, 'acopiador_id' => $acopiador->id, 'latitud' => -15.5, 'longitud' => -70.15]);
    $ruta = Ruta::factory()->create(['acopiador_id' => $acopiador->id, 'trazo' => [[-15.5, -70.1], [-15.5, -70.15], [-15.5, -70.2]]]);
    $ruta->agregarProveedor($proveedor->id);

    $this->actingAs($user);

    Livewire::test('pages::proveedor.panel')
        ->call('editarUbicacion')
        ->set('finca_ubicacion', 'Nueva ubicación')
        ->set('latitud', -15.51)
        ->set('longitud', -70.17)
        ->call('guardarUbicacion')
        ->assertHasNoErrors();

    expect($proveedor->rutas()->pluck('rutas.id')->all())->toBe([$ruta->id])
        ->and($ruta->fresh()->trazo)->toBe([[-15.5, -70.1], [-15.51, -70.17], [-15.5, -70.2]]);
});

test('al elegir un acopiador el proveedor queda en la ruta más cercana a su finca, insertado en el camino', function () {
    Notification::fake();

    $user = User::factory()->rol(Rol::Proveedor)->create();
    $proveedor = Proveedor::factory()->create(['user_id' => $user->id]);
    $acopiador = Acopiador::factory()->create();
    $rutaLejana = Ruta::factory()->create(['acopiador_id' => $acopiador->id, 'nombre' => 'A lejana', 'trazo' => [[-15.9, -69.9], [-15.91, -69.91]]]);
    $rutaCercana = Ruta::factory()->create(['acopiador_id' => $acopiador->id, 'nombre' => 'B cercana', 'trazo' => [[-15.5, -70.1], [-15.5, -70.2]]]);
    $planta = User::factory()->rol(Rol::PersonalPlanta)->create();

    $this->actingAs($user);

    Livewire::test('pages::settings.profile')
        ->set('acopiador_id', $acopiador->id)
        ->set('latitud', -15.51)
        ->set('longitud', -70.15)
        ->call('actualizarUbicacion')
        ->assertHasNoErrors()
        ->assertDispatched('mapa-ubicacion-rutas', fn (string $evento, array $datos) => in_array(
            ['nombre' => 'B cercana', 'trazo' => [[-15.5, -70.1], [-15.51, -70.15], [-15.5, -70.2]]],
            $datos['rutas'],
        ));

    expect($proveedor->rutas()->pluck('rutas.id')->all())->toBe([$rutaCercana->id])
        ->and($rutaLejana->fresh()->trazo)->toBe([[-15.9, -69.9], [-15.91, -69.91]]);
    Notification::assertNothingSentTo($planta);
});

test('editar la ubicación notifica a administrador y personal de planta, incluyendo roles secundarios, pero no a otros roles', function () {
    Notification::fake();

    $user = User::factory()->rol(Rol::Proveedor)->create();
    Proveedor::factory()->create(['user_id' => $user->id]);

    $admin = User::factory()->rol(Rol::Administrador)->create();
    $planta = User::factory()->rol(Rol::PersonalPlanta)->create();
    $vendedorConPlantaSecundario = User::factory()->rol(Rol::Vendedor)->create([
        'roles' => [Rol::Vendedor->value, Rol::PersonalPlanta->value],
    ]);
    $vendedor = User::factory()->rol(Rol::Vendedor)->create();
    $otroProveedor = User::factory()->rol(Rol::Proveedor)->create();

    $this->actingAs($user);

    Livewire::test('pages::proveedor.panel')
        ->call('editarUbicacion')
        ->set('finca_ubicacion', 'Nueva ubicación')
        ->call('guardarUbicacion')
        ->assertHasNoErrors();

    Notification::assertSentTo($admin, ProveedorUbicacionActualizada::class);
    Notification::assertSentTo($planta, ProveedorUbicacionActualizada::class);
    Notification::assertSentTo($vendedorConPlantaSecundario, ProveedorUbicacionActualizada::class);
    Notification::assertNotSentTo($vendedor, ProveedorUbicacionActualizada::class);
    Notification::assertNotSentTo($otroProveedor, ProveedorUbicacionActualizada::class);
    Notification::assertNotSentTo($user, ProveedorUbicacionActualizada::class);
});

test('al elegir un acopiador, el mapa del proveedor recibe el camino de sus rutas activas', function () {
    $user = User::factory()->rol(Rol::Proveedor)->create();
    Proveedor::factory()->create(['user_id' => $user->id]);
    $acopiador = Acopiador::factory()->create();
    Ruta::factory()->create(['acopiador_id' => $acopiador->id, 'nombre' => 'Ruta Lago', 'trazo' => [[-15.6, -69.9], [-15.61, -69.91]]]);
    Ruta::factory()->create(['acopiador_id' => $acopiador->id, 'nombre' => 'Ruta inactiva', 'activa' => false, 'trazo' => [[-15.7, -69.8], [-15.71, -69.81]]]);

    $this->actingAs($user);

    Livewire::test('pages::proveedor.panel')
        ->call('editarUbicacion')
        ->set('acopiador_id', $acopiador->id)
        ->assertDispatched('mapa-ubicacion-rutas', fn (string $evento, array $datos) => $datos['rutas'] === [
            ['nombre' => 'Ruta Lago', 'trazo' => [[-15.6, -69.9], [-15.61, -69.91]]],
        ]);
});

test('el proveedor guarda el acopiador elegido junto con su ubicación', function () {
    $user = User::factory()->rol(Rol::Proveedor)->create();
    $proveedor = Proveedor::factory()->create(['user_id' => $user->id]);
    $nuevoAcopiador = Acopiador::factory()->create();

    $this->actingAs($user);

    Livewire::test('pages::proveedor.panel')
        ->call('editarUbicacion')
        ->set('acopiador_id', null)
        ->call('guardarUbicacion')
        ->assertHasErrors('acopiador_id')
        ->set('acopiador_id', $nuevoAcopiador->id)
        ->call('guardarUbicacion')
        ->assertHasNoErrors();

    expect($proveedor->fresh()->acopiador_id)->toBe($nuevoAcopiador->id);
});

test('cambiar solo de acopiador quita al proveedor de las rutas del acopiador anterior y avisa a planta', function () {
    Notification::fake();

    $user = User::factory()->rol(Rol::Proveedor)->create();
    $acopiador = Acopiador::factory()->create();
    $proveedor = Proveedor::factory()->create(['user_id' => $user->id, 'acopiador_id' => $acopiador->id]);
    Ruta::factory()->create(['acopiador_id' => $acopiador->id])->proveedores()->attach($proveedor->id);
    $planta = User::factory()->rol(Rol::PersonalPlanta)->create();

    $this->actingAs($user);

    Livewire::test('pages::proveedor.panel')
        ->call('editarUbicacion')
        ->set('acopiador_id', Acopiador::factory()->create()->id)
        ->call('guardarUbicacion')
        ->assertHasNoErrors();

    expect($proveedor->rutas()->count())->toBe(0);
    Notification::assertSentTo($planta, ProveedorUbicacionActualizada::class);
});

test('en su perfil el proveedor elige el acopiador, ve su ruta en el mapa y guarda su ubicación', function () {
    $user = User::factory()->rol(Rol::Proveedor)->create();
    $proveedor = Proveedor::factory()->create(['user_id' => $user->id]);
    $acopiador = Acopiador::factory()->create();
    Ruta::factory()->create(['acopiador_id' => $acopiador->id, 'nombre' => 'Ruta Lago', 'trazo' => [[-15.6, -69.9], [-15.61, -69.91]]]);

    $this->actingAs($user);

    Livewire::test('pages::settings.profile')
        ->assertSee('Acopiador que recoge tu leche')
        ->set('acopiador_id', $acopiador->id)
        ->assertDispatched('mapa-ubicacion-rutas', fn (string $evento, array $datos) => $datos['rutas'] === [
            ['nombre' => 'Ruta Lago', 'trazo' => [[-15.6, -69.9], [-15.61, -69.91]]],
        ])
        ->set('latitud', -15.605)
        ->set('longitud', -69.905)
        ->call('actualizarUbicacion')
        ->assertHasNoErrors();

    $proveedor->refresh();

    expect($proveedor->acopiador_id)->toBe($acopiador->id)
        ->and((float) $proveedor->latitud)->toBe(-15.605)
        ->and((float) $proveedor->longitud)->toBe(-69.905);
});

test('en su perfil el proveedor no puede guardar la ubicación sin elegir acopiador', function () {
    $user = User::factory()->rol(Rol::Proveedor)->create();
    Proveedor::factory()->create(['user_id' => $user->id]);

    $this->actingAs($user);

    Livewire::test('pages::settings.profile')
        ->set('acopiador_id', null)
        ->call('actualizarUbicacion')
        ->assertHasErrors('acopiador_id');
});

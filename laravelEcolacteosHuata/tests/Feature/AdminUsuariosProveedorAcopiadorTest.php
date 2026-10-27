<?php

use App\Enums\Rol;
use App\Models\Acopiador;
use App\Models\Proveedor;
use App\Models\Ruta;
use App\Models\User;
use App\Notifications\ProveedorUbicacionActualizada;
use Illuminate\Support\Facades\Notification;
use Livewire\Livewire;

test('crear un usuario con rol proveedor exige elegir su acopiador y lo enlaza correctamente', function () {
    $admin = User::factory()->rol(Rol::Administrador)->create();
    $acopiador = Acopiador::factory()->create();

    $this->actingAs($admin);

    Livewire::test('pages::admin.usuarios')
        ->call('nuevo')
        ->set('nombres', 'Rosa')
        ->set('apellidos', 'Mamani')
        ->set('documento', '12345678')
        ->set('rolesSeleccionados', [Rol::Proveedor->value])
        ->set('usuario', 'rosa.mamani')
        ->set('password', 'secreto1')
        ->set('finca_ubicacion', 'Chillora, comunidad de Huata')
        ->call('guardar')
        ->assertHasErrors(['proveedorAcopiadorId']);

    Livewire::test('pages::admin.usuarios')
        ->call('nuevo')
        ->set('nombres', 'Rosa')
        ->set('apellidos', 'Mamani')
        ->set('documento', '12345678')
        ->set('rolesSeleccionados', [Rol::Proveedor->value])
        ->set('usuario', 'rosa.mamani')
        ->set('password', 'secreto1')
        ->set('finca_ubicacion', 'Chillora, comunidad de Huata')
        ->set('proveedorAcopiadorId', $acopiador->id)
        ->call('guardar')
        ->assertHasNoErrors();

    $usuario = User::where('usuario', 'rosa.mamani')->firstOrFail();
    $proveedor = Proveedor::where('user_id', $usuario->id)->first();

    expect($proveedor)->not->toBeNull()
        ->and($proveedor->acopiador_id)->toBe($acopiador->id)
        ->and($acopiador->proveedores()->pluck('id'))->toContain($proveedor->id);
});

test('crear un proveedor nuevo desde Usuarios no dispara la notificación de cambio de ubicación', function () {
    Notification::fake();

    $admin = User::factory()->rol(Rol::Administrador)->create();
    $acopiador = Acopiador::factory()->create();

    $this->actingAs($admin);

    Livewire::test('pages::admin.usuarios')
        ->call('nuevo')
        ->set('nombres', 'Rosa')
        ->set('apellidos', 'Mamani')
        ->set('documento', '12345678')
        ->set('rolesSeleccionados', [Rol::Proveedor->value])
        ->set('usuario', 'rosa.mamani')
        ->set('password', 'secreto1')
        ->set('finca_ubicacion', 'Chillora, comunidad de Huata')
        ->set('proveedorAcopiadorId', $acopiador->id)
        ->call('guardar')
        ->assertHasNoErrors();

    Notification::assertNothingSent();
});

test('editar la ubicación de un proveedor existente desde Usuarios lo quita de sus rutas y notifica a planta', function () {
    Notification::fake();

    $admin = User::factory()->rol(Rol::Administrador)->create();
    $planta = User::factory()->rol(Rol::PersonalPlanta)->create();
    $acopiador = Acopiador::factory()->create();
    $proveedorUser = User::factory()->rol(Rol::Proveedor)->create();
    $proveedor = Proveedor::factory()->create([
        'user_id' => $proveedorUser->id,
        'acopiador_id' => $acopiador->id,
        'finca_ubicacion' => 'Ubicación vieja',
    ]);
    $ruta = Ruta::factory()->create(['acopiador_id' => $acopiador->id]);
    $ruta->proveedores()->attach($proveedor->id);

    $this->actingAs($admin);

    Livewire::test('pages::admin.usuarios')
        ->call('editar', $proveedorUser->id)
        ->set('finca_ubicacion', 'Ubicación nueva')
        ->set('proveedorAcopiadorId', $acopiador->id)
        ->call('guardar')
        ->assertHasNoErrors();

    expect($proveedor->rutas()->count())->toBe(0);
    Notification::assertSentTo($planta, ProveedorUbicacionActualizada::class);
});

test('cambiar la ubicación y el acopiador a la vez desde Usuarios respeta el acopiador elegido', function () {
    Notification::fake();

    $admin = User::factory()->rol(Rol::Administrador)->create();
    $acopiadorViejo = Acopiador::factory()->create();
    $acopiadorNuevo = Acopiador::factory()->create();
    $proveedorUser = User::factory()->rol(Rol::Proveedor)->create();
    $proveedor = Proveedor::factory()->create([
        'user_id' => $proveedorUser->id,
        'acopiador_id' => $acopiadorViejo->id,
        'finca_ubicacion' => 'Ubicación vieja',
    ]);
    $ruta = Ruta::factory()->create(['acopiador_id' => $acopiadorViejo->id]);
    $ruta->proveedores()->attach($proveedor->id);

    $this->actingAs($admin);

    // El admin cambia la ubicación Y de paso elige otro acopiador en el mismo formulario.
    Livewire::test('pages::admin.usuarios')
        ->call('editar', $proveedorUser->id)
        ->set('finca_ubicacion', 'Ubicación nueva')
        ->set('proveedorAcopiadorId', $acopiadorNuevo->id)
        ->call('guardar')
        ->assertHasNoErrors();

    // El acopiador elegido en el formulario siempre se respeta, aunque la ubicación también
    // haya cambiado; solo la ruta queda pendiente de reasignación manual desde Rutas.
    expect($proveedor->fresh()->acopiador_id)->toBe($acopiadorNuevo->id)
        ->and($proveedor->rutas()->count())->toBe(0);
});

test('editar un proveedor existente desde Usuarios sin cambiar la ubicación no dispara la notificación', function () {
    Notification::fake();

    $admin = User::factory()->rol(Rol::Administrador)->create();
    $acopiador = Acopiador::factory()->create();
    $proveedorUser = User::factory()->rol(Rol::Proveedor)->create();
    $proveedor = Proveedor::factory()->create([
        'user_id' => $proveedorUser->id,
        'acopiador_id' => $acopiador->id,
        'finca_ubicacion' => 'Misma ubicación',
    ]);
    $ruta = Ruta::factory()->create(['acopiador_id' => $acopiador->id]);
    $ruta->proveedores()->attach($proveedor->id);

    $this->actingAs($admin);

    Livewire::test('pages::admin.usuarios')
        ->call('editar', $proveedorUser->id)
        ->set('proveedorAcopiadorId', $acopiador->id)
        ->call('guardar')
        ->assertHasNoErrors();

    expect($proveedor->rutas()->count())->toBe(1);
    Notification::assertNothingSent();
});

test('la página de usuarios carga Leaflet desde el inicio para que el mapa del proveedor se dibuje', function () {
    $admin = usuarioAdmin();
    $this->actingAs($admin);

    // El mapa aparece recién al elegir el rol Proveedor (HTML que Livewire inserta sin ejecutar
    // sus <script>), por eso Leaflet tiene que venir ya en la página.
    $this->get(route('admin.usuarios', ['current_team' => $admin->currentTeam]))
        ->assertOk()
        ->assertSee('leaflet@1.9.4/dist/leaflet.js', false);
});

test('al editar un proveedor el mapa recibe su ubicación, y al crear uno nuevo se limpia', function () {
    $admin = User::factory()->rol(Rol::Administrador)->create();
    $proveedor = Proveedor::factory()->create(['latitud' => -15.51, 'longitud' => -70.18]);

    $this->actingAs($admin);

    Livewire::test('pages::admin.usuarios')
        ->call('editar', $proveedor->user_id)
        ->assertDispatched('mapa-ubicacion-rutas', lat: -15.51, lng: -70.18)
        ->call('nuevo')
        ->assertDispatched('mapa-ubicacion-rutas', lat: null, lng: null);
});

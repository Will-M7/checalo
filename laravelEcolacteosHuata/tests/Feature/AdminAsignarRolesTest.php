<?php

use App\Enums\Rol;
use App\Models\Acopiador;
use App\Models\Proveedor;
use App\Models\Ruta;
use App\Models\User;
use App\Notifications\ProveedorUbicacionActualizada;
use Illuminate\Support\Facades\Notification;
use Livewire\Livewire;

test('el administrador puede asignarle varios roles a un usuario', function () {
    $admin = User::factory()->rol(Rol::Administrador)->create();
    $usuario = User::factory()->rol(Rol::Vendedor)->create();

    $this->actingAs($admin);

    Livewire::test('pages::admin.asignar-roles')
        ->call('abrirAsignar', $usuario->id)
        ->set('rolesSeleccionados', [Rol::Vendedor->value, Rol::Produccion->value])
        ->call('guardar')
        ->assertHasNoErrors();

    $usuario->refresh();

    expect($usuario->rolesAsignados()->map(fn (Rol $r) => $r->value)->sort()->values()->all())
        ->toBe([Rol::Produccion->value, Rol::Vendedor->value]);

    // El usuario ahora tiene acceso a rutas de ambos roles.
    $this->actingAs($usuario);
    $this->get(route('vendedor.panel'))->assertOk();
    $this->get(route('produccion.panel'))->assertOk();
    $this->get(route('planta.panel'))->assertForbidden();
});

test('asignar el rol de proveedor exige la ubicación de la finca y el acopiador, y crea el registro enlazado a su acopiador', function () {
    $admin = User::factory()->rol(Rol::Administrador)->create();
    $usuario = User::factory()->rol(Rol::Vendedor)->create();
    $acopiador = Acopiador::factory()->create();

    $this->actingAs($admin);

    Livewire::test('pages::admin.asignar-roles')
        ->call('abrirAsignar', $usuario->id)
        ->set('rolesSeleccionados', [Rol::Vendedor->value, Rol::Proveedor->value])
        ->call('guardar')
        ->assertHasErrors(['finca_ubicacion', 'proveedorAcopiadorId']);

    Livewire::test('pages::admin.asignar-roles')
        ->call('abrirAsignar', $usuario->id)
        ->set('rolesSeleccionados', [Rol::Vendedor->value, Rol::Proveedor->value])
        ->set('finca_ubicacion', 'Chillora, comunidad de Huata')
        ->set('proveedorAcopiadorId', $acopiador->id)
        ->call('guardar')
        ->assertHasNoErrors();

    $proveedor = Proveedor::where('user_id', $usuario->id)->first();

    expect($proveedor)->not->toBeNull()
        ->and($proveedor->acopiador_id)->toBe($acopiador->id)
        ->and($acopiador->proveedores()->pluck('id'))->toContain($proveedor->id);
});

test('editar la ubicación de un proveedor desde Asignar roles lo quita de sus rutas y notifica a planta', function () {
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

    Livewire::test('pages::admin.asignar-roles')
        ->call('abrirAsignar', $proveedorUser->id)
        ->set('finca_ubicacion', 'Ubicación nueva')
        ->call('guardar')
        ->assertHasNoErrors();

    expect($proveedor->rutas()->count())->toBe(0);
    Notification::assertSentTo($planta, ProveedorUbicacionActualizada::class);
});

test('asignar el rol de acopiador a un usuario nuevo crea su registro de acopiador', function () {
    $admin = User::factory()->rol(Rol::Administrador)->create();
    $usuario = User::factory()->rol(Rol::Vendedor)->create();

    $this->actingAs($admin);

    Livewire::test('pages::admin.asignar-roles')
        ->call('abrirAsignar', $usuario->id)
        ->set('rolesSeleccionados', [Rol::Acopiador->value])
        ->call('guardar')
        ->assertHasNoErrors();

    // Los turnos de ruta ya no se crean por acopiador: son una lista compartida que se
    // configura una sola vez desde Admin > Turnos y usan todos los acopiadores por igual.
    expect(Acopiador::where('user_id', $usuario->id)->exists())->toBeTrue();
});

test('quitar el rol principal de un usuario promueve otro rol seleccionado como principal', function () {
    $admin = User::factory()->rol(Rol::Administrador)->create();
    $usuario = User::factory()->rol(Rol::Acopiador)->create();
    Acopiador::factory()->create(['user_id' => $usuario->id]);

    $this->actingAs($admin);

    Livewire::test('pages::admin.asignar-roles')
        ->call('abrirAsignar', $usuario->id)
        ->set('rolesSeleccionados', [Rol::Vendedor->value])
        ->call('guardar')
        ->assertHasNoErrors();

    expect($usuario->fresh()->rol)->toBe(Rol::Vendedor);
});

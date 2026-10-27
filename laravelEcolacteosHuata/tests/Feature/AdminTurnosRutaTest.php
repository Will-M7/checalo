<?php

use App\Enums\Rol;
use App\Models\Turno;
use App\Models\User;
use Livewire\Livewire;

test('el administrador puede crear un turno de ruta sin elegir ningún acopiador', function () {
    $admin = User::factory()->rol(Rol::Administrador)->create();
    $this->actingAs($admin);

    Livewire::test('pages::admin.turnos')
        ->call('nuevoTurnoRuta')
        ->set('turnoRutaNombre', 'Mañana')
        ->set('turnoRutaInicio', '06:00')
        ->set('turnoRutaFin', '09:00')
        ->call('guardarTurnoRuta')
        ->assertHasNoErrors();

    $turno = Turno::where('nombre', 'Mañana')->firstOrFail();

    expect($turno->es_produccion)->toBeFalse();
});

test('la tabla de turnos de ruta muestra todos los turnos de ruta juntos, en una sola lista compartida', function () {
    $admin = User::factory()->rol(Rol::Administrador)->create();
    $this->actingAs($admin);

    Turno::factory()->create(['nombre' => 'Turno A']);
    Turno::factory()->create(['nombre' => 'Turno B']);

    Livewire::test('pages::admin.turnos')
        ->assertSee('Turno A')
        ->assertSee('Turno B');
});

test('editar un turno de ruta actualiza su nombre y horario', function () {
    $admin = User::factory()->rol(Rol::Administrador)->create();
    $this->actingAs($admin);

    $turno = Turno::factory()->create(['nombre' => 'Mañana', 'hora_inicio' => '06:00', 'hora_fin' => '09:00']);

    Livewire::test('pages::admin.turnos')
        ->call('editarTurnoRuta', $turno->id)
        ->assertSet('turnoRutaNombre', 'Mañana')
        ->set('turnoRutaNombre', 'Mañana temprano')
        ->set('turnoRutaInicio', '05:00')
        ->call('guardarTurnoRuta')
        ->assertHasNoErrors();

    expect($turno->fresh()->nombre)->toBe('Mañana temprano')
        ->and($turno->fresh()->hora_inicio)->toBe('05:00');
});

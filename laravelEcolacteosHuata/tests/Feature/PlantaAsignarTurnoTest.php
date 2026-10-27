<?php

use App\Enums\Rol;
use App\Models\Acopiador;
use App\Models\Ruta;
use App\Models\Turno;
use App\Models\User;
use Livewire\Livewire;

test('el personal de planta puede asignar y quitar el turno de una ruta', function () {
    $planta = User::factory()->rol(Rol::PersonalPlanta)->create();
    $this->actingAs($planta);

    $acopiador = Acopiador::factory()->create();
    $ruta = Ruta::factory()->create(['acopiador_id' => $acopiador->id]);
    $turno = Turno::factory()->create(['nombre' => 'Mañana']);

    $component = Livewire::test('pages::planta.rutas')
        ->call('abrirAsignarTurno', $ruta->id)
        ->assertSee('Mañana');

    $component->call('asignarTurno', $turno->id);

    expect($ruta->fresh()->turnos->pluck('id'))->toContain($turno->id);

    $component->call('quitarTurno', $turno->id);

    expect($ruta->fresh()->turnos->pluck('id'))->not->toContain($turno->id);
});

test('una ruta puede tener asignados dos turnos al mismo tiempo', function () {
    $planta = User::factory()->rol(Rol::PersonalPlanta)->create();
    $this->actingAs($planta);

    $acopiador = Acopiador::factory()->create();
    $ruta = Ruta::factory()->create(['acopiador_id' => $acopiador->id]);
    $manana = Turno::factory()->create(['nombre' => 'Mañana']);
    $tarde = Turno::factory()->create(['nombre' => 'Tarde']);

    Livewire::test('pages::planta.rutas')
        ->call('abrirAsignarTurno', $ruta->id)
        ->call('asignarTurno', $manana->id)
        ->call('asignarTurno', $tarde->id);

    expect($ruta->fresh()->turnos->pluck('id')->sort()->values()->all())
        ->toBe([$manana->id, $tarde->id]);
});

test('los turnos asignados a una ruta se muestran en la cabecera de la ruta', function () {
    $planta = User::factory()->rol(Rol::PersonalPlanta)->create();
    $this->actingAs($planta);

    $acopiador = Acopiador::factory()->create();
    $turno = Turno::factory()->create(['nombre' => 'Tarde', 'hora_inicio' => '14:00', 'hora_fin' => '18:00']);
    $ruta = Ruta::factory()->create(['acopiador_id' => $acopiador->id]);
    $ruta->turnos()->attach($turno->id);

    Livewire::test('pages::planta.rutas')
        ->assertSee('Tarde: 14:00 - 18:00');
});

test('el botón asignar turno de un acopiador sin rutas crea una ruta principal automáticamente', function () {
    $planta = User::factory()->rol(Rol::PersonalPlanta)->create();
    $this->actingAs($planta);

    $acopiador = Acopiador::factory()->create();
    $turno = Turno::factory()->create(['nombre' => 'Mañana']);

    expect($acopiador->rutas()->count())->toBe(0);

    Livewire::test('pages::planta.rutas')
        ->call('abrirAsignarTurnoDeAcopiador', $acopiador->id)
        ->assertSee('Mañana');

    expect($acopiador->rutas()->count())->toBe(1);
});

test('el botón asignar turno de un acopiador sin rutas permite asignar y luego quitar igual que uno con ruta', function () {
    $planta = User::factory()->rol(Rol::PersonalPlanta)->create();
    $this->actingAs($planta);

    $acopiador = Acopiador::factory()->create();
    $turno = Turno::factory()->create(['nombre' => 'Mañana']);

    expect($acopiador->rutas()->count())->toBe(0);

    $component = Livewire::test('pages::planta.rutas')
        ->call('abrirAsignarTurnoDeAcopiador', $acopiador->id)
        ->call('asignarTurno', $turno->id);

    $ruta = $acopiador->rutas()->first();

    expect($ruta->fresh()->turnos->pluck('id'))->toContain($turno->id);

    $component->call('quitarTurno', $turno->id);

    expect($ruta->fresh()->turnos->pluck('id'))->not->toContain($turno->id)
        ->and($acopiador->rutas()->count())->toBe(1);
});

test('el modal de asignar turno muestra la lista completa de turnos de ruta, compartida entre todos los acopiadores', function () {
    $planta = User::factory()->rol(Rol::PersonalPlanta)->create();
    $this->actingAs($planta);

    $acopiadorA = Acopiador::factory()->create();
    $ruta = Ruta::factory()->create(['acopiador_id' => $acopiadorA->id]);
    $turno = Turno::factory()->create(['nombre' => 'Turno compartido']);

    $component = Livewire::test('pages::planta.rutas')
        ->call('abrirAsignarTurno', $ruta->id)
        ->assertSee('Turno compartido');

    $component->call('asignarTurno', $turno->id);

    expect($ruta->fresh()->turnos->pluck('id'))->toContain($turno->id);
});

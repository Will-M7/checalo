<?php

use App\Enums\Rol;
use App\Models\Acopiador;
use App\Models\EntregaPlanta;
use App\Models\Proveedor;
use App\Models\Recojo;
use App\Models\Turno;
use App\Models\User;
use Livewire\Livewire;

test('corregir los litros de un recojo actualiza su total y recalcula la entrega del acopiador', function () {
    $planta = User::factory()->rol(Rol::PersonalPlanta)->create();
    $this->actingAs($planta);

    $acopiador = Acopiador::factory()->create();
    $turno = Turno::factory()->create();
    $proveedor = Proveedor::factory()->create(['acopiador_id' => $acopiador->id]);

    $recojo = Recojo::factory()->create([
        'proveedor_id' => $proveedor->id,
        'acopiador_id' => $acopiador->id,
        'turno_id' => $turno->id,
        'fecha' => now()->toDateString(),
        'litros' => 20,
        'precio_litro' => 1.8,
        'total' => 36,
    ]);

    $entrega = EntregaPlanta::factory()->create([
        'acopiador_id' => $acopiador->id,
        'turno_id' => $turno->id,
        'fecha' => now()->toDateString(),
        'litros_total' => 20,
    ]);

    Livewire::test('pages::planta.entregas')
        ->call('editarRecojo', $recojo->id)
        ->set('recojoEditLitros', '25')
        ->call('guardarRecojoLitros')
        ->assertHasNoErrors();

    expect($recojo->fresh()->litros)->toEqual(25.0)
        ->and((float) $recojo->fresh()->total)->toEqual(45.0)
        ->and((float) $entrega->fresh()->litros_total)->toEqual(25.0);
});

test('corregir los litros totales de una entrega del acopiador la actualiza directamente', function () {
    $planta = User::factory()->rol(Rol::PersonalPlanta)->create();
    $this->actingAs($planta);

    $entrega = EntregaPlanta::factory()->create([
        'fecha' => now()->toDateString(),
        'litros_total' => 50,
    ]);

    Livewire::test('pages::planta.entregas')
        ->call('editarEntrega', $entrega->id)
        ->set('entregaEditLitros', '99.5')
        ->call('guardarEntregaLitros')
        ->assertHasNoErrors();

    expect((float) $entrega->fresh()->litros_total)->toEqual(99.5);
});

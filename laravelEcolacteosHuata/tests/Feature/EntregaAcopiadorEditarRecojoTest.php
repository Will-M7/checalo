<?php

use App\Enums\Rol;
use App\Models\Acopiador;
use App\Models\Proveedor;
use App\Models\Recojo;
use App\Models\Turno;
use App\Models\User;
use Livewire\Livewire;

test('el acopiador puede editar un recojo desde la tabla de Entrega a planta', function () {
    $acopiadorUser = User::factory()->rol(Rol::Acopiador)->create();
    $acopiador = Acopiador::factory()->create(['user_id' => $acopiadorUser->id]);
    $turno = Turno::factory()->create();
    $proveedor = Proveedor::factory()->create(['acopiador_id' => $acopiador->id]);

    $recojo = Recojo::factory()->create([
        'proveedor_id' => $proveedor->id,
        'acopiador_id' => $acopiador->id,
        'turno_id' => $turno->id,
        'fecha' => now()->toDateString(),
        'litros' => 10,
        'precio_litro' => 1.8,
        'total' => 18,
        'estado_leche' => 'normal',
    ]);

    $this->actingAs($acopiadorUser);

    Livewire::test('pages::acopiador.entrega')
        ->call('editar', $recojo->id)
        ->set('editLitros', '12.5')
        ->set('editEstadoLeche', 'soleado')
        ->call('guardarEdicion')
        ->assertHasNoErrors();

    expect(Recojo::count())->toBe(1)
        ->and($recojo->fresh()->litros)->toEqual(12.5)
        ->and($recojo->fresh()->estado_leche)->toBe('soleado')
        ->and((float) $recojo->fresh()->total)->toEqual(22.5);
});

test('al entregar a planta, el proveedor deja de contar como recogido en la tabla de entrega', function () {
    $acopiadorUser = User::factory()->rol(Rol::Acopiador)->create();
    $acopiador = Acopiador::factory()->create(['user_id' => $acopiadorUser->id]);
    $turno = Turno::factory()->create();
    $proveedor = Proveedor::factory()->create(['acopiador_id' => $acopiador->id]);

    Recojo::factory()->create([
        'proveedor_id' => $proveedor->id,
        'acopiador_id' => $acopiador->id,
        'turno_id' => $turno->id,
        'fecha' => now()->toDateString(),
        'litros' => 15,
        'entregado' => false,
    ]);

    $this->actingAs($acopiadorUser);

    Livewire::test('pages::acopiador.entrega')
        ->assertSee('15.00 L');

    Recojo::where('proveedor_id', $proveedor->id)->update(['entregado' => true]);

    Livewire::test('pages::acopiador.entrega')
        ->assertSee('0.00 L');
});

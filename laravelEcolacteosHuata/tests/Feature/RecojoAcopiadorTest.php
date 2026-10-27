<?php

use App\Enums\Rol;
use App\Models\Acopiador;
use App\Models\Proveedor;
use App\Models\Recojo;
use App\Models\Turno;
use App\Models\User;
use Livewire\Livewire;

test('el acopiador puede editar un recojo ya registrado sin duplicarlo', function () {
    $acopiadorUser = User::factory()->rol(Rol::Acopiador)->create();
    $acopiador = Acopiador::factory()->create(['user_id' => $acopiadorUser->id]);
    $turno = Turno::factory()->create();
    $proveedor = Proveedor::factory()->create(['acopiador_id' => $acopiador->id]);

    $recojo = Recojo::factory()->create([
        'proveedor_id' => $proveedor->id,
        'acopiador_id' => $acopiador->id,
        'turno_id' => $turno->id,
        'fecha' => now()->toDateString(),
        'litros' => 15,
        'precio_litro' => 1.8,
        'total' => 27,
        'estado_leche' => 'normal',
    ]);

    $this->actingAs($acopiadorUser);

    Livewire::test('pages::acopiador.recojo')
        ->call('editar', $recojo->id)
        ->set('litros', '18')
        ->set('estadoLeche', 'aguado')
        ->call('guardar')
        ->assertHasNoErrors();

    expect(Recojo::count())->toBe(1)
        ->and($recojo->fresh()->litros)->toEqual(18.0)
        ->and($recojo->fresh()->estado_leche)->toBe('aguado')
        ->and((float) $recojo->fresh()->total)->toEqual(32.4);
});

test('un proveedor sin recojo hoy muestra la insignia falta recoger', function () {
    $acopiadorUser = User::factory()->rol(Rol::Acopiador)->create();
    $acopiador = Acopiador::factory()->create(['user_id' => $acopiadorUser->id]);
    Proveedor::factory()->create(['acopiador_id' => $acopiador->id]);

    $this->actingAs($acopiadorUser);

    Livewire::test('pages::acopiador.recojo')
        ->assertSee('Falta recoger');
});

test('al entregar a planta, el estado y los litros del proveedor se restablecen en recojo de ruta', function () {
    $acopiadorUser = User::factory()->rol(Rol::Acopiador)->create();
    $acopiador = Acopiador::factory()->create(['user_id' => $acopiadorUser->id]);
    $turno = Turno::factory()->create();
    $proveedor = Proveedor::factory()->create(['acopiador_id' => $acopiador->id]);

    Recojo::factory()->create([
        'proveedor_id' => $proveedor->id,
        'acopiador_id' => $acopiador->id,
        'turno_id' => $turno->id,
        'fecha' => now()->toDateString(),
        'litros' => 20,
        'entregado' => false,
    ]);

    $this->actingAs($acopiadorUser);

    Livewire::test('pages::acopiador.recojo')
        ->assertSee('Ya recogido hoy')
        ->assertSee('20.00 L');

    // Se marca como entregado a planta (lo hace acopiador/⚡entrega.blade.php al confirmar).
    Recojo::where('proveedor_id', $proveedor->id)->update(['entregado' => true]);

    Livewire::test('pages::acopiador.recojo')
        ->assertSee('Falta recoger')
        ->assertSee('0.00 L');
});

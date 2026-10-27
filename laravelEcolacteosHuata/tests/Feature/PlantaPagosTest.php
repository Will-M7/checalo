<?php

use App\Enums\Rol;
use App\Models\Acopiador;
use App\Models\PagoProveedor;
use App\Models\Proveedor;
use App\Models\Recojo;
use App\Models\Turno;
use App\Models\User;
use Livewire\Livewire;

test('la tabla de pagos muestra el litraje y el pendiente que confirma el laboratorio, no el que se recogió', function () {
    $planta = User::factory()->rol(Rol::PersonalPlanta)->create();
    $this->actingAs($planta);

    $acopiador = Acopiador::factory()->create();
    $turno = Turno::factory()->create();
    $proveedor = Proveedor::factory()->create(['acopiador_id' => $acopiador->id]);
    Recojo::factory()->create([
        'proveedor_id' => $proveedor->id,
        'acopiador_id' => $acopiador->id,
        'turno_id' => $turno->id,
        'entregado' => true,
        'pagado' => false,
        'litros' => 10,
        'litros_entregados' => 10,
        'precio_litro' => 1.8,
        'total' => 18,
    ]);

    // El laboratorio confirma que solo llegaron 9 L de los 10 que entregó el proveedor.
    Livewire::test('pages::planta.laboratorio')
        ->call('nuevo')
        ->set('proveedorId', $proveedor->id)
        ->set('litros', '9')
        ->call('guardar')
        ->assertHasNoErrors();

    Livewire::test('pages::planta.pagos')
        ->assertSee('9.00 L') // litraje que colocó el laboratorio, no los 10 L entregados
        ->assertSee('S/ 16.20'); // 9 L x S/ 1.80, no los S/ 18.00 originales
});

test('el personal de planta puede registrar el pago pendiente de un proveedor', function () {
    $planta = User::factory()->rol(Rol::PersonalPlanta)->create();
    $this->actingAs($planta);

    $proveedor = Proveedor::factory()->create();
    Recojo::factory()->create([
        'proveedor_id' => $proveedor->id,
        'fecha' => now()->toDateString(),
        'total' => 45,
    ]);

    Livewire::test('pages::planta.pagos')
        ->call('abrirPago', $proveedor->id, 45.0)
        ->assertSet('montoPagar', 45.0)
        ->call('pagar');

    $pago = PagoProveedor::where('proveedor_id', $proveedor->id)->firstOrFail();

    expect((float) $pago->monto)->toEqual(45.0)
        ->and(Recojo::where('proveedor_id', $proveedor->id)->first()->pagado)->toBeTrue();
});

test('ver historial de pagos muestra los pagos anteriores de ese proveedor', function () {
    $planta = User::factory()->rol(Rol::PersonalPlanta)->create();
    $this->actingAs($planta);

    $proveedor = Proveedor::factory()->create();
    $otro = Proveedor::factory()->create();

    PagoProveedor::factory()->create([
        'proveedor_id' => $proveedor->id,
        'fecha' => now()->subWeek()->toDateString(),
        'monto' => 120,
        'metodo_pago' => 'yape',
        'periodo' => 'semana',
    ]);
    PagoProveedor::factory()->create([
        'proveedor_id' => $otro->id,
        'fecha' => now()->subMonths(2)->toDateString(),
        'monto' => 999,
    ]);

    Livewire::test('pages::planta.pagos')
        ->call('verHistorialPago', $proveedor->id)
        ->assertSee('120.00')
        ->assertSee('Yape')
        ->assertDontSee('999.00');
});

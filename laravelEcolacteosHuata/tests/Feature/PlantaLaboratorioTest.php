<?php

use App\Enums\Rol;
use App\Models\Acopiador;
use App\Models\Proveedor;
use App\Models\Recojo;
use App\Models\ResultadoLaboratorio;
use App\Models\Turno;
use App\Models\User;
use Livewire\Livewire;

test('el formulario de nuevo resultado solo lista proveedores a los que ya se les presionó entregar a planta', function () {
    $planta = User::factory()->rol(Rol::PersonalPlanta)->create();
    $this->actingAs($planta);

    $acopiador = Acopiador::factory()->create();
    $turno = Turno::factory()->create();

    $entregado = Proveedor::factory()->create(['acopiador_id' => $acopiador->id]);
    Recojo::factory()->create([
        'proveedor_id' => $entregado->id,
        'acopiador_id' => $acopiador->id,
        'turno_id' => $turno->id,
        'entregado' => true,
    ]);

    $pendiente = Proveedor::factory()->create(['acopiador_id' => $acopiador->id]);
    Recojo::factory()->create([
        'proveedor_id' => $pendiente->id,
        'acopiador_id' => $acopiador->id,
        'turno_id' => $turno->id,
        'entregado' => false,
    ]);

    $sinRecojo = Proveedor::factory()->create(['acopiador_id' => $acopiador->id]);

    Livewire::test('pages::planta.laboratorio')
        ->call('nuevo')
        ->assertSee($entregado->user->nombres)
        ->assertDontSee($pendiente->user->nombres)
        ->assertDontSee($sinRecojo->user->nombres);
});

test('el personal de planta puede registrar un nuevo resultado de laboratorio, y el resultado se deriva del switch conforme', function () {
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
    ]);

    Livewire::test('pages::planta.laboratorio')
        ->call('nuevo')
        ->set('proveedorId', $proveedor->id)
        ->set('litros', '20')
        ->set('conforme', false)
        ->call('guardar')
        ->assertHasNoErrors();

    $resultado = ResultadoLaboratorio::where('proveedor_id', $proveedor->id)->first();

    expect($resultado)->not->toBeNull()
        ->and($resultado->resultado)->toBe('no conforme')
        ->and($resultado->conforme)->toBeFalse();
});

test('guarda los datos del ticket del analizador: SN, Mode, hora y fecha del análisis', function () {
    $planta = User::factory()->rol(Rol::PersonalPlanta)->create();
    $this->actingAs($planta);

    $acopiador = Acopiador::factory()->create();
    $proveedor = Proveedor::factory()->create(['acopiador_id' => $acopiador->id]);
    Recojo::factory()->create([
        'proveedor_id' => $proveedor->id,
        'acopiador_id' => $acopiador->id,
        'turno_id' => Turno::factory()->create()->id,
        'entregado' => true,
    ]);

    Livewire::test('pages::planta.laboratorio')
        ->call('nuevo')
        ->assertSee('SN')
        ->assertSee('Mode')
        ->assertDontSee('Acidez')
        ->set('proveedorId', $proveedor->id)
        ->set('litros', '20')
        ->set('sn', '48359')
        ->set('modo', '1')
        ->set('ph', '11.0')
        ->set('horaAnalisis', '09:42')
        ->set('fechaAnalisis', '2026-06-06')
        ->call('guardar')
        ->assertHasNoErrors();

    $resultado = ResultadoLaboratorio::where('proveedor_id', $proveedor->id)->first();

    expect($resultado->sn)->toBe('48359')
        ->and($resultado->modo)->toBe('1')
        ->and($resultado->horaFechaAnalisis())->toBe('09:42 · 06/06/26');
});

test('la hora del análisis debe tener formato HH:MM', function () {
    $planta = User::factory()->rol(Rol::PersonalPlanta)->create();
    $this->actingAs($planta);

    Livewire::test('pages::planta.laboratorio')
        ->call('nuevo')
        ->set('horaAnalisis', '9 y 42')
        ->call('guardar')
        ->assertHasErrors(['horaAnalisis']);
});

test('registrar un nuevo resultado sin elegir proveedor muestra un error de validación', function () {
    $planta = User::factory()->rol(Rol::PersonalPlanta)->create();
    $this->actingAs($planta);

    Livewire::test('pages::planta.laboratorio')
        ->call('nuevo')
        ->set('litros', '20')
        ->call('guardar')
        ->assertHasErrors(['proveedorId']);
});

test('el campo de texto libre "Resultado" ya no aparece en el formulario', function () {
    $planta = User::factory()->rol(Rol::PersonalPlanta)->create();
    $this->actingAs($planta);

    Livewire::test('pages::planta.laboratorio')
        ->call('nuevo')
        ->assertDontSee('conforme, aguado, acidificado...');
});

test('el estado de laboratorio muestra Sí solo para acopiadores con un resultado registrado ese día', function () {
    $planta = User::factory()->rol(Rol::PersonalPlanta)->create();
    $this->actingAs($planta);

    $acopiadorConLab = Acopiador::factory()->create();
    $proveedorConLab = Proveedor::factory()->create(['acopiador_id' => $acopiadorConLab->id]);
    ResultadoLaboratorio::factory()->create([
        'proveedor_id' => $proveedorConLab->id,
        'fecha' => now()->toDateString(),
    ]);

    Acopiador::factory()->create();

    Livewire::test('pages::planta.entregas')
        ->assertSee('Laboratorio: Sí')
        ->assertSee('Laboratorio: Pendiente');
});

test('el botón enviar laboratorio ya no aparece en el dashboard de entregas, pero el estado de laboratorio sigue visible', function () {
    $planta = User::factory()->rol(Rol::PersonalPlanta)->create();
    $this->actingAs($planta);

    Acopiador::factory()->create();

    Livewire::test('pages::planta.entregas')
        ->assertDontSee('Enviar laboratorio')
        ->assertSee('Laboratorio: Pendiente');
});

test('guardar un resultado ajusta el litros y el total del recojo del día cuando aún no está pagado', function () {
    $planta = User::factory()->rol(Rol::PersonalPlanta)->create();
    $this->actingAs($planta);

    $acopiador = Acopiador::factory()->create();
    $turno = Turno::factory()->create();
    $proveedor = Proveedor::factory()->create(['acopiador_id' => $acopiador->id]);
    $recojo = Recojo::factory()->create([
        'proveedor_id' => $proveedor->id,
        'acopiador_id' => $acopiador->id,
        'turno_id' => $turno->id,
        'entregado' => true,
        'pagado' => false,
        'litros' => 20,
        'precio_litro' => 1.8,
        'total' => 36,
    ]);

    Livewire::test('pages::planta.laboratorio')
        ->call('nuevo')
        ->set('proveedorId', $proveedor->id)
        ->set('litros', '18')
        ->call('guardar')
        ->assertHasNoErrors();

    expect((float) $recojo->fresh()->litros)->toBe(18.0)
        ->and((float) $recojo->fresh()->total)->toBe(32.4);

    $resultado = ResultadoLaboratorio::where('proveedor_id', $proveedor->id)->first();
    expect($resultado->recojo_id)->toBe($recojo->id);
});

test('guardar un resultado no modifica el litros del recojo si ya fue pagado', function () {
    $planta = User::factory()->rol(Rol::PersonalPlanta)->create();
    $this->actingAs($planta);

    $acopiador = Acopiador::factory()->create();
    $turno = Turno::factory()->create();
    $proveedor = Proveedor::factory()->create(['acopiador_id' => $acopiador->id]);
    $recojo = Recojo::factory()->create([
        'proveedor_id' => $proveedor->id,
        'acopiador_id' => $acopiador->id,
        'turno_id' => $turno->id,
        'entregado' => true,
        'pagado' => true,
        'litros' => 20,
        'precio_litro' => 1.8,
        'total' => 36,
    ]);

    Livewire::test('pages::planta.laboratorio')
        ->call('nuevo')
        ->set('proveedorId', $proveedor->id)
        ->set('litros', '18')
        ->call('guardar')
        ->assertHasNoErrors();

    expect((float) $recojo->fresh()->litros)->toBe(20.0)
        ->and((float) $recojo->fresh()->total)->toBe(36.0);
});

test('guardar un resultado sin un recojo entregado de ese proveedor en esa fecha muestra un error de validación', function () {
    $planta = User::factory()->rol(Rol::PersonalPlanta)->create();
    $this->actingAs($planta);

    $acopiador = Acopiador::factory()->create();
    $turno = Turno::factory()->create();
    $proveedor = Proveedor::factory()->create(['acopiador_id' => $acopiador->id]);
    Recojo::factory()->create([
        'proveedor_id' => $proveedor->id,
        'acopiador_id' => $acopiador->id,
        'turno_id' => $turno->id,
        'fecha' => now()->subDay()->toDateString(),
        'entregado' => true,
    ]);

    Livewire::test('pages::planta.laboratorio')
        ->call('nuevo')
        ->set('proveedorId', $proveedor->id)
        ->set('litros', '18')
        ->call('guardar')
        ->assertHasErrors(['proveedorId']);

    expect(ResultadoLaboratorio::where('proveedor_id', $proveedor->id)->count())->toBe(0);
});

test('guardar un resultado reparte el litraje proporcionalmente si el proveedor tuvo más de un recojo ese día', function () {
    $planta = User::factory()->rol(Rol::PersonalPlanta)->create();
    $this->actingAs($planta);

    $acopiador = Acopiador::factory()->create();
    $turnoManana = Turno::factory()->create(['nombre' => 'Mañana']);
    $turnoTarde = Turno::factory()->create(['nombre' => 'Tarde']);
    $proveedor = Proveedor::factory()->create(['acopiador_id' => $acopiador->id]);

    $recojoManana = Recojo::factory()->create([
        'proveedor_id' => $proveedor->id,
        'acopiador_id' => $acopiador->id,
        'turno_id' => $turnoManana->id,
        'entregado' => true,
        'pagado' => false,
        'litros' => 15,
        'precio_litro' => 2,
        'total' => 30,
    ]);
    $recojoTarde = Recojo::factory()->create([
        'proveedor_id' => $proveedor->id,
        'acopiador_id' => $acopiador->id,
        'turno_id' => $turnoTarde->id,
        'entregado' => true,
        'pagado' => false,
        'litros' => 5,
        'precio_litro' => 2,
        'total' => 10,
    ]);

    // El laboratorio confirma 16 L en total para ese proveedor ese día (20 L originales entre
    // los dos recojos): se reparte 75%/25% según el peso original de cada uno (15 L y 5 L).
    Livewire::test('pages::planta.laboratorio')
        ->call('nuevo')
        ->set('proveedorId', $proveedor->id)
        ->set('litros', '16')
        ->call('guardar')
        ->assertHasNoErrors();

    expect((float) $recojoManana->fresh()->litros)->toBe(12.0)
        ->and((float) $recojoManana->fresh()->total)->toBe(24.0)
        ->and((float) $recojoTarde->fresh()->litros)->toBe(4.0)
        ->and((float) $recojoTarde->fresh()->total)->toBe(8.0);
});

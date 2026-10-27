<?php

use App\Enums\Rol;
use App\Models\Insumo;
use App\Models\PrecioLeche;
use App\Models\ResultadoLaboratorio;
use App\Models\User;
use Livewire\Livewire;

test('enviarAProduccion suma el stock de leche y marca el resultado como enviado', function () {
    PrecioLeche::factory()->create(['precio_litro' => 1.8, 'vigente_desde' => now()->subDay()->toDateString()]);
    $insumoLeche = Insumo::factory()->create(['es_leche' => true, 'stock_actual' => 100, 'costo_unitario' => 1.5]);

    $resultado = ResultadoLaboratorio::factory()->create(['litros' => 50, 'conforme' => true]);

    $resultado->enviarAProduccion();

    // 100 a 1.50 (150) + 50 a 1.80 (90) = 150 unidades, valor 240 -> costo promedio 1.60
    expect((float) $insumoLeche->fresh()->stock_actual)->toBe(150.0)
        ->and($resultado->fresh()->enviado_produccion)->toBeTrue();
});

test('enviarAProduccion no hace nada si el resultado no es conforme', function () {
    $insumoLeche = Insumo::factory()->create(['es_leche' => true, 'stock_actual' => 100]);

    $resultado = ResultadoLaboratorio::factory()->noConforme()->create(['litros' => 50]);

    $resultado->enviarAProduccion();

    expect((float) $insumoLeche->fresh()->stock_actual)->toBe(100.0)
        ->and($resultado->fresh()->enviado_produccion)->toBeFalse();
});

test('enviarAProduccion no vuelve a sumar el stock si ya fue enviado', function () {
    $insumoLeche = Insumo::factory()->create(['es_leche' => true, 'stock_actual' => 100, 'costo_unitario' => 1.5]);

    $resultado = ResultadoLaboratorio::factory()->create(['litros' => 50, 'conforme' => true]);

    $resultado->enviarAProduccion();
    $resultado->fresh()->enviarAProduccion();

    expect((float) $insumoLeche->fresh()->stock_actual)->toBe(150.0);
});

test('el botón enviar a producción solo aparece en resultados conformes y todavía no enviados', function () {
    $planta = User::factory()->rol(Rol::PersonalPlanta)->create();
    $this->actingAs($planta);

    Insumo::factory()->create(['es_leche' => true, 'stock_actual' => 0]);

    $conforme = ResultadoLaboratorio::factory()->create(['litros' => 20, 'conforme' => true]);
    $noConforme = ResultadoLaboratorio::factory()->noConforme()->create(['litros' => 15]);

    Livewire::test('pages::planta.laboratorio')
        ->assertSeeHtml('pedirEnviarProduccion('.$conforme->id.')')
        ->assertDontSeeHtml('pedirEnviarProduccion('.$noConforme->id.')');
});

test('enviarAProduccion crea automáticamente el insumo de leche si no existe todavía', function () {
    PrecioLeche::factory()->create(['precio_litro' => 1.8, 'vigente_desde' => now()->subDay()->toDateString()]);

    expect(Insumo::where('es_leche', true)->exists())->toBeFalse();

    $resultado = ResultadoLaboratorio::factory()->create(['litros' => 20, 'conforme' => true]);
    $resultado->enviarAProduccion();

    $insumoLeche = Insumo::where('es_leche', true)->first();

    expect($insumoLeche)->not->toBeNull()
        ->and((float) $insumoLeche->stock_actual)->toBe(20.0)
        ->and($insumoLeche->unidadMedida->simbolo)->toBe('L')
        ->and($resultado->fresh()->enviado_produccion)->toBeTrue();
});

test('el personal de planta puede enviar un resultado conforme a producción desde el panel', function () {
    $planta = User::factory()->rol(Rol::PersonalPlanta)->create();
    $this->actingAs($planta);

    $insumoLeche = Insumo::factory()->create(['es_leche' => true, 'stock_actual' => 0, 'costo_unitario' => 1.5]);
    $resultado = ResultadoLaboratorio::factory()->create(['litros' => 30, 'conforme' => true]);

    Livewire::test('pages::planta.laboratorio')
        ->call('pedirEnviarProduccion', $resultado->id)
        ->call('enviarProduccion')
        ->assertSee('Enviado a producción');

    expect((float) $insumoLeche->fresh()->stock_actual)->toBe(30.0)
        ->and($resultado->fresh()->enviado_produccion)->toBeTrue();
});

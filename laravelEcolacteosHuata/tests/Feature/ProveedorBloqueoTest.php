<?php

use App\Models\Proveedor;

test('un proveedor no se bloquea con menos de tres alteraciones', function () {
    $proveedor = Proveedor::factory()->create();

    $proveedor->registrarAlteracion();
    $proveedor->registrarAlteracion();
    $proveedor->refresh();

    expect($proveedor->alteraciones)->toBe(2)
        ->and($proveedor->bloqueado)->toBeFalse();
});

test('el proveedor se bloquea automáticamente al llegar a tres alteraciones', function () {
    $proveedor = Proveedor::factory()->create();

    $proveedor->registrarAlteracion();
    $proveedor->registrarAlteracion();
    $proveedor->registrarAlteracion();
    $proveedor->refresh();

    expect($proveedor->alteraciones)->toBe(3)
        ->and($proveedor->bloqueado)->toBeTrue();
});

test('desbloquear reinicia el contador de alteraciones', function () {
    $proveedor = Proveedor::factory()->bloqueado()->create();

    $proveedor->desbloquear();
    $proveedor->refresh();

    expect($proveedor->bloqueado)->toBeFalse()
        ->and($proveedor->alteraciones)->toBe(0);
});

<?php

use App\Enums\Rol;
use App\Models\Comunicado;

test('un comunicado sin roles es visible para cualquier rol', function () {
    $comunicado = Comunicado::factory()->create(['roles' => null]);

    expect($comunicado->visibleParaRol(Rol::Proveedor))->toBeTrue()
        ->and($comunicado->visibleParaRol(Rol::Vendedor))->toBeTrue()
        ->and($comunicado->etiquetasRoles())->toBe(['Todos los roles']);
});

test('un comunicado con roles específicos solo es visible para esos roles', function () {
    $comunicado = Comunicado::factory()->create([
        'roles' => [Rol::Proveedor->value, Rol::Acopiador->value],
    ]);

    expect($comunicado->visibleParaRol(Rol::Proveedor))->toBeTrue()
        ->and($comunicado->visibleParaRol(Rol::Acopiador))->toBeTrue()
        ->and($comunicado->visibleParaRol(Rol::Vendedor))->toBeFalse();
});

test('el scope paraRol filtra los comunicados dirigidos a todos y a un rol específico', function () {
    $paraTodos = Comunicado::factory()->create(['roles' => null]);
    $paraProveedor = Comunicado::factory()->create(['roles' => [Rol::Proveedor->value]]);
    $paraVendedor = Comunicado::factory()->create(['roles' => [Rol::Vendedor->value]]);

    $visiblesParaProveedor = Comunicado::query()->paraRol(Rol::Proveedor)->pluck('id');

    expect($visiblesParaProveedor)->toContain($paraTodos->id)
        ->and($visiblesParaProveedor)->toContain($paraProveedor->id)
        ->and($visiblesParaProveedor)->not->toContain($paraVendedor->id);
});

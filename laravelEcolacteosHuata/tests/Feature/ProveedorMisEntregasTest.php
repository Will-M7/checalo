<?php

use App\Enums\Rol;
use App\Models\Proveedor;
use App\Models\Recojo;
use App\Models\User;
use Livewire\Livewire;

test('mis entregas muestra por separado el litraje entregado y el litraje llegado que se le va a pagar', function () {
    $user = User::factory()->rol(Rol::Proveedor)->create();
    $proveedor = Proveedor::factory()->create(['user_id' => $user->id]);

    Recojo::factory()->create([
        'proveedor_id' => $proveedor->id,
        'fecha' => now()->toDateString(),
        'litros' => 9,
        'litros_entregados' => 10,
        'precio_litro' => 1.8,
        'total' => 16.2,
    ]);

    $this->actingAs($user);

    Livewire::test('pages::proveedor.entregas')
        ->assertSee('10.00 L')
        ->assertSee('9.00 L')
        ->assertSee('S/ 16.20');
});

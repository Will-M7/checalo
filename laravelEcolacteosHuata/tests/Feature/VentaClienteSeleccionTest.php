<?php

use App\Enums\Rol;
use App\Models\Cliente;
use App\Models\User;
use Livewire\Livewire;

test('el punto de venta lista todos los clientes guardados en un selector', function () {
    $vendedor = User::factory()->rol(Rol::Vendedor)->create();
    $this->actingAs($vendedor);

    Cliente::create(['nombres' => 'Rosa', 'apellidos' => 'Mamani Quispe', 'documento' => '70300099']);
    Cliente::create(['nombres' => 'Juan', 'apellidos' => 'Condori Flores', 'documento' => '70300100']);

    Livewire::test('pages::vendedor.ventas')
        ->assertSee('Rosa Mamani Quispe')
        ->assertSee('Juan Condori Flores');
});

test('elegir un cliente del selector lo deja asignado y oculta los campos manuales', function () {
    $vendedor = User::factory()->rol(Rol::Vendedor)->create();
    $this->actingAs($vendedor);

    $cliente = Cliente::create(['nombres' => 'Rosa', 'apellidos' => 'Mamani Quispe', 'documento' => '70300099', 'compras_count' => 3]);

    Livewire::test('pages::vendedor.ventas')
        ->set('clienteId', $cliente->id)
        ->assertSet('clienteId', $cliente->id)
        ->assertSee('3 compra(s) anteriores')
        ->assertDontSee('Nombre en boleta');
});

test('sin ningún cliente elegido, se pueden ingresar los datos manualmente', function () {
    $vendedor = User::factory()->rol(Rol::Vendedor)->create();
    $this->actingAs($vendedor);

    Livewire::test('pages::vendedor.ventas')
        ->assertSee('Nombre en boleta')
        ->set('clienteNombreLibre', 'Cliente anónimo')
        ->assertSet('clienteNombreLibre', 'Cliente anónimo');
});

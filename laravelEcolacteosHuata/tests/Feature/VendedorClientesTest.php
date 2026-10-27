<?php

use App\Enums\Rol;
use App\Models\Cliente;
use App\Models\User;
use App\Models\Venta;
use Livewire\Livewire;

test('el rol de vendedor puede crear un cliente', function () {
    $vendedor = User::factory()->rol(Rol::Vendedor)->create();

    $this->actingAs($vendedor);

    Livewire::test('pages::vendedor.clientes')
        ->call('nuevo')
        ->set('nombres', 'Rosa')
        ->set('apellidos', 'Mamani Quispe')
        ->set('documento', '70399999')
        ->call('guardar')
        ->assertHasNoErrors();

    $cliente = Cliente::where('documento', '70399999')->firstOrFail();

    expect($cliente->nombres)->toBe('Rosa')
        ->and($cliente->apellidos)->toBe('Mamani Quispe');
});

test('el rol de vendedor puede editar y eliminar un cliente', function () {
    $vendedor = User::factory()->rol(Rol::Vendedor)->create();
    $cliente = Cliente::factory()->create(['nombres' => 'Juan', 'documento' => '70399998']);

    $this->actingAs($vendedor);

    Livewire::test('pages::vendedor.clientes')
        ->call('editar', $cliente->id)
        ->set('nombres', 'Juan Carlos')
        ->call('guardar')
        ->assertHasNoErrors();

    expect($cliente->fresh()->nombres)->toBe('Juan Carlos');

    Livewire::test('pages::vendedor.clientes')
        ->call('pedirEliminar', $cliente->id)
        ->call('eliminar');

    expect(Cliente::find($cliente->id))->toBeNull();
});

test('la tabla de clientes muestra el porcentaje de descuento recibido', function () {
    $vendedor = User::factory()->rol(Rol::Vendedor)->create();

    $cliente = Cliente::factory()->create(['nombres' => 'Rosa']);
    Venta::factory()->create([
        'cliente_id' => $cliente->id,
        'subtotal' => 100,
        'descuento' => 20,
        'descuento_tipo' => 'Descuento por volumen',
    ]);

    $this->actingAs($vendedor);

    Livewire::test('pages::vendedor.clientes')
        ->assertSee('20.00%');
});

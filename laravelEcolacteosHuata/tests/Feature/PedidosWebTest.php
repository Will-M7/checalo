<?php

use App\Enums\EstadoPedidoWeb;
use App\Enums\EstadoProduccion;
use App\Enums\Rol;
use App\Enums\TeamRole;
use App\Models\ClienteWeb;
use App\Models\Insumo;
use App\Models\PedidoWeb;
use App\Models\Produccion;
use App\Models\Receta;
use App\Models\Team;
use App\Models\UnidadMedida;
use App\Models\User;
use App\Models\Venta;
use Illuminate\Support\Str;
use Livewire\Livewire;

function produccionPublicada(string $nombre = 'Queso fresco', float $precio = 7.5, float $producida = 5): Produccion
{
    $unidad = UnidadMedida::factory()->create();
    $insumo = Insumo::factory()->create(['unidad_medida_id' => $unidad->id, 'costo_unitario' => 2]);
    $receta = Receta::factory()->create(['nombre' => $nombre, 'precio_venta' => $precio]);
    $receta->detalles()->create(['insumo_id' => $insumo->id, 'cantidad' => 10, 'unidad_medida_id' => $unidad->id]);

    return Produccion::factory()->create([
        'nombre' => $nombre,
        'receta_id' => $receta->id,
        'cantidad_producida' => $producida,
        'estado' => EstadoProduccion::Completada,
    ]);
}

function vendedorConEquipo(): User
{
    $vendedor = User::factory()->rol(Rol::Vendedor)->create();
    $team = Team::principal();
    $team->members()->attach($vendedor, ['role' => TeamRole::Owner->value]);
    $vendedor->switchTeam($team);

    return $vendedor;
}

function clientePedidoWeb(): ClienteWeb
{
    return ClienteWeb::create(['nombres' => 'María', 'apellidos' => 'Quispe', 'celular' => '51951234567', 'email' => 'pedido@example.test', 'password' => 'Password-123456']);
}

test('un comprador autenticado guarda su carrito con precios del servidor y contacto de su perfil', function () {
    vendedorConEquipo();
    $client = clientePedidoWeb();
    $queso = produccionPublicada('Queso fresco', precio: 7.5);
    $yogurt = produccionPublicada('Yogurt natural', precio: 4);
    $this->actingAs($client, 'cliente')->postJson(route('pedidos-web.store'), [
        'items' => [['produccion_id' => $queso->id, 'cantidad' => 2, 'precio_unitario' => 0.01], ['produccion_id' => $yogurt->id, 'cantidad' => 3]],
        'nombre' => 'Nombre manipulado', 'celular' => '999999999', 'metodo_pago' => 'efectivo', 'total' => 1, 'idempotency_key' => (string) Str::uuid(),
    ])->assertCreated()->assertJsonStructure(['id', 'total', 'url_pedido']);
    $pedido = PedidoWeb::with('items')->firstOrFail();
    expect($pedido->total)->toBe('27.00')->and($pedido->nombre)->toBe('María Quispe')->and($pedido->celular)->toBe($client->celular)
        ->and($pedido->estado)->toBe(EstadoPedidoWeb::Pendiente)->and($pedido->items)->toHaveCount(2)
        ->and((float) $pedido->items->first()->precio_unitario)->toBe(7.5);
    expect($client->fresh()->celular)->toBe('51999999999')->and($pedido->cliente_web_id)->toBe($client->id);
    $this->assertAuthenticatedAs($client, 'cliente');
    $this->assertGuest('web');
});

test('checkout normaliza y conserva el celular editable para futuras compras', function (string $phone) {
    vendedorConEquipo();
    $client = clientePedidoWeb();
    $product = produccionPublicada();
    $this->actingAs($client, 'cliente')->postJson(route('pedidos-web.store'), [
        'items' => [['produccion_id' => $product->id, 'cantidad' => 1]], 'metodo_pago' => 'efectivo',
        'celular' => $phone, 'idempotency_key' => (string) Str::uuid(),
    ])->assertCreated();
    expect($client->fresh()->celular)->toBe('51987654321');
    $this->get(route('home'))->assertOk()->assertSee('value="51987654321" id="pedido-celular"', false);
    $this->get(route('cliente.cuenta'))->assertOk()->assertSee('51987654321');
    expect(PedidoWeb::first()->celular)->toBe('51987654321')->and(PedidoWeb::first()->nombre)->toBe('María Quispe');
})->with(['987654321', '+51 987 654 321', '51987654321']);

test('checkout rechaza celular inválido sin cambiar perfil ni crear un pedido', function ($phone) {
    $client = clientePedidoWeb();
    $product = produccionPublicada();
    $this->actingAs($client, 'cliente')->postJson(route('pedidos-web.store'), [
        'items' => [['produccion_id' => $product->id, 'cantidad' => 1]], 'metodo_pago' => 'efectivo',
        'celular' => $phone, 'idempotency_key' => (string) Str::uuid(),
    ])->assertUnprocessable()->assertJsonValidationErrors('celular');
    expect($client->fresh()->celular)->toBe('51951234567')->and(PedidoWeb::count())->toBe(0);
})->with(['', '123', '+52 987654321', '519876543210', 'tableau' => [['987654321']]]);

test('checkout no permite apropiarse del celular de otra cuenta', function () {
    $client = clientePedidoWeb();
    $other = ClienteWeb::create(['nombres' => 'Otra', 'apellidos' => 'Cuenta', 'celular' => '51987654321', 'email' => 'otra@example.test', 'password' => 'Password-123456']);
    $product = produccionPublicada();
    $this->actingAs($client, 'cliente')->postJson(route('pedidos-web.store'), [
        'items' => [['produccion_id' => $product->id, 'cantidad' => 1]], 'metodo_pago' => 'efectivo',
        'celular' => '+51 987 654 321', 'idempotency_key' => (string) Str::uuid(),
    ])->assertUnprocessable()->assertJsonValidationErrors('celular');
    expect($client->fresh()->celular)->toBe('51951234567')->and($other->fresh()->celular)->toBe('51987654321')->and(PedidoWeb::count())->toBe(0);
});

test('se rechazan carritos vacíos cantidades excesivas repetidas y productos no publicados', function () {
    $this->actingAs(clientePedidoWeb(), 'cliente');
    vendedorConEquipo();
    $p = produccionPublicada(producida: 3);
    $hidden = produccionPublicada('Mantequilla');
    $hidden->update(['estado' => EstadoProduccion::Planificada]);
    foreach ([[], [['produccion_id' => $p->id, 'cantidad' => 4]], [['produccion_id' => $p->id, 'cantidad' => 1], ['produccion_id' => $p->id, 'cantidad' => 1]], [['produccion_id' => $hidden->id, 'cantidad' => 1]]] as $items) {
        $this->postJson(route('pedidos-web.store'), ['items' => $items, 'metodo_pago' => 'efectivo', 'idempotency_key' => (string) Str::uuid()])->assertUnprocessable();
    }
    expect(PedidoWeb::count())->toBe(0);
});

test('el vendedor ve los pedidos pendientes y los cancela', function () {
    $vendedor = vendedorConEquipo();

    $uno = PedidoWeb::factory()->conItem(['producto' => 'Yogurt natural'])->create(['nombre' => 'Rosa Mamani']);
    $dos = PedidoWeb::factory()->conItem()->create(['nombre' => 'Pedro Flores']);

    $this->actingAs($vendedor)
        ->get(route('vendedor.pedidos-web'))
        ->assertOk()
        ->assertSee('Rosa Mamani')
        ->assertSee('Yogurt natural');

    Livewire::test('pages::vendedor.pedidos-web')
        ->call('cancelar', $dos->id)
        ->call('pedirConfirmar', $dos->id)
        ->call('confirmar');

    expect($dos->fresh()->estado)->toBe(EstadoPedidoWeb::Cancelado)
        ->and($uno->fresh()->estado)->toBe(EstadoPedidoWeb::Pendiente)
        ->and(Venta::count())->toBe(0);
});

test('confirmar un pedido registra una venta con boleta, todas sus líneas, y descuenta el stock', function () {
    $vendedor = vendedorConEquipo();

    $queso = produccionPublicada('Queso fresco', precio: 7.5, producida: 5);
    $yogurt = produccionPublicada('Yogurt natural', precio: 4, producida: 10);

    $pedido = PedidoWeb::factory()->create(['nombre' => 'Rosa Mamani', 'total' => 27]);
    $pedido->items()->createMany([
        ['produccion_id' => $queso->id, 'producto' => 'Queso fresco', 'cantidad' => 2, 'precio_unitario' => 7.5, 'subtotal' => 15],
        ['produccion_id' => $yogurt->id, 'producto' => 'Yogurt natural', 'cantidad' => 3, 'precio_unitario' => 4, 'subtotal' => 12],
    ]);

    $this->actingAs($vendedor);

    Livewire::test('pages::vendedor.pedidos-web')
        ->call('pedirConfirmar', $pedido->id)
        ->set('metodoPago', 'efectivo')
        ->call('confirmar');

    $pedido->refresh();
    $venta = Venta::with('items')->firstOrFail();

    expect($pedido->estado)->toBe(EstadoPedidoWeb::Confirmado)
        ->and($pedido->venta_id)->toBe($venta->id)
        ->and($venta->numero_boleta)->toStartWith('B001-')
        ->and($venta->cliente_nombre_libre)->toBe('Rosa Mamani')
        ->and($venta->metodo_pago)->toBe('efectivo')
        ->and((float) $venta->total)->toBe(27.0)
        ->and($venta->vendedor_id)->toBe($vendedor->id)
        ->and($venta->items)->toHaveCount(2)
        ->and($queso->fresh()->cantidadDisponible())->toBe(3.0)
        ->and($yogurt->fresh()->cantidadDisponible())->toBe(7.0);

    $this->get(route('vendedor.ventas.boleta', $venta->id))->assertOk();

    // Confirmar de nuevo no duplica la venta ni vuelve a descontar.
    Livewire::test('pages::vendedor.pedidos-web')->call('pedirConfirmar', $pedido->id)->call('confirmar');

    expect(Venta::count())->toBe(1)
        ->and($queso->fresh()->cantidadDisponible())->toBe(3.0);
});

test('no se confirma un pedido si una de sus líneas ya no tiene stock, y no se registra nada', function () {
    $vendedor = vendedorConEquipo();

    $queso = produccionPublicada('Queso fresco', producida: 5);
    $yogurt = produccionPublicada('Yogurt natural', producida: 1);

    $pedido = PedidoWeb::factory()->create(['total' => 20]);
    $pedido->items()->createMany([
        ['produccion_id' => $queso->id, 'producto' => 'Queso fresco', 'cantidad' => 2, 'precio_unitario' => 7.5, 'subtotal' => 15],
        ['produccion_id' => $yogurt->id, 'producto' => 'Yogurt natural', 'cantidad' => 3, 'precio_unitario' => 4, 'subtotal' => 12],
    ]);

    $this->actingAs($vendedor);

    Livewire::test('pages::vendedor.pedidos-web')->call('pedirConfirmar', $pedido->id)->call('confirmar');

    expect($pedido->fresh()->estado)->toBe(EstadoPedidoWeb::Pendiente)
        ->and(Venta::count())->toBe(0)
        ->and($queso->fresh()->cantidadDisponible())->toBe(5.0)
        ->and($yogurt->fresh()->cantidadDisponible())->toBe(1.0);
});

test('quienes no son vendedor ni administrador no ven los pedidos de la web', function () {
    $planta = User::factory()->rol(Rol::PersonalPlanta)->create();
    $team = Team::principal();
    $team->members()->attach($planta, ['role' => TeamRole::Owner->value]);
    $planta->switchTeam($team);

    $this->actingAs($planta)->get(route('vendedor.pedidos-web'))->assertForbidden();
});

test('la portada ofrece el carrito cuando hay productos', function () {
    produccionPublicada('Queso fresco', precio: 7.5);

    $this->get(route('home'))
        ->assertOk()
        ->assertSee('id="carrito"', escape: false)
        ->assertSee('Agregar')
        ->assertSee('Tu carrito');
});

test('el checkout admite métodos comerciales definidos y rechaza métodos ajenos', function () {
    $this->actingAs(clientePedidoWeb(), 'cliente');
    vendedorConEquipo();
    $p = produccionPublicada();
    $this->postJson(route('pedidos-web.store'), ['items' => [['produccion_id' => $p->id, 'cantidad' => 1]], 'metodo_pago' => 'tarjeta', 'idempotency_key' => (string) Str::uuid()])->assertUnprocessable()->assertJsonValidationErrors('metodo_pago');
    $this->postJson(route('pedidos-web.store'), ['items' => [['produccion_id' => $p->id, 'cantidad' => 1]], 'metodo_pago' => 'efectivo', 'codigo_operacion' => 'ignorado', 'idempotency_key' => (string) Str::uuid()])->assertCreated();
    expect(PedidoWeb::firstOrFail()->codigo_operacion)->toBeNull();
});

test('el cliente abre su comprobante sin iniciar sesión y luego la boleta oficial', function () {
    $queso = produccionPublicada('Queso fresco', precio: 7.5, producida: 5);

    // Preserve old guest orders already stored before mandatory buyer accounts.
    $legacy = PedidoWeb::factory()->create(['nombre' => 'Rosa Mamani', 'metodo_pago' => 'plin', 'total' => 15]);
    $legacy->items()->create(['produccion_id' => $queso->id, 'producto' => 'Queso fresco', 'cantidad' => 2, 'precio_unitario' => 7.5, 'subtotal' => 15]);
    $url = $legacy->urlBoleta();

    // Visitante sin sesión: comprobante del pedido.
    $this->get($url)->assertOk()->assertHeader('content-type', 'application/pdf');
    expect(PedidoWeb::firstOrFail()->venta_id)->toBeNull();

    // El vendedor confirma el pago (con el método elegido por el cliente) y la misma dirección da la boleta.
    $vendedor = vendedorConEquipo();
    $this->actingAs($vendedor);
    $pedido = PedidoWeb::firstOrFail();

    Livewire::test('pages::vendedor.pedidos-web')->call('pedirConfirmar', $pedido->id)->call('confirmar');

    $venta = Venta::firstOrFail();

    expect($venta->metodo_pago)->toBe('plin')
        ->and($pedido->fresh()->venta_id)->toBe($venta->id);

    auth()->logout();
    $this->get($url)->assertOk()->assertHeader('content-type', 'application/pdf');
    $this->get(route('pedidos-web.boleta', 'token-que-no-existe'))->assertNotFound();
});

test('la portada ofrece pagar con Yape, Plin o efectivo en el carrito', function () {
    produccionPublicada();

    $this->get(route('home'))
        ->assertOk()
        ->assertSee('¿Cómo vas a pagar?')
        ->assertSee('Yape')
        ->assertSee('Plin')
        ->assertSee('Confirmar pedido');
});

test('Yape ya no muestra QR fijo ni solicita número de operación y un visitante no compra', function () {
    produccionPublicada();
    $this->get(route('home'))->assertOk()->assertDontSee('images/yape-qr.jpg', false)->assertDontSee('id="pedido-operacion"', false);
    $this->postJson(route('pedidos-web.store'), ['metodo_pago' => 'yape'])->assertUnauthorized();
});

test('el vendedor no convierte Yape no verificado en venta cambiando el método', function () {
    $vendedor = vendedorConEquipo();
    $pedido = PedidoWeb::factory()->conItem()->create(['metodo_pago' => 'yape']);
    $this->actingAs($vendedor, 'web');
    Livewire::test('pages::vendedor.pedidos-web')->call('pedirConfirmar', $pedido->id)->set('metodoPago', 'efectivo')->call('confirmar');
    expect(Venta::count())->toBe(0)->and($pedido->fresh()->estado)->toBe(EstadoPedidoWeb::Pendiente);
});

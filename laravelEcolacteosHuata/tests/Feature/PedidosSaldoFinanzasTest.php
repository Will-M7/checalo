<?php

use App\Enums\{EstadoProduccion, Rol};
use App\Models\{ClienteWeb, IntentoPago, MovimientoFinanciero, MovimientoSaldo, PagoProveedor, PedidoWeb, Produccion, ReceptorPago, Receta, User, Venta};
use App\Services\Finanzas\FinanzasService;
use App\Services\Payments\{CobrosService, SaldoService, StockInsuficiente};
use Illuminate\Support\Facades\{Http, Storage};
use Illuminate\Support\Str;
use Illuminate\Validation\ValidationException;
use Livewire\Livewire;

function finanzasFixture(): array
{
    Storage::fake('public');
    $seller = User::factory()->rol(Rol::Vendedor)->create(['activo' => true]);
    Storage::disk('public')->put('pagos/prueba.png', 'fixture-qr');
    $receiver = ReceptorPago::create(['user_id' => $seller->id, 'nombre_receptor' => 'Receptor autorizado', 'celular' => '51999999999', 'whatsapp' => '51988888888', 'referencia_externa' => 'r1', 'qr_path' => 'pagos/prueba.png', 'qr_sha256' => hash('sha256', 'fixture-qr'), 'verified_at' => now(), 'enabled' => true]);
    $client = ClienteWeb::create(['nombres' => 'Comprador', 'apellidos' => 'Prueba', 'celular' => '51977777777', 'email' => 'saldo@example.test', 'password' => 'Test-1234']);
    $recipe = Receta::factory()->create(['precio_venta' => '10.00']);
    $product = Produccion::factory()->create(['receta_id' => $recipe->id, 'cantidad_producida' => 10, 'cantidad_vendida' => 0, 'estado' => EstadoProduccion::Completada]);
    config(['payments.enabled' => true, 'payments.url' => 'http://127.0.0.1:8087', 'payments.key' => str_repeat('x', 40), 'payments.allow_local_http' => true]);
    Http::preventStrayRequests();
    Http::fake(['*/api/receivers' => Http::response([['id' => 'r1', 'name' => $receiver->nombre_receptor, 'phone' => $receiver->celular, 'qrSha256' => $receiver->qr_sha256, 'enabled' => true, 'verified' => true, 'online' => true]])]);
    return [$client, $seller, $receiver, $product];
}

function finanzasPagoRespuesta(IntentoPago $i, string $state = 'PAID', ?string $result = null): array
{
    return ['id' => $i->external_id ?: 'PAY-'.$i->referencia, 'reference' => $i->referencia, 'amountMinor' => $i->amount_minor,
        'currency' => '604', 'receiverId' => $i->receptor_snapshot['external_reference'], 'qrSha256' => $i->receptor_snapshot['qr_sha256'],
        'status' => $state, 'expiresAt' => now()->addMinutes(5)->toIso8601String(), 'evidenceId' => $state === 'PAID' ? 'evidence_'.$i->referencia : null, 'result' => $result];
}

function finanzasConfirmar(IntentoPago $i): void
{
    Http::swap(new \Illuminate\Http\Client\Factory);
    Http::preventStrayRequests();
    Http::fake(fn () => Http::response(finanzasPagoRespuesta($i)));
    app(CobrosService::class)->consultar($i);
}

test('carrito guarda una sola vez sin intención ni stock ni contacto manipulable', function () {
    [$client, $seller, , $p] = finanzasFixture();
    $data = ['items' => [['produccion_id' => $p->id, 'cantidad' => 3, 'precio_unitario' => '0.01']], 'idempotency_key' => (string) Str::uuid(), 'metodo_pago' => 'saldo', 'nombre' => 'Suplantado'];
    $this->actingAs($client, 'cliente')->postJson(route('pedidos-web.store'), $data)->assertCreated()->assertJsonPath('metodo_pago', 'pendiente');
    $this->postJson(route('pedidos-web.store'), $data)->assertCreated();
    $order = PedidoWeb::firstOrFail();
    expect(PedidoWeb::count())->toBe(1)->and($order->total)->toBe('30.00')->and($order->nombre)->toBe($client->nombreCompleto())
        ->and($order->vendedor_asignado_id)->toBe($seller->id)->and(IntentoPago::count())->toBe(0)->and(Venta::count())->toBe(0)
        ->and($p->fresh()->cantidadDisponible())->toBe(10.0)->and(MovimientoFinanciero::count())->toBe(0);
    $this->get(route('cliente.cuenta'))->assertOk()->assertSee('PENDIENTE DE PAGO')->assertSee('RECARGAR SALDO');
    Http::assertNothingSent();
});

test('saldo paga una vez preservando venta y pendiente al faltar stock o fondos', function () {
    [$client, , , $p] = finanzasFixture();
    $service = app(CobrosService::class);
    $order = $service->pedido($client, [['produccion_id' => $p->id, 'cantidad' => 3]], 'pendiente', (string) Str::uuid());
    expect(fn () => $service->pagarConSaldo($client, $order))->toThrow(ValidationException::class);
    app(SaldoService::class)->movimiento($client, 10000, 'ajuste', 'saldo-prueba', 'Prueba aislada');
    $p->update(['cantidad_vendida' => 10]);
    expect(fn () => $service->pagarConSaldo($client, $order))->toThrow(StockInsuficiente::class);
    expect($client->saldoMinor())->toBe(10000)->and(Venta::count())->toBe(0)->and(MovimientoSaldo::where('tipo', 'compra')->count())->toBe(0);
    $p->update(['cantidad_vendida' => 0]);
    $this->actingAs($client, 'cliente')->post(route('cliente.pedido.saldo', $order->id))->assertRedirect();
    $this->post(route('cliente.pedido.saldo', $order->id))->assertRedirect();
    expect($client->saldoMinor())->toBe(7000)->and(Venta::count())->toBe(1)->and($p->fresh()->cantidadDisponible())->toBe(7.0)
        ->and(MovimientoSaldo::where('tipo', 'compra')->count())->toBe(1)->and($order->fresh()->pago_estado)->toBe('pagado');
    expect(app(FinanzasService::class)->resumen())->toMatchArray(['recaudado' => 0, 'ventas' => 3000, 'saldos_clientes' => 7000]);
});

test('recarga cien compra treinta y rendición no duplican fondos ni pasivos', function () {
    [$client, , , $p] = finanzasFixture();
    $service = app(CobrosService::class);
    $order = $service->pedido($client, [['produccion_id' => $p->id, 'cantidad' => 3]], 'pendiente', (string) Str::uuid());
    $key = (string) Str::uuid();
    $topup = $service->recarga($client, 10000, $key);
    expect($service->recarga($client, 10000, $key)->id)->toBe($topup->id);
    finanzasConfirmar($topup);
    $service->consultar($topup->fresh());
    $service->pagarConSaldo($client, $order);
    $service->pagarConSaldo($client, $order);
    $fin = app(FinanzasService::class);
    expect($fin->resumen())->toMatchArray(['recaudado' => 10000, 'recargas' => 10000, 'ventas' => 3000, 'saldos_clientes' => 7000, 'custodia' => 10000, 'empresa' => 0, 'disponible' => 0]);
    $admin = User::factory()->rol(Rol::Administrador)->create();
    $source = MovimientoFinanciero::where('tipo', 'ingreso_yape')->sole();
    $key = (string) Str::uuid();
    $first = $fin->rendir($admin, $source->id, 10000, $key, 'Transferencia prueba aislada');
    expect($fin->rendir($admin, $source->id, 10000, $key, 'Transferencia prueba aislada')->id)->toBe($first->id);
    expect($fin->resumen())->toMatchArray(['recaudado' => 10000, 'ventas' => 3000, 'recargas' => 10000, 'custodia' => 0, 'empresa' => 10000, 'disponible' => 3000]);
    expect(fn () => $fin->rendir($admin, $source->id, 1, (string) Str::uuid(), 'Otra entrega indebida'))->toThrow(ValidationException::class);
    expect(fn () => $source->update(['amount_minor' => 1]))->toThrow(LogicException::class);
    expect($client->saldoMinor())->toBe(7000)->and(MovimientoSaldo::count())->toBe(2);
});

test('no mezcla saldo con intento externo ni permite pagar pedidos ajenos', function () {
    [$client, , , $p] = finanzasFixture();
    $service = app(CobrosService::class);
    $order = $service->pedido($client, [['produccion_id' => $p->id, 'cantidad' => 1]], 'yape', (string) Str::uuid());
    app(SaldoService::class)->movimiento($client, 10000, 'ajuste', 'prueba-fondos', 'Prueba aislada');
    expect(fn () => $service->pagarConSaldo($client, $order))->toThrow(ValidationException::class);
    $other = ClienteWeb::create(['nombres' => 'Otro', 'apellidos' => 'Cliente', 'email' => 'otro@example.test', 'celular' => '51966666666', 'password' => '1234']);
    $this->actingAs($other, 'cliente')->postJson(route('cliente.pedido.saldo', $order->id))->assertNotFound();
    expect($client->saldoMinor())->toBe(10000)->and(Venta::count())->toBe(0);
});

test('receptor de recarga es estable y deshabilitado no crea intención ni publica QR', function () {
    [$client, $seller, $receiver, $p] = finanzasFixture();
    $service = app(CobrosService::class);
    $service->pedido($client, [['produccion_id' => $p->id, 'cantidad' => 1]], 'pendiente', (string) Str::uuid());
    $i = $service->recarga($client, 1000, (string) Str::uuid());
    expect($i->user_id)->toBe($seller->id)->and($i->receptor_pago_id)->toBe($receiver->id);
    $receiver->update(['enabled' => false]);
    $this->actingAs($client, 'cliente')->postJson(route('cliente.recarga'), ['importe' => '20.00', 'celular' => '+51 977 777 777', 'referencia' => (string) Str::uuid()])->assertUnprocessable()->assertDontSee('storage/pagos/prueba.png');
    expect(IntentoPago::count())->toBe(1)->and($client->fresh()->celular)->toBe('51977777777')->and($client->saldoMinor())->toBe(0);
});

test('notificación sin código o ambigua no acredita recarga', function (string $result) {
    [$client] = finanzasFixture();
    $service = app(CobrosService::class);
    $i = $service->recarga($client, 10000, (string) Str::uuid());
    Http::swap(new \Illuminate\Http\Client\Factory);
    Http::preventStrayRequests();
    Http::fake(fn () => Http::response(finanzasPagoRespuesta($i, 'PENDING', $result)));
    $this->actingAs($client, 'cliente')->getJson(route('cliente.pago.estado', $i->id))->assertOk()->assertJsonPath('aplicado', false);
    expect($client->saldoMinor())->toBe(0)->and(MovimientoFinanciero::count())->toBe(0);
})->with(['AMBIGUOUS', 'REVIEW_CODE_UNAVAILABLE']);

test('WhatsApp solo tras pago y pertenece al vendedor originario con receptor compartido', function () {
    [$client, , , $p] = finanzasFixture();
    $service = app(CobrosService::class);
    $order = $service->pedido($client, [['produccion_id' => $p->id, 'cantidad' => 1]], 'pendiente', (string) Str::uuid());
    expect($order->whatsappPagado())->toBeNull();
    $order->update(['receptor_snapshot' => [...$order->receptor_snapshot, 'whatsapp' => '51911111111']]);
    app(SaldoService::class)->movimiento($client, 1000, 'ajuste', 'whatsapp-fondos', 'Prueba aislada');
    $service->pagarConSaldo($client, $order);
    expect($order->fresh()->whatsappPagado())->toStartWith('https://wa.me/51988888888?')->not->toContain('51911111111');
});

test('Finanzas exige pantalla explícita incluso en acciones Livewire', function () {
    [, $seller] = finanzasFixture();
    $admin = User::factory()->rol(Rol::Administrador)->create();
    $this->actingAs($admin);
    expect($admin->puedePantalla('admin.finanzas'))->toBeTrue();
    Livewire::test('pages::admin.finanzas')->assertOk()->assertSee('Fondos bajo custodia');
    $this->actingAs($seller);
    Livewire::test('pages::admin.finanzas')->assertForbidden();
    expect(fn () => app(FinanzasService::class)->rendir($seller, 1, 100, (string) Str::uuid(), 'Solicitud manipulada'))->toThrow(\Symfony\Component\HttpKernel\Exception\HttpException::class);
    $role = \App\Authorization\Autorizacion::guardarRol($admin, null, 'Finanzas explícitas', 'finanzas_prueba', true, ['admin.finanzas']);
    \App\Authorization\Autorizacion::asignar($admin, $seller, [$role->codigo]);
    Livewire::test('pages::admin.finanzas')->assertOk();
});

test('egreso confirmado se enlaza una vez al pago existente sin inventar ingresos', function () {
    finanzasFixture();
    $actor = User::factory()->rol(Rol::PersonalPlanta)->create();
    $pago = PagoProveedor::factory()->create(['monto' => '25.00']);
    $service = app(FinanzasService::class);
    $service->egresoProveedor($pago, $actor);
    $service->egresoProveedor($pago, $actor);
    expect(MovimientoFinanciero::where('tipo', 'egreso_proveedor')->count())->toBe(1)
        ->and($service->resumen())->toMatchArray(['egresos' => 2500, 'empresa' => -2500, 'disponible' => 0, 'recaudado' => 0]);
});

test('modal prepara QR después del alta canónica y actualiza celular sin acreditación falsa', function () {
    [$client, , $receiver] = finanzasFixture();
    Http::swap(new \Illuminate\Http\Client\Factory);
    Http::preventStrayRequests();
    Http::fake(function ($request) use ($receiver) {
        if (str_ends_with($request->url(), '/api/receivers')) return Http::response([['id' => 'r1', 'name' => $receiver->nombre_receptor, 'phone' => $receiver->celular, 'qrSha256' => $receiver->qr_sha256, 'enabled' => true, 'verified' => true, 'online' => true]]);
        $i = IntentoPago::where('referencia', $request['reference'])->firstOrFail();
        return Http::response(finanzasPagoRespuesta($i, 'PENDING'));
    });
    $key = (string) Str::uuid();
    $data = ['importe' => '100.00', 'celular' => '+51 988 123 456', 'referencia' => $key];
    $this->actingAs($client, 'cliente')->postJson(route('cliente.recarga'), $data)->assertOk()->assertJsonPath('receptor.nombre', $receiver->nombre_receptor)->assertJsonPath('aplicado', false);
    $this->postJson(route('cliente.recarga'), $data)->assertOk();
    expect($client->fresh()->celular)->toBe('51988123456')->and(IntentoPago::count())->toBe(1)->and($client->saldoMinor())->toBe(0)->and(MovimientoFinanciero::count())->toBe(0);
});

test('reservas de proveedores reducen disponible sin inventar un egreso ni saldo bancario', function () {
    finanzasFixture();
    \App\Models\Recojo::factory()->create(['entregado' => true, 'pagado' => false, 'total' => '18.35']);
    $summary = app(FinanzasService::class)->resumen();
    expect($summary)->toMatchArray(['proveedores_pendientes' => 1835, 'disponible' => 0, 'egresos' => 0, 'recaudado' => 0]);
});

test('rendición requiere recepción explícita desde interfaz administrativa', function () {
    [$client] = finanzasFixture();
    $topup = app(CobrosService::class)->recarga($client, 10000, (string) Str::uuid());
    finanzasConfirmar($topup);
    $source = MovimientoFinanciero::where('tipo', 'ingreso_yape')->sole();
    $admin = User::factory()->rol(Rol::Administrador)->create();
    $this->actingAs($admin);
    Livewire::test('pages::admin.finanzas')->call('prepararRendicion', $source->id)->set('referencia', 'Constancia prueba aislada')->call('rendir')->assertHasErrors('recibido')
        ->set('recibido', true)->call('rendir')->assertHasNoErrors();
    expect(MovimientoFinanciero::where('tipo', 'rendicion')->count())->toBe(1);
});

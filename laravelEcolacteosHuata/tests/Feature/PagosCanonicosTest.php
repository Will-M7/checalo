<?php

use App\Authorization\Autorizacion;
use App\Enums\EstadoProduccion;
use App\Enums\Rol;
use App\Models\ClienteWeb;
use App\Models\IntentoPago;
use App\Models\MovimientoSaldo;
use App\Models\PedidoWeb;
use App\Models\Produccion;
use App\Models\ReceptorPago;
use App\Models\Receta;
use App\Models\Team;
use App\Models\User;
use App\Models\Venta;
use App\Services\Payments\CobrosService;
use App\Services\Payments\SaldoService;
use App\Services\Payments\VentasService;
use Illuminate\Http\Client\ConnectionException;
use Illuminate\Http\Client\Factory;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Storage;
use Illuminate\Support\Str;
use Illuminate\Validation\ValidationException;
use Symfony\Component\HttpKernel\Exception\HttpException;

function pagosFixture(): array
{
    Storage::fake('public');
    $seller = User::factory()->rol(Rol::Vendedor)->create(['activo' => true]);
    Storage::disk('public')->put('pagos/qr.png', 'fixture-image');
    $r = ReceptorPago::create(['user_id' => $seller->id, 'nombre_receptor' => 'Receptor autorizado', 'celular' => '51999999999', 'whatsapp' => '51988888888', 'referencia_externa' => 'r1', 'qr_path' => 'pagos/qr.png', 'qr_sha256' => hash('sha256', 'fixture-image'), 'verified_at' => now(), 'enabled' => true]);
    $client = ClienteWeb::create(['nombres' => 'Cliente', 'apellidos' => 'Prueba', 'celular' => '51977777777', 'email' => 'cliente@example.test', 'password' => 'TestPassword-123']);
    $recipe = Receta::factory()->create(['precio_venta' => '7.00']);
    $product = Produccion::factory()->create(['receta_id' => $recipe->id, 'cantidad_producida' => 10, 'cantidad_vendida' => 0, 'estado' => EstadoProduccion::Completada]);
    config(['payments.enabled' => true, 'payments.url' => 'http://127.0.0.1:8087', 'payments.key' => str_repeat('x', 40), 'payments.allow_local_http' => true]);
    Http::preventStrayRequests();
    Http::fake(function ($request) use ($r) {
        if (str_ends_with($request->url(), '/api/receivers')) {
            return Http::response([['id' => 'r1', 'name' => $r->nombre_receptor, 'enabled' => true, 'verified' => true, 'online' => true, 'phone' => $r->celular, 'qrSha256' => $r->qr_sha256]]);
        }
        $i = isset($request['reference']) ? IntentoPago::where('referencia', $request['reference'])->firstOrFail() : IntentoPago::where('external_id', basename(str_replace('/resolve-yape', '', $request->url())))->firstOrFail();

        return Http::response(pagoRespuesta($i, 'PENDING'));
    });

    return [$client, $seller, $r, $product];
}
function pagoRespuesta(IntentoPago $i, string $status): array
{
    return ['id' => $i->external_id ?: 'PAY-'.$i->referencia, 'reference' => $i->referencia, 'amountMinor' => $i->amount_minor, 'currency' => '604', 'receiverId' => $i->receptor_snapshot['external_reference'], 'qrSha256' => $i->receptor_snapshot['qr_sha256'], 'status' => $status, 'expiresAt' => now()->addMinutes(5)->toIso8601String(), 'evidenceId' => $status === 'PAID' ? 'evidence_'.$i->referencia : null];
}
function pagoConfirmado(IntentoPago $i): void
{
    Http::swap(new Factory);
    Http::preventStrayRequests();
    Http::fake(fn ($r) => Http::response(pagoRespuesta($i, 'PAID')));
}

test('un rol dinámico puede cobrar sin la categoría vendedor y conserva las verificaciones', function (string $canal) {
    [$client, $seller, $receiver, $product] = pagosFixture();
    User::withoutEvents(fn () => $seller->update(['rol' => Rol::PersonalPlanta, 'roles' => []]));
    $admin = User::factory()->rol(Rol::Administrador)->create();
    $pantalla = $canal === 'pos' ? 'vendedor.ventas' : 'vendedor.pedidos-web';
    $rol = Autorizacion::guardarRol($admin, null, 'Cobros dinámicos', 'cobros_dinamicos', true, [$pantalla, 'vendedor.configuracion-pagos']);
    Autorizacion::asignar($admin, $seller, [$rol->codigo]);
    expect($seller->fresh()->tieneRol(Rol::Vendedor))->toBeFalse();
    expect(User::conPantalla('vendedor.configuracion-pagos')->whereKey($seller->id)->exists())->toBeTrue();
    $service = app(CobrosService::class);
    $items = [['produccion_id' => $product->id, 'cantidad' => 1]];
    if ($canal === 'pos') {
        $intent = $service->presencial($seller, $items, [], [], (string) Str::uuid());
    } else {
        $order = $service->pedido($client, $items, 'yape', (string) Str::uuid());
        expect($order->vendedor_asignado_id)->toBe($seller->id);
        $intent = IntentoPago::where('pedido_web_id', $order->id)->firstOrFail();
    }
    expect($service->indisponibilidad($intent))->toBeNull();
    $receiver->update(['enabled' => false]);
    expect($service->indisponibilidad($intent))->not->toBeNull();
    $receiver->update(['enabled' => true]);
    Autorizacion::guardarRol($admin, $rol->id, $rol->nombre, $rol->codigo, true, ['vendedor.configuracion-pagos']);
    expect($service->indisponibilidad($intent))->not->toBeNull();
    expect($intent->fresh()->estado)->toBe('PENDING')->and(Venta::count())->toBe(0);
})->with(['pos', 'web']);

test('el importe se calcula en unidades menores y el pedido no reserva stock', function () {
    [$client, , , $p] = pagosFixture();
    $order = app(CobrosService::class)->pedido($client, [['produccion_id' => $p->id, 'cantidad' => 2, 'precio_unitario' => 0]], 'yape', (string) Str::uuid());
    expect($order->total)->toBe('14.00')->and($p->fresh()->cantidadDisponible())->toBe(10.0);
    expect(IntentoPago::first()->amount_minor)->toBe(1400);
});
test('reintentar un pedido mantiene vendedor receptor e intención', function () {
    [$client, , , $p] = pagosFixture();
    $key = (string) Str::uuid();
    $items = [['produccion_id' => $p->id, 'cantidad' => 1]];
    $a = app(CobrosService::class)->pedido($client, $items, 'yape', $key);
    $b = app(CobrosService::class)->pedido($client, $items, 'yape', $key);
    expect($a->id)->toBe($b->id)->and(IntentoPago::count())->toBe(1);
    app(CobrosService::class)->iniciar(IntentoPago::first());
    app(CobrosService::class)->iniciar(IntentoPago::first());
    Http::assertSentCount(2);
});
test('un código sin ingreso no crea venta y un ingreso confirmado se aplica una sola vez', function () {
    [$client, , , $p] = pagosFixture();
    $order = app(CobrosService::class)->pedido($client, [['produccion_id' => $p->id, 'cantidad' => 2]], 'yape', (string) Str::uuid());
    $i = app(CobrosService::class)->validar(IntentoPago::first(), '381', 'cliente:'.$client->id, '127.0.0.1');
    expect($i->estado)->toBe('PENDING')->and(Venta::count())->toBe(0);
    pagoConfirmado($i);
    app(CobrosService::class)->consultar($i);
    app(CobrosService::class)->consultar($i->fresh());
    expect(Venta::count())->toBe(1)->and($p->fresh()->cantidadDisponible())->toBe(8.0)->and($order->fresh()->venta_id)->not->toBeNull();
});
test('pago sin stock conserva el ingreso y acredita saldo exactamente una vez', function () {
    [$client, , , $p] = pagosFixture();
    app(CobrosService::class)->pedido($client, [['produccion_id' => $p->id, 'cantidad' => 2]], 'yape', (string) Str::uuid());
    $i = app(CobrosService::class)->iniciar(IntentoPago::first());
    $p->update(['cantidad_vendida' => 10]);
    pagoConfirmado($i);
    app(CobrosService::class)->consultar($i);
    app(CobrosService::class)->consultar($i->fresh());
    expect($client->saldoMinor())->toBe(1400)->and(MovimientoSaldo::count())->toBe(1)->and(Venta::count())->toBe(0)->and($i->fresh()->estado)->toBe('PAID');
});
test('recarga y compra con saldo sin doble débito ni doble boleta', function () {
    [$client, , , $p] = pagosFixture();
    $i = app(CobrosService::class)->recarga($client, 2100, (string) Str::uuid());
    $i = app(CobrosService::class)->iniciar($i);
    pagoConfirmado($i);
    app(CobrosService::class)->consultar($i);
    expect($client->saldoMinor())->toBe(2100);
    $key = (string) Str::uuid();
    $items = [['produccion_id' => $p->id, 'cantidad' => 2]];
    app(CobrosService::class)->pedido($client, $items, 'saldo', $key);
    app(CobrosService::class)->pedido($client, $items, 'saldo', $key);
    expect($client->saldoMinor())->toBe(700)->and(Venta::count())->toBe(1)->and(MovimientoSaldo::count())->toBe(2);
});
test('saldo insuficiente no crea venta ni consume inventario', function () {
    [$client, , , $p] = pagosFixture();
    try {
        app(CobrosService::class)->pedido($client, [['produccion_id' => $p->id, 'cantidad' => 1]], 'saldo', (string) Str::uuid());
    } catch (ValidationException) {
    }
    expect(Venta::count())->toBe(0)->and(MovimientoSaldo::count())->toBe(0)->and($p->fresh()->cantidadDisponible())->toBe(10.0);
});
test('datos canónicos incompatibles no acreditan ni generan boleta', function () {
    [$client, , , $p] = pagosFixture();
    app(CobrosService::class)->pedido($client, [['produccion_id' => $p->id, 'cantidad' => 1]], 'yape', (string) Str::uuid());
    $i = IntentoPago::first();
    $response = pagoRespuesta($i, 'PAID');
    $response['amountMinor'] = 1;
    Http::swap(new Factory);
    Http::fake(fn () => Http::response($response));
    expect(fn () => app(CobrosService::class)->iniciar($i))->toThrow(ValidationException::class);
    expect(Venta::count())->toBe(0)->and($client->saldoMinor())->toBe(0);
});
test('límite de cinco intentos pertenece a cuenta pago y receptor', function () {
    [$client, , , $p] = pagosFixture();
    app(CobrosService::class)->pedido($client, [['produccion_id' => $p->id, 'cantidad' => 1]], 'yape', (string) Str::uuid());
    $i = IntentoPago::first();
    for ($n = 0; $n < 5; $n++) {
        app(CobrosService::class)->validar($i->fresh(), '123', 'cliente:'.$client->id, '127.0.0.1');
    }
    expect(fn () => app(CobrosService::class)->validar($i->fresh(), '123', 'otra-sesion', '127.0.0.2'))->toThrow(ValidationException::class);
});

test('Round Robin alterna únicamente receptores habilitados y conserva snapshots', function () {
    [$client, , $r1, $p] = pagosFixture();
    $seller2 = User::factory()->rol(Rol::Vendedor)->create(['activo' => true]);
    $r2 = ReceptorPago::create(['user_id' => $seller2->id, 'nombre_receptor' => 'Receptor dos', 'celular' => '51966666666', 'whatsapp' => '51955555555', 'referencia_externa' => 'r2', 'qr_path' => $r1->qr_path, 'qr_sha256' => $r1->qr_sha256, 'verified_at' => now(), 'enabled' => true]);
    Http::swap(new Factory);
    Http::fake(fn () => Http::response(collect([$r1, $r2])->map(fn ($r) => ['id' => $r->referencia_externa, 'name' => $r->nombre_receptor, 'phone' => $r->celular, 'qrSha256' => $r->qr_sha256, 'verified' => true, 'enabled' => true, 'online' => true])->all()));
    $orders = [];
    for ($n = 0; $n < 3; $n++) {
        $orders[] = app(CobrosService::class)->pedido($client, [['produccion_id' => $p->id, 'cantidad' => 1]], 'yape', (string) Str::uuid());
    }
    expect(array_map(fn ($o) => $o->vendedor_asignado_id, $orders))->toBe([$r1->user_id, $r2->user_id, $r1->user_id]);
    $snapshot = $orders[0]->receptor_snapshot;
    $r1->update(['whatsapp' => '51944444444', 'qr_path' => 'otra.png', 'version' => 2]);
    expect($orders[0]->fresh()->receptor_snapshot)->toBe($snapshot);
});
test('no hay fallback QR si el motor no reconoce receptor disponible', function () {
    [$client, , , $p] = pagosFixture();
    Http::swap(new Factory);
    Http::fake(fn () => Http::response([]));
    $saved = app(CobrosService::class)->pedido($client, [['produccion_id' => $p->id, 'cantidad' => 1]], 'yape', (string) Str::uuid());
    expect($saved->venta_id)->toBeNull();
    expect(IntentoPago::count())->toBe(0);
});
test('timeout de creación conserva referencia y se recupera sin otro intento local', function () {
    [$client, , , $p] = pagosFixture();
    app(CobrosService::class)->pedido($client, [['produccion_id' => $p->id, 'cantidad' => 1]], 'yape', (string) Str::uuid());
    $i = IntentoPago::first();
    Http::swap(new Factory);
    Http::fake(fn () => throw new ConnectionException('simulated timeout'));
    expect(fn () => app(CobrosService::class)->iniciar($i))->toThrow(ValidationException::class);
    expect($i->fresh()->external_id)->toBeNull();
    pagoConfirmado($i);
    app(CobrosService::class)->consultar($i->fresh());
    expect(IntentoPago::count())->toBe(1)->and(Venta::count())->toBe(1);
});
test('el código tiene formato exacto y no se conserva en sesión', function () {
    [$client, , , $p] = pagosFixture();
    app(CobrosService::class)->pedido($client, [['produccion_id' => $p->id, 'cantidad' => 1]], 'yape', (string) Str::uuid());
    $this->actingAs($client, 'cliente')->postJson(route('cliente.pago.validar', IntentoPago::first()->id), ['codigo' => 'OP-381'])->assertUnprocessable()->assertJsonValidationErrors('codigo');
    expect(Venta::count())->toBe(0);
});
test('venta presencial Yape espera evidencia y el cambio de método no omite la comprobación', function () {
    [, $seller, , $p] = pagosFixture();
    $team = Team::principal();
    $team->members()->attach($seller, ['role' => 'owner']);
    $seller->switchTeam($team);
    $this->actingAs($seller, 'web');
    $component = Livewire\Livewire::test('pages::vendedor.ventas')->call('agregar', $p->id)->set('metodoPago', 'yape')->call('guardar');
    expect(Venta::count())->toBe(0)->and(IntentoPago::count())->toBe(1);
    $component->set('metodoPago', 'efectivo')->set('efectivoRecibido', 100)->call('guardar');
    expect(Venta::count())->toBe(0);
    $i = IntentoPago::first();
    pagoConfirmado($i);
    $component->set('codigoYape', '381')->call('guardar');
    expect(Venta::count())->toBe(1)->and($p->fresh()->cantidadDisponible())->toBe(9.0);
});
test('el POS reconstruye precios y subtotales manipulados', function () {
    [, $seller, , $p] = pagosFixture();
    $this->actingAs($seller, 'web');
    Livewire\Livewire::test('pages::vendedor.ventas')->call('agregar', $p->id)->set('carrito.0.precio_unitario', 0.01)->set('carrito.0.subtotal', 0.01)->set('efectivoRecibido', 7)->call('guardar');
    expect(Venta::first()->total)->toBe('7.00');
});

test('reintento del POS manual recupera la misma boleta aunque el stock ya se agotó', function () {
    [, $seller, , $p] = pagosFixture();
    $p->update(['cantidad_producida' => 1]);
    $service = app(VentasService::class);
    $reference = (string) Str::uuid();
    $a = $service->presencialManual($seller, [['produccion_id' => $p->id, 'cantidad' => 1]], [], 'efectivo', [], 700, $reference);
    $b = $service->presencialManual($seller, [['produccion_id' => $p->id, 'cantidad' => 1]], [], 'efectivo', [], 700, $reference);
    expect($a->id)->toBe($b->id)->and(Venta::count())->toBe(1)->and($p->fresh()->cantidadDisponible())->toBe(0.0);
    expect(fn () => $service->presencialManual($seller, [['produccion_id' => $p->id, 'cantidad' => 2]], [], 'efectivo', [], 1400, $reference))->toThrow(HttpException::class);
});
test('la página del comprador carga QR asignado y no incluye códigos ni tokens en WhatsApp', function () {
    [$client, , , $p] = pagosFixture();
    $order = app(CobrosService::class)->pedido($client, [['produccion_id' => $p->id, 'cantidad' => 1]], 'yape', (string) Str::uuid());
    $i = app(CobrosService::class)->iniciar(IntentoPago::first());
    pagoConfirmado($i);
    app(CobrosService::class)->consultar($i);
    $response = $this->actingAs($client, 'cliente')->get(route('cliente.pedido', $order->id))->assertOk()->assertSee('https://wa.me/51988888888', false);
    preg_match('~href="(https://wa\.me/[^"\s]+)"~', $response->getContent(), $matches);
    $whatsapp = rawurldecode(html_entity_decode($matches[1] ?? ''));
    expect($whatsapp)->toContain('51988888888', 'confirmado')->not->toContain($order->token, '381', $i->external_id);
});
test('el historial financiero no se borra y el libro no permite edición o saldo negativo', function () {
    [$client, $seller, , $p] = pagosFixture();
    $m = app(SaldoService::class)->movimiento($client, 700, 'ajuste', 'fixture:credit', 'fixture');
    app(CobrosService::class)->pedido($client, [['produccion_id' => $p->id, 'cantidad' => 1]], 'saldo', (string) Str::uuid());
    expect(Venta::first()->protegida())->toBeTrue();
    expect(fn () => Venta::first()->delete())->toThrow(RuntimeException::class);
    expect(fn () => $m->update(['amount_minor' => 999]))->toThrow(LogicException::class);
    expect(fn () => app(SaldoService::class)->ajuste($client, 100, 'ajuste no autorizado', $seller))->toThrow(HttpException::class);
});
test('cambiar QR deshabilita receptor y no concede verificación al vendedor', function () {
    [, $seller, $r] = pagosFixture();
    $this->actingAs($seller, 'web');
    Livewire\Livewire::test('pages::vendedor.configuracion-pagos')->set('whatsapp', '988777666')->call('guardar');
    expect($r->fresh()->whatsapp)->toBe('51988777666')->and($r->fresh()->enabled)->toBeTrue();
    Livewire\Livewire::test('pages::vendedor.configuracion-pagos')->set('declaracionTitularidad', true)->set('qr', UploadedFile::fake()->image('nuevo.png', 300, 300))->call('guardar');
    expect($r->fresh()->enabled)->toBeFalse()->and($r->fresh()->verified_at)->toBeNull();
    Livewire\Livewire::test('pages::vendedor.configuracion-pagos')->call('verificar')->assertForbidden();
});

test('solo el administrador verifica correspondencia exacta de cuenta QR y Bridge', function () {
    [, $seller, $r] = pagosFixture();
    $r->update(['enabled' => false, 'verified_at' => null]);
    $admin = User::factory()->rol(Rol::Administrador)->create(['activo' => true]);
    new \Tests\Support\ReceiverCoreFixture($r);
    $this->actingAs($admin, 'web');
    Livewire\Livewire::withQueryParams(['vendedor' => $seller->id])->test('pages::vendedor.configuracion-pagos')->set('confirmacionFisica', true)->call('verificar');
    expect($r->fresh()->verified_by)->toBe($admin->id)->and($r->fresh()->enabled)->toBeFalse();
    Livewire\Livewire::withQueryParams(['vendedor' => $seller->id])->test('pages::vendedor.configuracion-pagos')->call('alternar');
    expect($r->fresh()->enabled)->toBeTrue();
    Storage::disk('public')->put($r->qr_path, 'another-image');
    $r->refresh()->update(['enabled' => false, 'verified_at' => null]);
    Livewire\Livewire::withQueryParams(['vendedor' => $seller->id])->test('pages::vendedor.configuracion-pagos')->set('confirmacionFisica', true)->call('verificar');
    expect($r->fresh()->verified_at)->toBeNull();
});

test('reconciliación periódica recupera ingreso después de desconexión y vendedor desactivado', function () {
    [$client, $seller, , $p] = pagosFixture();
    app(CobrosService::class)->pedido($client, [['produccion_id' => $p->id, 'cantidad' => 1]], 'yape', (string) Str::uuid());
    $i = app(CobrosService::class)->iniciar(IntentoPago::first());
    $seller->update(['activo' => false]);
    pagoConfirmado($i);
    $this->artisan('pagos:reconciliar')->assertSuccessful();
    $this->artisan('pagos:reconciliar')->assertSuccessful();
    expect(Venta::count())->toBe(1)->and($p->fresh()->cantidadDisponible())->toBe(9.0)->and($i->fresh()->applied_at)->not->toBeNull();
});

test('compra con saldo conserva WhatsApp asignado y no muestra un QR Yape', function () {
    [$client, , $receiver, $p] = pagosFixture();
    app(SaldoService::class)->movimiento($client, 700, 'ajuste', 'fixture:saldo', 'fixture');
    $order = app(CobrosService::class)->pedido($client, [['produccion_id' => $p->id, 'cantidad' => 1]], 'saldo', (string) Str::uuid());
    $receiver->update(['whatsapp' => '51911111111']);
    $this->actingAs($client, 'cliente')->get(route('cliente.pedido', $order->id))->assertOk()->assertSee('https://wa.me/51988888888', false)->assertDontSee('QR del receptor asignado');
});

test('ajuste y reverso administrativos son auditables idempotentes y no sobregiran saldo', function () {
    [$client] = pagosFixture();
    $admin = User::factory()->rol(Rol::Administrador)->create(['activo' => true]);
    $options = ['cliente' => $client->id, 'importe' => '700', '--actor' => $admin->id, '--referencia' => 'expediente-credit-001'];
    $this->artisan('saldo:ajustar', $options)->assertSuccessful();
    $this->artisan('saldo:ajustar', $options)->assertSuccessful();
    $credit = MovimientoSaldo::first();
    $this->artisan('saldo:ajustar', ['cliente' => $client->id, 'importe' => '-700', '--actor' => $admin->id, '--referencia' => 'expediente-reverse-001', '--reverso' => $credit->id])->assertSuccessful();
    $this->artisan('saldo:ajustar', ['cliente' => $client->id, 'importe' => '-700', '--actor' => $admin->id, '--referencia' => 'expediente-reverse-002', '--reverso' => $credit->id])->assertFailed();
    expect($client->saldoMinor())->toBe(0)->and(MovimientoSaldo::count())->toBe(2)->and(MovimientoSaldo::latest('id')->first()->autorizado_por)->toBe($admin->id);
});

test('checkout guarda pendiente sin intención ni QR incluso si un cliente antiguo envía Yape', function () {
    [$client, , , $product] = pagosFixture();
    $response = $this->actingAs($client, 'cliente')->postJson(route('pedidos-web.store'), [
        'items' => [['produccion_id' => $product->id, 'cantidad' => 2]], 'metodo_pago' => 'yape',
        'celular' => '+51 987 654 321', 'idempotency_key' => (string) Str::uuid(),
    ])->assertCreated()->assertJsonPath('metodo_pago', 'pendiente');
    expect(IntentoPago::count())->toBe(0)->and(Venta::count())->toBe(0)->and($client->fresh()->celular)->toBe('51987654321');
    $this->get($response->json('url_pedido'))->assertOk()->assertSee('PENDIENTE DE PAGO')->assertDontSee('QR del receptor asignado');
    Http::assertNothingSent();
});

test('un pedido pendiente oculta QR y código si su receptor deja de estar disponible', function (string $reason) {
    [$client, , $receiver, $product] = pagosFixture();
    $service = app(CobrosService::class);
    $order = $service->pedido($client, [['produccion_id' => $product->id, 'cantidad' => 1]], 'yape', (string) Str::uuid());
    $intent = $service->iniciar(IntentoPago::firstOrFail());
    $snapshot = $intent->receptor_snapshot;
    if ($reason === 'local_disabled') {
        $receiver->update(['enabled' => false]);
    }
    if ($reason === 'local_unverified') {
        $receiver->update(['verified_at' => null]);
    }
    if ($reason === 'missing_qr') {
        Storage::disk('public')->delete($receiver->qr_path);
    }
    if ($reason === 'missing_config') {
        config(['payments.enabled' => false]);
    }
    if (in_array($reason, ['remote_disabled', 'remote_offline', 'remote_unverified', 'remote_qr_changed'])) {
        Http::swap(new Factory);
        Http::preventStrayRequests();
        Http::fake(fn () => Http::response([['id' => 'r1', 'name' => $receiver->nombre_receptor, 'phone' => $receiver->celular,
            'qrSha256' => $reason === 'remote_qr_changed' ? str_repeat('0', 64) : $receiver->qr_sha256,
            'enabled' => $reason !== 'remote_disabled', 'verified' => $reason !== 'remote_unverified', 'online' => $reason !== 'remote_offline']]));
    }
    foreach ([route('cliente.pedido', $order->id), route('cliente.pago', $intent->id)] as $url) {
        $this->actingAs($client, 'cliente')->get($url)->assertOk()->assertSee('No realices el pago.')
            ->assertDontSee('QR del receptor asignado')->assertDontSee('id="payment-code-form"', false);
    }
    expect($intent->fresh()->receptor_snapshot)->toBe($snapshot)->and($intent->fresh()->estado)->toBe('PENDING')
        ->and($intent->fresh()->applied_at)->toBeNull()->and(Venta::count())->toBe(0)->and($product->fresh()->cantidadDisponible())->toBe(10.0);
})->with(['local_disabled', 'local_unverified', 'missing_qr', 'missing_config', 'remote_disabled', 'remote_offline', 'remote_unverified', 'remote_qr_changed']);

test('checkout sin configuración comercial no inicia cobro ni declara una venta', function () {
    [$client, , , $product] = pagosFixture();
    config(['payments.enabled' => false, 'payments.key' => null]);
    $this->actingAs($client, 'cliente')->postJson(route('pedidos-web.store'), [
        'items' => [['produccion_id' => $product->id, 'cantidad' => 1]], 'metodo_pago' => 'yape', 'idempotency_key' => (string) Str::uuid(),
    ])->assertCreated();
    expect(IntentoPago::count())->toBe(0)->and(PedidoWeb::count())->toBe(1)->and(Venta::count())->toBe(0)
        ->and($product->fresh()->cantidadDisponible())->toBe(10.0)->and($client->saldoMinor())->toBe(0);
    Http::assertNothingSent();
});

test('intención sin alta remota informa el bloqueo y no muestra QR', function () {
    [$client, , , $product] = pagosFixture();
    $order = app(CobrosService::class)->pedido($client, [['produccion_id' => $product->id, 'cantidad' => 1]], 'yape', (string) Str::uuid());
    config(['payments.enabled' => false]);
    $this->actingAs($client, 'cliente')->get(route('cliente.pedido', $order->id))->assertOk()
        ->assertSee('vinculación comercial con Payment Core está pendiente')->assertDontSee('QR del receptor asignado')
        ->assertDontSee('id="payment-code-form"', false)->assertDontSee('Esperando pago: consultando estado…');
    expect(IntentoPago::first()->external_id)->toBeNull()->and(IntentoPago::first()->estado)->toBe('PENDING')->and(Venta::count())->toBe(0);
});

test('guardar configuración emite una sola confirmación por operación sin habilitar al receptor', function () {
    [, $seller, $receiver] = pagosFixture();
    $this->actingAs($seller, 'web');
    $component = Livewire\Livewire::test('pages::vendedor.configuracion-pagos');
    foreach (['988777666', '988777665'] as $phone) {
        $component->set('whatsapp', $phone)->call('guardar')->assertHasNoErrors()->assertDispatched('toast-show');
        expect(collect($component->effects['dispatches'])->where('name', 'toast-show'))->toHaveCount(1);
    }
    expect($receiver->fresh()->whatsapp)->toBe('51988777665');
});


test('admisión de cobro normaliza teléfono nacional de Core sin omitir identidad ni QR', function () {
    [$client, , $r, $p] = pagosFixture();
    Http::swap(new Factory);
    Http::preventStrayRequests();
    Http::fake(fn () => Http::response([['id' => 'r1', 'name' => $r->nombre_receptor, 'enabled' => true, 'verified' => true, 'online' => true, 'phone' => '999999999', 'qrSha256' => $r->qr_sha256]]));
    app(CobrosService::class)->pedido($client, [['produccion_id' => $p->id, 'cantidad' => 1]], 'yape', (string) Str::uuid());
    expect(IntentoPago::firstOrFail()->estado)->toBe('PENDING')->and(Venta::count())->toBe(0);
});


test('pagar después de vencer crea una sola nueva intención y conserva receptor sin reservar stock', function () {
    [$client, , $r, $p] = pagosFixture();
    $service = app(CobrosService::class);
    $order = $service->pedido($client, [['produccion_id' => $p->id, 'cantidad' => 1]], 'yape', (string) Str::uuid());
    $old = $service->iniciar(IntentoPago::firstOrFail());
    $this->travel(6)->minutes();
    Http::swap(new Factory); Http::preventStrayRequests();
    Http::fake(function ($req) use ($r, $old) {
        if (str_ends_with($req->url(), '/api/receivers')) return Http::response([['id'=>'r1','name'=>$r->nombre_receptor,'phone'=>$r->celular,'qrSha256'=>$r->qr_sha256,'verified'=>true,'enabled'=>true,'online'=>true]]);
        return Http::response(pagoRespuesta($old, 'EXPIRED'));
    });
    $next = $service->prepararPedido($order, $client);
    expect($next->id)->not->toBe($old->id)->and($next->receptor_snapshot)->toBe($old->receptor_snapshot)->and($old->fresh()->estado)->toBe('EXPIRED');
    expect($service->prepararPedido($order->fresh(), $client)->id)->toBe($next->id)->and(IntentoPago::count())->toBe(2)->and($p->fresh()->cantidadDisponible())->toBe(10.0)->and(Venta::count())->toBe(0);
});

<?php

use App\Enums\EstadoProduccion;
use App\Enums\Rol;
use App\Models\ClienteWeb;
use App\Models\IntentoPago;
use App\Models\MovimientoSaldo;
use App\Models\Produccion;
use App\Models\ReceptorPago;
use App\Models\Receta;
use App\Models\User;
use App\Models\Venta;
use App\Services\Payments\CobrosService;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Storage;
use Illuminate\Support\Str;

/** Opt-in: arranca un Core aislado con tools/test_bridge_wifi.py; nunca apunta al Core operativo. */
function configuracionCoreWifi(): array
{
    $file = getenv('BRIDGE_TEST_CORE_CONFIG');
    $url = getenv('BRIDGE_TEST_CORE_URL');
    if (! $file || ! $url || (! str_starts_with($file, '/tmp/huata-bridge-test-') && ! str_starts_with($file, '/tmp/huata-receiver-test-')) || ! preg_match('~\Ahttp://127\.0\.0\.1:[0-9]+\z~', $url) || in_array(parse_url($url, PHP_URL_PORT), [8000, 8087], true)) {
        throw new RuntimeException('Esta prueba solo admite su servidor temporal privado.');
    }

    return json_decode(file_get_contents($file), true, flags: JSON_THROW_ON_ERROR);
}
function pedidoWifiReal(): array
{
    $cfg = configuracionCoreWifi();
    config(['payments.enabled' => true, 'payments.url' => getenv('BRIDGE_TEST_CORE_URL'), 'payments.key' => $cfg['clients'][0]['key'], 'payments.allow_local_http' => true]);
    test()->withToken($cfg['devices'][0]['key'])->postJson('/api/bridge/heartbeat', ['listenerConnected' => true])->assertOk()->assertJsonPath('receiptOnly', true);
    Storage::fake('public');
    $seller = User::factory()->rol(Rol::Vendedor)->create();
    Storage::disk('public')->put('pagos/qr-wifi.png', 'fixture-image');
    ReceptorPago::create(['user_id' => $seller->id, 'nombre_receptor' => $cfg['receivers'][0]['name'], 'celular' => $cfg['receivers'][0]['phone'], 'whatsapp' => '51988888888', 'referencia_externa' => 'r1', 'qr_path' => 'pagos/qr-wifi.png', 'qr_sha256' => $cfg['receivers'][0]['qrSha256'], 'verified_at' => now(), 'enabled' => true]);
    $buyer = ClienteWeb::create(['nombres' => 'Cliente', 'apellidos' => 'Wifi ficticio', 'celular' => '51977777777', 'email' => 'wifi@example.test', 'password' => 'FixturePassword-123']);
    $recipe = Receta::factory()->create(['precio_venta' => '7.00']);
    $product = Produccion::factory()->create(['receta_id' => $recipe->id, 'cantidad_producida' => 10, 'cantidad_vendida' => 0, 'estado' => EstadoProduccion::Completada]);

    return [$buyer, $product, app(CobrosService::class)];
}
function eventoWifiReal(string $code, string $amount = '7.00'): array
{
    return ['bridgeEventId' => str_replace('-', '', (string) Str::uuid()), 'receiverId' => 'r1', 'packageName' => 'com.bcp.innovacxion.yapeapp', 'postedAt' => now()->utc()->format('Y-m-d\TH:i:s.u\Z'), 'text' => "Yape! Persona Ficticia te envió un pago por S/ $amount", 'securityCode' => $code];
}

beforeEach(function () {
    if (! getenv('BRIDGE_TEST_CORE_URL')) {
        $this->markTestSkipped('Ejecutar con el runner de Core aislado; no usar servicios reales.');
    }
});

test('Bridge a Laravel a Core confirma una sola venta tras correlación exacta y consulta canónica', function () {
    [$buyer, $p, $service] = pedidoWifiReal();
    $service->pedido($buyer, [['produccion_id' => $p->id, 'cantidad' => 1]], 'yape', (string) Str::uuid());
    $intent = $service->iniciar(IntentoPago::firstOrFail());
    expect($intent->external_id)->toStartWith('PAY-')->and($intent->estado)->toBe('PENDING');
    $event = eventoWifiReal('381');
    $this->postJson('/api/bridge/notifications', $event)->assertStatus(202)->assertJsonPath('accepted', true)->assertJsonPath('eventId', $event['bridgeEventId'])->assertJsonPath('receiptOnly', true);
    $pending = $service->consultar($intent->fresh());
    expect($pending->estado)->toBe('PENDING')->and(Venta::count())->toBe(0)->and($p->fresh()->cantidadDisponible())->toBe(10.0);
    $paid = $service->validar($intent->fresh(), '381', 'cliente:'.$buyer->id, '127.0.0.1');
    expect($paid->estado)->toBe('PAID')->and($paid->evidence_id)->toBe($event['bridgeEventId'])->and(Venta::count())->toBe(1)->and($p->fresh()->cantidadDisponible())->toBe(9.0);
    $this->postJson('/api/bridge/notifications', $event)->assertStatus(202)->assertJsonPath('result', 'DUPLICATE');
    $service->consultar($paid->fresh());
    expect(Venta::count())->toBe(1)->and(MovimientoSaldo::count())->toBe(0)->and($p->fresh()->cantidadDisponible())->toBe(9.0);
});

test('notificación válida sin intención se conserva sin inventar un cobro', function () {
    pedidoWifiReal();
    $event = eventoWifiReal('927', '12.34');
    $this->postJson('/api/bridge/notifications', $event)->assertStatus(202)->assertJsonPath('result', 'OBSERVED');
    expect(IntentoPago::count())->toBe(0)->and(Venta::count())->toBe(0)->and(MovimientoSaldo::count())->toBe(0);
});

test('dos intenciones competidoras permanecen pendientes y ambiguas sin acreditar arbitrariamente', function () {
    [$buyer, , $service] = pedidoWifiReal();
    $a = $service->iniciar($service->recarga($buyer, 700, (string) Str::uuid()));
    $b = $service->iniciar($service->recarga($buyer, 700, (string) Str::uuid()));
    expect($service->validar($a, '492', 'cliente:'.$buyer->id, '127.0.0.1')->estado)->toBe('PENDING');
    expect($service->validar($b, '492', 'cliente:'.$buyer->id, '127.0.0.1')->estado)->toBe('PENDING');
    $event = eventoWifiReal('492');
    $this->postJson('/api/bridge/notifications', $event)->assertStatus(202);
    expect($service->consultar($a->fresh())->ultimo_resultado)->toBe('AMBIGUOUS')->and($service->consultar($b->fresh())->ultimo_resultado)->toBe('AMBIGUOUS');
    expect($buyer->saldoMinor())->toBe(0)->and(MovimientoSaldo::count())->toBe(0)->and(Venta::count())->toBe(0);
});

test('la clave de aplicación no autentica Bridge y un receptor no verificado sigue en revisión', function () {
    pedidoWifiReal();
    $cfg = configuracionCoreWifi();
    $this->withToken($cfg['clients'][0]['key'])->postJson('/api/bridge/notifications', eventoWifiReal('827'))->assertUnauthorized();
    $event = [...eventoWifiReal('827'), 'receiverId' => 'r2'];
    $this->withToken($cfg['devices'][1]['key'])->postJson('/api/bridge/notifications', $event)->assertStatus(202)->assertJsonPath('result', 'REVIEW_RECEIVER_UNVERIFIED');
    $receivers = Http::withToken($cfg['clients'][0]['key'])->get(getenv('BRIDGE_TEST_CORE_URL').'/api/receivers')->throw()->json();
    expect(collect($receivers)->firstWhere('id', 'r2')['enabled'])->toBeFalse();
    expect(IntentoPago::count())->toBe(0)->and(Venta::count())->toBe(0);
    foreach (['/api/bridge/raw-notification', '/api/yape/waits', '/api/lab/payments/PAY-fixture/confirm'] as $path) {
        expect(Http::post(getenv('BRIDGE_TEST_CORE_URL').$path, [])->status())->toBe(404);
    }
});

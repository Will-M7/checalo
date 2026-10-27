<?php

use App\Models\IntentoPago;
use App\Models\Venta;
use Illuminate\Http\Client\ConnectionException;
use Illuminate\Http\Client\Factory;
use Illuminate\Support\Facades\Http;

function eventoBridgeWifi(): array
{
    return ['bridgeEventId' => 'wifi_event_identity_0001', 'receiverId' => 'receptor_prueba', 'packageName' => 'com.bcp.innovacxion.yapeapp', 'postedAt' => '2026-10-27T12:00:00.001Z', 'text' => 'Yape! Persona Prueba te envió un pago por S/ 7.00', 'securityCode' => '381'];
}
function autenticarBridgeWifi(): void
{
    test()->withToken(str_repeat('d', 40));
}
function coreBridgeWifi(callable $respuesta): void
{
    Http::fake(fn ($r) => str_ends_with($r->url(), '/health') ? Http::response(['status' => 'ok', 'mode' => 'commercial']) : $respuesta($r));
}

beforeEach(function () {
    config(['payments.enabled' => true, 'payments.url' => 'http://127.0.0.1:8087', 'payments.key' => str_repeat('c', 40), 'payments.allow_local_http' => true]);
    Http::preventStrayRequests();
});

test('ingreso requiere credencial y no usa autenticación de compradores ni personal', function () {
    $this->postJson('/api/bridge/notifications', eventoBridgeWifi())->assertUnauthorized()->assertJsonPath('accepted', false);
    Http::assertNothingSent();
});

test('heartbeat autentica dispositivo en Core comercial privado y confirma solo conectividad', function () {
    autenticarBridgeWifi();
    coreBridgeWifi(fn ($r) => Http::response(['accepted' => true]));
    $this->postJson('/api/bridge/heartbeat', ['listenerConnected' => true])->assertOk()->assertJsonPath('accepted', true)->assertJsonPath('coreMode', 'commercial')->assertJsonPath('receiptOnly', true);
    Http::assertSent(fn ($r) => $r->url() === 'http://127.0.0.1:8087/api/bridge/heartbeat' && $r->hasHeader('Authorization', 'Bearer '.str_repeat('d', 40)) && $r['listenerConnected'] === true);
    Http::assertNotSent(fn ($r) => $r->hasHeader('Authorization', 'Bearer '.str_repeat('c', 40)));
    Http::assertSent(fn ($r) => str_ends_with($r->url(), '/health') && ! $r->hasHeader('Authorization'));
});

test('evento canónico conserva identidad receptor instante importe y código pero no extras', function () {
    autenticarBridgeWifi();
    $data = eventoBridgeWifi();
    coreBridgeWifi(function ($r) use ($data) {
        expect($r->data())->toBe($data);
        expect($r->hasHeader('Authorization', 'Bearer '.str_repeat('d', 40)))->toBeTrue();

        return Http::response(['accepted' => true, 'eventId' => $data['bridgeEventId'], 'result' => 'OBSERVED']);
    });
    $this->postJson('/api/bridge/notifications', [...$data, 'extras' => ['secret' => 'discard'], 'paid' => true, 'status' => 'PAID', 'url' => 'http://otro-servidor'])->assertStatus(202)->assertJsonPath('eventId', $data['bridgeEventId'])->assertJsonPath('receiptOnly', true)->assertJsonMissingPath('status');
    expect(IntentoPago::count())->toBe(0)->and(Venta::count())->toBe(0);
    Http::assertSentCount(2);
});

test('sin vinculación comercial no inventa claves ni transmite evidencia', function (array $config) {
    autenticarBridgeWifi();
    config($config);
    $this->postJson('/api/bridge/notifications', eventoBridgeWifi())->assertServiceUnavailable()->assertJsonPath('code', 'COMMERCIAL_LINK_PENDING');
    Http::assertNothingSent();
})->with([
    [['payments.enabled' => false]], [['payments.key' => null]], [['payments.url' => 'http://192.168.1.70:8087']],
    [['payments.allow_local_http' => false]], [['payments.url' => 'https://user:secret@example.test']],
]);

test('no reenvía a motores de laboratorio aunque compartan nombres de rutas', function () {
    autenticarBridgeWifi();
    Http::fake(fn ($r) => Http::response(['mode' => 'synthetic-lab']));
    $this->postJson('/api/bridge/notifications', eventoBridgeWifi())->assertServiceUnavailable()->assertJsonPath('code', 'COMMERCIAL_CORE_REQUIRED');
    Http::assertSentCount(1);
    Http::assertNotSent(fn ($r) => str_contains($r->url(), '/notifications'));
});

test('un 200 sin ACK booleano exacto e identidad correspondiente no confirma recepción', function (array $ack) {
    autenticarBridgeWifi();
    coreBridgeWifi(fn ($r) => Http::response($ack));
    $this->postJson('/api/bridge/notifications', eventoBridgeWifi())->assertStatus(502)->assertJsonPath('accepted', false)->assertJsonPath('code', 'CORE_ACK_INVALID');
})->with([
    [[]], [['accepted' => 'true', 'eventId' => 'wifi_event_identity_0001', 'result' => 'OBSERVED']],
    [['accepted' => true, 'eventId' => 'otra-identidad', 'result' => 'OBSERVED']],
    [['accepted' => true, 'eventId' => 'wifi_event_identity_0001', 'result' => 'PAID']],
    [['accepted' => false, 'eventId' => 'wifi_event_identity_0001', 'result' => 'OBSERVED']],
]);

test('rechazos del motor y secretos en sus errores no se reflejan ni se convierten en aceptación', function (int $status) {
    autenticarBridgeWifi();
    coreBridgeWifi(fn ($r) => Http::response(['secret' => str_repeat('d', 40), 'accepted' => true], $status));
    $response = $this->postJson('/api/bridge/notifications', eventoBridgeWifi())->assertStatus($status)->assertJsonPath('accepted', false);
    expect($response->getContent())->not->toContain(str_repeat('d', 40));
})->with([400, 401, 403, 409, 429, 503]);

test('caída y reconexión conservan el identificador para el reenvío idempotente', function () {
    autenticarBridgeWifi();
    Http::fake(fn ($r) => throw new ConnectionException('mensaje privado '.str_repeat('d', 40)));
    $this->postJson('/api/bridge/notifications', eventoBridgeWifi())->assertServiceUnavailable()->assertJsonPath('code', 'CORE_UNREACHABLE');
    Http::swap(new Factory);
    Http::preventStrayRequests();
    coreBridgeWifi(fn ($r) => Http::response(['accepted' => true, 'eventId' => eventoBridgeWifi()['bridgeEventId'], 'result' => 'DUPLICATE']));
    $this->postJson('/api/bridge/notifications', eventoBridgeWifi())->assertStatus(202)->assertJsonPath('eventId', eventoBridgeWifi()['bridgeEventId'])->assertJsonPath('result', 'DUPLICATE');
    expect(IntentoPago::count())->toBe(0)->and(Venta::count())->toBe(0);
});

test('aplicaciones falsas códigos inválidos o payload excesivo son rechazados antes del motor', function (array $override) {
    autenticarBridgeWifi();
    $this->postJson('/api/bridge/notifications', [...eventoBridgeWifi(), ...$override])->assertStatus(422);
    Http::assertNothingSent();
})->with([
    [['packageName' => 'fake.bank']], [['securityCode' => '1234']], [['bridgeEventId' => 'short']], [['postedAt' => 'tomorrow']],
]);

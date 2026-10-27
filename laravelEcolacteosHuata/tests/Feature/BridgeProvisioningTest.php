<?php

use App\Enums\Rol;
use App\Models\ReceptorPago;
use App\Models\User;
use App\Services\Payments\BridgeProvisioning;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Storage;
use Illuminate\Validation\ValidationException;
use Symfony\Component\HttpKernel\Exception\HttpException;

function enrollmentFixture(): array
{
    config(['payments.enabled' => true, 'payments.url' => 'http://127.0.0.1:8087', 'payments.key' => str_repeat('x', 40), 'payments.allow_local_http' => true]);
    Storage::fake('public');
    Storage::disk('public')->put('qr/enroll.png', 'fixture-image');
    $admin = User::factory()->rol(Rol::Administrador)->create(['activo' => true]);
    $owner = User::factory()->rol(Rol::Vendedor)->create(['activo' => true]);
    $other = User::factory()->rol(Rol::Vendedor)->create(['activo' => true]);
    $r = ReceptorPago::create(['user_id' => $owner->id, 'nombre_receptor' => 'Prueba', 'celular' => '51999999999', 'whatsapp' => '51999999999', 'referencia_externa' => 'personal_'.$owner->id, 'qr_path' => 'qr/enroll.png', 'qr_sha256' => hash('sha256', 'fixture-image'), 'enabled' => false]);
    Http::preventStrayRequests();
    Http::fake(function ($request) {
        if (str_ends_with($request->url(), '/api/admin/devices') && $request->method() === 'GET') {
            return Http::response([]);
        }
        if (str_ends_with($request->url(), '/health')) {
            return Http::response(['mode' => 'commercial']);
        }
        if (str_ends_with($request->url(), '/grant')) {
            return Http::response(['token' => str_repeat('g', 64)]);
        }
        if (str_ends_with($request->url(), '/state')) {
            return Http::response(['enabled' => $request['enabled']]);
        }

        return Http::response(['id' => $request['id'] ?? 'fixture', 'enabled' => true]);
    });

    return [$admin, $owner, $other, $r, app(BridgeProvisioning::class)];
}

test('inscripción idempotente conserva instalación y no verifica cuenta financiera', function () {
    [$admin,,,$r,$service] = enrollmentFixture();
    $ticket = $service->ticket($admin, $r, 'personal', false);
    $a = $service->enroll($ticket, str_repeat('a', 64));
    $b = $service->enroll($ticket, str_repeat('a', 64));
    expect($b)->toBe($a)->and(DB::table('payment_bridge_devices')->count())->toBe(1)->and($r->fresh()->verified_at)->toBeNull()->and($r->fresh()->enabled)->toBeFalse();
    expect(fn () => $service->enroll($ticket, str_repeat('b', 64)))->toThrow(ValidationException::class);
});

test('sesión compatible autoriza un solo evento y prueba repetida suspende sin borrar', function () {
    [$admin,$owner,,$r,$service] = enrollmentFixture();
    $cfg = $service->enroll($service->ticket($admin, $r, 'personal', false), str_repeat('a', 64));
    $nonce = str_repeat('n', 64);
    $id = 'personal_event_000001';
    $proof = $service->assertion($owner, $cfg['deviceId'], $nonce, $id, $owner->usuario);
    $data = ['receiverId' => $r->referencia_externa, 'bridgeEventId' => $id, 'identityProof' => $proof['proof'], 'identityNonce' => $nonce];
    $result = $service->authorizeIngress($cfg['credential'], $data, false);
    expect($result['authorizationGrant'])->toBe(str_repeat('g', 64))->and($result)->not->toHaveKey('identityProof');
    expect(fn () => $service->authorizeIngress($cfg['credential'], $data, false))->toThrow(HttpException::class);
    expect(DB::table('payment_bridge_devices')->find($cfg['deviceId'])->status)->toBe('suspended');
});

test('cambio de usuario o nombre escrito no prueba identidad y sesión correcta recupera acceso', function () {
    [$admin,$owner,$other,$r,$service] = enrollmentFixture();
    $cfg = $service->enroll($service->ticket($admin, $r, 'personal', false), str_repeat('a', 64));
    foreach ([[$other, $other->usuario], [$owner, 'Nombre escrito']] as [$user,$name]) {
        expect(fn () => $service->assertion($user, $cfg['deviceId'], str_repeat('n', 64), 'heartbeat', $name))->toThrow(HttpException::class);
    }
    expect(fn () => $service->authorizeIngress($cfg['credential'], ['listenerConnected' => true], true))->toThrow(HttpException::class);
    expect(DB::table('payment_bridge_devices')->find($cfg['deviceId'])->status)->toBe('suspended');
    $proof = $service->assertion($owner, $cfg['deviceId'], str_repeat('n', 64), 'heartbeat', $owner->usuario);
    $service->authorizeIngress($cfg['credential'], ['listenerConnected' => true, 'identityProof' => $proof['proof'], 'identityNonce' => $proof['nonce']], true);
    expect(DB::table('payment_bridge_devices')->find($cfg['deviceId'])->status)->toBe('active');
});

test('receptor compartido no exige sesión de cada vendedor y revocación bloquea inscripción antigua', function () {
    [$admin,,,$r,$service] = enrollmentFixture();
    $ticket = $service->ticket($admin, $r, 'shared', true);
    $cfg = $service->enroll($ticket, str_repeat('a', 64));
    $data = ['receiverId' => $r->referencia_externa, 'bridgeEventId' => 'shared_event_000001'];
    expect($service->authorizeIngress($cfg['credential'], $data, false))->toBe($data);
    $service->revoke($admin, $cfg['deviceId']);
    expect(fn () => $service->enroll($ticket, str_repeat('a', 64)))->toThrow(ValidationException::class);
    expect(fn () => $service->authorizeIngress($cfg['credential'], ['listenerConnected' => true], true))->toThrow(HttpException::class);
    expect(DB::table('payment_bridge_devices')->count())->toBe(1);
});

test('inscripción vencida y modo directo sin autorización se rechazan', function () {
    [$admin,$owner,,$r,$service] = enrollmentFixture();
    expect(fn () => $service->ticket($owner, $r, 'direct', true))->toThrow(HttpException::class);
    $ticket = $service->ticket($admin,$r,'personal',false);
    $this->travel(11)->minutes();
    expect(fn () => $service->enroll($ticket,str_repeat('a',64)))->toThrow(ValidationException::class);
});

test('credencial anterior no atraviesa el ingreso de un receptor administrado', function () {
    [$admin,,,$r,$service] = enrollmentFixture();
    $service->ticket($admin, $r, 'personal', false);
    expect(fn () => $service->authorizeIngress(str_repeat('z', 64), [
        'receiverId' => $r->referencia_externa,
        'bridgeEventId' => 'old_key_event_000001',
    ], false))->toThrow(HttpException::class);
    expect(DB::table('payment_bridge_devices')->count())->toBe(1)
        ->and(DB::table('payment_bridge_assertions')->count())->toBe(0);
});

<?php

use App\Enums\Rol;
use App\Models\IntentoPago;
use App\Models\ReceptorPago;
use App\Models\User;
use App\Services\Payments\PaymentCore;
use App\Services\Payments\ReceptoresService;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Storage;
use Illuminate\Support\Str;
use Illuminate\Validation\ValidationException;

beforeEach(function () {
    if (! getenv('RECEIVER_TEST_CORE_URL')) {
        $this->markTestSkipped('Requiere tools/test_receiver_provisioning.py y su Core temporal.');
    }
});

test('Laravel verifica habilita y deshabilita receptor inicialmente bloqueado contra Core comercial real aislado', function () {
    $file = getenv('RECEIVER_TEST_CORE_CONFIG');
    $url = getenv('RECEIVER_TEST_CORE_URL');
    if (! $file || ! str_starts_with($file, '/tmp/huata-receiver-test-') || ! preg_match('~\Ahttp://127\.0\.0\.1:[0-9]+\z~', $url) || in_array(parse_url($url, PHP_URL_PORT), [8000, 8087], true)) {
        throw new RuntimeException('Solo se admite el Core temporal de pruebas.');
    }
    $cfg = json_decode(file_get_contents($file), true, flags: JSON_THROW_ON_ERROR);
    config(['payments.enabled' => true, 'payments.url' => $url, 'payments.key' => $cfg['clients'][0]['key'], 'payments.allow_local_http' => true]);
    Storage::fake('public');
    Storage::disk('public')->put('pagos/qr-admin.png', 'fixture-image');
    $admin = User::factory()->rol(Rol::Administrador)->create(['activo' => true]);
    $seller = User::factory()->rol(Rol::Vendedor)->create(['activo' => true]);
    $r = ReceptorPago::create(['user_id' => $seller->id, 'nombre_receptor' => $cfg['receivers'][2]['name'], 'celular' => '51977777777', 'whatsapp' => '51988888888',
        'referencia_externa' => 'r3', 'qr_path' => 'pagos/qr-admin.png', 'qr_sha256' => hash('sha256', 'fixture-image'), 'enabled' => false]);
    $r->refresh();
    \Illuminate\Support\Facades\DB::table('payment_bridge_devices')->insert(['id' => $cfg['devices'][2]['id'], 'receiver_id' => $r->id, 'owner_id' => $seller->id, 'mode' => 'direct', 'status' => 'active', 'created_at' => now(), 'updated_at' => now()]);
    $service = app(ReceptoresService::class);
    expect($service->diagnostico($r))->toContain('pendiente de verificación física');
    expect(fn () => $service->cambiar($admin, $r, 'verify', false))->toThrow(ValidationException::class);
    $service->cambiar($admin, $r, 'verify', true);
    expect($r->fresh()->verified_by)->toBe($admin->id)->and($r->fresh()->enabled)->toBeFalse();
    $remote = $service->remoto($r);
    expect($remote['verified'])->toBeTrue()->and($remote['enabled'])->toBeFalse()->and($remote['verifiedBy'])->toBe('wifi_suite:staff_'.$admin->id);
    expect(fn () => $service->cambiar($admin, $r->fresh(), 'enable'))->toThrow(ValidationException::class);
    $this->withToken($cfg['devices'][1]['key'])->postJson('/api/bridge/heartbeat', ['listenerConnected' => true])->assertOk();
    expect($service->remoto($r)['online'])->toBeFalse();
    $this->withToken($cfg['devices'][2]['key'])->postJson('/api/bridge/heartbeat', ['listenerConnected' => true])->assertOk();
    $service->cambiar($admin, $r->fresh(), 'enable');
    expect($r->fresh()->enabled)->toBeTrue()->and($service->diagnostico($r->fresh()))->toContain('Recepción comercial habilitada');
    $intent = new IntentoPago(['referencia' => (string) Str::uuid(), 'amount_minor' => 700, 'cliente_web_id' => 1,
        'receptor_snapshot' => ['external_reference' => 'r3', 'qr_sha256' => $r->qr_sha256]]);
    $pay = app(PaymentCore::class)->create($intent);
    expect($pay['id'])->toStartWith('PAY-')->and($pay['status'])->toBe('PENDING');
    $service->cambiar($admin, $r->fresh(), 'disable');
    expect($r->fresh()->enabled)->toBeFalse()->and($service->remoto($r)['enabled'])->toBeFalse();
    $intent->referencia = (string) Str::uuid();
    expect(fn () => app(PaymentCore::class)->create($intent))->toThrow(ValidationException::class);
    expect(Http::withToken($cfg['devices'][2]['key'])->post($url.'/api/admin/receivers/r3/state', [])->status())->toBe(401);
    expect(app(PaymentCore::class)->get($pay['id'])['status'])->toBe('PENDING');
});

test('inscripción personal sesión revocable y evento sin código recorren Laravel y Core reales aislados', function () {
    $file = getenv('RECEIVER_TEST_CORE_CONFIG'); $url = getenv('RECEIVER_TEST_CORE_URL');
    if (! $file || ! str_starts_with($file, '/tmp/huata-receiver-test-') || ! preg_match('~\Ahttp://127\.0\.0\.1:[0-9]+\z~', $url) || in_array(parse_url($url, PHP_URL_PORT), [8000, 8087], true)) throw new RuntimeException('Solo Core aislado.');
    $cfg=json_decode(file_get_contents($file),true,flags:JSON_THROW_ON_ERROR);
    config(['payments.enabled'=>true,'payments.url'=>$url,'payments.key'=>$cfg['clients'][0]['key'],'payments.allow_local_http'=>true]);
    Storage::fake('public'); Storage::disk('public')->put('qr/native.png','fixture-image');
    $admin=User::factory()->rol(Rol::Administrador)->create(['activo'=>true]);
    $owner=User::factory()->rol(Rol::Vendedor)->create(['activo'=>true]);
    $other=User::factory()->rol(Rol::Vendedor)->create(['activo'=>true]);
    $r=ReceptorPago::create(['user_id'=>$owner->id,'nombre_receptor'=>'Personal ficticio','celular'=>'51966666666','whatsapp'=>'51966666666','referencia_externa'=>'r4','qr_path'=>'qr/native.png','qr_sha256'=>hash('sha256','fixture-image'),'enabled'=>false]);
    $provision=app(\App\Services\Payments\BridgeProvisioning::class);
    $ticket=$provision->ticket($admin,$r,'personal',false);
    $enrollment=$this->postJson('/api/bridge/enroll',['ticket'=>$ticket,'claim'=>str_repeat('a',64)])->assertOk()->json();
    $device=$enrollment['deviceId'];$key=$enrollment['credential'];
    $this->withToken($key)->postJson('/api/bridge/heartbeat',['listenerConnected'=>true])->assertForbidden();
    $nonce=str_repeat('n',64);
    $proof=$provision->assertion($owner,$device,$nonce,'heartbeat',$owner->usuario);
    $this->withToken($key)->postJson('/api/bridge/heartbeat',['listenerConnected'=>true,'identityProof'=>$proof['proof'],'identityNonce'=>$nonce])->assertOk();
    $receivers=app(ReceptoresService::class);
    $receivers->cambiar($admin,$r->fresh(),'verify',true);
    $receivers->cambiar($admin,$r->fresh(),'enable');
    $buyer=\App\Models\ClienteWeb::create(['nombres'=>'Comprador','apellidos'=>'Prueba','celular'=>'51955555555','email'=>'native@example.test','password'=>'TestPassword-123']);
    $cobros=app(\App\Services\Payments\CobrosService::class);
    $pay=$cobros->iniciar($cobros->recarga($buyer,100,(string)Str::uuid()));
    $event=['bridgeEventId'=>'personal_real_format_00001','receiverId'=>'r4','packageName'=>'com.bcp.innovacxion.yapeapp','postedAt'=>now()->utc()->format('Y-m-d\TH:i:s.u\Z'),'title'=>'Confirmación de Pago','text'=>'Yape! Remitente Ficticio te envió un pago por S/ 1.00'];
    expect(Http::withToken($key)->post($url.'/api/bridge/notifications',$event)->status())->toBe(403);
    $proof=$provision->assertion($owner,$device,$nonce,$event['bridgeEventId'],$owner->usuario);
    $this->withToken($key)->postJson('/api/bridge/notifications',[...$event,'identityProof'=>$proof['proof'],'identityNonce'=>$nonce])->assertStatus(202)->assertJsonPath('result','REVIEW_CODE_UNAVAILABLE')->assertJsonPath('reviewRequired',true);
    expect($cobros->validar($pay,'123','cliente:'.$buyer->id,'127.0.0.1')->estado)->toBe('PENDING')->and($buyer->saldoMinor())->toBe(0);
    $this->withToken($key)->postJson('/api/bridge/heartbeat',['listenerConnected'=>true])->assertForbidden();
    expect(Http::withToken($key)->post($url.'/api/bridge/notifications',$event)->status())->toBe(401);
    expect(\Illuminate\Support\Facades\DB::table('payment_bridge_devices')->find($device)->status)->toBe('suspended');
});

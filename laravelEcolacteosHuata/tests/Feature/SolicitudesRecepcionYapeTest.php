<?php

use App\Enums\Rol;
use App\Livewire\ReceptoresYape;
use App\Models\{ReceptorPago, RolDinamico, User};
use App\Services\Payments\{AsignacionesReceptor, HuataYape, SolicitudesRecepcionYape};
use App\Services\VendedoresWeb;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\{DB, Http, Storage};
use Illuminate\Support\Str;
use Livewire\Livewire;

function solicitudYapeFixture(bool $custom = false): array
{
    config(['payments.enabled' => true, 'payments.url' => 'http://127.0.0.1:8087', 'payments.key' => str_repeat('x', 40), 'payments.allow_local_http' => true]);
    Storage::fake('public'); Storage::disk('public')->put('qr/solicitud.png', 'qr-solicitud');
    $admin = User::factory()->rol(Rol::Administrador)->create();
    $user = User::factory()->rol(Rol::Proveedor)->create();
    if ($custom) {
        $role = RolDinamico::create(['codigo' => 'control_solicitud', 'nombre' => 'Control personalizado', 'activo' => true, 'pantallas' => ['proveedor.panel']]);
        $user->rolesDinamicos()->sync([$role->id]);
    }
    $service = app(SolicitudesRecepcionYape::class);
    $r = $service->guardar($admin, null, 'Cuenta autorizada', '999999999', $user->id, 'qr/solicitud.png', hash('sha256', 'qr-solicitud'));
    $token = Str::random(40);
    DB::table('mobile_tokens')->insert(['user_id' => $user->id, 'token' => hash('sha256', $token), 'created_at' => now(), 'updated_at' => now()]);
    Http::preventStrayRequests();
    Http::fake(function ($req) {
        if (str_ends_with($req->url(), '/health')) return Http::response(['mode' => 'commercial']);
        if ($req->method() === 'GET' && str_ends_with($req->url(), '/api/admin/devices')) return Http::response([]);
        if (str_ends_with($req->url(), '/state')) return Http::response(['enabled' => $req['enabled']]);
        if (str_ends_with($req->url(), '/api/bridge/heartbeat')) return Http::response(['accepted' => true]);
        if (str_ends_with($req->url(), '/api/bridge/notifications')) return Http::response(['accepted' => true, 'eventId' => $req['bridgeEventId'], 'result' => 'REVIEW_CODE_UNAVAILABLE', 'reviewRequired' => true]);
        return Http::response(['id' => $req['id'] ?? 'fixture', 'enabled' => true]);
    });
    return [$admin, $user, $r, $service, $token, str_repeat('a', 64)];
}

function solicitudYapeStatus($test, string $key, bool $consent = false, bool $listener = false)
{
    return $test->postJson('/api/mobile/yape/status', ['installation' => $key, 'label' => 'Android compatible', 'listener' => $listener, 'permission' => $listener, 'consent' => $consent]);
}

function solicitudYapeDecision($test, $service, $r, $key, bool $accept = true)
{
    return $test->postJson('/api/mobile/yape/decision', ['installation' => $key, 'requestId' => $service->actual($r->id)->id, 'accept' => $accept]);
}

test('proveedor y rol personalizado reciben y aceptan sin adquirir permisos comerciales', function (bool $custom) {
    [$admin, $user, $r, $s, $token, $key] = solicitudYapeFixture($custom);
    $roles = $user->rolesDinamicos()->pluck('pantallas')->all();
    $this->withToken($token);
    solicitudYapeStatus($this, $key)->assertOk()->assertJsonPath('authorized', false)->assertJsonPath('requests.0.name', 'Cuenta autorizada')->assertJsonPath('requests.0.phone', '51999999999')->assertJsonMissingPath('credential');
    solicitudYapeDecision($this, $s, $r, $key)->assertOk()->assertJsonPath('saved', true);
    solicitudYapeDecision($this, $s, $r, $key)->assertOk();
    solicitudYapeStatus($this, $key, true)->assertOk()->assertJsonPath('authorized', true)->assertJsonPath('serverAccepted', true)->assertJsonCount(0, 'requests');
    $this->postJson('/api/mobile/yape/activate', ['installation' => $key])->assertForbidden();
    solicitudYapeStatus($this, $key, true, true)->assertOk();
    $this->postJson('/api/mobile/yape/activate', ['installation' => $key])->assertOk()->assertJsonPath('status', 'active')->assertJsonMissingPath('credential');
    expect($user->rolesDinamicos()->pluck('pantallas')->all())->toBe($roles)->and($user->puedeVender('vendedor.ventas'))->toBeFalse();
    expect($r->fresh()->verified_at)->toBeNull()->and($r->fresh()->enabled)->toBeFalse()->and(DB::table('payment_bridge_devices')->count())->toBe(1);
})->with([false, true]);

test('rechazo persistente no autoriza ni crea dispositivos y se refleja en administración', function () {
    [$admin, $user, $r, $s, $token, $key] = solicitudYapeFixture();
    $this->withToken($token);
    solicitudYapeStatus($this, $key)->assertOk();
    solicitudYapeDecision($this, $s, $r, $key, false)->assertOk();
    solicitudYapeDecision($this, $s, $r, $key, false)->assertOk();
    solicitudYapeStatus($this, $key, true, true)->assertJsonPath('authorized', false)->assertJsonCount(0, 'requests');
    $this->postJson('/api/mobile/yape/activate', ['installation' => $key])->assertForbidden();
    expect($s->actual($r->id)->status)->toBe('rejected')->and(DB::table('payment_bridge_devices')->count())->toBe(0);
    $this->actingAs($admin); Livewire::test(ReceptoresYape::class)->assertSee('Rechazado');
    Http::assertNothingSent();
});

test('otra sesión no decide ni transmite por el responsable', function () {
    [$admin, $user, $r, $s, $token, $key] = solicitudYapeFixture();
    $this->withToken($token); solicitudYapeStatus($this, $key)->assertOk();
    $other = User::factory()->rol(Rol::Proveedor)->create(); $otherToken = Str::random(40);
    DB::table('mobile_tokens')->insert(['user_id' => $other->id, 'token' => hash('sha256', $otherToken), 'created_at' => now(), 'updated_at' => now()]);
    $this->withToken($otherToken);
    solicitudYapeDecision($this, $s, $r, $key)->assertForbidden();
    solicitudYapeStatus($this, $key)->assertJsonCount(0, 'requests');
    $this->withHeader('X-Huata-Installation', $key)->postJson('/api/mobile/yape/evidence', [])->assertForbidden();
});

test('aceptación pertenece a una sola instalación aunque se repita desde otra', function () {
    [, , $r, $s, $token, $key] = solicitudYapeFixture(); $this->withToken($token);
    solicitudYapeStatus($this, $key); solicitudYapeDecision($this, $s, $r, $key)->assertOk();
    $other = str_repeat('b', 64); solicitudYapeStatus($this, $other);
    solicitudYapeDecision($this, $s, $r, $other)->assertUnprocessable();
    solicitudYapeStatus($this, $other, true, true)->assertJsonPath('authorized', false);
    expect(DB::table('huata_yape_installations')->where('status', 'authorized')->count())->toBe(1);
});

test('administración crea receptor con QR y cualquier usuario activo desde una única pantalla', function () {
    [$admin, $user] = solicitudYapeFixture();
    $target = User::factory()->rol(Rol::Proveedor)->create();
    $team = \App\Models\Team::principal();
    $team->members()->attach($admin, ['role' => \App\Enums\TeamRole::Owner->value]);
    $admin->switchTeam($team);
    $this->actingAs($admin);
    $this->get(route('admin.receptores-yape', ['current_team' => $team->slug]))->assertOk()->assertSee('Receptores Yape')->assertSee('Crear receptor');
    Livewire::test(ReceptoresYape::class)->set('nombre', 'Otra cuenta')->set('numero', '988888888')->set('responsable', (string) $target->id)
        ->set('qr', UploadedFile::fake()->image('qr.png', 200, 200))->call('guardar')->assertHasNoErrors()->assertSee('Pendiente de aceptación');
    expect(ReceptorPago::count())->toBe(2)->and(DB::table('yape_reception_requests')->where('responsible_id', $target->id)->sole()->status)->toBe('pending');
    expect($target->puedePantalla('vendedor.ventas'))->toBeFalse();
    $this->actingAs($user); Livewire::test(ReceptoresYape::class)->assertForbidden();
});

test('reutiliza cuenta compatible sin duplicar y rechaza número de identidad incompatible', function () {
    [$admin, $user, $r, $s] = solicitudYapeFixture();
    $again = $s->guardar($admin, null, $r->nombre_receptor, '999999999', $user->id, null, null);
    expect($again->id)->toBe($r->id)->and(ReceptorPago::count())->toBe(1)->and(DB::table('yape_reception_requests')->count())->toBe(1);
    expect(fn () => $s->guardar($admin, null, 'Otro titular', '999999999', $user->id, null, null))->toThrow(\Illuminate\Validation\ValidationException::class);
});

test('cambiar responsable o identidad conserva historia y bloquea verificación y autorización anteriores', function (bool $identity) {
    [$admin, $user, $r, $s, $token, $key] = solicitudYapeFixture(); $this->withToken($token);
    solicitudYapeStatus($this, $key); solicitudYapeDecision($this, $s, $r, $key)->assertOk();
    $old = $s->actual($r->id); $r->update(['verified_at' => now(), 'enabled' => true]);
    $next = User::factory()->rol(Rol::Proveedor)->create();
    $s->guardar($admin, $r->id, $identity ? 'Titular revisado' : $r->nombre_receptor, $r->celular, $identity ? $user->id : $next->id, null, null);
    expect(DB::table('yape_reception_requests')->where('id', $old->id)->value('status'))->toBe('revoked');
    expect($s->actual($r->id)->status)->toBe('pending')->and($r->fresh()->enabled)->toBeFalse()->and(DB::table('yape_reception_requests')->count())->toBe(2);
    if ($identity) expect($r->fresh()->verified_at)->toBeNull();
    expect($r->fresh()->user_id)->toBe($user->id); // Custodia anterior conservada.
    solicitudYapeStatus($this, $key, true, true)->assertJsonPath('authorized', false);
})->with([false, true]);

test('vendedores comparten receptor pendiente por asignación administrativa sin modificar historial', function () {
    [$admin, , $r, $s] = solicitudYapeFixture();
    $one = User::factory()->rol(Rol::Vendedor)->create(); $two = User::factory()->rol(Rol::Vendedor)->create();
    $s->asignar($admin, $one, $r); $s->asignar($admin, $two, $r);
    expect(app(AsignacionesReceptor::class)->receptor($one)->id)->toBe($r->id)->and(app(AsignacionesReceptor::class)->receptor($two)->id)->toBe($r->id);
    expect($r->fresh()->enabled)->toBeFalse()->and(DB::table('intentos_pago')->count())->toBe(0)->and(DB::table('movimientos_financieros')->count())->toBe(0);
    expect(fn () => app(AsignacionesReceptor::class)->seleccionar($one, 'personal', null))->toThrow(\Symfony\Component\HttpKernel\Exception\HttpException::class);
    $this->actingAs($one); Livewire::test('pages::vendedor.configuracion-pagos')->assertForbidden();
    expect(collect($one->navegacionAgrupada())->flatten(1)->pluck('route')->all())->not->toContain('vendedor.configuracion-pagos');
});

test('administrador puede escuchar el receptor central sin vender y sin confirmar pagos por aceptación', function () {
    [$admin, , $r, $s] = solicitudYapeFixture();
    $s->guardar($admin, $r->id, $r->nombre_receptor, $r->celular, $admin->id, null, null);
    expect($s->actual($r->id)->responsible_id)->toBe($admin->id)->and($admin->puedeVender('vendedor.ventas'))->toBeFalse()->and($r->fresh()->enabled)->toBeFalse();
});

test('ser responsable de recepción no asigna implícitamente ese receptor a sus propias ventas', function () {
    [$admin, , , $s] = solicitudYapeFixture();
    $seller = User::factory()->rol(Rol::Vendedor)->create();
    $r = $s->guardar($admin, null, 'Receptor del responsable vendedor', '944444444', $seller->id, 'qr/solicitud.png', hash('sha256', 'qr-solicitud'));
    expect(app(AsignacionesReceptor::class)->receptor($seller))->toBeNull();
    $s->asignar($admin, $seller, $r);
    expect(app(AsignacionesReceptor::class)->receptor($seller)->id)->toBe($r->id);
});

test('revocación impide transmisiones nuevas y evidencia sin código solo queda en revisión', function () {
    [$admin, , $r, $s, $token, $key] = solicitudYapeFixture(); $this->withToken($token)->withHeader('X-Huata-Installation', $key);
    solicitudYapeStatus($this, $key); solicitudYapeDecision($this, $s, $r, $key)->assertOk(); solicitudYapeStatus($this, $key, true, true);
    $this->postJson('/api/mobile/yape/activate', ['installation' => $key])->assertOk();
    $event = ['receiverId' => $r->referencia_externa, 'bridgeEventId' => 'huata_event_000000000001', 'packageName' => 'com.bcp.innovacxion.yapeapp', 'postedAt' => now()->toIso8601String(), 'title' => 'Yape', 'text' => 'Recibiste S/ 1.00', 'securityCode' => null];
    $this->postJson('/api/mobile/yape/evidence', $event)->assertStatus(202)->assertJsonPath('reviewRequired', true);
    $this->postJson('/api/mobile/yape/evidence', $event)->assertStatus(202);
    expect(DB::table('movimientos_financieros')->count())->toBe(0)->and(DB::table('movimientos_saldo')->count())->toBe(0);
    $s->revocar($admin, $r);
    $this->postJson('/api/mobile/yape/evidence', $event)->assertForbidden();
    $this->postJson('/api/mobile/yape/heartbeat', ['listenerConnected' => true])->assertForbidden();
    expect(DB::table('payment_bridge_devices')->count())->toBe(1)->and($r->fresh()->enabled)->toBeFalse();
});

test('backend no permite aprobar por administración una solicitud todavía no aceptada', function () {
    [$admin, , $r, , $token, $key] = solicitudYapeFixture(); $this->withToken($token); solicitudYapeStatus($this, $key);
    $id = DB::table('huata_yape_installations')->sole()->id;
    expect(fn () => app(HuataYape::class)->approve($admin, $id, $r->id, 'shared', false))->toThrow(\Illuminate\Validation\ValidationException::class);
});

test('transferencia después de revocar reutiliza dispositivo y conserva decisiones y credencial histórica', function () {
    [$admin, , $r, $s, $token, $key] = solicitudYapeFixture(); $this->withToken($token);
    solicitudYapeStatus($this, $key); solicitudYapeDecision($this, $s, $r, $key)->assertOk(); solicitudYapeStatus($this, $key, true, true);
    $this->postJson('/api/mobile/yape/activate', ['installation' => $key])->assertOk();
    $device = DB::table('payment_bridge_devices')->sole();
    $s->revocar($admin, $r);
    $s->guardar($admin, $r->id, $r->nombre_receptor, $r->celular, DB::table('yape_reception_requests')->sole()->responsible_id, null, null, true);
    $key = str_repeat('b', 64); // Otro teléfono: exige transferencia y no habilita dos listeners.
    solicitudYapeStatus($this, $key); solicitudYapeDecision($this, $s, $r, $key)->assertOk(); solicitudYapeStatus($this, $key, true, true);
    $this->postJson('/api/mobile/yape/activate', ['installation' => $key])->assertUnprocessable();
    expect(DB::table('payment_bridge_devices')->sole()->secret_hash)->toBe($device->secret_hash);
    $this->actingAs($admin);
    Livewire::test(ReceptoresYape::class)->set('confirmacionTransferencia.'.$r->id, true)->call('transferir', $r->id)->assertHasNoErrors();
    $this->postJson('/api/mobile/yape/activate', ['installation' => $key])->assertOk();
    expect(DB::table('payment_bridge_devices')->sole()->id)->toBe($device->id)->and(DB::table('payment_bridge_devices')->sole()->secret_hash)->not->toBe($device->secret_hash);
    expect(DB::table('yape_reception_requests')->count())->toBe(2)->and(DB::table('payment_authorization_events')->where('action', 'installation_binding_replaced')->count())->toBe(2);
});

test('gestor con permiso administrativo de receptores no necesita configuración comercial heredada', function () {
    [$admin, $user, $r, $s, $token, $key] = solicitudYapeFixture();
    $role = RolDinamico::create(['codigo' => 'gestor_receptores', 'nombre' => 'Gestor de receptores', 'activo' => true, 'pantallas' => ['admin.receptores-yape']]);
    $manager = User::factory()->rol(Rol::Proveedor)->create(); $manager->rolesDinamicos()->sync([$role->id]);
    $s->guardar($manager, $r->id, $r->nombre_receptor, $r->celular, $user->id, null, null, true);
    $this->withToken($token); solicitudYapeStatus($this, $key); solicitudYapeDecision($this, $s, $r, $key)->assertOk(); solicitudYapeStatus($this, $key, true, true);
    $this->postJson('/api/mobile/yape/activate', ['installation' => $key])->assertOk();
    expect($manager->puedePantalla('vendedor.configuracion-pagos'))->toBeFalse()->and($manager->puedeVender('vendedor.ventas'))->toBeFalse();
});

test('vendedor no pausa ni reactiva la recepción web y administración resuelve pausas históricas', function () {
    [$admin] = solicitudYapeFixture(); $seller = User::factory()->rol(Rol::Vendedor)->create(['pedidos_web_autorizado' => false, 'pedidos_web_pausado' => true]);
    expect(fn () => app(VendedoresWeb::class)->cambiar($seller, $seller, false, false))->toThrow(\Symfony\Component\HttpKernel\Exception\HttpException::class);
    app(VendedoresWeb::class)->cambiar($admin, $seller, true, true);
    expect($seller->fresh()->pedidos_web_autorizado)->toBeTrue()->and($seller->fresh()->pedidos_web_pausado)->toBeFalse();
    $this->actingAs($seller); Livewire::test(\App\Livewire\DisponibilidadWeb::class)->assertDontSee('Pausar recepción web')->assertDontSee('Activar recepción web');
});

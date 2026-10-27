<?php

use App\Enums\Rol;
use App\Models\ReceptorPago;
use App\Models\User;
use App\Services\Payments\ReceptoresService;
use Illuminate\Support\Facades\Storage;
use Illuminate\Validation\ValidationException;
use Livewire\Livewire;
use Tests\Support\ReceiverCoreFixture;

function receptorAdminFixture(): array
{
    Storage::fake('public');
    Storage::disk('public')->put('pagos/receptor.png', 'fixture-image');
    config(['payments.enabled' => true, 'payments.url' => 'http://127.0.0.1:8087', 'payments.key' => str_repeat('x', 40), 'payments.allow_local_http' => true]);
    $admin = User::factory()->rol(Rol::Administrador)->create(['activo' => true]);
    $seller = User::factory()->rol(Rol::Vendedor)->create(['activo' => true]);
    $r = ReceptorPago::create(['user_id' => $seller->id, 'nombre_receptor' => 'Titular', 'celular' => '51999999999', 'whatsapp' => '51999999999',
        'referencia_externa' => 'r1', 'qr_path' => 'pagos/receptor.png', 'qr_sha256' => hash('sha256', 'fixture-image'), 'enabled' => false]);

    return [$admin, $seller, $r->refresh(), new ReceiverCoreFixture($r)];
}

test('administrador verifica por primera vez sin exigir habilitación previa y después habilita', function () {
    [$admin, $seller, $r, $core] = receptorAdminFixture();
    $this->actingAs($admin);
    $page = Livewire::withQueryParams(['vendedor' => $seller->id])->test('pages::vendedor.configuracion-pagos');
    $page->call('consultarConexion')->assertSet('operatividad', fn ($text) => str_contains($text, 'pendiente de verificación física'));
    $page->set('confirmacionFisica', true)->call('verificar')->assertHasNoErrors();
    expect($r->fresh()->verified_by)->toBe($admin->id)->and($r->fresh()->enabled)->toBeFalse()->and($core->state['verified'])->toBeTrue();
    $page->call('alternar');
    expect($r->fresh()->enabled)->toBeTrue()->and($core->state['enabled'])->toBeTrue();
    $page->call('alternar');
    expect($r->fresh()->enabled)->toBeFalse()->and($core->state['enabled'])->toBeFalse();
});

test('confirmación física es explícita y un heartbeat no la sustituye', function () {
    [$admin, , $r, $core] = receptorAdminFixture();
    expect(fn () => app(ReceptoresService::class)->cambiar($admin, $r, 'verify'))->toThrow(ValidationException::class);
    expect(fn () => app(ReceptoresService::class)->cambiar($admin, $r->fresh(), 'enable'))->toThrow(ValidationException::class);
    expect($core->mutations)->toBe(0)->and($r->fresh()->enabled)->toBeFalse()->and($r->fresh()->verified_at)->toBeNull();
});

test('vendedor no verifica ni activa con peticiones manipuladas', function () {
    [, $seller, , $core] = receptorAdminFixture();
    $this->actingAs($seller);
    foreach (['verificar', 'alternar', 'desactivar'] as $action) {
        Livewire::test('pages::vendedor.configuracion-pagos')->set('confirmacionFisica', true)->call($action)->assertForbidden();
    }
    expect($core->mutations)->toBe(0);
});

test('diagnóstico distingue identidad QR registro bridge y verificación', function (string $field, mixed $value, string $message) {
    [, , $r, $core] = receptorAdminFixture();
    $core->state[$field] = $value;
    expect(app(ReceptoresService::class)->diagnostico($r))->toContain($message);
})->with([
    ['id', 'other', 'no registrado'], ['name', 'Otro', 'Nombre del receptor no coincide'],
    ['phone', '988888888', 'Número del receptor no coincide'], ['qrSha256', str_repeat('b', 64), 'Huella QR no coincide'],
    ['online', false, 'Bridge no comunicado'], ['deviceLinked', false, 'No hay dispositivo asociado'], ['verified', false, 'pendiente de verificación física'],
]);

test('datos técnicos incorrectos bloquean verificación inicial y no llaman administración', function (string $field, mixed $value) {
    [$admin, , $r, $core] = receptorAdminFixture();
    $core->state[$field] = $value;
    expect(fn () => app(ReceptoresService::class)->cambiar($admin, $r, 'verify', true))->toThrow(ValidationException::class);
    expect($core->mutations)->toBe(0)->and($r->fresh()->enabled)->toBeFalse()->and($r->fresh()->verified_at)->toBeNull();
})->with([['name', 'Otro'], ['phone', '988888888'], ['qrSha256', str_repeat('b', 64)], ['id', 'other']]);

test('QR inexistente o archivo alterado bloquean sin mutaciones remotas', function (bool $missing) {
    [$admin, , $r, $core] = receptorAdminFixture();
    if ($missing) {
        Storage::disk('public')->delete($r->qr_path);
    } else {
        Storage::disk('public')->put($r->qr_path, 'altered');
    }
    expect(fn () => app(ReceptoresService::class)->cambiar($admin, $r, 'verify', true))->toThrow(ValidationException::class);
    expect($core->mutations)->toBe(0)->and($r->fresh()->enabled)->toBeFalse();
})->with([true, false]);

test('verificar offline es válido pero activar exige bridge online', function () {
    [$admin, , $r, $core] = receptorAdminFixture();
    $core->state['online'] = false;
    app(ReceptoresService::class)->cambiar($admin, $r, 'verify', true);
    expect($r->fresh()->verified_at)->not->toBeNull();
    expect(fn () => app(ReceptoresService::class)->cambiar($admin, $r->fresh(), 'enable'))->toThrow(ValidationException::class);
    expect($r->fresh()->enabled)->toBeFalse()->and($core->state['enabled'])->toBeFalse();
});

test('Core no disponible o sin alcance administrativo conserva bloqueo local', function (bool $denied) {
    [$admin, , $r, $core] = receptorAdminFixture();
    $r->update(['enabled' => true, 'verified_at' => now()]);
    $core->denyAdmin = $denied;
    $core->unavailable = ! $denied;
    expect(fn () => app(ReceptoresService::class)->cambiar($admin, $r, 'verify', true))->toThrow(ValidationException::class);
    expect($r->fresh()->enabled)->toBeFalse()->and($r->fresh()->verified_at)->toBeNull();
})->with([true, false]);

test('fallo posterior al commit remoto nunca habilita localmente y permite reconciliar', function () {
    [$admin, , $r, $core] = receptorAdminFixture();
    app(ReceptoresService::class)->cambiar($admin, $r, 'verify', true);
    $core->failAfterMutation = true;
    // Fail only the post-write read, not the preflight read.
    $core->mutations = 0;
    expect(fn () => app(ReceptoresService::class)->cambiar($admin, $r->fresh(), 'enable'))->toThrow(ValidationException::class);
    expect($core->state['enabled'])->toBeTrue()->and($r->fresh()->enabled)->toBeFalse();
    $core->failAfterMutation = false;
    app(ReceptoresService::class)->cambiar($admin, $r->fresh(), 'disable');
    expect($r->fresh()->enabled)->toBeFalse()->and($core->state['enabled'])->toBeFalse();
});

test('teléfono nacional y prefijo 51 coinciden sin modificar titularidad', function () {
    [$admin, , $r, $core] = receptorAdminFixture();
    $core->state['phone'] = '999999999';
    app(ReceptoresService::class)->cambiar($admin, $r, 'verify', true);
    expect($r->fresh()->verified_by)->toBe($admin->id);
});

test('configuración concurrente no hereda una verificación de QR anterior', function () {
    [$admin, , $r, $core] = receptorAdminFixture();
    $r->fresh()->update(['version' => $r->version + 1]);
    expect(fn () => app(ReceptoresService::class)->cambiar($admin, $r, 'verify', true))->toThrow(ValidationException::class);
    expect($core->mutations)->toBe(0)->and($r->fresh()->verified_at)->toBeNull();
});

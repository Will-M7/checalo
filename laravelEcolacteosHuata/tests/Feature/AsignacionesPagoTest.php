<?php

use App\Enums\EstadoProduccion;
use App\Enums\Rol;
use App\Models\ClienteWeb;
use App\Models\IntentoPago;
use App\Models\PedidoWeb;
use App\Models\Produccion;
use App\Models\ReceptorPago;
use App\Models\Receta;
use App\Models\User;
use App\Services\Payments\AsignacionesReceptor;
use App\Services\Payments\CobrosService;
use Illuminate\Support\Facades\Storage;
use Illuminate\Support\Str;
use Illuminate\Validation\ValidationException;
use Symfony\Component\HttpKernel\Exception\HttpException;
use Tests\Support\ReceiverCoreFixture;

function assignedPaymentFixture(): array
{
    config(['payments.enabled' => true, 'payments.url' => 'http://127.0.0.1:8087', 'payments.key' => str_repeat('x', 40), 'payments.allow_local_http' => true]);
    Storage::fake('public');
    Storage::disk('public')->put('qr/central.png', 'fixture-image');
    $admin = User::factory()->rol(Rol::Administrador)->create(['activo' => true]);
    $owner = User::factory()->rol(Rol::Vendedor)->create(['activo' => true]);
    $seller = User::factory()->rol(Rol::Vendedor)->create(['activo' => true]);
    $r = ReceptorPago::create(['user_id' => $owner->id, 'nombre_receptor' => 'Central', 'celular' => '51999999999', 'whatsapp' => '51999999999', 'referencia_externa' => 'r1',
        'qr_path' => 'qr/central.png', 'qr_sha256' => hash('sha256', 'fixture-image'), 'enabled' => true, 'verified_at' => now()]);
    $core = new ReceiverCoreFixture($r);
    $core->state['enabled'] = true;
    $core->state['verified'] = true;
    $policy = app(AsignacionesReceptor::class);
    $policy->central($admin, $r->id, true);
    $policy->asignar($admin, $seller, 'central', null, false, true);
    $p = Produccion::factory()->create(['receta_id' => Receta::factory()->create(['precio_venta' => '7.00'])->id, 'cantidad_producida' => 10, 'cantidad_vendida' => 0, 'estado' => EstadoProduccion::Completada]);
    $client = ClienteWeb::create(['nombres' => 'Cliente', 'apellidos' => 'Pendiente', 'celular' => '51977777777', 'email' => 'pending@example.test', 'password' => 'TestPassword-123']);

    return [$admin, $owner, $seller, $r, $p, $client, $core];
}

test('cuenta central y compartida conservan vendedor real sin duplicar receptor', function () {
    [$admin, $owner, $seller, $r, $p] = assignedPaymentFixture();
    $service = app(CobrosService::class);
    $a = $service->presencial($seller, [['produccion_id' => $p->id, 'cantidad' => 1]], [], [], (string) Str::uuid());
    expect($a->user_id)->toBe($seller->id)->and($a->pedido->vendedor_asignado_id)->toBe($seller->id)
        ->and($a->receptor_snapshot['vendedor_id'])->toBe($seller->id)->and($a->receptor_pago_id)->toBe($r->id)->and(ReceptorPago::count())->toBe(1);
    app(AsignacionesReceptor::class)->asignar($admin, $seller, 'shared', $r->id, false, true);
    expect(app(AsignacionesReceptor::class)->receptor($seller)->id)->toBe($r->id)->and($a->fresh()->receptor_snapshot)->toBe($a->receptor_snapshot);
    app(AsignacionesReceptor::class)->asignar($admin, $seller, 'personal', null, true, false);
    expect(app(AsignacionesReceptor::class)->puedePersonal($seller))->toBeTrue()->and($a->fresh()->receptor_pago_id)->toBe($r->id);
});

test('vendedor no cambia asignación por solicitudes manipuladas ni omite autorización del responsable', function () {
    [$admin, , $seller, $r] = assignedPaymentFixture();
    expect(fn () => app(AsignacionesReceptor::class)->asignar($seller, $seller, 'shared', $r->id, true, true))->toThrow(HttpException::class);
    expect(fn () => app(AsignacionesReceptor::class)->asignar($admin, $seller, 'shared', $r->id, false, false))->toThrow(ValidationException::class);
    expect(app(AsignacionesReceptor::class)->receptor($seller)->id)->toBe($r->id);
});

test('pedido diferido persiste sin Core ni reserva de stock y conserva receptor tras reasignar', function () {
    [$admin, $owner, $seller, $r, $p, $client, $core] = assignedPaymentFixture();
    $core->unavailable = true;
    $service = app(CobrosService::class);
    $order = $service->pedido($client, [['produccion_id' => $p->id, 'cantidad' => 2]], 'yape', (string) Str::uuid(), true);
    expect(IntentoPago::count())->toBe(0)->and($p->fresh()->cantidadDisponible())->toBe(10.0)->and($order->receptor_pago_id)->toBe($r->id);
    $actualSeller = User::findOrFail($order->vendedor_asignado_id);
    app(AsignacionesReceptor::class)->asignar($admin, $actualSeller, 'personal', null, false, false);
    $core->unavailable = false;
    $i = $service->prepararPedido($order, $client);
    expect($i->receptor_snapshot)->toBe($order->receptor_snapshot)->and($i->receptor_pago_id)->toBe($r->id)->and($i->estado)->toBe('PENDING');
    expect($service->prepararPedido($order, $client)->id)->toBe($i->id)->and(IntentoPago::count())->toBe(1)->and($p->fresh()->cantidadDisponible())->toBe(10.0);
});

test('checkout conserva pedido y muestra estado bloqueado cuando Core está desconectado', function () {
    [, , , , $p, $client, $core] = assignedPaymentFixture();
    $core->unavailable = true;
    $this->actingAs($client, 'cliente')->postJson(route('pedidos-web.store'), ['items' => [['produccion_id' => $p->id, 'cantidad' => 1]], 'metodo_pago' => 'yape', 'idempotency_key' => (string) Str::uuid()])->assertCreated();
    $order = PedidoWeb::firstOrFail();
    $this->get(route('cliente.pedido', $order->id))->assertOk()->assertDontSee('alt="QR del receptor asignado"', false);
    $this->get(route('cliente.cuenta'))->assertOk()->assertSee('Pedido #'.$order->id);
    expect(IntentoPago::count())->toBe(0)->and($p->fresh()->cantidadDisponible())->toBe(10.0);
});

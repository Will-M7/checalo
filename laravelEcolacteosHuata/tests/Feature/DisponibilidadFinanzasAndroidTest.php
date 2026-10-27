<?php

use App\Authorization\{Autorizacion, Pantallas};
use App\Enums\{EstadoProduccion, Rol};
use App\Models\{ClienteWeb, IntentoPago, MovimientoFinanciero, MovimientoSaldo, PedidoWeb, Produccion, ReceptorPago, Receta, User};
use App\Services\Finanzas\{ConsultasFinanzas, FinanzasService};
use App\Services\Payments\{CobrosService, SaldoService};
use App\Services\VendedoresWeb;
use Illuminate\Support\Facades\{DB, Http, Storage};
use Illuminate\Support\Str;
use Illuminate\Validation\ValidationException;
use Livewire\Livewire;

function disponibilidadFixture(): array
{
    Http::preventStrayRequests();
    $admin = User::factory()->rol(Rol::Administrador)->create();
    $flora = User::factory()->rol(Rol::Vendedor)->create(['nombres' => 'Flora', 'celular' => '988888888']);
    $pepe = User::factory()->rol(Rol::Vendedor)->create(['nombres' => 'Pepe', 'celular' => '977777777']);
    $client = ClienteWeb::create(['nombres' => 'Comprador', 'apellidos' => 'Disponible', 'celular' => '51966666666', 'email' => 'disponibilidad@example.test', 'password' => 'prueba']);
    $recipe = Receta::factory()->create(['precio_venta' => '10.00']);
    $p = Produccion::factory()->create(['receta_id' => $recipe->id, 'cantidad_producida' => 20, 'cantidad_vendida' => 0, 'estado' => EstadoProduccion::Completada]);
    return [$admin, $flora, $pepe, $client, $p];
}
function disponiblePedido(ClienteWeb $c, Produccion $p): PedidoWeb
{
    return app(CobrosService::class)->pedido($c, [['produccion_id' => $p->id, 'cantidad' => 1]], 'pendiente', (string) Str::uuid());
}
function disponibleToken(User $u): string
{
    $token = Str::random(40);
    DB::table('mobile_tokens')->insert(['user_id' => $u->id, 'token' => hash('sha256', $token), 'created_at' => now(), 'updated_at' => now()]);
    return $token;
}

test('administrador receptor no se convierte en vendedor y reparto es por pedido sin receptor', function () {
    [$admin, $flora, $pepe, $c, $p] = disponibilidadFixture();
    ReceptorPago::create(['user_id' => $admin->id, 'nombre_receptor' => 'Empresa', 'celular' => '51999999999', 'whatsapp' => '51999999999']);
    $orders = collect(range(1, 4))->map(fn () => disponiblePedido($c, $p));
    expect($orders->pluck('vendedor_asignado_id')->all())->toBe([$flora->id, $pepe->id, $flora->id, $pepe->id])
        ->and($admin->puedeVender('vendedor.ventas'))->toBeFalse()->and(IntentoPago::count())->toBe(0);
    expect($orders[0]->receptor_snapshot['vendedor_whatsapp'])->toBe('51988888888');
    $this->assertDatabaseMissing('clientes_web', ['id' => $c->id, 'nombres' => 'Flora']);
    Http::assertNothingSent();
});

test('pausa y revocacion administrativa excluyen nuevos pedidos y conservan historia', function () {
    [$admin, $flora, $pepe, $c, $p] = disponibilidadFixture();
    $old = disponiblePedido($c, $p);
    $service = app(VendedoresWeb::class);
    $service->cambiar($flora, $flora, true, false);
    expect(disponiblePedido($c, $p)->vendedor_asignado_id)->toBe($pepe->id);
    $service->cambiar($admin, $flora, false, true);
    expect(fn () => $service->cambiar($flora, $flora, false, false))->toThrow(ValidationException::class);
    $service->cambiar($admin, $flora, true, true);
    expect($flora->fresh()->pedidos_web_pausado)->toBeTrue();
    $pepe->update(['activo' => false]);
    $count = PedidoWeb::count();
    expect(fn () => disponiblePedido($c, $p))->toThrow(ValidationException::class);
    expect(PedidoWeb::count())->toBe($count)->and($old->fresh()->vendedor_asignado_id)->toBe($flora->id);
    $service->cambiar($flora, $flora, false, false);
    expect(disponiblePedido($c, $p)->vendedor_asignado_id)->toBe($flora->id);
});

test('administrador con rol comercial explicito participa y rol personalizado tambien', function () {
    [$admin, $flora] = disponibilidadFixture();
    Autorizacion::asignar($admin, $admin, ['administrador', 'vendedor']);
    expect(User::disponibleWeb()->whereKey($admin->id)->exists())->toBeTrue();
    $rol = Autorizacion::guardarRol($admin, null, 'Atención web', 'atencion_web', true, ['vendedor.pedidos-web']);
    Autorizacion::asignar($admin, $flora, [$rol->codigo]);
    expect(User::disponibleWeb()->whereKey($flora->id)->exists())->toBeTrue();
});

test('reasignacion conserva intencion original receptor y auditoria sin cambiar historicos pagados', function () {
    [$admin, $flora, $pepe, $c, $p] = disponibilidadFixture();
    $order = disponiblePedido($c, $p);
    $receiver = ReceptorPago::create(['user_id' => $admin->id, 'nombre_receptor' => 'Empresa', 'celular' => '51999999999', 'whatsapp' => '51999999999']);
    $i = IntentoPago::create(['referencia' => (string) Str::uuid(), 'cliente_web_id' => $c->id, 'pedido_web_id' => $order->id, 'user_id' => $flora->id, 'receptor_pago_id' => $receiver->id, 'receptor_snapshot' => ['nombre' => 'Empresa', 'qr_sha256' => 'original'], 'amount_minor' => 1000, 'proposito' => 'compra']);
    $before = $i->fresh()->toArray();
    app(VendedoresWeb::class)->reasignar($admin, $order->id, $pepe->id, 'Vendedor anterior indisponible');
    expect($i->fresh()->toArray())->toBe($before)->and($order->fresh()->vendedor_asignado_id)->toBe($pepe->id);
    $this->assertDatabaseHas('authorization_audit', ['accion' => 'reasignar_pedido_web', 'objeto' => 'pedido:'.$order->id]);
    $i->update(['estado' => 'PAID']);
    expect(fn () => app(VendedoresWeb::class)->reasignar($admin, $order->id, $flora->id, 'No debe modificar un pago'))->toThrow(ValidationException::class);
});

test('finanzas web y API admiten rol dinamico sin administracion general y deniegan consultas ajenas', function () {
    [$admin, $flora, $pepe, $c, $p] = disponibilidadFixture();
    disponiblePedido($c, $p); disponiblePedido($c, $p);
    app(SaldoService::class)->movimiento($c, 10000, 'ajuste', 'prueba-aislada', 'Saldo aislado');
    $order = PedidoWeb::first();
    app(CobrosService::class)->pagarConSaldo($c, $order);
    $role = Autorizacion::guardarRol($admin, null, 'Finanzas test', 'finanzas_test', true, ['admin.finanzas']);
    Autorizacion::asignar($admin, $flora, [$role->codigo]);
    $token = disponibleToken($flora);
    $this->withToken($token)->getJson('/api/mobile/finanzas')->assertOk()->assertJsonPath('resumen.saldos_clientes', 9000);
    $this->getJson('/api/mobile/finanzas/clientes')->assertOk()->assertJsonPath('data.0.saldo_minor', 9000)->assertJsonPath('data.0.consumido_minor', 1000)->assertJsonPath('data.0.pedidos_total', 2)->assertJsonPath('data.0.pedidos_pagados', 1);
    $this->getJson('/api/mobile/finanzas/clientes/'.$c->id.'/pedidos')->assertOk()->assertJsonCount(2, 'data')->assertJsonPath('data.0.vendedor_id', $pepe->id)->assertJsonPath('data.1.vendedor_id', $flora->id);
    $this->getJson('/api/mobile/finanzas/clientes?busquedaCliente=inexistente')->assertJsonCount(0, 'data');
    $this->getJson('/api/mobile/finanzas/clientes?saldoCliente=cero')->assertJsonCount(0, 'data');
    expect($flora->puedeAdministracionCritica())->toBeFalse()->and(Pantallas::android()['admin.finanzas'])->toBe('AdminFinanzas');
    $this->actingAs($flora);
    Livewire::test('pages::admin.finanzas')->assertOk()->assertSee('Clientes y saldos')->set('clienteSeleccionado', $c->id)->assertSee('Flora')->assertSee('Pepe');
    $this->withToken(disponibleToken($pepe))->getJson('/api/mobile/finanzas/clientes')->assertForbidden();
    $this->getJson('/api/mobile/finanzas')->assertForbidden();
});

test('rendicion movil exige recepcion servidor e idempotencia incluso con nueva clave para misma constancia', function () {
    [$admin, $flora] = disponibilidadFixture();
    $source = MovimientoFinanciero::create(['clave' => 'fixture:ingreso', 'tipo' => 'ingreso_efectivo', 'amount_minor' => 10000, 'origen' => 'fixture', 'custodio_id' => $flora->id, 'custodia_minor' => 10000, 'recaudado_minor' => 10000, 'created_at' => now()]);
    $this->withToken(disponibleToken($admin));
    $d = ['ingreso' => $source->id, 'amount_minor' => 3000, 'clave' => (string) Str::uuid(), 'referencia' => 'Constancia prueba aislada 001', 'recibido' => false];
    $this->postJson('/api/mobile/finanzas/rendiciones', $d)->assertUnprocessable();
    $d['recibido'] = true;
    $id = $this->postJson('/api/mobile/finanzas/rendiciones', $d)->assertOk()->assertJsonPath('confirmado', true)->json('id');
    $this->postJson('/api/mobile/finanzas/rendiciones', $d)->assertOk()->assertJsonPath('id', $id);
    $d['clave'] = (string) Str::uuid();
    $this->postJson('/api/mobile/finanzas/rendiciones', $d)->assertOk()->assertJsonPath('id', $id);
    expect(MovimientoFinanciero::where('tipo', 'rendicion')->count())->toBe(1)->and($source->pendienteRendir())->toBe(7000);
});

test('usuario referenciado no se elimina por cascada y se desactiva sin error SQL ni perder pedido', function () {
    [$admin, $flora, , $c, $p] = disponibilidadFixture();
    $order = disponiblePedido($c, $p);
    $this->actingAs($admin);
    Livewire::test('pages::admin.usuarios')->set('eliminarId', $flora->id)->call('eliminar')->assertHasErrors(['usuario'])->assertSee('Desactivar usuario')->call('desactivarReferenciado')->assertHasNoErrors();
    expect($flora->fresh()->activo)->toBeFalse()->and($order->fresh()->vendedor_asignado_id)->toBe($flora->id);
});

test('API de disponibilidad no deja que vendedor habilite la autorizacion administrativa', function () {
    [$admin, $flora, $pepe] = disponibilidadFixture();
    $this->withToken(disponibleToken($admin))->postJson('/api/mobile/vendedores-web/'.$flora->id.'/autorizacion', ['autorizado' => false])->assertOk()->assertJsonPath('estado', 'inhabilitado');
    $this->withToken(disponibleToken($flora))->postJson('/api/mobile/vendedores-web/pausa', ['pausado' => false])->assertUnprocessable();
    $this->postJson('/api/mobile/vendedores-web/'.$flora->id.'/autorizacion', ['autorizado' => true])->assertForbidden();
    expect($flora->fresh()->pedidos_web_autorizado)->toBeFalse();
    $this->withToken(disponibleToken($pepe))->postJson('/api/mobile/vendedores-web/pausa', ['pausado' => true])->assertOk()->assertJsonPath('estado', 'pausado');
});

test('recarga central sin vendedores usa responsable financiero y congela receptor incluso al cambiar politica', function () {
    [$admin, $flora, $pepe, $c] = disponibilidadFixture();
    $flora->update(['activo' => false]); $pepe->update(['activo' => false]);
    Storage::fake('public');
    Storage::disk('public')->put('pagos/central.png', 'qr-aislado');
    $r = ReceptorPago::create(['user_id' => $admin->id, 'nombre_receptor' => 'Central autorizada', 'celular' => '51999999999', 'whatsapp' => '51999999999', 'qr_path' => 'pagos/central.png', 'qr_sha256' => hash('sha256', 'qr-aislado'), 'referencia_externa' => 'central-test', 'enabled' => true, 'verified_at' => now()]);
    DB::table('payment_receiver_policy')->updateOrInsert(['id' => 1], ['central_receiver_id' => $r->id, 'updated_by' => $admin->id, 'created_at' => now(), 'updated_at' => now()]);
    config(['payments.enabled' => true, 'payments.url' => 'http://127.0.0.1:8087', 'payments.key' => str_repeat('x', 40), 'payments.allow_local_http' => true]);
    Http::fake(['*/api/receivers' => Http::response([['id' => 'central-test', 'name' => $r->nombre_receptor, 'phone' => $r->celular, 'qrSha256' => $r->qr_sha256, 'enabled' => true, 'verified' => true, 'online' => true]])]);
    $key = (string) Str::uuid();
    $i = app(CobrosService::class)->recarga($c, 1000, $key);
    expect($i->receptor_pago_id)->toBe($r->id)->and($i->receptor_snapshot['vendedor_id'])->toBeNull()->and(User::disponibleWeb()->count())->toBe(0);
    DB::table('payment_receiver_policy')->where('id', 1)->update(['central_receiver_id' => null]);
    expect(app(CobrosService::class)->recarga($c, 1000, $key)->id)->toBe($i->id)->and(IntentoPago::count())->toBe(1);
});

test('solo usuario sin referencias admite eliminacion fisica y vendedor pausado conserva sus pedidos', function () {
    [$admin, $flora, $pepe, $c, $p] = disponibilidadFixture();
    $one = disponiblePedido($c, $p); $two = disponiblePedido($c, $p);
    app(VendedoresWeb::class)->cambiar($flora, $flora, true, false);
    $this->actingAs($flora);
    expect(fn () => Livewire::test('pages::vendedor.pedidos-web')->assertOk()->call('pedirConfirmar', $two->id))->toThrow(\Illuminate\Database\Eloquent\ModelNotFoundException::class);
    Livewire::test('pages::vendedor.pedidos-web')->assertOk()->assertSet('filtro', 'pendiente');
    expect(Autorizacion::snapshot($admin)['capacidades_comerciales'])->toBe([]);
    expect(Autorizacion::snapshot($flora)['capacidades_comerciales'])->toContain('vendedor.pedidos-web');
    $empty = User::create(['name' => 'Sin referencias', 'nombres' => 'Prueba', 'apellidos' => 'Aislada', 'documento' => '00007777', 'usuario' => 'sin_referencias', 'email' => 'sinref@example.test', 'password' => 'password', 'activo' => true, 'rol' => null, 'roles' => []]);
    $this->actingAs($admin);
    Livewire::test('pages::admin.usuarios')->set('eliminarId', $empty->id)->call('eliminar')->assertHasNoErrors();
    $this->assertDatabaseMissing('users', ['id' => $empty->id]);
    expect($one->fresh()->vendedor_asignado_id)->toBe($flora->id);
});

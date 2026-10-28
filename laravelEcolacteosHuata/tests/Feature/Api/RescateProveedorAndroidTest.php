<?php

use App\Enums\Rol;
use App\Models\{User, Proveedor, Recojo, ResultadoLaboratorio, Comunicado};
use Illuminate\Support\Facades\{Hash, Http};

test('proveedor descarga su perfil entregas laboratorio y comunicados con token movil', function () {
    Http::preventStrayRequests();
    $this->travelTo(now()->setDate(2026, 10, 27)->setTime(10, 0));
    $user = User::factory()->rol(Rol::Proveedor)->create(['usuario' => 'proveedor_rescate', 'nombres' => 'Proveedor', 'apellidos' => 'Rescate', 'password' => Hash::make('solo-prueba')]);
    $p = Proveedor::factory()->create(['user_id' => $user->id]);
    Recojo::factory()->create(['proveedor_id' => $p->id, 'acopiador_id' => $p->acopiador_id, 'litros' => 12, 'litros_entregados' => 12, 'total' => 21.60]);
    ResultadoLaboratorio::factory()->create(['proveedor_id' => $p->id]);
    Comunicado::factory()->create(['roles' => ['proveedor'], 'titulo' => 'Aviso de prueba']);
    Proveedor::factory()->create();
    $this->getJson('/api/mobile/sync')->assertUnauthorized();
    $login = $this->postJson('/api/mobile/login', ['usuario' => $user->usuario, 'password' => 'solo-prueba'])->assertOk();
    $this->withToken($login->json('token'));
    $auth = $this->getJson('/api/mobile/autorizaciones')->assertOk();
    $sync = $this->getJson('/api/mobile/sync')->assertOk()->assertJsonCount(1, 'proveedores')->assertJsonPath('proveedores.0.usuario_id', (string) $user->id)->assertJsonCount(1, 'recojos')->assertJsonCount(1, 'resultados_laboratorio')->assertJsonCount(1, 'comunicados');
    expect($auth->json('pantallas'))->toContain('proveedor.panel', 'proveedor.entregas', 'proveedor.laboratorio', 'proveedor.comunicados');
    $data = $sync->json();
    foreach ($data['usuarios'] as &$u) { $u['password_hash'] = ''; }
    unset($u);
    if ($path = getenv('HUATA_EXPORT_PROVEEDOR_FIXTURE')) {
        file_put_contents($path, json_encode($data, JSON_PRETTY_PRINT|JSON_UNESCAPED_UNICODE));
    }
    Http::assertNothingSent();
});

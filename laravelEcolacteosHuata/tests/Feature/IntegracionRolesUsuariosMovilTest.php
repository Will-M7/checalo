<?php

use App\Authorization\Autorizacion;
use App\Enums\Rol;
use App\Models\RolDinamico;
use App\Models\User;
use Illuminate\Support\Facades\DB;
use Livewire\Livewire;

function tokenAdministradorRoles(User $admin): string
{
    $token = 'token-administrativo-'.$admin->id;
    DB::table('mobile_tokens')->insert(['user_id' => $admin->id, 'token' => hash('sha256', $token), 'created_at' => now(), 'updated_at' => now()]);

    return $token;
}

test('supervisor_prueba se crea en Usuarios sin categoría inventada y se administra mediante la API nativa', function () {
    expect(DB::connection()->getDatabaseName())->toBe(':memory:');
    $admin = User::factory()->rol(Rol::Administrador)->create();
    $this->actingAs($admin);
    Livewire::test('pages::admin.roles')->call('editar')->set('codigo', 'supervisor_prueba')->set('nombre', 'Supervisor de prueba')
        ->set('pantallas', ['vendedor.clientes', 'produccion.unidades'])->call('guardar')->assertHasNoErrors();
    $rol = RolDinamico::where('codigo', 'supervisor_prueba')->firstOrFail();
    Livewire::test('pages::admin.usuarios')->call('nuevo')->assertSee('Supervisor de prueba')
        ->set('nombres', 'Supervisor')->set('apellidos', 'Prueba')->set('documento', '11223344')
        ->set('usuario', 'supervisor_prueba')->set('password', 'secreto123')->set('rolesSeleccionados', ['supervisor_prueba'])
        ->call('guardar')->assertHasNoErrors();
    $user = User::where('usuario', 'supervisor_prueba')->firstOrFail();
    expect($user->rol)->toBeNull()->and($user->roles)->toBe([])->and($user->rolesDinamicos()->pluck('codigo')->all())->toBe(['supervisor_prueba']);
    expect($user->currentTeam)->not->toBeNull();
    expect($user->teams()->whereKey($user->current_team_id)->exists())->toBeTrue();
    auth()->logout();
    $this->post('/login', ['usuario' => 'supervisor_prueba', 'password' => 'secreto123'])->assertRedirect();
    $this->assertAuthenticatedAs($user);
    $team = $user->currentTeam;
    $this->get(route('vendedor.clientes', ['current_team' => $team->slug]))->assertOk();
    $this->get(route('produccion.unidades', ['current_team' => $team->slug]))->assertOk();
    $this->get(route('admin.usuarios', ['current_team' => $team->slug]))->assertForbidden();
    Livewire::test('pages::vendedor.clientes')->set('nombres', 'Cliente supervisor')->set('documento', '55667788')->call('guardar')->assertHasNoErrors();
    Livewire::test('pages::produccion.unidades')->set('unidadNombre', 'Unidad supervisor')->set('unidadSimbolo', 'usp')->call('guardarUnidad')->assertHasNoErrors();
    $this->assertDatabaseHas('clientes', ['nombres' => 'Cliente supervisor']);
    $this->assertDatabaseHas('unidades_medida', ['simbolo' => 'usp']);
    $login = $this->postJson('/api/mobile/login', ['usuario' => $user->usuario, 'password' => 'secreto123'])->assertOk()->assertJsonPath('rol', null)->assertJsonPath('autorizacion.categorias_historicas', []);
    file_put_contents('/tmp/huata-supervisor-login.json', json_encode($login->json()));
    $token = $login->json('token');
    $this->withToken($token)->getJson('/api/mobile/autorizaciones/roles')->assertForbidden();
    $this->withToken($token)->postJson('/api/mobile/autorizaciones/roles', ['nombre' => 'Escalada', 'codigo' => 'escalada', 'activo' => true, 'pantallas' => ['admin.roles']])->assertForbidden();
    $this->withToken($token)->postJson('/api/mobile/autorizaciones/asignar', ['usuario_id' => $user->id, 'roles' => ['administrador']])->assertForbidden();
    $this->withToken($token)->postJson('/api/mobile/autorizaciones/usuarios', [])->assertForbidden();
    $adminToken = tokenAdministradorRoles($admin);
    $catalogo = $this->withToken($adminToken)->getJson('/api/mobile/autorizaciones/roles')->assertOk()->assertJsonFragment(['admin.roles' => 'AdminRoles'])->json();
    file_put_contents('/tmp/huata-supervisor-catalogo.json', json_encode($catalogo));
    $datos = ['nombre' => $rol->nombre, 'codigo' => $rol->codigo, 'activo' => true, 'pantallas' => ['vendedor.clientes']];
    file_put_contents('/tmp/huata-supervisor-modificar.json', json_encode($datos));
    $this->withToken($adminToken)->putJson('/api/mobile/autorizaciones/roles/'.$rol->id, $datos)->assertOk();
    $nuevo = $this->withToken($token)->getJson('/api/mobile/autorizaciones')->assertOk()->assertJsonPath('roles.0.id', 'supervisor_prueba')->assertJsonPath('pantallas', ['vendedor.clientes'])->json();
    file_put_contents('/tmp/huata-supervisor-revocado.json', json_encode($nuevo));
    $this->withHeader('X-Huata-Authorization-Protocol', '1')->withToken($token)->postJson('/api/mobile/cambios', ['cambios' => [
        ['tabla' => 'unidad_medida', 'id' => 'pendiente-supervisor', 'operacion' => 'guardar', 'datos' => ['nombre' => 'Trabajo offline supervisor', 'simbolo' => 'os']],
        ['tabla' => 'cliente', 'id' => 'cliente-supervisor', 'operacion' => 'guardar', 'datos' => ['nombres' => 'Sigue autorizado', 'documento' => '22334455']],
    ]])->assertOk()->assertJsonPath('errores.0.code', 'AUTHORIZATION_REVOKED')->assertJsonPath('ids.0.id_local', 'cliente-supervisor');
    $this->assertDatabaseMissing('unidades_medida', ['nombre' => 'Trabajo offline supervisor']);
    $this->assertDatabaseHas('clientes', ['nombres' => 'Sigue autorizado']);
    $this->get(route('produccion.unidades', ['current_team' => $team->slug]))->assertForbidden();
    $this->get(route('vendedor.clientes', ['current_team' => $team->slug]))->assertOk();
});

test('usuarios y asignación comparten catálogo y guardan varios roles sin perder equipos', function () {
    $admin = User::factory()->rol(Rol::Administrador)->create();
    $this->actingAs($admin);
    $a = Autorizacion::guardarRol($admin, null, 'Ventas', 'ventas', true, ['vendedor.clientes']);
    $b = Autorizacion::guardarRol($admin, null, 'Producción propia', 'produccion_propia', true, ['produccion.unidades']);
    $off = Autorizacion::guardarRol($admin, null, 'Desactivado', 'desactivado', false, []);
    $form = Livewire::test('pages::admin.usuarios')->call('nuevo')->assertSee('Ventas')->assertDontSee('Desactivado');
    $form->set('nombres', 'Múltiples')->set('apellidos', 'Roles')->set('documento', '66778899')->set('usuario', 'multiple')->set('password', 'secreto')->set('rolesSeleccionados', [$a->codigo, $b->codigo])->call('guardar')->assertHasNoErrors();
    $user = User::where('usuario', 'multiple')->firstOrFail();
    $teams = $user->teams()->pluck('teams.id')->all();
    Livewire::test('pages::admin.usuarios')->call('editar', $user->id)->assertSet('rolesSeleccionados', fn ($v) => collect($v)->sort()->values()->all() === collect([$a->codigo, $b->codigo])->sort()->values()->all())->set('rolesSeleccionados', [$b->codigo])->call('guardar')->assertHasNoErrors();
    Livewire::test('pages::admin.asignar-roles')->call('abrirAsignar', $user->id)->assertSee('Ventas')->assertSet('rolesSeleccionados', [$b->codigo])->set('rolesSeleccionados', [$a->codigo])->call('guardar')->assertHasNoErrors();
    expect($user->rolesDinamicos()->pluck('codigo')->all())->toBe([$a->codigo])->and($user->teams()->pluck('teams.id')->all())->toBe($teams);
    Livewire::test('pages::admin.usuarios')->call('editar', $user->id)->set('rolesSeleccionados', [$off->codigo])->call('guardar')->assertHasErrors('rolesSeleccionados');
    expect($user->rolesDinamicos()->pluck('codigo')->all())->toBe([$a->codigo]);
    Livewire::test('pages::admin.usuarios')->call('editar', $admin->id)->set('rolesSeleccionados', [$a->codigo])->call('guardar')->assertHasErrors('rolesSeleccionados');
    expect($admin->puedePantalla('admin.roles'))->toBeTrue();
});

test('API administrativa valida catálogo, roles protegidos y usuarios sin conceder privilegios por sync', function () {
    $admin = User::factory()->rol(Rol::Administrador)->create();
    $token = tokenAdministradorRoles($admin);
    $this->withToken($token)->postJson('/api/mobile/autorizaciones/roles', ['nombre' => 'Nuevo desde APK', 'codigo' => 'apk_nuevo', 'activo' => true, 'pantallas' => ['vendedor.clientes', 'produccion.unidades']])->assertOk();
    $rol = RolDinamico::where('codigo', 'apk_nuevo')->firstOrFail();
    $payload = ['nombres' => 'Cuenta', 'apellidos' => 'APK', 'documento' => '34567890', 'celular' => '', 'usuario' => 'cuenta_apk', 'password' => 'secreto', 'activo' => true, 'rolesSeleccionados' => ['apk_nuevo']];
    $user = $this->postJson('/api/mobile/autorizaciones/usuarios', $payload)->assertOk()->json();
    expect(User::find($user['id'])->rol)->toBeNull();
    $this->putJson('/api/mobile/autorizaciones/usuarios/'.$user['id'], [...$payload, 'password' => '', 'rolesSeleccionados' => []])->assertOk()->assertJsonPath('autorizacion.pantallas', []);
    $this->putJson('/api/mobile/autorizaciones/roles/'.$rol->id, ['nombre' => $rol->nombre, 'codigo' => $rol->codigo, 'activo' => false, 'pantallas' => []])->assertOk();
    $this->putJson('/api/mobile/autorizaciones/usuarios/'.$user['id'], $payload)->assertUnprocessable();
    $this->postJson('/api/mobile/autorizaciones/roles', ['nombre' => 'Falso', 'codigo' => 'falso', 'activo' => true, 'pantallas' => ['endpoint_inventado']])->assertUnprocessable();
    $protegido = RolDinamico::where('codigo', 'administrador')->firstOrFail();
    $this->putJson('/api/mobile/autorizaciones/roles/'.$protegido->id, ['nombre' => $protegido->nombre, 'codigo' => $protegido->codigo, 'activo' => false, 'pantallas' => []])->assertUnprocessable();
    $this->assertDatabaseHas('authorization_audit', ['actor_id' => $admin->id, 'accion' => 'rol.guardado', 'objeto' => (string) $rol->id]);
});

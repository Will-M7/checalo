<?php

use App\Authorization\Autorizacion;
use App\Authorization\Pantallas;
use App\Enums\Rol;
use App\Enums\TeamRole;
use App\Models\Acopiador;
use App\Models\Proveedor;
use App\Models\Recojo;
use App\Models\RolDinamico;
use App\Models\Ruta;
use App\Models\Team;
use App\Models\Turno;
use App\Models\User;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Gate;
use Illuminate\Validation\ValidationException;
use Livewire\Livewire;
use Symfony\Component\HttpKernel\Exception\HttpException;

beforeEach(fn () => $this->withHeader('X-Huata-Authorization-Protocol', '1'));

function usuarioDinamico(): array
{
    $admin = User::factory()->rol(Rol::Administrador)->create();
    $usuario = User::factory()->rol(Rol::Vendedor)->create(['usuario' => 'dinamico', 'password' => 'secreto']);
    $team = Team::principal();
    $team->members()->syncWithoutDetaching([$usuario->id => ['role' => TeamRole::Owner->value]]);
    $usuario->switchTeam($team);
    $rol = Autorizacion::guardarRol($admin, null, 'Dos funciones', 'dos_funciones', true, ['vendedor.clientes', 'produccion.unidades']);
    Autorizacion::asignar($admin, $usuario, [$rol->codigo]);

    return [$admin, $usuario, $rol, $team];
}

test('un rol nuevo usa dos pantallas reales en Laravel y el mismo contrato móvil', function () {
    [$admin, $user, $rol, $team] = usuarioDinamico();
    $this->actingAs($user)->get(route('vendedor.clientes', ['current_team' => $team->slug]))->assertOk();
    $this->get(route('produccion.unidades', ['current_team' => $team->slug]))->assertOk();
    $this->get(route('vendedor.ventas', ['current_team' => $team->slug]))->assertForbidden();
    Livewire::test('pages::produccion.unidades')->set('unidadNombre', 'Litro')->set('unidadSimbolo', 'L')->call('guardarUnidad')->assertHasNoErrors();
    $this->assertDatabaseHas('unidades_medida', ['nombre' => 'Litro']);
    $login = $this->postJson('/api/mobile/login', ['usuario' => 'dinamico', 'password' => 'secreto'])->assertOk();
    $login->assertJsonPath('autorizacion.roles.0.id', 'dos_funciones');
    $token = $login->json('token');
    $snapshot = $this->withToken($token)->getJson('/api/mobile/autorizaciones')->assertOk()->json();
    file_put_contents('/tmp/huata-auth-contract.json', json_encode($snapshot));
    expect($snapshot['pantallas'])->toBe(['produccion.unidades', 'vendedor.clientes']);
    expect($snapshot['mapeo_android']['produccion.unidades'])->toBe('ProduccionUnidades');
    $this->withToken($token)->postJson('/api/mobile/cambios', ['cambios' => [
        ['tabla' => 'cliente', 'id' => 'cliente-offline', 'operacion' => 'guardar', 'datos' => ['nombres' => 'Prueba', 'apellidos' => 'Central', 'documento' => '12345678', 'compras_count' => '0']],
        ['tabla' => 'unidad_medida', 'id' => 'unidad-offline', 'operacion' => 'guardar', 'datos' => ['nombre' => 'Kilogramo', 'simbolo' => 'kg']],
    ]])->assertOk()->assertJsonPath('errores', []);
    $this->assertDatabaseHas('clientes', ['nombres' => 'Prueba']);
    $this->assertDatabaseHas('unidades_medida', ['simbolo' => 'kg']);
});

test('revocar función deniega página abierta y cambios móviles sin tocar datos', function () {
    [$admin, $user, $rol, $team] = usuarioDinamico();
    $this->actingAs($user);
    $abierta = Livewire::test('pages::produccion.unidades');
    $antes = Autorizacion::snapshot($user)['version'];
    Autorizacion::guardarRol($admin, $rol->id, $rol->nombre, $rol->codigo, true, ['vendedor.clientes']);
    expect(Autorizacion::snapshot($user)['version'])->not->toBe($antes);
    file_put_contents('/tmp/huata-auth-revoked.json', json_encode(Autorizacion::snapshot($user)));
    $abierta->call('guardarUnidad')->assertForbidden();
    $token = $this->postJson('/api/mobile/login', ['usuario' => 'dinamico', 'password' => 'secreto'])->json('token');
    $this->withToken($token)->postJson('/api/mobile/cambios', ['cambios' => [['tabla' => 'unidad_medida', 'id' => 'pendiente', 'operacion' => 'guardar', 'datos' => ['nombre' => 'No', 'simbolo' => 'n']]]])->assertOk()->assertJsonPath('errores.0.code', 'AUTHORIZATION_REVOKED');
    $this->assertDatabaseMissing('unidades_medida', ['nombre' => 'No']);
});

test('API impide autoasignación y un rol vacío no hereda privilegios de su categoría', function () {
    [$admin, $user, $rol] = usuarioDinamico();
    $token = $this->postJson('/api/mobile/login', ['usuario' => 'dinamico', 'password' => 'secreto'])->json('token');
    $this->withToken($token)->postJson('/api/mobile/autorizaciones/asignar', ['usuario_id' => $user->id, 'roles' => ['administrador']])->assertForbidden();
    $this->withToken($token)->postJson('/api/mobile/cambios', ['cambios' => [['tabla' => 'usuario', 'id' => (string) $user->id, 'operacion' => 'guardar', 'datos' => ['rol' => 'ADMIN', 'roles' => 'ADMIN', 'usuario' => $user->usuario, 'documento' => $user->documento, 'activo' => '1']]]])->assertOk()->assertJsonCount(1, 'errores');
    expect($user->fresh()->rol)->toBe(Rol::Vendedor)->and($user->puedePantalla('admin.roles'))->toBeFalse();
    Autorizacion::guardarRol($admin, $rol->id, $rol->nombre, $rol->codigo, false, $rol->pantallas);
    expect(Autorizacion::pantallas($user))->toBe([]);
});

test('los seis roles conservan sus pantallas históricas y la configuración se audita', function () {
    foreach (Rol::cases() as $rol) {
        $user = User::factory()->rol($rol)->create();
        foreach ($rol->navegacion() as $item) {
            expect($user->puedePantalla($item['route']))->toBeTrue();
        }
    }
    usuarioDinamico();
    expect(DB::table('authorization_audit')->whereIn('accion', ['rol.guardado', 'usuario.roles'])->count())->toBe(2);
});

test('no se puede retirar la administración crítica de la propia sesión', function () {
    [$admin] = usuarioDinamico();
    $this->expectException(ValidationException::class);
    Autorizacion::asignar($admin, $admin, ['vendedor']);
});

test('todas las pantallas no asignadas se deniegan y el sync no filtra módulos por el nombre del rol', function () {
    [$admin, $user, $rol, $team] = usuarioDinamico();
    $this->actingAs($user);
    foreach (array_keys(Pantallas::todas()) as $ruta) {
        if (in_array($ruta, ['vendedor.clientes', 'produccion.unidades'], true)) {
            continue;
        }
        $this->get(route($ruta, ['current_team' => $team->slug]))->assertForbidden();
    }
    $token = $this->postJson('/api/mobile/login', ['usuario' => 'dinamico', 'password' => 'secreto'])->json('token');
    $sync = $this->withToken($token)->getJson('/api/mobile/sync')->assertOk();
    $sync->assertJsonPath('autorizacion.roles.0.id', 'dos_funciones')->assertJsonPath('ventas', [])->assertJsonPath('recojos', [])->assertJsonPath('ids.venta', []);
    expect(collect($sync->json('usuarios'))->pluck('id')->all())->toBe([(string) $user->id]);
    Autorizacion::asignar($admin, $user, []);
    expect(Autorizacion::pantallas($user))->toBe([]);
    $this->get(route('vendedor.clientes', ['current_team' => $team->slug]))->assertForbidden();
});

test('un administrador delegado no puede conceder funciones fuera de su alcance', function () {
    [$admin] = usuarioDinamico();
    $delegado = User::factory()->rol(Rol::Vendedor)->create();
    $limitado = Autorizacion::guardarRol($admin, null, 'Gestión limitada', 'gestion_limitada', true, ['admin.roles', 'admin.asignar-roles', 'admin.usuarios']);
    Autorizacion::asignar($admin, $delegado, [$limitado->codigo]);
    $this->expectException(ValidationException::class);
    Autorizacion::guardarRol($delegado, null, 'No autorizado', 'sin_techo', true, ['vendedor.ventas']);
});

test('el administrador principal no se puede desactivar ni eliminar', function () {
    [$admin] = usuarioDinamico();
    expect(fn () => $admin->update(['activo' => false]))->toThrow(ValidationException::class);
    expect(fn () => $admin->fresh()->delete())->toThrow(ValidationException::class);
    expect($admin->fresh()->activo)->toBeTrue();
});

test('un rol mixto de acopio y clientes mantiene el ámbito propio al sincronizar', function () {
    $admin = User::factory()->rol(Rol::Administrador)->create();
    $propio = Acopiador::factory()->create();
    $ajeno = Acopiador::factory()->create();
    $pro = Proveedor::factory()->create(['acopiador_id' => $propio->id]);
    $otro = Proveedor::factory()->create(['acopiador_id' => $ajeno->id]);
    $ruta = Ruta::factory()->create(['acopiador_id' => $propio->id]);
    $turno = Turno::factory()->create();
    $ruta->turnos()->attach($turno);
    $rec = Recojo::factory()->create(['proveedor_id' => $pro->id, 'acopiador_id' => $propio->id, 'turno_id' => $turno->id]);
    $recAjeno = Recojo::factory()->create(['proveedor_id' => $otro->id, 'acopiador_id' => $ajeno->id]);
    $rol = Autorizacion::guardarRol($admin, null, 'Acopio y clientes', 'acopio_clientes', true, ['acopiador.recojo', 'vendedor.clientes']);
    $user = $propio->user;
    $user->update(['usuario' => 'acopio-dinamico', 'password' => 'secreto']);
    Autorizacion::asignar($admin, $user, [$rol->codigo]);
    $token = $this->postJson('/api/mobile/login', ['usuario' => $user->usuario, 'password' => 'secreto'])->json('token');
    $sync = $this->withToken($token)->getJson('/api/mobile/sync')->assertOk();
    expect(collect($sync->json('recojos'))->pluck('id')->all())->toBe([(string) $rec->id]);
    expect($sync->json('ids.recojo'))->toBe([(string) $rec->id]);
    expect($sync->json('turnos.0.acopiador_id'))->toBe((string) $propio->id);
    $incremental = $this->withToken($token)->getJson('/api/mobile/sync?since='.urlencode(now()->addDay()->toIso8601String()))->assertOk();
    expect($incremental->json('ids.recojo'))->toBe([(string) $rec->id]);
});

test('un proveedor sincroniza su perfil y sus entregas sin descargar las de otro proveedor', function () {
    $pro = Proveedor::factory()->create();
    $otro = Proveedor::factory()->create();
    $rec = Recojo::factory()->create(['proveedor_id' => $pro->id]);
    Recojo::factory()->create(['proveedor_id' => $otro->id]);
    $user = $pro->user;
    $user->update(['usuario' => 'proveedor-sync', 'password' => 'secreto']);
    $token = $this->postJson('/api/mobile/login', ['usuario' => $user->usuario, 'password' => 'secreto'])->json('token');
    $sync = $this->withToken($token)->getJson('/api/mobile/sync')->assertOk();
    expect(collect($sync->json('proveedores'))->pluck('id')->all())->toBe([(string) $pro->id]);
    expect(collect($sync->json('recojos'))->pluck('id')->all())->toBe([(string) $rec->id]);
});

test('cada pantalla administrativa conserva sus operaciones sin exigir la otra pantalla', function () {
    [$principal] = usuarioDinamico();
    $gestor = User::factory()->rol(Rol::Vendedor)->create();
    $rolGestor = Autorizacion::guardarRol($principal, null, 'Diseñador de roles', 'disenador', true, ['admin.roles', 'vendedor.clientes']);
    Autorizacion::asignar($principal, $gestor, [$rolGestor->codigo]);
    $nuevo = Autorizacion::guardarRol($gestor, null, 'Clientes', 'solo_clientes', true, ['vendedor.clientes']);
    expect($nuevo->pantallas)->toBe(['vendedor.clientes']);
    $this->expectException(HttpException::class);
    Autorizacion::asignar($gestor, $gestor, [$nuevo->codigo]);
});

test('administradores históricos conservan asignación de acopio y el perfil móvil se crea centralmente', function () {
    [$principal] = usuarioDinamico();
    $otroAdmin = User::factory()->rol(Rol::Administrador)->create();
    $destino = User::factory()->rol(Rol::Vendedor)->create();
    Autorizacion::asignar($otroAdmin, $destino, ['acopiador']);
    expect($destino->fresh()->rol)->toBe(Rol::Acopiador);
    expect($destino->fresh()->acopiador)->not->toBeNull();
    expect($destino->puedePantalla('acopiador.recojo'))->toBeTrue();
    expect($otroAdmin->puedePantalla('acopiador.recojo'))->toBeFalse();
});

test('desactivar un rol conserva su pertenencia y permite retirarla explícitamente', function () {
    [$admin, $user, $rol] = usuarioDinamico();
    Autorizacion::guardarRol($admin, $rol->id, $rol->nombre, $rol->codigo, false, $rol->pantallas);
    expect(Autorizacion::snapshot($user)['roles'][0]['activo'])->toBeFalse();
    Autorizacion::asignar($admin, $user, [$rol->codigo]);
    expect($user->rolesDinamicos()->count())->toBe(1)->and(Autorizacion::pantallas($user))->toBe([]);
    Autorizacion::asignar($admin, $user, []);
    expect($user->rolesDinamicos()->count())->toBe(0);
});

test('Teams no permite retirar al administrador principal ni borrar su equipo operativo', function () {
    [$admin, $usuario, $rol, $team] = usuarioDinamico();
    $team->members()->syncWithoutDetaching([$admin->id => ['role' => TeamRole::Owner->value]]);
    expect(Gate::forUser($admin)->allows('delete', $team))->toBeFalse();
    $this->actingAs($usuario);
    Livewire::test('pages::teams.remove-member-modal', ['team' => $team, 'memberId' => $admin->id])->call('removeMember')->assertForbidden();
    expect($admin->belongsToTeam($team))->toBeTrue();
});

test('una APK antigua recibe conflicto y conserva el lote entero cuando se revoca una función', function () {
    [$admin, $user, $rol] = usuarioDinamico();
    Autorizacion::guardarRol($admin, $rol->id, $rol->nombre, $rol->codigo, true, ['vendedor.clientes']);
    $token = $this->postJson('/api/mobile/login', ['usuario' => 'dinamico', 'password' => 'secreto'])->json('token');
    $this->withHeader('X-Huata-Authorization-Protocol', '0')->withToken($token)->postJson('/api/mobile/cambios', ['cambios' => [
        ['tabla' => 'cliente', 'id' => 'cliente-antiguo', 'operacion' => 'guardar', 'datos' => ['nombres' => 'Lote heredado', 'apellidos' => 'Pendiente', 'documento' => '22222222']],
        ['tabla' => 'unidad_medida', 'id' => 'unidad-revocada', 'operacion' => 'guardar', 'datos' => ['nombre' => 'Revocada', 'simbolo' => 'r']],
    ]])->assertStatus(409)->assertJsonPath('ids', [])->assertJsonPath('errores.0.code', 'AUTHORIZATION_REVOKED');
    $this->assertDatabaseMissing('clientes', ['nombres' => 'Lote heredado']);
    $this->assertDatabaseMissing('mobile_ids', ['uuid' => 'cliente-antiguo']);
});

test('un gestor limitado tampoco puede quitar funciones de un rol fuera de su alcance', function () {
    [$admin] = usuarioDinamico();
    $gestor = User::factory()->rol(Rol::Vendedor)->create();
    $limitado = Autorizacion::guardarRol($admin, null, 'Gestor limitado', 'gestor_sin_ventas', true, ['admin.roles', 'vendedor.clientes']);
    Autorizacion::asignar($admin, $gestor, [$limitado->codigo]);
    $vendedor = RolDinamico::where('codigo', 'vendedor')->firstOrFail();
    expect(fn () => Autorizacion::guardarRol($gestor, $vendedor->id, $vendedor->nombre, $vendedor->codigo, true, ['vendedor.clientes']))->toThrow(ValidationException::class);
    expect($vendedor->fresh()->pantallas)->toContain('vendedor.ventas');
});

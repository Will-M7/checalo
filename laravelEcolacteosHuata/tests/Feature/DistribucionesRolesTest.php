<?php

use App\Authorization\Autorizacion;
use App\Authorization\Distribuciones;
use App\Authorization\Pantallas;
use App\Enums\Rol;
use App\Livewire\DistribucionesRoles;
use App\Models\DistribucionRoles;
use App\Models\RolDinamico;
use App\Models\Team;
use App\Models\User;
use Illuminate\Support\Facades\DB;
use Illuminate\Validation\ValidationException;
use Livewire\Livewire;
use Symfony\Component\HttpKernel\Exception\HttpException;

function principalDistribuciones(): User
{
    return User::factory()->rol(Rol::Administrador)->create();
}
function instantaneaDistribuciones(): string
{
    return json_encode([DB::table('users')->orderBy('id')->get()->toArray(), DB::table('authorization_role_user')->orderBy('role_id')->orderBy('user_id')->get()->toArray(), DB::table('teams')->orderBy('id')->get()->toArray(), DB::table('team_members')->orderBy('id')->get()->toArray()]);
}
function copiaDistribuciones(User $admin, array $pantallas = ['vendedor.clientes']): DistribucionRoles
{
    return Distribuciones::guardar($admin, ['nombre' => 'Prueba', 'asignaciones' => [['codigo' => 'vendedor', 'pantallas' => $pantallas]]]);
}

test('instalación conserva base de 41 pantallas más Finanzas y Receptores Yape y 40 nativas', function () {
    expect(Pantallas::todas())->toHaveCount(43)->and(Pantallas::android())->toHaveCount(40);
    expect(RolDinamico::count())->toBe(6)->and(User::count())->toBe(0);
    $base = DistribucionRoles::where('codigo', 'inicial')->firstOrFail();
    expect($base->predeterminada)->toBeTrue()->and($base->tipo)->toBe('base');
    $aprobada = json_decode(file_get_contents(resource_path('authorization/inicial.json')), true)['asignaciones'];
    expect(array_map('count', $aprobada))->toBe(['administrador' => 15, 'personal_planta' => 7, 'acopiador' => 6, 'proveedor' => 4, 'vendedor' => 9, 'produccion' => 9]);
    expect(collect($aprobada)->flatten()->unique()->count())->toBe(41);
    foreach ($aprobada as $codigo => $pantallas) {
        expect(RolDinamico::where('codigo', $codigo)->firstOrFail()->pantallas)->toBe($codigo === 'administrador' ? [...$pantallas, 'admin.finanzas', 'admin.receptores-yape'] : $pantallas);
    }
    $admin = principalDistribuciones();
    $rol = RolDinamico::where('codigo', 'vendedor')->first();
    Autorizacion::guardarRol($admin, $rol->id, $rol->nombre, $rol->codigo, true, ['vendedor.clientes']);
    Distribuciones::registrarBases();
    expect($rol->fresh()->pantallas)->toBe(['vendedor.clientes'])->and(DistribucionRoles::count())->toBe(2);
});

test('En desarrollo contiene la revisión clonada verificable y adaptaciones explícitas', function () {
    $d = DistribucionRoles::where('codigo', 'en-desarrollo')->first();
    expect($d->origen['revision'])->toBe('0fd6a98e3010187a041baa1ce60cf79333f582d4');
    expect($d->origen['fuentes_sha256'])->toHaveCount(6)->and($d->origen['adaptaciones'])->not->toBeEmpty();
    $a = collect($d->asignaciones)->pluck('pantallas', 'codigo');
    expect(array_map('count', $a->all()))->toBe(['administrador' => 31, 'personal_planta' => 7, 'acopiador' => 6, 'proveedor' => 5, 'vendedor' => 8, 'produccion' => 9]);
    expect($a['administrador'])->toContain('admin.roles', 'asistencia.tomar')->not->toContain('vendedor.configuracion-pagos');
    expect($a->keys()->all())->not->toContain('tomar_asistencia', '001');
});

test('registrar bases sobre instalación operativa no aplica Inicial ni cambia usuarios', function () {
    $admin = principalDistribuciones();
    $r = RolDinamico::where('codigo', 'proveedor')->first();
    Autorizacion::guardarRol($admin, $r->id, $r->nombre, $r->codigo, true, ['proveedor.panel']);
    $antes = instantaneaDistribuciones();
    DistribucionRoles::query()->delete(); // escenario de instalación previa sin tablas de plantillas pobladas
    Distribuciones::registrarBases();
    expect($r->fresh()->pantallas)->toBe(['proveedor.panel'])->and(instantaneaDistribuciones())->toBe($antes);
    expect(DB::table('authorization_distribution_runs')->count())->toBe(0);
});

test('plantillas base protegidas y personalizadas editables duplicables sin aplicar', function () {
    $admin = principalDistribuciones();
    $antes = instantaneaDistribuciones();
    $base = DistribucionRoles::where('codigo', 'inicial')->first();
    expect(fn () => Distribuciones::guardar($admin, ['id' => $base->id, 'version' => 1, 'nombre' => 'Cambio', 'asignaciones' => $base->asignaciones]))->toThrow(ValidationException::class);
    $copia = Distribuciones::copiar($admin, 'Mi distribución', $base->id);
    $nueva = Distribuciones::guardar($admin, ['id' => $copia->id, 'version' => 1, 'nombre' => 'Editada', 'asignaciones' => [['codigo' => 'vendedor', 'pantallas' => ['vendedor.clientes']]]]);
    expect($nueva->version)->toBe(2)->and($nueva->predeterminada)->toBeFalse()->and($nueva->tipo)->toBe('personalizada');
    expect(fn () => Distribuciones::guardar($admin, ['id' => $copia->id, 'version' => 1, 'nombre' => 'Obsoleta', 'asignaciones' => $copia->asignaciones]))->toThrow(HttpException::class);
    $actual = Distribuciones::copiar($admin, 'Actual');
    expect($actual->asignaciones)->toHaveCount(6)->and(instantaneaDistribuciones())->toBe($antes);
});

test('preview es de solo lectura detalla usuarios y requiere confirmación', function () {
    $admin = principalDistribuciones();
    $user = User::factory()->rol(Rol::Vendedor)->create();
    $d = copiaDistribuciones($admin);
    $antes = instantaneaDistribuciones();
    $revision = DB::table('authorization_state')->value('revision');
    $vista = Distribuciones::previsualizar($admin, 'aplicar', $d->id);
    expect($vista['roles'][0]['usuarios'][0]['id'])->toBe((string) $user->id)->and($vista['roles'][0]['retira'])->toContain('vendedor.ventas');
    expect($vista['errores'])->toBe([])->and($vista['no_afectados'])->toHaveCount(5);
    expect(instantaneaDistribuciones())->toBe($antes)->and(DB::table('authorization_state')->value('revision'))->toBe($revision);
    expect(fn () => Distribuciones::confirmar($admin, $vista['token'], false))->toThrow(ValidationException::class);
    expect(DB::table('authorization_distribution_runs')->count())->toBe(0);
});

test('aplicar y recuperar modifica exclusivamente diferencias sin tocar usuarios Teams ni roles ajenos', function () {
    $admin = principalDistribuciones();
    $user = User::factory()->rol(Rol::Vendedor)->create();
    $ajeno = Autorizacion::guardarRol($admin, null, 'Ajeno', 'ajeno', true, ['produccion.unidades']);
    $d = copiaDistribuciones($admin);
    $antes = instantaneaDistribuciones();
    $version = Autorizacion::snapshot($user)['version'];
    $pantallas = $user->rolesDinamicos()->first()->pantallas;
    $vista = Distribuciones::previsualizar($admin, 'aplicar', $d->id);
    Distribuciones::confirmar($admin, $vista['token'], true);
    expect($user->puedePantalla('vendedor.ventas'))->toBeFalse()->and($user->puedePantalla('vendedor.clientes'))->toBeTrue();
    expect(Autorizacion::snapshot($user)['version'])->not->toBe($version);
    expect(instantaneaDistribuciones())->toBe($antes)->and($ajeno->fresh()->pantallas)->toBe(['produccion.unidades']);
    $run = DB::table('authorization_distribution_runs')->first();
    $recovery = Distribuciones::previsualizar($admin, 'recuperar', null, $run->id);
    expect($recovery['errores'])->toBe([]);
    Distribuciones::confirmar($admin, $recovery['token'], true);
    expect($user->rolesDinamicos()->first()->pantallas)->toBe($pantallas)->and(instantaneaDistribuciones())->toBe($antes);
    expect(Distribuciones::previsualizar($admin, 'recuperar', null, $run->id)['errores'])->not->toBeEmpty();
});

test('cambios posteriores de roles usuarios o plantilla bloquean tokens obsoletos y recuperación', function () {
    $admin = principalDistribuciones();
    $d = copiaDistribuciones($admin);
    $vista = Distribuciones::previsualizar($admin, 'aplicar', $d->id);
    Distribuciones::guardar($admin, ['id' => $d->id, 'version' => 1, 'nombre' => 'Cambio', 'asignaciones' => [['codigo' => 'vendedor', 'pantallas' => []]]]);
    expect(fn () => Distribuciones::confirmar($admin, $vista['token'], true))->toThrow(HttpException::class);
    $vista = Distribuciones::previsualizar($admin, 'aplicar', $d->id);
    Distribuciones::confirmar($admin, $vista['token'], true);
    $run = DB::table('authorization_distribution_runs')->first();
    $recovery = Distribuciones::previsualizar($admin, 'recuperar', null, $run->id);
    Autorizacion::guardarRol($admin, null, 'Posterior', 'posterior', true, []);
    expect(fn () => Distribuciones::confirmar($admin, $recovery['token'], true))->toThrow(HttpException::class);
    expect(Distribuciones::previsualizar($admin, 'recuperar', null, $run->id)['token'])->toBeNull();
});

test('referencias a pantallas inexistentes o roles eliminados y recreados bloquean aplicación', function () {
    $admin = principalDistribuciones();
    $d = copiaDistribuciones($admin);
    $r = Autorizacion::guardarRol($admin, null, 'Temporal', 'temporal', true, []);
    $d->update(['asignaciones' => [['codigo' => 'temporal', 'rol_id' => $r->id, 'pantallas' => []]]]);
    $r->delete();
    Autorizacion::guardarRol($admin, null, 'Otro', 'temporal', true, []);
    expect(Distribuciones::previsualizar($admin, 'aplicar', $d->id)['token'])->toBeNull();
    $r = RolDinamico::where('codigo', 'vendedor')->first();
    $d->update(['asignaciones' => [['codigo' => $r->codigo, 'rol_id' => $r->id, 'pantallas' => ['no.existe']]]]);
    expect(Distribuciones::previsualizar($admin, 'aplicar', $d->id)['errores'])->not->toBeEmpty();
});

test('no permite retirar administración crítica ni usar tokens de otro actor o manipulados', function () {
    $admin = principalDistribuciones();
    $d = Distribuciones::guardar($admin, ['nombre' => 'Bloqueada', 'asignaciones' => [['codigo' => 'administrador', 'pantallas' => ['admin.panel']]]]);
    expect(Distribuciones::previsualizar($admin, 'aplicar', $d->id)['token'])->toBeNull();
    expect(fn () => Distribuciones::confirmar($admin, 'manipulado', true))->toThrow(ValidationException::class);
    $otro = User::factory()->rol(Rol::Administrador)->create();
    expect(Distribuciones::catalogo($otro)['distribuciones'])->toHaveCount(3);
    expect(Distribuciones::previsualizar($otro, 'aplicar', $d->id)['token'])->toBeNull();
    expect($admin->puedePantalla('admin.roles'))->toBeTrue();
});

test('retirar 001 exige destino explícito preserva cuenta registros y roles adicionales', function () {
    $admin = principalDistribuciones();
    $r = Autorizacion::guardarRol($admin, null, 'ventas', '001', true, ['vendedor.ventas']);
    $u = User::factory()->rol(Rol::Vendedor)->create();
    Autorizacion::asignar($admin, $u, ['001', 'produccion']);
    $before = DB::table('users')->where('id', $u->id)->first();
    $team = $u->current_team_id;
    $bad = Distribuciones::previsualizar($admin, 'retirar-001');
    expect($bad['token'])->toBeNull()->and(RolDinamico::where('codigo', '001')->exists())->toBeTrue();
    $ok = Distribuciones::previsualizar($admin, 'retirar-001', reemplazos: [['usuario_id' => (string) $u->id, 'codigo' => 'administrador']]);
    expect($ok['roles'][0]['agrega'])->toContain('admin.roles');
    Distribuciones::confirmar($admin, $ok['token'], true);
    $after = $u->fresh();
    expect($after->password)->toBe($before->password)->and($after->usuario)->toBe($before->usuario)->and($after->current_team_id)->toBe($team);
    expect($after->rolesDinamicos()->pluck('codigo')->all())->toContain('administrador', 'produccion')->not->toContain('001');
    expect(User::count())->toBe(2)->and(RolDinamico::where('codigo', '001')->exists())->toBeFalse();
});

test('auxiliar se retira únicamente sin dependencias y pantalla sigue disponible', function () {
    $admin = principalDistribuciones();
    expect($admin->puedePantalla('asistencia.tomar'))->toBeTrue();
    expect(RolDinamico::where('codigo', 'tomar_asistencia')->exists())->toBeFalse();
    expect(fn () => Autorizacion::guardarRol($admin, null, 'Auxiliar', 'tomar_asistencia', true, ['asistencia.tomar']))->toThrow(ValidationException::class);
    expect(Autorizacion::guardarRol($admin, null, 'Control', 'control_asistencia', true, ['asistencia.tomar'])->codigo)->toBe('control_asistencia');
    $aux = RolDinamico::create(['codigo' => 'tomar_asistencia', 'nombre' => 'Legado', 'activo' => true, 'protegido' => false, 'pantallas' => ['asistencia.tomar']]);
    $u = User::factory()->rol(Rol::Vendedor)->create();
    $u->rolesDinamicos()->attach($aux);
    Distribuciones::registrarBases();
    expect($aux->fresh())->not->toBeNull();
    $u->rolesDinamicos()->detach($aux);
    Distribuciones::registrarBases();
    expect($aux->fresh())->toBeNull();
});

test('gestor web presenta plantilla preview confirmación e historial sin otra pantalla', function () {
    $admin = principalDistribuciones();
    $this->actingAs($admin);
    $d = copiaDistribuciones($admin);
    $roles = json_encode(DB::table('authorization_roles')->get());
    Livewire::test(DistribucionesRoles::class)->call('abrir')->assertSee('Distribuciones de roles')->assertSee('Inicial')->assertSee('En desarrollo')->call('ver', $d->id)->call('previsualizar', 'aplicar')->assertSee('Vista previa')->assertSee('Confirmar operación')->assertHasNoErrors();
    expect(json_encode(DB::table('authorization_roles')->get()))->toBe($roles);
    Livewire::test(DistribucionesRoles::class)->call('abrir')->call('ver', $d->id)->call('previsualizar', 'aplicar')->set('confirmado', true)->call('aplicar')->assertHasNoErrors()->assertSee('Prueba');
    expect(DB::table('authorization_distribution_runs')->count())->toBe(1);
});

test('API nativa comparte catálogo preview servicio y actualización sin permitir autoescalada', function () {
    $admin = principalDistribuciones();
    $admin->update(['usuario' => 'admin-distribucion', 'password' => 'secreto']);
    $token = $this->postJson('/api/mobile/login', ['usuario' => $admin->usuario, 'password' => 'secreto'])->assertOk()->json('token');
    $url = '/api/mobile/autorizaciones/distribuciones';
    $catalogo = $this->withToken($token)->getJson($url)->assertOk()->assertJsonCount(2, 'distribuciones')->assertJsonCount(43, 'pantallas')->json();
    file_put_contents('/tmp/huata-distribuciones-catalogo.json', json_encode($catalogo));
    $this->postJson($url, ['modo' => 'guardar', 'nombre' => 'API', 'asignaciones' => [['codigo' => 'vendedor', 'pantallas' => ['vendedor.clientes', 'produccion.unidades']]]])->assertOk();
    $d = DistribucionRoles::where('nombre', 'API')->first();
    $vista = $this->postJson($url.'/previsualizar', ['operacion' => 'aplicar', 'distribucion_id' => $d->id])->assertOk()->json();
    file_put_contents('/tmp/huata-distribuciones-preview.json', json_encode($vista));
    $this->postJson($url.'/confirmar', ['token' => $vista['token'], 'confirmar' => true])->assertOk();
    expect(RolDinamico::where('codigo', 'vendedor')->first()->pantallas)->toBe(['produccion.unidades', 'vendedor.clientes']);
    $user = User::factory()->rol(Rol::Vendedor)->create(['usuario' => 'sin-privilegio', 'password' => 'secreto']);
    $userToken = $this->postJson('/api/mobile/login', ['usuario' => $user->usuario, 'password' => 'secreto'])->json('token');
    $this->withToken($userToken)->getJson($url)->assertForbidden();
    $this->postJson($url.'/confirmar', ['token' => $vista['token'], 'confirmar' => true])->assertForbidden();
});

test('token expira y un gestor limitado no puede aplicar funciones fuera de su alcance', function () {
    $admin = principalDistribuciones();
    $d = copiaDistribuciones($admin);
    $vista = Distribuciones::previsualizar($admin, 'aplicar', $d->id);
    $this->travel(11)->minutes();
    expect(fn () => Distribuciones::confirmar($admin, $vista['token'], true))->toThrow(HttpException::class);
    $this->travelBack();
    $user = User::factory()->rol(Rol::Vendedor)->create();
    $limitado = Autorizacion::guardarRol($admin, null, 'Gestor limitado', 'gestor_dist', true, ['admin.roles', 'vendedor.clientes']);
    Autorizacion::asignar($admin, $user, [$limitado->codigo]);
    expect(Distribuciones::previsualizar($user, 'aplicar', $d->id)['token'])->toBeNull();
    $this->actingAs($user);
    Livewire::test(DistribucionesRoles::class)->call('abrir')->call('ver', $d->id)->call('previsualizar', 'aplicar')->assertSee('Fuera de tu alcance')->assertDontSee('Confirmar operación');
});

test('preview no anuncia pérdida efectiva a usuarios que conservan la función por otro rol', function () {
    $admin = principalDistribuciones();
    $u = User::factory()->rol(Rol::Vendedor)->create();
    $extra = Autorizacion::guardarRol($admin, null, 'Complemento', 'complemento', true, RolDinamico::where('codigo', 'vendedor')->first()->pantallas);
    Autorizacion::asignar($admin, $u, ['vendedor', $extra->codigo]);
    $d = copiaDistribuciones($admin);
    expect(Distribuciones::previsualizar($admin, 'aplicar', $d->id)['roles'][0]['usuarios'])->toBe([]);
});

test('distribución de un rol nuevo actualiza web y contrato móvil sin eliminar datos previos', function () {
    $admin = principalDistribuciones();
    $r = Autorizacion::guardarRol($admin, null, 'Supervisor', 'supervisor_dist', true, ['vendedor.clientes', 'produccion.unidades']);
    $u = User::factory()->rol(Rol::Vendedor)->create(['usuario' => 'supervisor-dist', 'password' => 'secreto']);
    Autorizacion::asignar($admin, $u, [$r->codigo]);
    $team = Team::principal();
    $team->members()->syncWithoutDetaching([$u->id => ['role' => 'owner']]);
    $u->switchTeam($team);
    $this->actingAs($u)->get(route('vendedor.clientes', ['current_team' => $team->slug]))->assertOk();
    $this->get(route('produccion.unidades', ['current_team' => $team->slug]))->assertOk();
    Livewire::test('pages::produccion.unidades')->set('unidadNombre', 'Pendiente conservado')->set('unidadSimbolo', 'pc')->call('guardarUnidad')->assertHasNoErrors();
    $this->get(route('vendedor.ventas', ['current_team' => $team->slug]))->assertForbidden();
    $token = $this->postJson('/api/mobile/login', ['usuario' => $u->usuario, 'password' => 'secreto'])->assertOk()->json('token');
    $antes = $this->withToken($token)->getJson('/api/mobile/autorizaciones')->assertOk()->assertJsonPath('roles.0.id', 'supervisor_dist')->json('version');
    $d = Distribuciones::guardar($admin, ['nombre' => 'Supervisor sin Producción', 'asignaciones' => [['codigo' => $r->codigo, 'pantallas' => ['vendedor.clientes']]]]);
    Distribuciones::confirmar($admin, Distribuciones::previsualizar($admin, 'aplicar', $d->id)['token'], true);
    $this->get(route('produccion.unidades', ['current_team' => $team->slug]))->assertForbidden();
    $this->get(route('vendedor.clientes', ['current_team' => $team->slug]))->assertOk();
    $snap = $this->getJson('/api/mobile/autorizaciones')->assertOk()->assertJsonPath('roles.0.id', 'supervisor_dist')->assertJsonPath('pantallas', ['vendedor.clientes'])->json();
    expect($snap['version'])->not->toBe($antes);
    $this->withHeader('X-Huata-Authorization-Protocol', '1')->postJson('/api/mobile/cambios', ['cambios' => [['tabla' => 'unidad_medida', 'id' => 'pendiente-offline', 'operacion' => 'guardar', 'datos' => ['nombre' => 'Trabajo offline', 'simbolo' => 'o']]]])->assertOk()->assertJsonPath('errores.0.code', 'AUTHORIZATION_REVOKED');
    $this->assertDatabaseHas('unidades_medida', ['nombre' => 'Pendiente conservado']);
    expect($u->rolesDinamicos()->count())->toBe(1);
});

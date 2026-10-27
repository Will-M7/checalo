<?php

use App\Authorization\Pantallas;
use App\Enums\Rol;
use App\Models\Team;
use App\Models\User;
use Database\Seeders\DatabaseSeeder;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Livewire\Livewire;

test('instalación crea admin real idempotente con equipo, distribución y acceso administrativo', function () {
    $this->seed(DatabaseSeeder::class);
    $admin = User::where('usuario', 'admin')->firstOrFail();
    $hash = $admin->password;
    expect(Hash::check('1234', $hash))->toBeTrue()
        ->and($admin->rol)->toBe(Rol::Administrador)
        ->and($admin->puedeAdministracionCritica())->toBeTrue()
        ->and($admin->currentTeam)->not->toBeNull()
        ->and(count(Pantallas::todas()))->toBe(42);
    $this->seed(DatabaseSeeder::class);
    expect(User::count())->toBe(1)->and(Team::count())->toBe(1)
        ->and($admin->fresh()->password)->toBe($hash)
        ->and(DB::table('team_members')->count())->toBe(1)
        ->and(DB::table('authorization_role_user')->count())->toBe(1);
    $this->post('/login', ['usuario' => 'admin', 'password' => '1234'])->assertRedirect();
    $this->assertAuthenticatedAs($admin, 'web');
    foreach (Pantallas::CRITICAS as $route) {
        $this->get(route($route, ['current_team' => $admin->currentTeam->slug]))->assertOk();
    }
    Livewire::test('pages::admin.roles')->call('editar')->set('codigo', 'prueba_instalacion')->set('nombre', 'Prueba instalación')
        ->set('pantallas', ['vendedor.clientes'])->call('guardar')->assertHasNoErrors();
    Livewire::test('pages::admin.usuarios')->call('nuevo')
        ->set('nombres', 'Usuario')->set('apellidos', 'Prueba')->set('documento', '45678901')
        ->set('usuario', 'usuario_instalacion')->set('password', 'prueba-segura')
        ->set('rolesSeleccionados', ['prueba_instalacion'])->call('guardar')->assertHasNoErrors();
    expect(User::where('usuario', 'usuario_instalacion')->firstOrFail()->puedePantalla('vendedor.clientes'))->toBeTrue();
});

test('repetir seeder no altera una cuenta administrativa ni sus asignaciones existentes', function () {
    $admin = User::factory()->rol(Rol::Administrador)->create(['usuario' => 'admin']);
    $before = $admin->fresh()->getAttributes();
    $roles = $admin->rolesDinamicos()->pluck('role_id')->all();
    $this->seed(DatabaseSeeder::class);
    expect($admin->fresh()->getAttributes())->toBe($before)
        ->and($admin->rolesDinamicos()->pluck('role_id')->all())->toBe($roles);
});

test('instalación poblada con otro administrador no recibe otra cuenta privilegiada', function () {
    User::factory()->rol(Rol::Administrador)->create(['usuario' => 'admin_operativo']);
    expect(fn () => $this->artisan('db:seed', ['--class' => DatabaseSeeder::class, '--force' => true])->run())->toThrow(RuntimeException::class);
    expect(User::count())->toBe(1)->and(User::where('usuario', 'admin')->exists())->toBeFalse();
});

test('producción rechaza contraseña de prueba incluso con force y sin crear usuarios', function () {
    app()->instance('env', 'production');
    config(['initial-admin.password' => '1234']);
    expect(fn () => $this->artisan('db:seed', ['--class' => DatabaseSeeder::class, '--force' => true])->run())->toThrow(RuntimeException::class);
    expect(User::count())->toBe(0);
});

test('producción exige contraseña propia y no la reemplaza al repetir el seeder', function () {
    app()->instance('env', 'production');
    config(['initial-admin.password' => 'ClavePropia-2026!']);
    $this->artisan('db:seed', ['--class' => DatabaseSeeder::class, '--force' => true])->run();
    $admin = User::where('usuario', 'admin')->firstOrFail();
    expect(Hash::check('ClavePropia-2026!', $admin->password))->toBeTrue();
    config(['initial-admin.password' => 'OtraClave-2027!']);
    $this->artisan('db:seed', ['--class' => DatabaseSeeder::class, '--force' => true])->run();
    expect(Hash::check('ClavePropia-2026!', $admin->fresh()->password))->toBeTrue();
});

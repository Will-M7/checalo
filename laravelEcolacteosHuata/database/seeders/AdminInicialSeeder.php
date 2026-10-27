<?php

namespace Database\Seeders;

use App\Authorization\Autorizacion;
use App\Authorization\Pantallas;
use App\Enums\Rol;
use App\Enums\TeamRole;
use App\Models\RolDinamico;
use App\Models\Team;
use App\Models\User;
use Illuminate\Database\Seeder;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;

class AdminInicialSeeder extends Seeder
{
    public function run(): void
    {
        DB::transaction(function () {
            DB::table('authorization_state')->where('id', 1)->lockForUpdate()->first();
            $existing = User::where('usuario', 'admin')->first();
            if ($existing) {
                if (! app()->environment(['local', 'testing']) && Hash::check('1234', $existing->password)) {
                    throw new \RuntimeException('La contraseña de prueba admin/1234 está prohibida fuera de local/testing. No se modificó la cuenta existente.');
                }
                $this->command?->info('La cuenta admin ya existe; no se modificaron identidad, contraseña, roles ni membresías.');

                return;
            }
            if (User::exists()) {
                throw new \RuntimeException('Esta instalación ya tiene usuarios. El seeder no agrega privilegios ni altera su administrador operativo.');
            }
            $password = config('initial-admin.password');
            if (! is_string($password) || $password === '') {
                $password = app()->environment(['local', 'testing']) ? '1234' : '';
            }
            if (! app()->environment(['local', 'testing']) &&
                (strlen($password) < 12 || $password === '1234' || ! preg_match('/[a-z]/', $password) || ! preg_match('/[A-Z]/', $password) || ! preg_match('/[0-9]/', $password))) {
                throw new \RuntimeException('Configura HUATA_INITIAL_ADMIN_PASSWORD con al menos 12 caracteres, mayúsculas, minúsculas y números. admin/1234 solo se permite en local/testing.');
            }
            $role = RolDinamico::where('codigo', Rol::Administrador->value)->first();
            if (! $role || ! $role->activo || ! $role->protegido || array_diff(Pantallas::CRITICAS, $role->pantallas)) {
                throw new \RuntimeException('Ejecuta las migraciones del catálogo y distribución Inicial antes de crear al administrador.');
            }
            $admin = User::create([
                'name' => 'Administrador Inicial', 'nombres' => 'Administrador', 'apellidos' => 'Inicial',
                'usuario' => 'admin', 'email' => 'admin@huata.test', 'email_verified_at' => now(),
                'password' => Hash::make($password), 'rol' => Rol::Administrador,
                'roles' => [Rol::Administrador->value], 'activo' => true,
            ]);
            $team = Team::principal();
            $team->members()->syncWithoutDetaching([$admin->id => ['role' => TeamRole::Owner->value]]);
            $admin->switchTeam($team);
            Autorizacion::registrar($admin, 'instalacion.admin-inicial', 'usuario:'.$admin->id, null, ['rol' => $role->codigo, 'team_id' => $team->id]);
            $this->command?->info('Administrador inicial creado. Las credenciales no se imprimen ni sobrescriben en ejecuciones posteriores.');
        }, 5);
    }
}

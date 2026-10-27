<?php

use App\Authorization\Pantallas;
use App\Enums\Rol;
use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('authorization_roles', function (Blueprint $t) {
            $t->id();
            $t->string('codigo', 100)->unique();
            $t->string('nombre', 160);
            $t->boolean('activo')->default(true);
            $t->boolean('protegido')->default(false);
            $t->json('pantallas');
            $t->timestamps();
        });
        Schema::create('authorization_role_user', function (Blueprint $t) {
            $t->foreignId('role_id')->constrained('authorization_roles')->cascadeOnDelete();
            $t->foreignId('user_id')->constrained('users')->cascadeOnDelete();
            $t->primary(['role_id', 'user_id']);
        });
        Schema::create('authorization_state', function (Blueprint $t) {
            $t->unsignedInteger('id')->primary();
            $t->unsignedBigInteger('revision')->default(1);
            $t->unsignedBigInteger('administrador_protegido')->nullable();
        });
        Schema::create('authorization_audit', function (Blueprint $t) {
            $t->id();
            $t->unsignedBigInteger('actor_id')->nullable();
            $t->string('accion');
            $t->string('objeto');
            $t->json('antes')->nullable();
            $t->json('despues')->nullable();
            $t->timestamp('created_at');
        });
        foreach (Rol::cases() as $rol) {
            DB::table('authorization_roles')->insert(['codigo' => $rol->value, 'nombre' => $rol->label(),
                'activo' => true, 'protegido' => $rol === Rol::Administrador,
                'pantallas' => json_encode(Pantallas::heredadas($rol)), 'created_at' => now(), 'updated_at' => now()]);
        }
        $asistencia = DB::table('authorization_roles')->insertGetId(['codigo' => 'tomar_asistencia', 'nombre' => 'Tomar asistencia',
            'activo' => true, 'protegido' => false, 'pantallas' => json_encode(['asistencia.tomar']), 'created_at' => now(), 'updated_at' => now()]);
        $roles = DB::table('authorization_roles')->pluck('id', 'codigo');
        DB::table('users')->orderBy('id')->each(function ($user) use ($roles, $asistencia) {
            $codigos = array_unique(array_filter([...json_decode($user->roles ?: '[]', true), $user->rol]));
            foreach ($codigos as $codigo) {
                if (isset($roles[$codigo])) {
                    DB::table('authorization_role_user')->insertOrIgnore(['user_id' => $user->id, 'role_id' => $roles[$codigo]]);
                }
            }
            if ($user->toma_asistencia) {
                DB::table('authorization_role_user')->insertOrIgnore(['user_id' => $user->id, 'role_id' => $asistencia]);
            }
        });
        $admin = DB::table('users')->where('activo', true)->where(function ($q) {
            $q->where('rol', 'administrador')->orWhereJsonContains('roles', 'administrador');
        })->min('id');
        DB::table('authorization_state')->insert(['id' => 1, 'revision' => 1, 'administrador_protegido' => $admin]);
    }

    public function down(): void
    {
        // El rollback se realiza mediante copia validada; nunca eliminar asignaciones por accidente.
        throw new RuntimeException('Restaurar una copia validada para revertir la autorización central.');
    }
};

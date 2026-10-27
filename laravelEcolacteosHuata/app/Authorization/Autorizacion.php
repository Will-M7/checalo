<?php

namespace App\Authorization;

use App\Enums\Rol;
use App\Models\Acopiador;
use App\Models\Proveedor;
use App\Models\RolDinamico;
use App\Models\Team;
use App\Models\User;
use Illuminate\Support\Facades\DB;
use Illuminate\Validation\Rule;
use Illuminate\Validation\ValidationException;

final class Autorizacion
{
    /** Roles delegables activos, más los ya asociados aunque estén inactivos. */
    public static function catalogoRoles(User $actor, ?User $destino = null)
    {
        $actuales = $destino?->rolesDinamicos()->pluck('codigo')->all() ?? [];

        return RolDinamico::orderBy('id')->get()->filter(fn ($r) => in_array($r->codigo, $actuales, true)
            || ($actor->puedePantalla('admin.asignar-roles') && $r->activo && collect($r->pantallas)->every(fn ($p) => self::puedeDelegar($actor, $p))))->values();
    }

    public static function roles(User $user)
    {
        return $user->rolesDinamicos()->where('activo', true)->orderBy('id')->get();
    }

    public static function pantallas(User $user): array
    {
        return $user->activo ? self::roles($user)->flatMap(fn ($r) => $r->pantallas)->unique()->values()->all() : [];
    }

    public static function permite(User $user, string $pantalla): bool
    {
        return in_array($pantalla, self::pantallas($user), true);
    }

    public static function snapshot(User $user): array
    {
        $roles = $user->rolesDinamicos()->orderBy('id')->get()->map(fn ($r) => ['id' => $r->codigo, 'nombre' => $r->nombre, 'auxiliar' => $r->codigo === 'tomar_asistencia', 'activo' => $r->activo, 'pantallas' => $user->activo && $r->activo ? $r->pantallas : []])->values()->all();
        $pantallas = self::pantallas($user);
        sort($pantallas);
        $catalogo = $user->puedePantalla('admin.asignar-roles')
            ? self::catalogoRoles($user)->map(fn ($r) => ['id' => $r->codigo, 'nombre' => $r->nombre, 'activo' => $r->activo, 'auxiliar' => $r->codigo === 'tomar_asistencia', 'pantallas' => $r->pantallas])->values()->all() : [];

        return ['pantallas_adicionales' => self::adicionales($user), 'categorias_historicas' => $user->rolesAsignados()->map(fn ($r) => $r->value)->values()->all(), 'catalogo_roles' => $catalogo, 'usuario_id' => (string) $user->id, 'version' => hash('sha256', json_encode([$user->activo, $roles, $pantallas, DB::table('authorization_state')->where('id', 1)->value('revision')])),
            'roles' => $roles, 'pantallas' => $pantallas, 'mapeo_android' => Pantallas::android(),
            'politica_offline' => 'ultima_autorizacion_conocida; cambios rechazados se conservan para revision'];
    }

    public static function adicionales(User $user): array
    {
        return $user->activo ? self::roles($user)->where('codigo', 'tomar_asistencia')->flatMap(fn ($r) => $r->pantallas)->unique()->values()->all() : [];
    }

    public static function importarUsuario(User $user): void
    {
        $codigos = $user->rolesAsignados()->map(fn ($r) => $r->value)->all();
        if ($user->activo && $user->tieneRol(Rol::Administrador)) {
            DB::table('authorization_state')->where('id', 1)->whereNull('administrador_protegido')->update(['administrador_protegido' => $user->id]);
        }
        $user->rolesDinamicos()->syncWithoutDetaching(RolDinamico::whereIn('codigo', $codigos)->pluck('id'));
    }

    public static function registrar(?User $actor, string $accion, string $objeto, $antes, $despues): void
    {
        DB::table('authorization_state')->where('id', 1)->increment('revision');
        DB::table('authorization_audit')->insert(['actor_id' => $actor?->id, 'accion' => $accion, 'objeto' => $objeto,
            'antes' => json_encode($antes), 'despues' => json_encode($despues), 'created_at' => now()]);
    }

    public static function puedeDelegar(User $actor, string $pantalla): bool
    {
        $principal = (int) DB::table('authorization_state')->where('id', 1)->value('administrador_protegido');

        return isset(Pantallas::todas()[$pantalla]) && $actor->activo && ($actor->id === $principal || $actor->rolesDinamicos()->where('activo', true)->where('protegido', true)->exists() || $actor->puedePantalla($pantalla));
    }

    private static function exigirAdministrador(User $actor, string $pantalla = 'admin.roles'): void
    {
        abort_unless($actor->puedePantalla($pantalla), 403);
    }

    /** Todas las mutaciones se serializan con la misma fila para conservar al último administrador. */
    public static function guardarRol(User $actor, ?int $id, string $nombre, string $codigo, bool $activo, array $pantallas, bool $propagarUsuarios = true): RolDinamico
    {
        self::exigirAdministrador($actor);
        validator(compact('nombre', 'codigo', 'pantallas'), ['nombre' => 'required|string|max:160', 'codigo' => 'required|alpha_dash|max:100', 'pantallas' => 'array', 'pantallas.*' => ['required', Rule::in(array_keys(Pantallas::todas()))]])->validate();

        if ($codigo === 'tomar_asistencia') {
            self::error('Tomar asistencia es una pantalla, no un rol. Asígnala a un rol existente.');
        }

        return DB::transaction(function () use ($actor, $id, $nombre, $codigo, $activo, $pantallas, $propagarUsuarios) {
            DB::table('authorization_state')->where('id', 1)->lockForUpdate()->first();
            $rol = $id ? RolDinamico::findOrFail($id) : new RolDinamico;
            $antes = $rol->toArray();
            foreach ($rol->pantallas ?? [] as $p) {
                if (! self::puedeDelegar($actor, $p)) {
                    self::error('No puedes modificar un rol con funciones fuera de tu alcance.');
                }
            }
            if ($rol->exists && $rol->codigo !== $codigo) {
                self::error('El identificador de un rol existente es estable.');
            }
            if ($rol->protegido && $actor->id !== (int) DB::table('authorization_state')->where('id', 1)->value('administrador_protegido')) {
                abort(403, 'El rol protegido lo administra el administrador principal.');
            }
            if ($rol->protegido && (! $activo || array_diff(Pantallas::CRITICAS, $pantallas))) {
                self::error('El administrador principal debe conservar la administración.');
            }
            foreach ($pantallas as $p) {
                if (! self::puedeDelegar($actor, $p)) {
                    self::error('No puedes delegar funciones que no posees.');
                }
            }
            if ($rol->exists && $rol->usuarios()->whereKey($actor->id)->exists()) {
                $restantes = $actor->rolesDinamicos()->where('activo', true)->whereKeyNot($rol->id)->get()->flatMap(fn ($r) => $r->pantallas)->all();
                $futuras = [...$restantes, ...($activo ? $pantallas : [])];
                if (array_diff(array_intersect(Pantallas::CRITICAS, self::pantallas($actor)), $futuras)) {
                    self::error('No puedes deshabilitar tu propia administración crítica.');
                }
            }
            if (RolDinamico::where('codigo', $codigo)->when($id, fn ($q) => $q->whereKeyNot($id))->exists()) {
                self::error('El identificador ya existe.');
            }
            $rol->fill(['nombre' => $nombre, 'codigo' => $codigo, 'activo' => $activo, 'pantallas' => array_values(array_unique($pantallas))])->save();
            if ($propagarUsuarios) {
                $rol->usuarios()->update(['users.updated_at' => now()]);
            }
            self::registrar($actor, 'rol.guardado', (string) $rol->id, $antes, $rol->toArray());

            return $rol;
        });
    }

    public static function asignar(User $actor, User $destino, array $codigos): void
    {
        self::exigirAdministrador($actor, 'admin.asignar-roles');
        self::autorizarGestionUsuario($actor, $destino);
        DB::transaction(function () use ($actor, $destino, $codigos) {
            $state = DB::table('authorization_state')->where('id', 1)->lockForUpdate()->first();
            $roles = RolDinamico::whereIn('codigo', $codigos)->get();
            if ($roles->count() !== count(array_unique($codigos))) {
                self::error('Hay roles inexistentes o desactivados.');
            }
            foreach ($roles as $r) {
                if (! $r->activo && ! $destino->rolesDinamicos()->whereKey($r->id)->exists()) {
                    self::error('No se puede asignar un rol desactivado.');
                }
            }
            $ids = $roles->pluck('id');
            $antes = $destino->rolesDinamicos()->pluck('codigo')->all();
            $funciones = $roles->where('activo', true)->flatMap(fn ($r) => $r->pantallas)->unique()->all();
            foreach (array_diff($funciones, self::pantallas($destino)) as $p) {
                if (! self::puedeDelegar($actor, $p)) {
                    self::error('No puedes delegar funciones que no posees.');
                }
            }
            $criticas = $destino->id === (int) $state->administrador_protegido ? Pantallas::CRITICAS : array_intersect(Pantallas::CRITICAS, self::pantallas($destino));
            if (($destino->id === $actor->id || $destino->id === (int) $state->administrador_protegido) && array_diff($criticas, $funciones)) {
                self::error('No puedes retirar la administración crítica del administrador protegido ni de tu propia sesión.');
            }
            $destino->rolesDinamicos()->sync($ids);
            // Proyección de categorías/perfiles heredados: la autorización sigue en los pivotes centrales.
            $historicos = $roles->map(fn ($r) => Rol::tryFrom($r->codigo))->filter()->values();
            $atributos = ['toma_asistencia' => $roles->contains('codigo', 'tomar_asistencia')];
            if ($historicos->isNotEmpty()) {
                $atributos['rol'] = $historicos->contains($destino->rol) ? $destino->rol : $historicos->first();
                $atributos['roles'] = $historicos->map(fn ($r) => $r->value)->all();
            }
            User::withoutEvents(fn () => $destino->forceFill($atributos)->save());
            if ($historicos->contains(Rol::Acopiador)) {
                Acopiador::firstOrCreate(['user_id' => $destino->id], ['ruta_nombre' => '']);
            }
            if ($historicos->contains(Rol::Proveedor)) {
                Proveedor::firstOrCreate(['user_id' => $destino->id], ['finca_ubicacion' => '', 'finca_referencia' => '', 'bloqueado' => false, 'alteraciones' => 0]);
            }
            $destino->touch();
            self::registrar($actor, 'usuario.roles', (string) $destino->id, $antes, $codigos);
        });
    }

    public static function autorizarGestionUsuario(User $actor, User $destino): void
    {
        if ($actor->id === $destino->id) {
            return;
        }
        $principal = (int) DB::table('authorization_state')->where('id', 1)->value('administrador_protegido');
        abort_if($destino->id === $principal && $actor->id !== $principal, 403, 'El administrador principal está protegido.');
        foreach ($destino->rolesDinamicos()->where('activo', true)->get()->flatMap(fn ($r) => $r->pantallas)->unique() as $p) {
            abort_unless(self::puedeDelegar($actor, $p), 403, 'No puedes administrar una cuenta con funciones fuera de tu alcance.');
        }
    }

    public static function equipoProtegido(Team $team): bool
    {
        return (bool) DB::table('authorization_state')->where('id', 1)->value('administrador_protegido')
            && ! $team->is_personal && $team->id === (int) Team::where('is_personal', false)->min('id');
    }

    public static function protegerPertenencia(int $usuarioId, Team $team): void
    {
        $principal = (int) DB::table('authorization_state')->where('id', 1)->value('administrador_protegido');
        abort_if($usuarioId === $principal && self::equipoProtegido($team), 403, 'La pertenencia del administrador principal está protegida.');
    }

    public static function protegerUsuario(User $user): void
    {
        $protegido = DB::table('authorization_state')->where('id', 1)->value('administrador_protegido');
        if ($user->id === (int) $protegido) {
            self::error('El administrador principal está protegido.');
        }
        if (self::permite($user, 'admin.roles')) {
            $otros = User::whereKeyNot($user->id)->where('activo', true)->get()->contains(fn ($u) => self::permite($u, 'admin.roles'));
            if (! $otros) {
                self::error('Debe conservarse un administrador activo.');
            }
        }
    }

    private static function error(string $texto): never
    {
        throw ValidationException::withMessages(['rolesSeleccionados' => $texto, 'pantallas' => $texto]);
    }
}

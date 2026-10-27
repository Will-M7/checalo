<?php

namespace App\Authorization;

use App\Enums\Rol;
use App\Models\DistribucionRoles;
use App\Models\RolDinamico;
use App\Models\User;
use Illuminate\Support\Facades\Crypt;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Illuminate\Validation\ValidationException;

/** Plantillas de asignaciones; no son otra fuente de autorización activa. */
final class Distribuciones
{
    public static function registrarBases(): void
    {
        DB::transaction(function () {
            self::bloquear();
            $primera = ! DistribucionRoles::where('codigo', 'inicial')->exists();
            $nueva = $primera && ! User::exists();
            foreach (['inicial' => 'Inicial', 'en-desarrollo' => 'En desarrollo'] as $codigo => $nombre) {
                if (DistribucionRoles::where('codigo', $codigo)->exists()) {
                    continue;
                }
                $datos = json_decode(file_get_contents(resource_path("authorization/$codigo.json")), true, flags: JSON_THROW_ON_ERROR);
                $asignaciones = [];
                foreach ($datos['asignaciones'] as $rol => $pantallas) {
                    $registro = RolDinamico::where('codigo', $rol)->first();
                    if (! $registro && $nueva) {
                        $registro = RolDinamico::create(['codigo' => $rol, 'nombre' => Rol::from($rol)->label(), 'activo' => true, 'protegido' => $rol === 'administrador', 'pantallas' => []]);
                    }
                    $asignaciones[] = ['codigo' => $rol, 'rol_id' => $registro?->id, 'pantallas' => $pantallas];
                    if ($nueva && $codigo === 'inicial') {
                        $registro->update(['pantallas' => $pantallas]);
                    }
                }
                DistribucionRoles::create(['codigo' => $codigo, 'nombre' => $nombre, 'tipo' => 'base', 'version' => $datos['version'], 'predeterminada' => $codigo === 'inicial', 'asignaciones' => $asignaciones, 'origen' => $datos['origen']]);
            }
            self::retirarAuxiliar();
            if ($nueva) {
                Autorizacion::registrar(null, 'distribucion.inicializacion', 'inicial', null, ['primera_instalacion' => true]);
            }
        });
    }

    private static function bloquear(): void
    {
        DB::table('authorization_state')->where('id', 1)->lockForUpdate()->first();
    }

    private static function exigir(User $actor): void
    {
        abort_unless($actor->activo && $actor->puedePantalla('admin.roles'), 403);
    }

    private static function error(string $texto): never
    {
        throw ValidationException::withMessages(['distribucion' => $texto]);
    }

    /** El auxiliar solo se retira cuando no quedan dependencias efectivas. */
    private static function retirarAuxiliar(): void
    {
        $r = RolDinamico::where('codigo', 'tomar_asistencia')->first();
        if (! $r || $r->usuarios()->exists() || User::where('toma_asistencia', true)->exists()) {
            return;
        }
        if (DistribucionRoles::get()->contains(fn ($d) => collect($d->asignaciones)->contains('codigo', 'tomar_asistencia'))) {
            return;
        }
        $antes = $r->toArray();
        $r->delete();
        Autorizacion::registrar(null, 'rol.auxiliar-retirado', 'tomar_asistencia', $antes, null);
    }

    private static function actuales(): array
    {
        return RolDinamico::orderBy('id')->get()->map(fn ($r) => ['codigo' => $r->codigo, 'rol_id' => $r->id, 'pantallas' => $r->pantallas])->all();
    }

    private static function ordenar(array $valores): array
    {
        $valores = array_values(array_unique($valores));
        sort($valores);

        return $valores;
    }

    /** Incluye la revisión para detectar incluso cambios posteriormente deshechos (ABA). */
    private static function huella(): string
    {
        return hash('sha256', json_encode([
            DB::table('authorization_state')->where('id', 1)->first(),
            RolDinamico::orderBy('id')->get()->toArray(),
            DB::table('authorization_role_user')->orderBy('role_id')->orderBy('user_id')->get()->toArray(),
            DB::table('users')->orderBy('id')->get(['id', 'activo', 'updated_at'])->toArray(),
        ], JSON_THROW_ON_ERROR));
    }

    public static function catalogo(User $actor): array
    {
        self::exigir($actor);
        $actuales = collect(self::actuales())->keyBy('codigo');
        $distribuciones = DistribucionRoles::orderBy('id')->get()->map(function ($d) use ($actuales) {
            $datos = $d->toArray();
            $datos['coincide'] = collect($d->asignaciones)->every(fn ($a) => isset($actuales[$a['codigo']]) && $actuales[$a['codigo']]['rol_id'] === $a['rol_id'] && self::ordenar($actuales[$a['codigo']]['pantallas']) === self::ordenar($a['pantallas']));

            return $datos;
        })->all();
        $prueba = RolDinamico::where('codigo', '001')->first();
        $huella = self::huella();

        return [
            'distribuciones' => $distribuciones,
            'roles' => RolDinamico::orderBy('id')->get()->map(fn ($r) => ['registro_id' => $r->id, 'codigo' => $r->codigo, 'nombre' => $r->nombre, 'activo' => $r->activo, 'protegido' => $r->protegido, 'pantallas' => $r->pantallas])->all(),
            'pantallas' => collect(Pantallas::todas())->map(fn ($p, $id) => ['id' => $id, 'nombre' => $p['label'], 'area' => $p['area'], 'android' => $p['android'], 'delegable' => Autorizacion::puedeDelegar($actor, $id)])->values()->all(),
            'historial' => DB::table('authorization_distribution_runs')->orderByDesc('created_at')->orderByDesc('id')->limit(30)->get()->map(fn ($r) => ['id' => $r->id, 'nombre' => $r->nombre, 'tipo' => $r->tipo, 'fecha' => $r->created_at, 'actor_id' => $r->actor_id, 'recuperacion_de' => $r->recuperacion_de, 'recuperable' => $r->tipo === 'aplicacion' && $r->huella_posterior === $huella && ! DB::table('authorization_distribution_runs')->where('recuperacion_de', $r->id)->exists()])->all(),
            'usuarios_prueba' => $prueba ? $prueba->usuarios()->orderBy('users.id')->get()->map(fn ($u) => self::usuario($u))->all() : [],
            'rol_prueba_presente' => $prueba !== null,
            'auxiliar_pendiente' => RolDinamico::where('codigo', 'tomar_asistencia')->exists(),
            'autorizacion' => Autorizacion::snapshot($actor),
        ];
    }

    private static function usuario(User $u): array
    {
        return ['id' => (string) $u->id, 'nombre' => trim($u->nombres.' '.$u->apellidos), 'roles' => $u->rolesDinamicos()->pluck('codigo')->all()];
    }

    public static function guardar(User $actor, array $datos): DistribucionRoles
    {
        self::exigir($actor);
        validator($datos, [
            'id' => 'nullable|integer', 'version' => 'nullable|integer|min:1', 'nombre' => 'required|string|max:160',
            'asignaciones' => 'required|array|min:1', 'asignaciones.*.codigo' => 'required|string|distinct',
            'asignaciones.*.pantallas' => 'present|array', 'asignaciones.*.pantallas.*' => 'required|string',
        ])->validate();

        return DB::transaction(function () use ($actor, $datos) {
            self::bloquear();
            $d = ! empty($datos['id']) ? DistribucionRoles::findOrFail($datos['id']) : new DistribucionRoles;
            if ($d->exists) {
                if ($d->tipo === 'base') {
                    self::error('Las plantillas base están protegidas. Crea una copia.');
                }
                abort_unless($d->version === ($datos['version'] ?? null), 409, 'La distribución cambió. Actualiza antes de guardar.');
            }
            $asignaciones = [];
            foreach ($datos['asignaciones'] as $a) {
                $r = RolDinamico::where('codigo', $a['codigo'])->first();
                if (! $r || $r->codigo === 'tomar_asistencia') {
                    self::error('La distribución contiene un rol inexistente o auxiliar.');
                }
                if (array_diff($a['pantallas'], array_keys(Pantallas::todas()))) {
                    self::error('La distribución contiene pantallas inexistentes.');
                }
                $asignaciones[] = ['codigo' => $r->codigo, 'rol_id' => $r->id, 'pantallas' => self::ordenar($a['pantallas'])];
            }
            $antes = $d->exists ? $d->toArray() : null;
            $d->fill(['nombre' => trim($datos['nombre']), 'tipo' => 'personalizada', 'predeterminada' => false, 'asignaciones' => $asignaciones, 'version' => $d->exists ? $d->version + 1 : 1]);
            if (! $d->exists) {
                $d->codigo = (string) Str::uuid();
            }
            $d->save();
            // Editar una plantilla no cambia la autorización ni invalida trabajos offline.
            DB::table('authorization_audit')->insert(['actor_id' => $actor->id, 'accion' => 'distribucion.guardada', 'objeto' => (string) $d->id, 'antes' => json_encode($antes), 'despues' => json_encode($d->toArray()), 'created_at' => now()]);

            return $d;
        });
    }

    public static function copiar(User $actor, string $nombre, ?int $origen = null): DistribucionRoles
    {
        self::exigir($actor);
        $asignaciones = $origen ? DistribucionRoles::findOrFail($origen)->asignaciones : array_values(array_filter(self::actuales(), fn ($a) => $a['codigo'] !== 'tomar_asistencia'));

        return self::guardar($actor, ['nombre' => $nombre, 'asignaciones' => $asignaciones]);
    }

    private static function comparar(User $actor, array $destino): array
    {
        $filas = [];
        $errores = [];
        $usados = [];
        foreach ($destino as $a) {
            $r = RolDinamico::where('codigo', $a['codigo'])->first();
            $usados[] = $a['codigo'];
            if (! $r || $r->id !== $a['rol_id']) {
                $errores[] = 'Rol ausente o con otra identidad: '.$a['codigo'];
                $filas[] = ['codigo' => $a['codigo'], 'nombre' => $a['codigo'].' · Referencia ausente', 'activo' => false, 'actuales' => [], 'destino' => $a['pantallas'], 'agrega' => $a['pantallas'], 'retira' => [], 'usuarios' => []];

                continue;
            }
            $faltantes = array_diff($a['pantallas'], array_keys(Pantallas::todas()));
            if ($faltantes) {
                $errores[] = 'Pantallas inexistentes: '.implode(', ', $faltantes);
            }
            if ($r->protegido && (! $r->activo || array_diff(Pantallas::CRITICAS, $a['pantallas']))) {
                $errores[] = 'El administrador protegido debe conservar las tres pantallas de administración.';
            }
            $agrega = array_values(array_diff($a['pantallas'], $r->pantallas));
            $retira = array_values(array_diff($r->pantallas, $a['pantallas']));
            if ($agrega || $retira) {
                if ($r->protegido && $actor->id !== (int) DB::table('authorization_state')->where('id', 1)->value('administrador_protegido')) {
                    $errores[] = 'Solo el administrador principal puede cambiar su rol protegido.';
                }
                foreach (array_unique([...$r->pantallas, ...$a['pantallas']]) as $p) {
                    if (! Autorizacion::puedeDelegar($actor, $p)) {
                        $errores[] = 'Fuera de tu alcance: '.$r->codigo.' / '.$p;
                    }
                }
            }
            $usuarios = ($agrega || $retira) ? $r->usuarios()->orderBy('users.id')->get()->filter(function ($u) use ($destino) {
                if (! $u->activo) {
                    return false;
                }
                $porRol = collect($destino)->pluck('pantallas', 'codigo');
                $futuras = $u->rolesDinamicos()->where('activo', true)->get()->flatMap(fn ($rol) => $porRol->get($rol->codigo, $rol->pantallas))->all();

                return self::ordenar(Autorizacion::pantallas($u)) !== self::ordenar($futuras);
            })->map(fn ($u) => self::usuario($u))->values()->all() : [];
            $filas[] = ['codigo' => $r->codigo, 'nombre' => $r->nombre, 'activo' => $r->activo, 'actuales' => $r->pantallas, 'destino' => $a['pantallas'], 'agrega' => $agrega, 'retira' => $retira, 'usuarios' => $usuarios];
        }
        $porRol = collect($destino)->pluck('pantallas', 'codigo');
        $futurasActor = $actor->rolesDinamicos()->where('activo', true)->get()->flatMap(fn ($rol) => $porRol->get($rol->codigo, $rol->pantallas))->all();
        if (array_diff(array_intersect(Pantallas::CRITICAS, Autorizacion::pantallas($actor)), $futurasActor)) {
            $errores[] = 'No puedes retirar la administración crítica de tu propia sesión.';
        }
        $sinCambios = RolDinamico::whereNotIn('codigo', $usados)->orderBy('id')->pluck('nombre')->all();

        return ['roles' => $filas, 'errores' => $errores, 'no_afectados' => $sinCambios];
    }

    public static function previsualizar(User $actor, string $operacion, ?int $distribucionId = null, ?string $aplicacionId = null, array $reemplazos = []): array
    {
        self::exigir($actor);

        return DB::transaction(function () use ($actor, $operacion, $distribucionId, $aplicacionId, $reemplazos) {
            self::bloquear();
            $base = ['actor' => $actor->id, 'operacion' => $operacion, 'distribucion_id' => $distribucionId, 'aplicacion_id' => $aplicacionId, 'reemplazos' => $reemplazos, 'huella' => self::huella(), 'vence' => now()->addMinutes(10)->timestamp];
            $advertencias = [];
            if ($operacion === 'aplicar') {
                $d = DistribucionRoles::findOrFail($distribucionId);
                $base['version'] = $d->version;
                $vista = self::comparar($actor, $d->asignaciones);
                $nombre = $d->nombre;
                if ($d->codigo === 'en-desarrollo') {
                    $advertencias[] = 'Referencia histórica adaptada: Configuración de pagos no existía; admin.roles se conserva para recuperar el control. No se reproducen excepciones individuales de asistencia ni vías API antiguas.';
                }
            } elseif ($operacion === 'recuperar') {
                $r = DB::table('authorization_distribution_runs')->where('id', $aplicacionId)->first();
                abort_unless($r && $r->tipo === 'aplicacion', 404);
                $vista = self::comparar($actor, json_decode($r->antes, true));
                if ($r->huella_posterior !== self::huella() || DB::table('authorization_distribution_runs')->where('recuperacion_de', $r->id)->exists()) {
                    $vista['errores'][] = 'Conflicto: hubo cambios de autorización o usuarios posteriores. No se sobrescribirán. Compara y crea una nueva distribución.';
                }
                $nombre = 'Recuperar: '.$r->nombre;
            } elseif ($operacion === 'retirar-001') {
                $vista = self::compararReemplazos($actor, $reemplazos);
                $nombre = 'Reasignar usuarios y retirar ventas (001)';
                $advertencias[] = 'Los roles elegidos pueden conceder accesos diferentes. Se conservan los demás roles y todos los registros de cada cuenta.';
            } else {
                self::error('Operación desconocida.');
            }
            if ($operacion !== 'retirar-001') {
                $porRol = collect($vista['roles'])->pluck('destino', 'codigo');
                $cubiertas = RolDinamico::where('activo', true)->get()->flatMap(fn ($rol) => $porRol->get($rol->codigo, $rol->pantallas))->unique()->all();
                $sinAsignacion = array_diff(array_keys(Pantallas::todas()), $cubiertas);
                if ($sinAsignacion) {
                    $advertencias[] = 'Pantallas que quedarían sin roles activos: '.implode(', ', $sinAsignacion);
                }
            }

            return [...$vista, 'nombre' => $nombre, 'operacion' => $operacion, 'advertencias' => $advertencias, 'token' => $vista['errores'] ? null : Crypt::encryptString(json_encode($base, JSON_THROW_ON_ERROR))];
        });
    }

    private static function compararReemplazos(User $actor, array $reemplazos): array
    {
        abort_unless($actor->puedePantalla('admin.asignar-roles'), 403);
        $rol = RolDinamico::where('codigo', '001')->firstOrFail();
        $errores = [];
        $filas = [];
        $usuarios = $rol->usuarios()->orderBy('users.id')->get();
        if (count($reemplazos) !== $usuarios->count()) {
            $errores[] = 'Selecciona explícitamente un rol para cada usuario que depende de 001.';
        }
        foreach ($usuarios as $u) {
            $codigo = collect($reemplazos)->firstWhere('usuario_id', (string) $u->id)['codigo'] ?? null;
            $r = $codigo ? RolDinamico::where('codigo', $codigo)->where('activo', true)->first() : null;
            if (! $r || in_array($r->codigo, ['001', 'tomar_asistencia'], true)) {
                $errores[] = 'Falta un rol válido para '.$u->nombres.' (ID '.$u->id.').';

                continue;
            }
            Autorizacion::autorizarGestionUsuario($actor, $u);
            foreach ($r->pantallas as $p) {
                if (! Autorizacion::puedeDelegar($actor, $p)) {
                    $errores[] = 'No puedes delegar '.$p;
                }
            }
            $actuales = Autorizacion::pantallas($u);
            $destino = $u->rolesDinamicos()->where('activo', true)->where('codigo', '!=', '001')->get()->flatMap(fn ($r) => $r->pantallas)->merge($r->pantallas)->unique()->values()->all();
            $filas[] = ['codigo' => 'usuario:'.$u->id, 'nombre' => $u->nombres.' → '.$r->nombre, 'activo' => (bool) $u->activo, 'actuales' => $actuales, 'destino' => $destino, 'agrega' => array_values(array_diff($destino, $actuales)), 'retira' => array_values(array_diff($actuales, $destino)), 'usuarios' => [self::usuario($u)]];
        }

        return ['roles' => $filas, 'errores' => $errores, 'no_afectados' => RolDinamico::where('codigo', '!=', '001')->pluck('nombre')->all()];
    }

    public static function confirmar(User $actor, string $token, bool $confirmado): void
    {
        self::exigir($actor);
        if (! $confirmado) {
            self::error('Debes confirmar la vista previa antes de aplicar cambios.');
        }
        try {
            $datos = json_decode(Crypt::decryptString($token), true, flags: JSON_THROW_ON_ERROR);
        } catch (\Throwable) {
            self::error('La vista previa no es válida.');
        }
        abort_unless(($datos['actor'] ?? null) === $actor->id && ($datos['vence'] ?? 0) >= now()->timestamp, 409, 'La vista previa venció o pertenece a otra sesión.');
        DB::transaction(function () use ($actor, $datos) {
            self::bloquear();
            self::exigir($actor);
            abort_unless($datos['huella'] === self::huella(), 409, 'Las asignaciones o los usuarios cambiaron. Genera una nueva vista previa.');
            $vista = self::previsualizar($actor, $datos['operacion'], $datos['distribucion_id'], $datos['aplicacion_id'], $datos['reemplazos']);
            if ($vista['errores']) {
                self::error(implode(' ', $vista['errores']));
            }
            if ($datos['operacion'] === 'retirar-001') {
                $rol = RolDinamico::where('codigo', '001')->firstOrFail();
                foreach ($rol->usuarios()->get() as $u) {
                    $codigo = collect($datos['reemplazos'])->firstWhere('usuario_id', (string) $u->id)['codigo'];
                    $codigos = $u->rolesDinamicos()->where('codigo', '!=', '001')->pluck('codigo')->push($codigo)->unique()->values()->all();
                    Autorizacion::asignar($actor, $u, $codigos);
                }
                if ($rol->usuarios()->exists()) {
                    self::error('Quedan usuarios asociados al rol.');
                }
                $antes = $rol->toArray();
                $rol->delete();
                Autorizacion::registrar($actor, 'rol.prueba-retirado', '001', $antes, null);

                return;
            }
            $d = $datos['operacion'] === 'aplicar' ? DistribucionRoles::findOrFail($datos['distribucion_id']) : null;
            if ($d) {
                abort_unless($d->version === $datos['version'], 409, 'La plantilla cambió. Genera una nueva vista previa.');
            }
            $cambios = array_values(array_filter($vista['roles'], fn ($r) => $r['agrega'] || $r['retira']));
            if (! $cambios) {
                return;
            }
            $antes = self::actuales();
            foreach ($cambios as $fila) {
                $rol = RolDinamico::where('codigo', $fila['codigo'])->firstOrFail();
                Autorizacion::guardarRol($actor, $rol->id, $rol->nombre, $rol->codigo, $rol->activo, $fila['destino'], propagarUsuarios: false);
            }
            $despues = self::actuales();
            $id = (string) Str::uuid();
            Autorizacion::registrar($actor, 'distribucion.'.$datos['operacion'], $id, $antes, $despues);
            DB::table('authorization_distribution_runs')->insert([
                'id' => $id, 'distribution_id' => $d?->id ?? DB::table('authorization_distribution_runs')->where('id', $datos['aplicacion_id'])->value('distribution_id'),
                'nombre' => $d?->nombre ?? $vista['nombre'], 'version' => $d?->version ?? 1,
                'tipo' => $d ? 'aplicacion' : 'recuperacion', 'actor_id' => $actor->id,
                'antes' => json_encode($antes), 'despues' => json_encode($despues), 'huella_posterior' => self::huella(),
                'recuperacion_de' => $d ? null : $datos['aplicacion_id'], 'created_at' => now(),
            ]);
        });
    }
}

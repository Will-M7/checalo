<?php

namespace App\Actions;

use App\Authorization\Autorizacion;
use App\Enums\Rol;
use App\Enums\TeamRole;
use App\Models\Proveedor;
use App\Models\Team;
use App\Models\User;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Illuminate\Validation\Rule;

/** El formulario web y la administración móvil usan la misma escritura y asignación central. */
final class GuardarUsuarioConRoles
{
    public function __invoke(User $actor, ?int $id, array $datos): User
    {
        abort_unless($actor->puedePantalla('admin.usuarios'), 403);
        $user = $id ? User::findOrFail($id) : null;
        if ($user) {
            Autorizacion::autorizarGestionUsuario($actor, $user);
        }
        $rules = [
            'nombres' => 'required|string|max:255', 'apellidos' => 'required|string|max:255',
            'documento' => ['required', 'string', 'max:15', Rule::unique('users', 'documento')->ignore($id)],
            'celular' => 'nullable|string|max:20',
            'usuario' => ['required', 'string', 'max:255', Rule::unique('users', 'usuario')->ignore($id)],
            'password' => $id ? 'nullable|string|min:4' : 'required|string|min:4',
            'activo' => 'required|boolean', 'rolesSeleccionados' => 'present|array', 'rolesSeleccionados.*' => 'required|string|distinct',
        ];
        if (in_array(Rol::Proveedor->value, $datos['rolesSeleccionados'] ?? [], true)) {
            $rules += ['finca_ubicacion' => 'required|string|max:255', 'proveedorAcopiadorId' => 'required|exists:acopiadores,id'];
        }
        $data = validator($datos, $rules)->validate();

        return DB::transaction(function () use ($actor, $user, $data, $datos) {
            $nuevo = ! $user;
            $user ??= new User;
            $antes = $nuevo ? [] : $user->rolesDinamicos()->pluck('codigo')->all();
            $cambian = $nuevo || array_diff($antes, $data['rolesSeleccionados']) || array_diff($data['rolesSeleccionados'], $antes);
            if ($cambian) {
                abort_unless($actor->puedePantalla('admin.asignar-roles'), 403);
            }
            $payload = collect($data)->only(['nombres', 'apellidos', 'documento', 'celular', 'usuario', 'activo'])->all();
            $payload['name'] = $data['nombres'].' '.$data['apellidos'];
            if (! empty($data['password'])) {
                $payload['password'] = Hash::make($data['password']);
            }
            if ($nuevo) {
                $payload += ['rol' => null, 'roles' => [], 'email' => $data['usuario'].'@ecolacteoshuata.pe', 'email_verified_at' => now()];
            }
            $user->forceFill($payload)->save();
            $proveedor = in_array(Rol::Proveedor->value, $data['rolesSeleccionados'], true);
            $finca = [
                'finca_ubicacion' => $data['finca_ubicacion'] ?? '', 'finca_referencia' => $datos['finca_referencia'] ?? '',
                'latitud' => $datos['latitud'] ?? null, 'longitud' => $datos['longitud'] ?? null,
                'acopiador_id' => $data['proveedorAcopiadorId'] ?? null,
            ];
            if ($nuevo && $proveedor) {
                Proveedor::guardarConUbicacion($user->id, $finca);
            }
            if ($cambian) {
                Autorizacion::asignar($actor, $user, $data['rolesSeleccionados']);
            }
            if ($nuevo) {
                $team = Team::principal();
                $team->members()->attach($user, ['role' => TeamRole::Owner->value]);
                $user->switchTeam($team);
            }
            if (! $nuevo && $proveedor) {
                Proveedor::guardarConUbicacion($user->id, $finca);
            }

            return $user->fresh();
        });
    }
}

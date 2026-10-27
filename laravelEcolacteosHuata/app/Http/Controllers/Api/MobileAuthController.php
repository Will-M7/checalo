<?php

namespace App\Http\Controllers\Api;

use App\Authorization\Autorizacion;
use App\Http\Controllers\Controller;
use App\Models\User;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Validator;
use Illuminate\Support\Str;

class MobileAuthController extends Controller
{
    /**
     * Valida usuario/contraseña contra la base de datos MySQL y devuelve los datos del
     * usuario para que la app móvil (Kotlin Multiplatform) los guarde localmente y siga
     * trabajando offline-first, tal como ya hace con las cuentas creadas en el propio
     * dispositivo. También entrega el token con el que el teléfono pide `/api/mobile/sync`.
     */
    public function login(Request $request): JsonResponse
    {
        $validator = Validator::make($request->all(), [
            'usuario' => ['required', 'string'],
            'password' => ['required', 'string'],
        ]);

        if ($validator->fails()) {
            return response()->json([
                'message' => 'Usuario y contraseña son obligatorios.',
            ], 422);
        }

        $usuario = trim((string) $request->input('usuario'));
        $password = (string) $request->input('password');

        $user = User::query()->where('usuario', $usuario)->first();

        if (! $user || ! $user->activo || ! Hash::check($password, $user->password)) {
            return response()->json([
                'message' => 'Usuario o contraseña incorrectos.',
            ], 401);
        }

        $token = Str::random(60);

        DB::table('mobile_tokens')->insert([
            'user_id' => $user->id,
            'token' => hash('sha256', $token),
            'created_at' => now(),
            'updated_at' => now(),
        ]);

        return response()->json([
            'token' => $token,
            'id' => (string) $user->id,
            'usuario' => (string) $user->usuario,
            'nombres' => (string) $user->nombres,
            'apellidos' => (string) $user->apellidos,
            'documento' => (string) ($user->documento ?? ''),
            'celular' => (string) ($user->celular ?? ''),
            'rol' => $user->rol?->value,
            'activo' => (bool) $user->activo,
            'toma_asistencia' => (bool) $user->toma_asistencia,
            'autorizacion' => Autorizacion::snapshot($user),
            'roles' => $user->rolesAsignados()->map(fn ($r) => $r->value)->values()->all(),
        ]);
    }
}

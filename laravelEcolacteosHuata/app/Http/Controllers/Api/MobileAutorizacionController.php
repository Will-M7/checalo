<?php

namespace App\Http\Controllers\Api;

use App\Actions\GuardarUsuarioConRoles;
use App\Authorization\Autorizacion;
use App\Authorization\Pantallas;
use App\Http\Controllers\Controller;
use App\Models\RolDinamico;
use App\Models\User;
use Illuminate\Http\Request;

class MobileAutorizacionController extends Controller
{
    public function catalogo(Request $request)
    {
        $actor = $request->user();
        abort_unless($actor->puedePantalla('admin.roles'), 403);

        return response()->json([
            'roles' => RolDinamico::orderBy('id')->get()->map(fn ($r) => [
                'registro_id' => $r->id, 'codigo' => $r->codigo, 'nombre' => $r->nombre,
                'activo' => $r->activo, 'protegido' => $r->protegido, 'pantallas' => $r->pantallas,
            ]),
            'pantallas' => collect(Pantallas::todas())->map(fn ($p, $id) => [
                'id' => $id, 'nombre' => $p['label'], 'area' => $p['area'], 'android' => $p['android'],
                'delegable' => Autorizacion::puedeDelegar($actor, $id),
            ])->values(),
            'autorizacion' => Autorizacion::snapshot($actor),
        ]);
    }

    public function guardarRol(Request $request, ?int $rol = null)
    {
        abort_unless($request->user()->puedePantalla('admin.roles'), 403);
        $data = $request->validate(['nombre' => 'required|string|max:160', 'codigo' => 'required|string|max:100', 'activo' => 'required|boolean', 'pantallas' => 'present|array', 'pantallas.*' => 'required|string|distinct']);
        Autorizacion::guardarRol($request->user(), $rol, $data['nombre'], $data['codigo'], $data['activo'], $data['pantallas']);

        return $this->catalogo($request);
    }

    public function guardarUsuario(Request $request, ?int $usuario = null)
    {
        $user = app(GuardarUsuarioConRoles::class)($request->user(), $usuario, $request->all());

        return response()->json(['id' => (string) $user->id, 'autorizacion' => Autorizacion::snapshot($user)]);
    }

    public function show(Request $request)
    {
        return response()->json(Autorizacion::snapshot($request->user()));
    }

    public function asignar(Request $request)
    {
        $data = $request->validate(['usuario_id' => 'required|integer|exists:users,id', 'roles' => 'present|array', 'roles.*' => 'required|string']);
        $user = User::findOrFail($data['usuario_id']);
        Autorizacion::asignar($request->user(), $user, $data['roles']);

        return response()->json(Autorizacion::snapshot($user));
    }
}

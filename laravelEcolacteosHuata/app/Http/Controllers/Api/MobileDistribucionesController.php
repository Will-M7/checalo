<?php

namespace App\Http\Controllers\Api;

use App\Authorization\Distribuciones;
use App\Http\Controllers\Controller;
use Illuminate\Http\Request;

class MobileDistribucionesController extends Controller
{
    public function index(Request $request)
    {
        return response()->json(Distribuciones::catalogo($request->user()));
    }

    public function guardar(Request $request)
    {
        $datos = $request->validate(['modo' => 'required|in:guardar,copiar', 'nombre' => 'required|string|max:160', 'origen' => 'nullable|integer', 'id' => 'nullable|integer', 'version' => 'nullable|integer', 'asignaciones' => 'sometimes|array', 'asignaciones.*.codigo' => 'required|string', 'asignaciones.*.pantallas' => 'present|array', 'asignaciones.*.pantallas.*' => 'required|string']);
        if ($datos['modo'] === 'copiar') {
            $distribucion = Distribuciones::copiar($request->user(), $datos['nombre'], $datos['origen'] ?? null);
        } else {
            $distribucion = Distribuciones::guardar($request->user(), $datos);
        }

        return response()->json([...Distribuciones::catalogo($request->user()), 'guardada_id' => $distribucion->id]);
    }

    public function previsualizar(Request $request)
    {
        $datos = $request->validate(['operacion' => 'required|in:aplicar,recuperar,retirar-001', 'distribucion_id' => 'nullable|integer', 'aplicacion_id' => 'nullable|uuid', 'reemplazos' => 'sometimes|array', 'reemplazos.*.usuario_id' => 'required|string|distinct', 'reemplazos.*.codigo' => 'required|string']);

        return response()->json(Distribuciones::previsualizar($request->user(), $datos['operacion'], $datos['distribucion_id'] ?? null, $datos['aplicacion_id'] ?? null, $datos['reemplazos'] ?? []));
    }

    public function confirmar(Request $request)
    {
        $datos = $request->validate(['token' => 'required|string', 'confirmar' => 'required|boolean']);
        Distribuciones::confirmar($request->user(), $datos['token'], $datos['confirmar']);

        return $this->index($request);
    }
}

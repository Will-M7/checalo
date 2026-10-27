<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\{MovimientoFinanciero, ReceptorPago, User};
use App\Services\Finanzas\{ConsultasFinanzas, FinanzasService};
use Illuminate\Http\Request;

final class MobileFinanzasController extends Controller
{
    private function filtros(Request $request): array
    {
        return $request->validate(['desde' => 'nullable|date_format:Y-m-d', 'hasta' => 'nullable|date_format:Y-m-d',
            'vendedor' => 'nullable|integer', 'receptor' => 'nullable|integer', 'tipo' => 'nullable|string|max:40',
            'busqueda' => 'nullable|string|max:160', 'estado' => 'nullable|in:confirmado,no_conciliado',
            'busquedaCliente' => 'nullable|string|max:160', 'saldoCliente' => 'nullable|in:positivo,cero',
            'vendedorCliente' => 'nullable|integer', 'page' => 'nullable|integer|min:1']);
    }

    public function index(Request $request, ConsultasFinanzas $consultas, FinanzasService $service)
    {
        $query = $consultas->movimientos($request->user(), $this->filtros($request));
        $usuarios = User::orderBy('id')->get()->map(fn ($u) => ['id' => $u->id, 'nombre' => $u->nombreCompleto()]);
        $movimientos = $query->latest('id')->paginate(25);
        $movimientos->through(fn ($m) => [...$m->toArray(), 'pendiente_rendir_minor' => $m->pendienteRendir()]);
        return response()->json(['resumen' => $service->resumen(), 'actividad' => $consultas->actividad($consultas->movimientos($request->user(), $this->filtros($request))),
            'movimientos' => $movimientos, 'usuarios' => $usuarios,
            'receptores' => ReceptorPago::orderBy('id')->get()->map(fn ($r) => ['id' => $r->id, 'nombre' => $r->nombre_receptor]),
            'tipos' => MovimientoFinanciero::distinct()->pluck('tipo')]);
    }

    public function clientes(Request $request, ConsultasFinanzas $consultas)
    {
        $result = $consultas->clientes($request->user(), $this->filtros($request))->paginate(25);
        $result->through(fn ($c) => ['id' => $c->id, 'nombre' => $c->nombreCompleto(), 'contacto' => $c->celular, 'email' => $c->email,
            'saldo_minor' => (int) $c->saldo_minor, 'recargado_minor' => (int) $c->recargado_minor, 'consumido_minor' => -(int) $c->consumido_negativo_minor,
            'pedidos_total' => (int) $c->pedidos_total, 'pedidos_pendientes' => (int) $c->pedidos_pendientes, 'pedidos_pagados' => (int) $c->pedidos_pagados]);
        return response()->json($result);
    }

    public function pedidos(Request $request, int $cliente, ConsultasFinanzas $consultas)
    {
        $this->filtros($request);
        $result = $consultas->pedidosCliente($request->user(), $cliente)->paginate(25);
        $result->through(fn ($p) => ['id' => $p->id, 'fecha' => $p->created_at->toIso8601String(), 'vendedor_id' => $p->vendedor_asignado_id,
            'vendedor' => $p->vendedor?->nombreCompleto() ?? ($p->receptor_snapshot['vendedor_nombre'] ?? 'Sin registro'), 'total' => $p->total, 'estado' => $p->estado->value, 'pago_estado' => $p->pago_estado]);
        return response()->json($result);
    }

    public function rendir(Request $request, FinanzasService $service)
    {
        abort_unless($request->user()->puedePantalla('admin.finanzas'), 403);
        $d = $request->validate(['ingreso' => 'required|integer', 'amount_minor' => 'required|integer|min:1', 'clave' => 'required|uuid', 'referencia' => 'required|string|min:10|max:180', 'recibido' => 'required|accepted']);
        $m = $service->rendir($request->user(), $d['ingreso'], $d['amount_minor'], $d['clave'], $d['referencia']);
        return response()->json(['id' => $m->id, 'confirmado' => true]);
    }
}

<?php

namespace App\Services\Finanzas;

use App\Models\{ClienteWeb, IntentoPago, MovimientoFinanciero, MovimientoSaldo, PedidoWeb, User};
use Illuminate\Support\Facades\DB;

/** Consultas compartidas por Web y Android; importes enteros del libro vigente. */
final class ConsultasFinanzas
{
    public function autorizar(User $actor): void
    {
        abort_unless($actor->activo && $actor->puedePantalla('admin.finanzas'), 403);
    }

    public function movimientos(User $actor, array $f)
    {
        $this->autorizar($actor);
        return MovimientoFinanciero::query()
            ->when($f['busqueda'] ?? '', fn ($q, $v) => $q->where(fn ($t) => $t->where('origen', 'like', '%'.$v.'%')->orWhere('tipo', 'like', '%'.$v.'%')->orWhere('clave', 'like', '%'.$v.'%')))
            ->when($f['estado'] ?? '', fn ($q, $v) => $q->where('estado', $v))
            ->when($f['desde'] ?? '', fn ($q, $v) => $q->where('created_at', '>=', $v.' 00:00:00'))
            ->when($f['hasta'] ?? '', fn ($q, $v) => $q->where('created_at', '<=', $v.' 23:59:59'))
            ->when($f['vendedor'] ?? '', fn ($q, $v) => $q->where('vendedor_id', $v))
            ->when($f['receptor'] ?? '', fn ($q, $v) => $q->where('receptor_pago_id', $v))
            ->when($f['tipo'] ?? '', fn ($q, $v) => $q->where('tipo', $v));
    }

    public function actividad($query): array
    {
        $result = [];
        foreach (['recaudado', 'ventas', 'recargas', 'rendido', 'egresos'] as $col) $result[$col] = (int) (clone $query)->sum($col.'_minor');
        return $result;
    }

    public function clientes(User $actor, array $f)
    {
        $this->autorizar($actor);
        $saldo = MovimientoSaldo::selectRaw('coalesce(sum(amount_minor), 0)')->whereColumn('cliente_web_id', 'clientes_web.id')->where('estado', 'aplicado');
        $consumo = (clone $saldo)->where('amount_minor', '<', 0);
        $recargas = IntentoPago::selectRaw('coalesce(sum(amount_minor), 0)')->whereColumn('cliente_web_id', 'clientes_web.id')->where('proposito', 'recarga')->where('estado', 'PAID')->whereNotNull('applied_at');
        $pedidos = PedidoWeb::selectRaw('count(*)')->whereColumn('cliente_web_id', 'clientes_web.id');
        return ClienteWeb::query()->select('clientes_web.*')->selectSub($saldo, 'saldo_minor')->selectSub($consumo, 'consumido_negativo_minor')
            ->selectSub($recargas, 'recargado_minor')->selectSub($pedidos, 'pedidos_total')
            ->selectSub((clone $pedidos)->where('estado', 'pendiente'), 'pedidos_pendientes')
            ->selectSub((clone $pedidos)->where('pago_estado', 'pagado'), 'pedidos_pagados')
            ->when($f['busquedaCliente'] ?? '', fn ($q, $v) => $q->where(fn ($s) => $s->where('nombres', 'like', '%'.$v.'%')->orWhere('apellidos', 'like', '%'.$v.'%')->orWhere('celular', 'like', '%'.$v.'%')->orWhere('email', 'like', '%'.$v.'%')))
            ->when(($f['saldoCliente'] ?? '') === 'positivo', fn ($q) => $q->whereRaw('('.$saldo->toSql().') > 0', $saldo->getBindings()))
            ->when(($f['saldoCliente'] ?? '') === 'cero', fn ($q) => $q->whereRaw('('.$saldo->toSql().') = 0', $saldo->getBindings()))
            ->when($f['vendedorCliente'] ?? '', fn ($q, $v) => $q->whereIn('id', PedidoWeb::select('cliente_web_id')->where('vendedor_asignado_id', $v)))
            ->orderBy('nombres')->orderBy('id');
    }

    public function pedidosCliente(User $actor, int $id)
    {
        $this->autorizar($actor);
        ClienteWeb::findOrFail($id);
        return PedidoWeb::where('cliente_web_id', $id)->with('vendedor')->latest('id');
    }
}

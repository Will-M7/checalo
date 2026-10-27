<?php

namespace App\Services\Finanzas;

use App\Models\{IntentoPago, MovimientoFinanciero, MovimientoSaldo, PagoProveedor, ReceptorPago, User, Venta};
use App\Services\Payments\Dinero;
use Illuminate\Support\Facades\DB;
use Illuminate\Validation\ValidationException;

/** Libro operativo incremental. Nunca presupone un saldo bancario ni reconstruye ingresos antiguos. */
class FinanzasService
{
    private function registrar(string $key, array $values): MovimientoFinanciero
    {
        $values = array_map(fn ($v) => $v instanceof \BackedEnum ? $v->value : $v, $values);
        return DB::transaction(function () use ($key, $values) {
            DB::table('integracion_secuencias')->where('clave', 'finanzas')->increment('valor', 0);
            if ($old = MovimientoFinanciero::where('clave', $key)->first()) {
                foreach ($values as $field => $value) {
                    if ($field !== 'detalle' && $old->getAttribute($field) != $value) {
                        throw ValidationException::withMessages(['finanzas' => 'La referencia financiera ya pertenece a otra operación.']);
                    }
                }
                return $old;
            }
            return MovimientoFinanciero::create(['clave' => $key, 'created_at' => now(), ...$values]);
        }, 5);
    }

    public function ingresoCanonico(IntentoPago $i): MovimientoFinanciero
    {
        if ($i->estado !== 'PAID' || ! $i->external_id || ! $i->evidence_id) {
            throw new \LogicException('Finanzas solo admite ingresos Yape canónicos con evidencia.');
        }
        $receiver = ReceptorPago::findOrFail($i->receptor_pago_id);
        return $this->registrar('pago:'.$i->id, ['tipo' => 'ingreso_yape', 'amount_minor' => $i->amount_minor,
            'origen' => 'intento:'.$i->id, 'metodo' => 'yape', 'intento_pago_id' => $i->id,
            'vendedor_id' => $i->proposito === 'recarga' ? ($i->receptor_snapshot['vendedor_id'] ?? null) : $i->user_id, 'custodio_id' => $receiver->user_id, 'receptor_pago_id' => $receiver->id,
            'cliente_web_id' => $i->cliente_web_id, 'pedido_web_id' => $i->pedido_web_id,
            'recaudado_minor' => $i->amount_minor, 'custodia_minor' => $i->amount_minor,
            'recargas_minor' => $i->proposito === 'recarga' ? $i->amount_minor : 0,
            'detalle' => ['fuente' => 'PAID canónico Core; no es certificación de saldo bancario', 'receptor' => $i->receptor_snapshot['nombre'] ?? '', 'referencia' => $i->referencia]]);
    }

    public function saldo(MovimientoSaldo $m): MovimientoFinanciero
    {
        $income = $m->intento_pago_id ? MovimientoFinanciero::where('clave', 'pago:'.$m->intento_pago_id)->first() : null;
        $order = $m->pedido_web_id ? \App\Models\PedidoWeb::find($m->pedido_web_id) : null;
        return $this->registrar('saldo:'.$m->id, ['tipo' => $m->amount_minor > 0 ? 'saldo_abono' : 'saldo_consumo',
            'amount_minor' => abs($m->amount_minor), 'pasivo_minor' => $m->amount_minor, 'origen' => 'saldo:'.$m->id,
            'movimiento_saldo_id' => $m->id, 'cliente_web_id' => $m->cliente_web_id, 'intento_pago_id' => $m->intento_pago_id,
            'pedido_web_id' => $m->pedido_web_id, 'vendedor_id' => $order?->vendedor_asignado_id ?? $income?->vendedor_id,
            'receptor_pago_id' => $income?->receptor_pago_id, 'actor_id' => $m->autorizado_por,
            'originado_por_id' => $income?->id, 'estado' => $m->amount_minor > 0 && ! $income ? 'no_conciliado' : 'confirmado',
            'detalle' => ['tipo_saldo' => $m->tipo, 'referencia' => $m->origen]]);
    }

    public function venta(Venta $sale, ?IntentoPago $payment = null, ?\App\Models\PedidoWeb $order = null): MovimientoFinanciero
    {
        $minor = Dinero::minor($sale->total);
        $method = $sale->metodo_pago instanceof \BackedEnum ? $sale->metodo_pago->value : $sale->metodo_pago;
        $income = $payment ? MovimientoFinanciero::where('clave', 'pago:'.$payment->id)->first() : null;
        $wallet = $order ? MovimientoSaldo::where('pedido_web_id', $order->id)->where('tipo', 'compra')->first() : null;
        $income ??= $wallet ? MovimientoFinanciero::where('clave', 'saldo:'.$wallet->id)->first() : null;
        $movement = $this->registrar('venta:'.$sale->id, ['tipo' => 'venta', 'amount_minor' => $minor, 'origen' => 'venta:'.$sale->id,
            'venta_id' => $sale->id, 'vendedor_id' => $sale->vendedor_id, 'metodo' => $method, 'ventas_minor' => $minor,
            'intento_pago_id' => $payment?->id, 'receptor_pago_id' => $payment?->receptor_pago_id,
            'cliente_web_id' => $payment?->cliente_web_id ?? $order?->cliente_web_id, 'pedido_web_id' => $payment?->pedido_web_id ?? $order?->id,
            'originado_por_id' => $income?->id, 'estado' => in_array($method, ['saldo', 'yape', 'efectivo'], true) ? 'confirmado' : 'no_conciliado']);
        if ($method === 'efectivo') {
            $this->registrar('efectivo:venta:'.$sale->id, ['tipo' => 'ingreso_efectivo', 'amount_minor' => $minor, 'origen' => 'venta:'.$sale->id,
                'venta_id' => $sale->id, 'vendedor_id' => $sale->vendedor_id, 'custodio_id' => $sale->vendedor_id,
                'actor_id' => $sale->vendedor_id, 'metodo' => 'efectivo', 'recaudado_minor' => $minor, 'custodia_minor' => $minor, 'originado_por_id' => $movement->id]);
        }
        return $movement;
    }

    public function egresoProveedor(PagoProveedor $pago, User $actor): MovimientoFinanciero
    {
        abort_unless($actor->activo && $actor->puedePantalla('planta.pagos'), 403);
        $minor = Dinero::minor($pago->monto);
        if ($minor <= 0) {
            throw ValidationException::withMessages(['finanzas' => 'El egreso debe ser positivo.']);
        }
        return $this->registrar('proveedor:'.$pago->id, ['tipo' => 'egreso_proveedor', 'amount_minor' => $minor,
            'origen' => 'pago_proveedor:'.$pago->id, 'pago_proveedor_id' => $pago->id, 'metodo' => $pago->metodo_pago,
            'actor_id' => $actor->id, 'egresos_minor' => $minor, 'empresa_minor' => -$minor,
            'detalle' => ['proveedor_id' => $pago->proveedor_id, 'fuente' => 'Pago confirmado por operador autorizado; salida empresarial sin certificación bancaria', 'cuenta_origen' => 'No identificada por el flujo legado; se descuenta conservadoramente del disponible']]);
    }

    public function rendir(User $actor, int $source, int $minor, string $key, string $reference): MovimientoFinanciero
    {
        abort_unless($actor->activo && $actor->puedePantalla('admin.finanzas'), 403);
        validator(['clave' => $key, 'referencia' => $reference], ['clave' => 'required|uuid', 'referencia' => 'required|string|min:10|max:180'])->validate();
        return DB::transaction(function () use ($actor, $source, $minor, $key, $reference) {
            DB::table('integracion_secuencias')->where('clave', 'finanzas')->increment('valor', 0);
            $income = MovimientoFinanciero::findOrFail($source);
            $values = ['tipo' => 'rendicion', 'amount_minor' => $minor, 'origen' => $reference, 'actor_id' => $actor->id,
                'originado_por_id' => $income->id, 'vendedor_id' => $income->vendedor_id, 'custodio_id' => $income->custodio_id,
                'receptor_pago_id' => $income->receptor_pago_id, 'custodia_minor' => -$minor, 'empresa_minor' => $minor, 'rendido_minor' => $minor];
            $mismaConstancia = MovimientoFinanciero::where('tipo', 'rendicion')->where('originado_por_id', $income->id)->where('origen', $reference)->first();
            if ($mismaConstancia && $mismaConstancia->clave !== 'rendicion:'.$key) {
                if ($mismaConstancia->amount_minor !== $minor) throw ValidationException::withMessages(['finanzas' => 'La constancia ya se registró con otro importe.']);
                return $mismaConstancia;
            }
            if (! MovimientoFinanciero::where('clave', 'rendicion:'.$key)->exists() && ($minor <= 0 || $income->custodia_minor <= 0 || $minor > $income->pendienteRendir())) {
                throw ValidationException::withMessages(['finanzas' => 'La rendición excede los fondos pendientes de ese ingreso.']);
            }
            return $this->registrar('rendicion:'.$key, $values);
        }, 5);
    }

    public function resumen(): array
    {
        $result = [];
        foreach (['recaudado', 'ventas', 'recargas', 'custodia', 'empresa', 'rendido', 'egresos', 'pasivo'] as $col) {
            $result[$col] = (int) MovimientoFinanciero::sum($col.'_minor');
        }
        // Incluye obligaciones heredadas reales sin inventar la procedencia de sus fondos.
        $result['saldos_clientes'] = (int) MovimientoSaldo::where('estado', 'aplicado')->sum('amount_minor');
        $result['saldo_no_conciliado'] = $result['saldos_clientes'] - $result['pasivo'];
        $result['proveedores_pendientes'] = 0;
        foreach (\App\Models\Recojo::where('entregado', true)->where('pagado', false)->where('total', '>', 0)->cursor() as $recojo) {
            $result['proveedores_pendientes'] += Dinero::minor($recojo->total);
        }
        $result['disponible'] = max(0, $result['empresa'] - $result['saldos_clientes'] - $result['proveedores_pendientes']);
        $result['faltante_cobertura'] = max(0, $result['saldos_clientes'] + $result['proveedores_pendientes'] - $result['empresa']);
        $result['abonos_no_conciliados'] = (int) MovimientoFinanciero::where('tipo', 'saldo_abono')->where('estado', 'no_conciliado')->sum('amount_minor');
        $result['ventas_sin_registro'] = Venta::whereNotIn('id', MovimientoFinanciero::where('tipo', 'venta')->select('venta_id'))->count();
        $result['pagos_proveedor_sin_registro'] = PagoProveedor::whereNotIn('id', MovimientoFinanciero::where('tipo', 'egreso_proveedor')->select('pago_proveedor_id'))->count();
        $result['pagos_core_sin_registro'] = IntentoPago::where('estado', 'PAID')->whereNotIn('id', MovimientoFinanciero::where('tipo', 'ingreso_yape')->select('intento_pago_id'))->count();
        return $result;
    }
}

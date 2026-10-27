<?php

namespace App\Services\Payments;

use App\Models\ClienteWeb;
use App\Models\IntentoPago;
use App\Models\MovimientoSaldo;
use App\Models\PedidoWeb;
use App\Models\User;
use Illuminate\Support\Facades\DB;
use Illuminate\Validation\ValidationException;

class SaldoService
{
    public function movimiento(ClienteWeb $client, int $minor, string $type, string $key, string $source, ?IntentoPago $payment = null, ?PedidoWeb $order = null, ?User $actor = null, ?MovimientoSaldo $reversal = null): MovimientoSaldo
    {
        return DB::transaction(function () use ($client, $minor, $type, $key, $source, $payment, $order, $actor, $reversal) {
            ClienteWeb::whereKey($client->id)->increment('wallet_version');
            $old = MovimientoSaldo::where('idempotencia', $key)->first();
            if ($old) {
                if ($old->cliente_web_id !== $client->id || $old->amount_minor !== $minor || $old->tipo !== $type) {
                    throw new \RuntimeException('Conflicto de idempotencia de saldo.');
                }

                return $old;
            }
            if ($minor === 0 || $client->saldoMinor() + $minor < 0) {
                throw ValidationException::withMessages(['saldo' => 'Saldo insuficiente. No se efectuó la compra.']);
            }

            $movement = MovimientoSaldo::create(['cliente_web_id' => $client->id, 'tipo' => $type, 'amount_minor' => $minor, 'currency' => '604', 'idempotencia' => $key, 'origen' => $source,
                'intento_pago_id' => $payment?->id, 'pedido_web_id' => $order?->id, 'autorizado_por' => $actor?->id, 'reverso_de_id' => $reversal?->id]);
            app(\App\Services\Finanzas\FinanzasService::class)->saldo($movement);
            return $movement;
        }, 5);
    }

    public function ajuste(ClienteWeb $client, int $minor, string $reference, User $actor, ?MovimientoSaldo $reversal = null): MovimientoSaldo
    {
        abort_unless($actor->activo && $actor->puedeAdministracionCritica(), 403);
        if (strlen(trim($reference)) < 10 || ($reversal && ($reversal->cliente_web_id !== $client->id || $minor !== -$reversal->amount_minor))) {
            throw new \InvalidArgumentException('Se requiere motivo/referencia y reverso exacto del mismo cliente.');
        }

        return $this->movimiento($client, $minor, $reversal ? 'reverso' : 'ajuste', 'admin:'.$reference, $reference, actor: $actor, reversal: $reversal);
    }
}

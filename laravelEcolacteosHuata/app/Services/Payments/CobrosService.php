<?php

namespace App\Services\Payments;

use App\Enums\EstadoPedidoWeb;
use App\Models\ClienteWeb;
use App\Models\IntentoPago;
use App\Models\PedidoWeb;
use App\Models\ReceptorPago;
use App\Models\User;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\RateLimiter;
use Illuminate\Support\Facades\Storage;
use Illuminate\Support\Str;
use Illuminate\Validation\ValidationException;

class CobrosService
{
    public function __construct(private PaymentCore $core, private VentasService $sales, private SaldoService $wallet) {}

    private function receptor(array $remote, ?int $seller = null, bool $deferred = false): ReceptorPago
    {
        DB::table('integracion_secuencias')->where('clave', 'reparto')->increment('valor', 0);
        $eligible = User::conPantalla($seller ? 'vendedor.ventas' : 'vendedor.pedidos-web')->when($seller, fn ($q) => $q->whereKey($seller))->orderBy('id')->get()->map(function ($vendedor) use ($remote, $deferred) {
            $r = app(AsignacionesReceptor::class)->receptor($vendedor);
            if (! $r || (! $deferred && (! $r->enabled || ! $r->verified_at)) || ! $r->qr_path || ! $r->qr_sha256 || ! Storage::disk('public')->exists($r->qr_path) || ! hash_equals($r->qr_sha256, hash_file('sha256', Storage::disk('public')->path($r->qr_path)))) {
                return null;
            }
            $matched = $deferred || collect($remote)->contains(fn ($e) => ($e['id'] ?? null) === $r->referencia_externa && ($e['enabled'] ?? false) && ($e['verified'] ?? false) && ($e['online'] ?? false) && ($e['name'] ?? '') === $r->nombre_receptor && hash_equals($r->qr_sha256, (string) ($e['qrSha256'] ?? '')) && TelefonoPeru::coinciden((string) ($e['phone'] ?? ''), $r->celular));
            return $matched ? $r->setRelation('vendedorCobro', $vendedor) : null;
        })->filter();
        if ($eligible->isEmpty()) {
            throw ValidationException::withMessages(['pago' => 'Yape no está disponible: no hay receptor habilitado con QR verificado y Bridge comunicado. La tienda debe completar la vinculación técnica. No realices el pago.']);
        }
        $last = (int) DB::table('integracion_secuencias')->where('clave', 'reparto')->value('valor');
        $r = $eligible->first(fn ($r) => $r->getRelation('vendedorCobro')->id > $last) ?? $eligible->first();
        DB::table('integracion_secuencias')->where('clave', 'reparto')->update(['valor' => $r->getRelation('vendedorCobro')->id]);

        return $r;
    }

    public function pedido(ClienteWeb $client, array $items, string $method, string $key, bool $deferred = false): PedidoWeb
    {
        if (! in_array($method, ['yape', 'saldo', 'efectivo', 'plin'], true)) {
            throw ValidationException::withMessages(['metodo_pago' => 'Método no permitido.']);
        }
        $hash = hash('sha256', json_encode($deferred ? [$items, $method, 'deferred'] : [$items, $method], JSON_THROW_ON_ERROR));
        $previous = PedidoWeb::where('idempotency_key', $key)->first();
        if ($previous) {
            return $this->previous($previous, $client->id, $hash);
        }
        $quote = $this->sales->cotizar($items);
        if ($quote['amount_minor'] <= 0) {
            throw ValidationException::withMessages(['pago' => 'El importe debe ser positivo.']);
        }
        $remote = [];
        if ($method === 'yape' && ! $deferred) {
            try { $remote = $this->core->receivers(); }
            catch (ValidationException) { $deferred = true; }
        }

        return DB::transaction(function () use ($client, $method, $key, $hash, $quote, $remote, $deferred) {
            DB::table('integracion_secuencias')->where('clave', 'reparto')->increment('valor', 0);
            if ($old = PedidoWeb::where('idempotency_key', $key)->first()) {
                return $this->previous($old, $client->id, $hash);
            }
            $receiver = null;
            if ($method === 'yape') {
                try { $receiver = $this->receptor($remote, deferred: $deferred); }
                catch (ValidationException) { $receiver = $this->receptor([], deferred: true); $deferred = true; }
            }
            $seller = $receiver?->getRelation('vendedorCobro');
            if (! $seller) {
                $sellers = User::conPantalla('vendedor.pedidos-web')->orderBy('id')->get();
                $last = (int) DB::table('integracion_secuencias')->where('clave', 'reparto')->value('valor');
                $seller = $sellers->first(fn ($s) => $s->id > $last) ?? $sellers->first();
                if ($seller) {
                    DB::table('integracion_secuencias')->where('clave', 'reparto')->update(['valor' => $seller->id]);
                }
            }
            if (! $seller) {
                throw ValidationException::withMessages(['pago' => 'No hay vendedor disponible.']);
            }
            $contact = ReceptorPago::where('user_id', $seller->id)->first();
            $snapshot = $receiver?->snapshot() ?? ($contact ? ['vendedor_id' => $seller->id, 'vendedor_nombre' => $seller->nombreCompleto(), 'whatsapp' => $contact->whatsapp] : null);
            $order = PedidoWeb::create(['cliente_web_id' => $client->id, 'nombre' => $client->nombreCompleto(), 'celular' => $client->celular, 'metodo_pago' => $method,
                'total' => Dinero::decimal($quote['amount_minor']), 'canal' => 'web', 'idempotency_key' => $key, 'vendedor_asignado_id' => $seller->id,
                'receptor_pago_id' => $receiver?->id, 'receptor_snapshot' => $snapshot, 'detalle_cobro' => [...array_diff_key($quote, ['lines' => true]), 'request_hash' => $hash]]);
            $order->items()->createMany($quote['lines']);
            if ($receiver && ! $deferred) {
                $this->nuevoIntento($receiver, $quote['amount_minor'], 'compra', $client, $order);
            }
            if ($method === 'saldo') {
                $this->comprarConSaldo($client, $order, $quote, $seller);
            }

            return $order->fresh();
        }, 5);
    }

    /** Opening a saved Yape order starts a fresh time window, preserving its frozen receiver and quote. */
    public function prepararPedido(PedidoWeb $order, ClienteWeb $client): IntentoPago
    {
        abort_unless($order->cliente_web_id === $client->id && $order->metodo_pago === 'yape', 404);
        $last = IntentoPago::where('pedido_web_id', $order->id)->latest('id')->first();
        if ($last && $last->external_id && ! $last->applied_at && ($last->estado === 'EXPIRED' || ($last->expires_at && now()->gte($last->expires_at)))) {
            $last = $this->consultar($last);
        }
        if ($last && ! in_array($last->estado, ['EXPIRED', 'CANCELLED'])) {
            return $last;
        }
        if ($last?->estado === 'CANCELLED' || $order->fresh()->estado !== EstadoPedidoWeb::Pendiente || $order->venta_id) {
            throw ValidationException::withMessages(['pedido' => 'El pedido no admite otra intención. Conservamos su historial.']);
        }
        $candidate = new IntentoPago(['receptor_pago_id' => $order->receptor_pago_id, 'receptor_snapshot' => $order->receptor_snapshot, 'proposito' => 'compra']);
        if ($error = $this->indisponibilidad($candidate)) {
            throw ValidationException::withMessages(['pago' => $error]);
        }

        return DB::transaction(function () use ($order, $client, $last) {
            DB::table('integracion_secuencias')->where('clave', 'reparto')->increment('valor', 0);
            $current = IntentoPago::where('pedido_web_id', $order->id)->latest('id')->first();
            if ($current && $current->id !== $last?->id) {
                return $current;
            }
            if (IntentoPago::where('pedido_web_id', $order->id)->where('estado', 'PAID')->exists()) {
                throw ValidationException::withMessages(['pago' => 'Ya existe un ingreso confirmado para este pedido.']);
            }
            return IntentoPago::create(['referencia' => (string) Str::uuid(), 'cliente_web_id' => $client->id,
                'user_id' => $order->vendedor_asignado_id, 'receptor_pago_id' => $order->receptor_pago_id,
                'pedido_web_id' => $order->id, 'proposito' => 'compra', 'amount_minor' => Dinero::minor($order->total),
                'receptor_snapshot' => $order->receptor_snapshot])->fresh();
        }, 5);
    }

    private function previous(PedidoWeb $order, int $client, string $hash): PedidoWeb
    {
        abort_unless($order->cliente_web_id === $client, 404);
        if (($order->detalle_cobro['request_hash'] ?? '') !== $hash) {
            throw ValidationException::withMessages(['pedido' => 'Esta referencia ya corresponde a otro pedido.']);
        }

        return $order;
    }

    private function nuevoIntento(ReceptorPago $r, int $amount, string $purpose, ?ClienteWeb $client, ?PedidoWeb $order, ?string $reference = null): IntentoPago
    {
        return IntentoPago::create(['referencia' => $reference ?? (string) Str::uuid(), 'cliente_web_id' => $client?->id, 'user_id' => $order?->vendedor_asignado_id ?? ($r->relationLoaded('vendedorCobro') ? $r->getRelation('vendedorCobro')->id : $r->user_id),
            'receptor_pago_id' => $r->id, 'pedido_web_id' => $order?->id, 'proposito' => $purpose, 'amount_minor' => $amount, 'receptor_snapshot' => $r->snapshot()]);
    }

    public function recarga(ClienteWeb $client, int $minor, string $reference): IntentoPago
    {
        if ($minor < 100 || $minor > 99999999) {
            throw ValidationException::withMessages(['importe' => 'La recarga debe ser entre S/ 1 y S/ 999999.99.']);
        }
        if ($old = IntentoPago::where('referencia', $reference)->first()) {
            abort_unless($old->cliente_web_id === $client->id && $old->proposito === 'recarga' && $old->amount_minor === $minor, 409);

            return $old;
        }
        $receivers = $this->core->receivers();

        return DB::transaction(function () use ($client, $minor, $reference, $receivers) {
            DB::table('integracion_secuencias')->where('clave', 'reparto')->increment('valor', 0);
            if ($old = IntentoPago::where('referencia', $reference)->first()) {
                abort_unless($old->cliente_web_id === $client->id && $old->amount_minor === $minor && $old->proposito === 'recarga', 409);

                return $old;
            }

            return $this->nuevoIntento($this->receptor($receivers), $minor, 'recarga', $client, null, $reference);
        }, 5);
    }

    public function presencial(User $seller, array $items, array $discounts, array $customer, string $key): IntentoPago
    {
        abort_unless($seller->activo && $seller->puedePantalla('vendedor.ventas'), 403);
        if ($old = PedidoWeb::where('idempotency_key', $key)->first()) {
            abort_unless($old->canal === 'pos' && $old->vendedor_asignado_id === $seller->id, 404);

            return IntentoPago::where('pedido_web_id', $old->id)->firstOrFail();
        }
        $quote = $this->sales->cotizar($items, $discounts);
        if ($quote['amount_minor'] <= 0) {
            throw ValidationException::withMessages(['pago' => 'El cobro Yape debe tener un importe positivo.']);
        }
        $remote = $this->core->receivers();

        return DB::transaction(function () use ($seller, $quote, $customer, $key, $remote) {
            $r = $this->receptor($remote, $seller->id);
            if ($old = PedidoWeb::where('idempotency_key', $key)->first()) {
                return IntentoPago::where('pedido_web_id', $old->id)->where('user_id', $seller->id)->firstOrFail();
            }
            $order = PedidoWeb::create(['nombre' => $customer['cliente_nombre_libre'] ?? 'Venta presencial', 'metodo_pago' => 'yape', 'total' => Dinero::decimal($quote['amount_minor']),
                'canal' => 'pos', 'idempotency_key' => $key, 'vendedor_asignado_id' => $seller->id, 'receptor_pago_id' => $r->id, 'receptor_snapshot' => $r->snapshot(),
                'detalle_cobro' => [...array_diff_key($quote, ['lines' => true]), ...$customer]]);
            $order->items()->createMany($quote['lines']);

            return $this->nuevoIntento($r, $quote['amount_minor'], 'pos', null, $order);
        }, 5);
    }

    public function iniciar(IntentoPago $i): IntentoPago
    {
        if ($i->external_id) {
            return $i;
        }
        $remote = $this->core->create($i); // No local transaction or inventory lock during network IO.

        return $this->persistir($i, $remote);
    }

    /** Disponibilidad para mostrar instrucciones de cobro; no concilia ni altera la intención. */
    public function indisponibilidad(IntentoPago $i): ?string
    {
        $r = ReceptorPago::with('vendedor')->find($i->receptor_pago_id);
        $snapshot = $i->receptor_snapshot;
        $unavailable = 'Yape no está habilitado para el receptor asignado. La tienda debe verificar su vinculación, QR y comunicación del Bridge. No realices el pago.';
        if (! $r?->enabled || ! $r->verified_at || ! User::find($snapshot['vendedor_id'] ?? $r->user_id)?->puedePantalla($i->proposito === 'pos' ? 'vendedor.ventas' : 'vendedor.pedidos-web') ||
            ! ($snapshot['qr_path'] ?? null) || ! ($snapshot['qr_sha256'] ?? null) ||
            ! Storage::disk('public')->exists($snapshot['qr_path']) ||
            ! hash_equals($snapshot['qr_sha256'], hash_file('sha256', Storage::disk('public')->path($snapshot['qr_path'])))) {
            return $unavailable;
        }
        try {
            $remote = $this->core->receivers();
        } catch (ValidationException $e) {
            return $e->validator->errors()->first();
        }
        $ready = collect($remote)->contains(fn ($e) => ($e['id'] ?? null) === $snapshot['external_reference'] &&
            ($e['enabled'] ?? false) && ($e['verified'] ?? false) && ($e['online'] ?? false) &&
            ($e['name'] ?? '') === $snapshot['nombre'] && TelefonoPeru::coinciden((string) ($e['phone'] ?? ''), $snapshot['celular']) &&
            hash_equals($snapshot['qr_sha256'], (string) ($e['qrSha256'] ?? '')));

        return $ready ? null : $unavailable;
    }

    public function consultar(IntentoPago $i): IntentoPago
    {
        if ($i->applied_at) {
            return $i;
        }
        $i = $this->iniciar($i);

        return $this->persistir($i, $this->core->get($i->external_id));
    }

    public function cancelar(IntentoPago $i): IntentoPago
    {
        $i = $this->consultar($i);
        if ($i->estado === 'PAID' || $i->estado === 'CANCELLED') {
            return $i;
        }

        return $this->persistir($i, $this->core->cancel($i->external_id));
    }

    public function validar(IntentoPago $i, string $code, string $actor, string $ip): IntentoPago
    {
        if (! preg_match('/^\d{3}$/', $code)) {
            throw ValidationException::withMessages(['codigo' => 'El código Yape debe tener exactamente tres dígitos.']);
        }
        $keys = ['actor:'.$actor, 'payment:'.$i->id, 'receiver:'.$i->receptor_pago_id, 'ip:'.$ip];
        foreach ($keys as $key) {
            if (RateLimiter::tooManyAttempts('yape:'.$key, 5)) {
                throw ValidationException::withMessages(['codigo' => 'Máximo cinco intentos por minuto. Espera un minuto.']);
            }
        }
        foreach ($keys as $key) {
            RateLimiter::hit('yape:'.$key, 60);
        }
        $i = $this->iniciar($i);
        $this->core->resolve($i->external_id, $code);

        // An event/resolve response is advisory. Query authenticated canonical state before applying funds.
        return $this->persistir($i, $this->core->get($i->external_id));
    }

    private function persistir(IntentoPago $i, array $r): IntentoPago
    {
        if (($r['reference'] ?? '') !== $i->referencia || ($r['amountMinor'] ?? null) !== $i->amount_minor || ($r['currency'] ?? '') !== '604' || ($r['receiverId'] ?? '') !== $i->receptor_snapshot['external_reference'] ||
            ($r['qrSha256'] ?? '') !== $i->receptor_snapshot['qr_sha256'] || ! is_string($r['id'] ?? null) || (strlen($r['id']) > 100 || ! str_starts_with($r['id'], 'PAY-')) || ($i->external_id && $i->external_id !== $r['id']) || ! in_array($r['status'] ?? '', ['PENDING', 'PAID', 'EXPIRED', 'CANCELLED'], true) ||
            ($r['status'] === 'PAID' && (! is_string($r['evidenceId'] ?? null) || strlen($r['evidenceId']) < 16 || strlen($r['evidenceId']) > 128))) {
            throw ValidationException::withMessages(['pago' => 'El contrato del motor no coincide con la operación. No se aplicó dinero.']);
        }

        return DB::transaction(function () use ($i, $r) {
            IntentoPago::whereKey($i->id)->update(['updated_at' => now()]);
            $i = IntentoPago::findOrFail($i->id);
            if ($i->applied_at) {
                return $i;
            }
            if ($i->estado === 'PAID' && $r['status'] !== 'PAID') {
                throw new \RuntimeException('No se permite retroceder un pago confirmado.');
            }
            $i->update(['external_id' => $r['id'], 'estado' => $r['status'], 'ultimo_resultado' => in_array($r['result'] ?? '', ['CONFIRMED', 'WAITING_PAYMENT', 'PAYMENT_NOT_FOUND', 'CODE_NOT_MATCHED', 'AMBIGUOUS', 'REVIEW_CODE_UNAVAILABLE', 'REVIEW_FORMAT_UNRECOGNIZED', 'EXPIRED', 'CANCELLED'], true) ? $r['result'] : null, 'expires_at' => $r['expiresAt'] ?? null, 'evidence_id' => $r['evidenceId'] ?? null]);
            if ($i->pedido && ($r['status'] === 'PAID' || ! IntentoPago::where('pedido_web_id', $i->pedido_web_id)->where('id', '>', $i->id)->exists())) {
                $i->pedido->update(['pago_estado' => strtolower($r['status']), 'expires_at' => $i->expires_at]);
            }
            if ($i->estado === 'PAID') {
                $this->aplicar($i);
            }

            return $i->fresh();
        }, 5);
    }

    private function aplicar(IntentoPago $i): void
    {
        if ($i->proposito === 'recarga') {
            $this->wallet->movimiento($i->cliente, $i->amount_minor, 'recarga', 'payment:'.$i->id, $i->referencia, $i);
            $i->update(['applied_at' => now()]);

            return;
        }
        $order = $i->pedido;
        try {
            if ($order->estado !== EstadoPedidoWeb::Pendiente) {
                throw new StockInsuficiente('Pedido cancelado después del ingreso.');
            }
            $sale = $this->sales->confirmarPedido($order, User::findOrFail($order->vendedor_asignado_id));
            $i->update(['venta_id' => $sale->id, 'applied_at' => now()]);
        } catch (StockInsuficiente $e) {
            if (! $i->cliente) {
                $order->update(['pago_estado' => 'pagado_sin_stock_revision']);

                return;
            }
            $this->wallet->movimiento($i->cliente, $i->amount_minor, 'pedido_sin_stock', 'payment:'.$i->id, $i->referencia, $i, $order);
            $order->update(['estado' => EstadoPedidoWeb::Cancelado, 'pago_estado' => 'saldo_acreditado']);
            $i->update(['applied_at' => now()]);
        }
    }

    private function comprarConSaldo(ClienteWeb $client, PedidoWeb $order, array $quote, User $seller): void
    {
        $this->wallet->movimiento($client, -$quote['amount_minor'], 'compra', 'order:'.$order->id, 'pedido:'.$order->id, order: $order);
        $sale = $this->sales->registrar($quote, $seller, 'saldo', ['cliente_nombre_libre' => $client->nombreCompleto()]);
        $order->update(['estado' => EstadoPedidoWeb::Confirmado, 'venta_id' => $sale->id, 'pago_estado' => 'pagado']);
    }
}

<?php

namespace App\Services\Payments;

use App\Enums\EstadoPedidoWeb;
use App\Enums\EstadoProduccion;
use App\Enums\MetodoPago;
use App\Models\Cliente;
use App\Models\Descuento;
use App\Models\IntentoPago;
use App\Models\PedidoWeb;
use App\Models\Produccion;
use App\Models\User;
use App\Models\Venta;
use App\Models\VentaItem;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Validator;
use Illuminate\Validation\ValidationException;

class VentasService
{
    public function cotizar(array $items, array $descuentos = []): array
    {
        Validator::make(['items' => $items], ['items' => 'required|array|min:1|max:30', 'items.*.produccion_id' => 'required|integer|distinct', 'items.*.cantidad' => 'required|integer|min:1|max:1000'])->validate();
        $lines = [];
        foreach ($items as $item) {
            $p = Produccion::with('receta.detalles.insumo')->find($item['produccion_id']);
            if (! $p || $p->estado !== EstadoProduccion::Completada || ! $p->receta) {
                throw ValidationException::withMessages(['items' => 'Producto no disponible.']);
            }
            if ($p->cantidadDisponible() < $item['cantidad']) {
                throw new StockInsuficiente('No hay suficiente stock de '.($p->nombre ?: $p->receta->nombre).'.');
            }
            $price = Dinero::minor(number_format($p->receta->precioVenta(), 2, '.', ''));
            $lines[] = ['produccion_id' => $p->id, 'producto' => $p->nombre ?: $p->receta->nombre, 'cantidad' => (int) $item['cantidad'], 'precio_unitario' => Dinero::decimal($price), 'subtotal' => Dinero::decimal($price * (int) $item['cantidad'])];
        }
        $subtotal = array_sum(array_map(fn ($l) => Dinero::minor($l['subtotal']), $lines));
        $discounts = Descuento::where('activo', true)->whereIn('id', $descuentos)->get();
        $basis = (int) round($discounts->sum('porcentaje') * 100);
        if ($basis < 0 || $basis > 10000) {
            throw ValidationException::withMessages(['descuento' => 'El descuento acumulado debe estar entre 0 y 100%.']);
        }
        $discount = intdiv($subtotal * $basis + 5000, 10000);

        return ['lines' => $lines, 'subtotal_minor' => $subtotal, 'discount_minor' => $discount, 'amount_minor' => $subtotal - $discount, 'descuento_tipo' => $discounts->pluck('nombre')->implode(', ')];
    }

    public static function numeroBoleta(): string
    {
        // The update is the first write: it obtains SQLite's writer lock before any stock read.
        DB::table('integracion_secuencias')->where('clave', 'boleta')->increment('valor');
        $n = (int) DB::table('integracion_secuencias')->where('clave', 'boleta')->value('valor');
        while (Venta::where('numero_boleta', sprintf('B001-%06d', $n))->exists()) {
            DB::table('integracion_secuencias')->where('clave', 'boleta')->increment('valor');
            $n++;
        }

        return sprintf('B001-%06d', $n);
    }

    public function presencialManual(User $seller, array $items, array $discounts, string $method, array $customer, ?int $cash, string $reference): Venta
    {
        abort_unless($seller->activo && $seller->puedeVender('vendedor.ventas'), 403);
        if (in_array($method, ['yape', 'saldo'], true)) {
            throw ValidationException::withMessages(['pago' => 'Este método exige su flujo de pago verificado.']);
        }
        $lines = array_map(fn ($line) => ['produccion_id' => (int) ($line['produccion_id'] ?? 0), 'cantidad' => $line['cantidad'] ?? null], $items);
        $hash = hash('sha256', json_encode([$lines, $discounts, $method, $customer, $cash], JSON_THROW_ON_ERROR));

        return DB::transaction(function () use ($seller, $lines, $discounts, $method, $customer, $cash, $reference, $hash) {
            DB::table('integracion_secuencias')->where('clave', 'boleta')->increment('valor', 0);
            if ($old = Venta::where('referencia_canal', $reference)->first()) {
                abort_unless($old->vendedor_id === $seller->id && $old->request_hash === $hash, 409);

                return $old;
            }
            $sale = $this->registrar($this->cotizar($lines, $discounts), $seller, $method, $customer, $cash);
            $sale->update(['referencia_canal' => $reference, 'request_hash' => $hash]);

            return $sale;
        }, 5);
    }

    public function registrar(array $quote, User $seller, string $method, array $customer = [], ?int $cash = null, ?IntentoPago $payment = null, ?PedidoWeb $order = null): Venta
    {
        // A previously assigned, verified incoming payment must survive deactivation of its seller.
        $boundPayment = $method === 'yape' && $payment && $payment->estado === 'PAID' && ($payment->user_id === $seller->id || ($order && $payment->pedido_web_id === $order->id && $order->vendedor_asignado_id === $seller->id));
        abort_unless($boundPayment || ($seller->activo && ($seller->puedeVender('vendedor.ventas') || $seller->puedeVender('vendedor.pedidos-web'))), 403);
        if (! in_array($method, [...array_column(MetodoPago::options(), 'value'), 'saldo'], true)) {
            throw ValidationException::withMessages(['pago' => 'Método inválido.']);
        }
        if ($method === 'yape' && (! $payment || $payment->estado !== 'PAID' || $payment->amount_minor !== $quote['amount_minor'] || $payment->venta_id || $payment->applied_at)) {
            throw new \RuntimeException('Yape requiere un pago canónico confirmado y no aplicado.');
        }
        if ($method === 'efectivo' && ($cash === null || $cash < $quote['amount_minor'])) {
            throw ValidationException::withMessages(['efectivo' => 'El efectivo recibido debe cubrir el total.']);
        }

        return DB::transaction(function () use ($quote, $seller, $method, $customer, $cash, $payment, $order) {
            $number = self::numeroBoleta();
            foreach ($quote['lines'] as $line) {
                $p = Produccion::find($line['produccion_id']);
                if (! $p || $p->estado !== EstadoProduccion::Completada || $p->cantidadDisponible() < $line['cantidad']) {
                    throw new StockInsuficiente('El stock cambió durante el pago.');
                }
            }
            $sale = Venta::create([...$customer, 'numero_boleta' => $number, 'vendedor_id' => $seller->id, 'fecha' => $quote['fecha'] ?? now()->toDateString(),
                'subtotal' => Dinero::decimal($quote['subtotal_minor']), 'descuento' => Dinero::decimal($quote['discount_minor']), 'descuento_tipo' => $quote['descuento_tipo'] ?? '',
                'total' => Dinero::decimal($quote['amount_minor']), 'metodo_pago' => $method,
                'efectivo_recibido' => $method === 'efectivo' ? Dinero::decimal($cash) : null, 'vuelto' => $method === 'efectivo' ? Dinero::decimal($cash - $quote['amount_minor']) : null]);
            foreach ($quote['lines'] as $line) {
                VentaItem::create(['venta_id' => $sale->id, 'produccion_id' => $line['produccion_id'], 'nombre' => $line['producto'], 'cantidad' => $line['cantidad'], 'precio_unitario' => $line['precio_unitario'], 'subtotal' => $line['subtotal']]);
            }
            if ($customer['cliente_id'] ?? null) {
                Cliente::whereKey($customer['cliente_id'])->increment('compras_count');
            }

            app(\App\Services\Finanzas\FinanzasService::class)->venta($sale, $payment, $order);
            return $sale;
        }, 5);
    }

    public function confirmarPedido(PedidoWeb $order, User $seller, ?MetodoPago $method = null): Venta
    {
        return DB::transaction(function () use ($order, $seller, $method) {
            // Acquire a database writer lock, then re-read state (lockForUpdate alone does not lock rows on SQLite).
            DB::table('integracion_secuencias')->where('clave', 'boleta')->increment('valor', 0);
            $order = PedidoWeb::findOrFail($order->id);
            if ($order->vendedor_asignado_id && $order->vendedor_asignado_id !== $seller->id) {
                abort_unless($seller->activo && $seller->puedeAdministracionCritica() && $seller->puedeVender('vendedor.pedidos-web'), 403);
                $seller = User::findOrFail($order->vendedor_asignado_id);
            }
            if ($order->estado !== EstadoPedidoWeb::Pendiente) {
                throw new \RuntimeException('Ese pedido ya fue atendido.');
            }
            if ($order->metodo_pago === 'pendiente') {
                throw new \RuntimeException('El comprador debe pagar este pedido con saldo desde Mi cuenta.');
            }
            $payment = IntentoPago::where('pedido_web_id', $order->id)->where('estado', 'PAID')->latest('id')->first();
            if ($order->metodo_pago === 'yape') {
                if (! $payment || $payment->estado !== 'PAID' || $payment->applied_at) {
                    throw new \RuntimeException('Primero se debe verificar el ingreso Yape en Payment Core.');
                }
                $chosen = 'yape';
            } else {
                $chosen = $method?->value ?? $order->metodo_pago;
                if ($chosen === 'yape' || $chosen === 'saldo') {
                    throw new \RuntimeException('Este método requiere su flujo de pago verificado.');
                }
            }
            $lines = $order->items->map(fn ($i) => $i->only(['produccion_id', 'producto', 'cantidad', 'precio_unitario', 'subtotal']))->all();
            if (! $lines) {
                throw new \RuntimeException('Este pedido no tiene productos.');
            }
            $meta = $order->detalle_cobro ?? [];
            $total = Dinero::minor($order->total);
            $quote = ['lines' => $lines, 'subtotal_minor' => $meta['subtotal_minor'] ?? $total, 'discount_minor' => $meta['discount_minor'] ?? 0, 'amount_minor' => $total, 'descuento_tipo' => $meta['descuento_tipo'] ?? ''];
            $customer = ['cliente_id' => $meta['cliente_id'] ?? null, 'cliente_nombre_libre' => $order->nombre, 'cliente_doc_libre' => $meta['cliente_doc_libre'] ?? null];
            $sale = $this->registrar($quote, $seller, $chosen, $customer, $chosen === 'efectivo' ? $total : null, $payment, $order);
            $order->update(['estado' => EstadoPedidoWeb::Confirmado, 'venta_id' => $sale->id, 'pago_estado' => $chosen === 'yape' ? 'pagado' : 'declarado']);
            if ($payment) {
                $payment->update(['venta_id' => $sale->id, 'applied_at' => now()]);
            }

            return $sale;
        }, 5);
    }
}

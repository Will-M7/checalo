<?php

namespace App\Models;

use App\Enums\EstadoPedidoWeb;
use App\Enums\MetodoPago;
use App\Services\Payments\VentasService;
use Database\Factories\PedidoWebFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Support\Str;
use RuntimeException;

/**
 * Pedido (carrito) que un visitante arma en la página de inicio. Cada línea guarda una copia del
 * producto y del precio al momento de pedir, para que el vendedor lo vea tal como se pidió aunque
 * luego cambie.
 *
 * @property int $id
 * @property int|null $venta_id
 * @property string $total
 * @property string $nombre
 * @property string|null $celular
 * @property string $metodo_pago
 * @property string|null $codigo_operacion
 * @property string $token
 * @property EstadoPedidoWeb $estado
 */
#[Fillable(['venta_id', 'total', 'nombre', 'celular', 'metodo_pago', 'codigo_operacion', 'token', 'estado', 'cliente_web_id', 'vendedor_asignado_id', 'receptor_pago_id', 'receptor_snapshot', 'detalle_cobro', 'canal', 'pago_estado', 'idempotency_key', 'expires_at'])]
class PedidoWeb extends Model
{
    /** @use HasFactory<PedidoWebFactory> */
    use HasFactory;

    protected $table = 'pedidos_web';

    /**
     * Todo pedido nace con un token secreto para que el cliente abra su comprobante sin iniciar sesión.
     */
    protected static function booted(): void
    {
        static::creating(function (PedidoWeb $pedido): void {
            $pedido->token ??= Str::random(40);
        });
    }

    /**
     * Dirección pública del comprobante (o de la boleta, una vez confirmado el pago).
     */
    public function urlBoleta(): string
    {
        return route('pedidos-web.boleta', $this->token);
    }

    /**
     * @return HasMany<PedidoWebItem, $this>
     */
    public function items(): HasMany
    {
        return $this->hasMany(PedidoWebItem::class);
    }

    /**
     * @return BelongsTo<Venta, $this>
     */
    public function venta(): BelongsTo
    {
        return $this->belongsTo(Venta::class);
    }

    /**
     * Confirma el pedido registrando la venta con su boleta. Descuenta el stock de cada producción
     * (lo hace el ítem de venta al crearse) y deja el pedido ligado a esa venta. Todo o nada: si una
     * línea ya no tiene stock, no se registra nada.
     *
     * @throws RuntimeException si el pedido ya fue atendido o ya no alcanza el stock.
     */
    public function confirmarComoVenta(User $vendedor, ?MetodoPago $metodo = null): Venta
    {
        return app(VentasService::class)->confirmarPedido($this, $vendedor, $metodo);
    }

    /**
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'estado' => EstadoPedidoWeb::class,
            'receptor_snapshot' => 'array', 'detalle_cobro' => 'array', 'expires_at' => 'datetime',
            'total' => 'decimal:2',
        ];
    }
}

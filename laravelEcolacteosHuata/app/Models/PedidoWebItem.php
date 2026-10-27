<?php

namespace App\Models;

use Database\Factories\PedidoWebItemFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/**
 * Línea de un pedido web, con el nombre y el precio del producto al momento de pedir.
 *
 * @property int $id
 * @property int $pedido_web_id
 * @property int|null $produccion_id
 * @property string $producto
 * @property int $cantidad
 * @property string $precio_unitario
 * @property string $subtotal
 */
#[Fillable(['pedido_web_id', 'produccion_id', 'producto', 'cantidad', 'precio_unitario', 'subtotal'])]
class PedidoWebItem extends Model
{
    /** @use HasFactory<PedidoWebItemFactory> */
    use HasFactory;

    /**
     * @return BelongsTo<PedidoWeb, $this>
     */
    public function pedido(): BelongsTo
    {
        return $this->belongsTo(PedidoWeb::class, 'pedido_web_id');
    }

    /**
     * @return BelongsTo<Produccion, $this>
     */
    public function produccion(): BelongsTo
    {
        return $this->belongsTo(Produccion::class);
    }

    /**
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'cantidad' => 'integer',
            'precio_unitario' => 'decimal:2',
            'subtotal' => 'decimal:2',
        ];
    }
}

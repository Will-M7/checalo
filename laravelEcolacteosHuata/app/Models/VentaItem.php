<?php

namespace App\Models;

use Database\Factories\VentaItemFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

#[Fillable(['venta_id', 'produccion_id', 'nombre', 'precio_unitario', 'cantidad', 'subtotal'])]
class VentaItem extends Model
{
    /** @use HasFactory<VentaItemFactory> */
    use HasFactory;

    /**
     * @return BelongsTo<Venta, $this>
     */
    public function venta(): BelongsTo
    {
        return $this->belongsTo(Venta::class);
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
            'precio_unitario' => 'decimal:2',
            'subtotal' => 'decimal:2',
        ];
    }

    protected static function booted(): void
    {
        static::created(function (VentaItem $item): void {
            $item->produccion?->vender((float) $item->cantidad);
        });
    }
}

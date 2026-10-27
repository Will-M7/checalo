<?php

namespace App\Models;

use Database\Factories\ProduccionDetalleFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

#[Fillable(['produccion_id', 'insumo_id', 'cantidad_consumida'])]
class ProduccionDetalle extends Model
{
    /** @use HasFactory<ProduccionDetalleFactory> */
    use HasFactory;

    /**
     * @return BelongsTo<Produccion, $this>
     */
    public function produccion(): BelongsTo
    {
        return $this->belongsTo(Produccion::class);
    }

    /**
     * @return BelongsTo<Insumo, $this>
     */
    public function insumo(): BelongsTo
    {
        return $this->belongsTo(Insumo::class);
    }

    /**
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'cantidad_consumida' => 'decimal:3',
        ];
    }
}

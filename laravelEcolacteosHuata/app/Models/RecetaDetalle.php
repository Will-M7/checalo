<?php

namespace App\Models;

use Database\Factories\RecetaDetalleFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

#[Fillable(['receta_id', 'insumo_id', 'cantidad', 'unidad_medida_id'])]
class RecetaDetalle extends Model
{
    /** @use HasFactory<RecetaDetalleFactory> */
    use HasFactory;

    /**
     * @return BelongsTo<Receta, $this>
     */
    public function receta(): BelongsTo
    {
        return $this->belongsTo(Receta::class);
    }

    /**
     * @return BelongsTo<Insumo, $this>
     */
    public function insumo(): BelongsTo
    {
        return $this->belongsTo(Insumo::class);
    }

    /**
     * @return BelongsTo<UnidadMedida, $this>
     */
    public function unidadMedida(): BelongsTo
    {
        return $this->belongsTo(UnidadMedida::class);
    }

    /**
     * Costo de esta línea = cantidad × costo unitario del insumo.
     */
    public function costo(): float
    {
        return (float) $this->cantidad * (float) $this->insumo->costo_unitario;
    }

    /**
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'cantidad' => 'decimal:3',
        ];
    }
}

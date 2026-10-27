<?php

namespace App\Models;

use Database\Factories\UnidadMedidaFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\HasMany;

#[Fillable(['nombre', 'simbolo'])]
class UnidadMedida extends Model
{
    /** @use HasFactory<UnidadMedidaFactory> */
    use HasFactory;

    protected $table = 'unidades_medida';

    /**
     * @return HasMany<Insumo, $this>
     */
    public function insumos(): HasMany
    {
        return $this->hasMany(Insumo::class);
    }
}

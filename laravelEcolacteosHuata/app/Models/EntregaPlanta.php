<?php

namespace App\Models;

use Database\Factories\EntregaPlantaFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

#[Fillable([
    'acopiador_id', 'turno_id', 'fecha', 'litros_total', 'total_pagado',
    'total_deuda', 'proveedores_no_recogidos', 'guia',
])]
class EntregaPlanta extends Model
{
    /** @use HasFactory<EntregaPlantaFactory> */
    use HasFactory;

    protected $table = 'entregas_planta';

    /**
     * @return BelongsTo<Acopiador, $this>
     */
    public function acopiador(): BelongsTo
    {
        return $this->belongsTo(Acopiador::class);
    }

    /**
     * @return BelongsTo<Turno, $this>
     */
    public function turno(): BelongsTo
    {
        return $this->belongsTo(Turno::class);
    }

    /**
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'fecha' => 'date',
            'litros_total' => 'decimal:2',
            'total_pagado' => 'decimal:2',
            'total_deuda' => 'decimal:2',
        ];
    }
}

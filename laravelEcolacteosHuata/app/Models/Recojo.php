<?php

namespace App\Models;

use Database\Factories\RecojoFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

#[Fillable([
    'proveedor_id', 'acopiador_id', 'turno_id', 'fecha', 'litros', 'litros_entregados',
    'estado_leche', 'estado_otro', 'precio_litro', 'total', 'pagado', 'metodo_pago', 'entregado',
])]
class Recojo extends Model
{
    /** @use HasFactory<RecojoFactory> */
    use HasFactory;

    /**
     * @return BelongsTo<Proveedor, $this>
     */
    public function proveedor(): BelongsTo
    {
        return $this->belongsTo(Proveedor::class);
    }

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
            'litros' => 'decimal:2',
            'litros_entregados' => 'decimal:2',
            'precio_litro' => 'decimal:2',
            'total' => 'decimal:2',
            'pagado' => 'boolean',
            'entregado' => 'boolean',
        ];
    }
}

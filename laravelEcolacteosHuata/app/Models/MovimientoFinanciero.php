<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class MovimientoFinanciero extends Model
{
    public $timestamps = false;
    protected $table = 'movimientos_financieros';
    protected $guarded = ['id'];

    protected function casts(): array
    {
        $casts = ['created_at' => 'datetime', 'detalle' => 'array', 'amount_minor' => 'integer'];
        foreach (['recaudado', 'ventas', 'recargas', 'pasivo', 'custodia', 'empresa', 'rendido', 'egresos'] as $col) {
            $casts[$col.'_minor'] = 'integer';
        }
        return $casts;
    }

    protected static function booted(): void
    {
        static::updating(fn () => throw new \LogicException('Los movimientos financieros son inmutables. Registra un movimiento relacionado.'));
        static::deleting(fn () => throw new \LogicException('Los movimientos financieros no se eliminan.'));
    }

    public function pendienteRendir(): int
    {
        return max(0, $this->custodia_minor - (int) static::where('originado_por_id', $this->id)->where('tipo', 'rendicion')->sum('rendido_minor'));
    }
}

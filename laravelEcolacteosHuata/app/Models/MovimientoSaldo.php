<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class MovimientoSaldo extends Model
{
    protected $table = 'movimientos_saldo';

    protected $guarded = [];

    protected function casts(): array
    {
        return ['amount_minor' => 'integer'];
    }

    protected static function booted(): void
    {
        static::updating(fn () => throw new \LogicException('El libro se corrige mediante reversos.'));
        static::deleting(fn () => throw new \LogicException('El historial financiero no se elimina.'));
    }
}

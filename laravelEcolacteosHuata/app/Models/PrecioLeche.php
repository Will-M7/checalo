<?php

namespace App\Models;

use Database\Factories\PrecioLecheFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;

#[Fillable(['precio_litro', 'vigente_desde'])]
class PrecioLeche extends Model
{
    /** @use HasFactory<PrecioLecheFactory> */
    use HasFactory;

    protected $table = 'precios_leche';

    /**
     * Precio de leche vigente a la fecha (el más reciente cuya vigencia ya inició).
     */
    public static function vigente(): ?self
    {
        return static::query()
            ->where('vigente_desde', '<=', now()->toDateString())
            ->orderByDesc('vigente_desde')
            ->first();
    }

    /**
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'precio_litro' => 'decimal:2',
            'vigente_desde' => 'date',
        ];
    }
}

<?php

namespace App\Models;

use Database\Factories\DescuentoFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;

#[Fillable(['nombre', 'porcentaje', 'activo'])]
class Descuento extends Model
{
    /** @use HasFactory<DescuentoFactory> */
    use HasFactory;

    /**
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'porcentaje' => 'decimal:2',
            'activo' => 'boolean',
        ];
    }
}

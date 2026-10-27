<?php

namespace App\Models;

use Database\Factories\PagoProveedorFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

#[Fillable(['proveedor_id', 'recojo_id', 'fecha', 'monto', 'metodo_pago', 'periodo'])]
class PagoProveedor extends Model
{
    /** @use HasFactory<PagoProveedorFactory> */
    use HasFactory;

    protected $table = 'pagos_proveedor';

    /**
     * @return BelongsTo<Proveedor, $this>
     */
    public function proveedor(): BelongsTo
    {
        return $this->belongsTo(Proveedor::class);
    }

    /**
     * @return BelongsTo<Recojo, $this>
     */
    public function recojo(): BelongsTo
    {
        return $this->belongsTo(Recojo::class);
    }

    /**
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'fecha' => 'date',
            'monto' => 'decimal:2',
        ];
    }
}

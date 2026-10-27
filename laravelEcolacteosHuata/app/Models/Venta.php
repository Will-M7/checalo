<?php

namespace App\Models;

use App\Services\Payments\VentasService;
use Database\Factories\VentaFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Support\Facades\DB;

/**
 * @property-read Cliente|null $cliente
 */
#[Fillable([
    'numero_boleta', 'cliente_id', 'cliente_nombre_libre', 'cliente_doc_libre', 'vendedor_id',
    'fecha', 'subtotal', 'descuento', 'descuento_tipo', 'total', 'metodo_pago',
    'efectivo_recibido', 'vuelto', 'referencia_canal', 'request_hash',
])]
class Venta extends Model
{
    /** @use HasFactory<VentaFactory> */
    use HasFactory;

    /**
     * @return BelongsTo<Cliente, $this>
     */
    public function cliente(): BelongsTo
    {
        return $this->belongsTo(Cliente::class);
    }

    /**
     * @return BelongsTo<User, $this>
     */
    public function vendedor(): BelongsTo
    {
        return $this->belongsTo(User::class, 'vendedor_id');
    }

    /**
     * @return HasMany<VentaItem, $this>
     */
    public function items(): HasMany
    {
        return $this->hasMany(VentaItem::class);
    }

    /**
     * Siguiente número correlativo de boleta, con formato B001-000123.
     */
    public static function siguienteNumeroBoleta(): string
    {
        return DB::transaction(fn () => VentasService::numeroBoleta(), 5);
    }

    public function pedidoWeb()
    {
        return $this->hasOne(PedidoWeb::class, 'venta_id');
    }

    public function scopeSinTrazabilidadFinanciera($query)
    {
        return $query->whereNotIn('id', IntentoPago::whereNotNull('venta_id')->select('venta_id'))
            ->whereNotIn('id', PedidoWeb::where('metodo_pago', 'saldo')->whereNotNull('venta_id')->select('venta_id'));
    }

    public function protegida(): bool
    {
        return ! static::whereKey($this->id)->sinTrazabilidadFinanciera()->exists();
    }

    protected static function booted(): void
    {
        static::deleting(function (Venta $sale) {
            if ($sale->protegida()) {
                throw new \RuntimeException('La venta conserva trazabilidad financiera; requiere anulación auditada.');
            }
        });
    }

    /**
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'fecha' => 'date',
            'subtotal' => 'decimal:2',
            'descuento' => 'decimal:2',
            'total' => 'decimal:2',
            'efectivo_recibido' => 'decimal:2',
            'vuelto' => 'decimal:2',
        ];
    }
}

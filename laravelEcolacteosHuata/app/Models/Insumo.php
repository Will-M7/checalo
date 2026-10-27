<?php

namespace App\Models;

use Database\Factories\InsumoFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;

#[Fillable(['nombre', 'es_leche', 'unidad_medida_id', 'costo_unitario', 'stock_actual'])]
class Insumo extends Model
{
    /** @use HasFactory<InsumoFactory> */
    use HasFactory;

    /**
     * El insumo marcado como "leche": su stock se actualiza automáticamente con las entregas
     * confirmadas por laboratorio en vez de registrarse como una compra manual. Si no existe
     * (por ejemplo, tras vaciar la base de datos), se crea automáticamente para que el litraje
     * que llega a planta nunca se pierda por falta de un insumo donde acumularse.
     */
    public static function deLeche(): self
    {
        $litro = UnidadMedida::firstOrCreate(['simbolo' => 'L'], ['nombre' => 'Litro']);

        return static::firstOrCreate(
            ['es_leche' => true],
            ['nombre' => 'Leche fresca', 'unidad_medida_id' => $litro->id, 'costo_unitario' => 0, 'stock_actual' => 0]
        );
    }

    /**
     * @return BelongsTo<UnidadMedida, $this>
     */
    public function unidadMedida(): BelongsTo
    {
        return $this->belongsTo(UnidadMedida::class);
    }

    /**
     * @return HasMany<CompraDetalle, $this>
     */
    public function compraDetalles(): HasMany
    {
        return $this->hasMany(CompraDetalle::class);
    }

    /**
     * @return HasMany<RecetaDetalle, $this>
     */
    public function recetaDetalles(): HasMany
    {
        return $this->hasMany(RecetaDetalle::class);
    }

    /**
     * Registra el ingreso de una compra: aumenta el stock y recalcula el costo unitario
     * promedio ponderado.
     */
    public function registrarCompra(float $cantidad, float $costoUnitario): void
    {
        $valorActual = (float) $this->stock_actual * (float) $this->costo_unitario;
        $valorIngreso = $cantidad * $costoUnitario;
        $nuevoStock = (float) $this->stock_actual + $cantidad;

        $this->update([
            'stock_actual' => $nuevoStock,
            'costo_unitario' => $nuevoStock > 0 ? ($valorActual + $valorIngreso) / $nuevoStock : $costoUnitario,
        ]);
    }

    /**
     * Descuenta stock consumido en una producción. Nunca deja el stock por debajo de cero.
     */
    public function consumir(float $cantidad): void
    {
        $this->update(['stock_actual' => max(0, (float) $this->stock_actual - $cantidad)]);
    }

    /**
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'es_leche' => 'boolean',
            'costo_unitario' => 'decimal:4',
            'stock_actual' => 'decimal:3',
        ];
    }
}

<?php

namespace App\Models;

use App\Enums\EstadoProduccion;
use App\Services\Payments\StockInsuficiente;
use Database\Factories\ProduccionFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Support\Collection;

#[Fillable([
    'fecha', 'nombre', 'imagen', 'receta_id', 'cantidad_producida', 'cantidad_vendida', 'lote', 'turno_id', 'responsable_id', 'estado',
])]
class Produccion extends Model
{
    /** @use HasFactory<ProduccionFactory> */
    use HasFactory;

    protected $table = 'producciones';

    /**
     * URL pública de la foto de esta producción, o null si no tiene una asignada.
     */
    public function imagenUrl(): ?string
    {
        // Relativa al sitio actual (no a APP_URL), para que la foto cargue desde cualquier dirección.
        return $this->imagen ? asset('storage/'.ltrim($this->imagen, '/')) : null;
    }

    /**
     * Productos publicados con stock, de la producción más reciente a la más antigua, para la
     * página pública. El precio es el mismo precio de venta por unidad que usa el módulo de ventas.
     *
     * @return Collection<int, array{id: int, nombre: string, precio: float, imagen: ?string, disponible: float}>
     */
    public static function catalogoPublico(): Collection
    {
        return static::query()
            ->with('receta.detalles.insumo')
            ->where('estado', EstadoProduccion::Completada)
            ->orderByDesc('fecha')
            ->orderByDesc('id')
            ->get()
            ->filter(fn (self $produccion) => $produccion->receta !== null && $produccion->cantidadDisponible() > 0)
            ->map(fn (self $produccion) => [
                'id' => $produccion->id,
                'nombre' => $produccion->nombre ?? $produccion->receta->nombre,
                'precio' => $produccion->receta->precioVenta(),
                'imagen' => $produccion->imagenUrl(),
                'disponible' => $produccion->cantidadDisponible(),
            ])
            ->values();
    }

    /**
     * @return BelongsTo<Receta, $this>
     */
    public function receta(): BelongsTo
    {
        return $this->belongsTo(Receta::class);
    }

    /**
     * @return BelongsTo<Turno, $this>
     */
    public function turno(): BelongsTo
    {
        return $this->belongsTo(Turno::class);
    }

    /**
     * @return BelongsTo<User, $this>
     */
    public function responsable(): BelongsTo
    {
        return $this->belongsTo(User::class, 'responsable_id');
    }

    /**
     * @return HasMany<ProduccionDetalle, $this>
     */
    public function detalles(): HasMany
    {
        return $this->hasMany(ProduccionDetalle::class);
    }

    /**
     * @return HasMany<VentaItem, $this>
     */
    public function ventaItems(): HasMany
    {
        return $this->hasMany(VentaItem::class);
    }

    /**
     * Cantidad producida que todavía no se ha vendido.
     */
    public function cantidadDisponible(): float
    {
        return max(0, (float) $this->cantidad_producida - (float) $this->cantidad_vendida);
    }

    /**
     * Descuenta cantidad vendida de esta producción al registrarse una venta.
     */
    public function vender(float $cantidad): void
    {
        if ($cantidad <= 0 || static::whereKey($this->id)->whereRaw('cantidad_producida - cantidad_vendida >= CAST(? AS NUMERIC)', [$cantidad])->increment('cantidad_vendida', $cantidad) !== 1) {
            throw new StockInsuficiente('Cantidad insuficiente para registrar la venta.');
        }
        $this->refresh();
    }

    /**
     * Para cada insumo de la receta, cuánto falta consumir si se produjera la cantidad
     * indicada, descontando lo ya consumido previamente (si esta producción ya existe). Solo
     * incluye insumos con algo pendiente por consumir.
     *
     * @return Collection<int, array{insumo: Insumo, pendiente: float}>
     */
    private function pendientesPorInsumo(float $cantidadProducida): Collection
    {
        $receta = $this->receta()->with('detalles.insumo')->first();

        $yaConsumido = $this->exists
            ? $this->detalles()->selectRaw('insumo_id, SUM(cantidad_consumida) as total')->groupBy('insumo_id')->pluck('total', 'insumo_id')
            : collect();

        return collect($receta->detalles)
            ->map(function (RecetaDetalle $detalleReceta) use ($cantidadProducida, $yaConsumido) {
                $insumo = $detalleReceta->insumo;

                if ($insumo === null) {
                    return null;
                }

                return [
                    'insumo' => $insumo,
                    'pendiente' => ((float) $detalleReceta->cantidad * $cantidadProducida) - (float) ($yaConsumido[$detalleReceta->insumo_id] ?? 0),
                ];
            })
            ->filter()
            ->filter(fn (array $item) => $item['pendiente'] > 0)
            ->values();
    }

    /**
     * Insumos cuyo stock actual no alcanza para cubrir lo pendiente de consumir si se
     * produjera la cantidad indicada. Vacío si hay stock suficiente de todos. Se usa para
     * bloquear el registro/actualización de una producción antes de descontar nada.
     *
     * @return Collection<int, array{insumo: Insumo, pendiente: float, faltante: float}>
     */
    public function insumosInsuficientes(float $cantidadProducida): Collection
    {
        return $this->pendientesPorInsumo($cantidadProducida)
            ->filter(fn (array $item) => $item['pendiente'] > (float) $item['insumo']->stock_actual)
            ->map(fn (array $item) => [
                'insumo' => $item['insumo'],
                'pendiente' => $item['pendiente'],
                'faltante' => $item['pendiente'] - (float) $item['insumo']->stock_actual,
            ])
            ->values();
    }

    /**
     * Genera el detalle de consumo de insumos multiplicando la cantidad de cada ingrediente
     * de la receta por la cantidad producida, y descuenta el stock de cada insumo.
     * Solo descuenta lo que falte respecto a lo ya consumido previamente (por ejemplo, si la
     * cantidad se actualizó después de un registro o publicación anterior), para no descontar
     * dos veces. Se llama cada vez que se registra o actualiza la cantidad producida, no solo
     * al publicar, para que el stock de insumos se descuente apenas se hace la producción.
     */
    public function consumirInsumos(): void
    {
        foreach ($this->pendientesPorInsumo((float) $this->cantidad_producida) as $item) {
            $this->detalles()->create([
                'insumo_id' => $item['insumo']->id,
                'cantidad_consumida' => $item['pendiente'],
            ]);

            $item['insumo']->consumir($item['pendiente']);
        }
    }

    /**
     * Devuelve al stock de cada insumo lo que esta producción tenía consumido, deshaciendo
     * `consumirInsumos()`. Se usa antes de eliminar una producción para no perder ese stock.
     */
    public function revertirConsumo(): void
    {
        foreach ($this->detalles()->with('insumo')->get() as $detalle) {
            $detalle->insumo?->increment('stock_actual', $detalle->cantidad_consumida);
        }
    }

    /**
     * Marca la producción como completada (publicada, visible para la venta). El stock de
     * insumos ya se descontó al registrar/actualizar la producción, pero se vuelve a llamar
     * aquí por si la cantidad cambió justo antes de publicar sin pasar por "Actualizar".
     */
    public function completar(): void
    {
        $this->consumirInsumos();

        $this->update(['estado' => EstadoProduccion::Completada]);
    }

    /**
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'fecha' => 'date',
            'cantidad_producida' => 'decimal:3',
            'cantidad_vendida' => 'decimal:3',
            'estado' => EstadoProduccion::class,
        ];
    }
}

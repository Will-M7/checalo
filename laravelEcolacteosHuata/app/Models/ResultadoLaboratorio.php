<?php

namespace App\Models;

use Database\Factories\ResultadoLaboratorioFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

#[Fillable([
    'proveedor_id', 'recojo_id', 'fecha', 'turno_id', 'litros', 'resultado', 'densidad',
    'acidez', 'temperatura', 'grasa', 'sng', 'proteina', 'lactosa', 'sales',
    'total_solidos', 'agua_anadida', 'punto_congelacion', 'ph', 'observaciones',
    'conforme', 'enviado_produccion', 'sn', 'modo', 'hora_analisis', 'fecha_analisis',
])]
class ResultadoLaboratorio extends Model
{
    /** @use HasFactory<ResultadoLaboratorioFactory> */
    use HasFactory;

    protected $table = 'resultados_laboratorio';

    /**
     * @return BelongsTo<Proveedor, $this>
     */
    public function proveedor(): BelongsTo
    {
        return $this->belongsTo(Proveedor::class);
    }

    /**
     * @return BelongsTo<Turno, $this>
     */
    public function turno(): BelongsTo
    {
        return $this->belongsTo(Turno::class);
    }

    /**
     * El recojo (de ruta) al que corresponde este análisis: se usa para saber cuál recojo
     * actualizar cuando el laboratorio registra el litraje real que llegó a planta.
     *
     * @return BelongsTo<Recojo, $this>
     */
    public function recojo(): BelongsTo
    {
        return $this->belongsTo(Recojo::class);
    }

    /**
     * Suma la leche de este resultado al stock del insumo de leche y lo marca como enviado,
     * para que no pueda volver a sumarse. Solo procede si el resultado es conforme y todavía
     * no fue enviado.
     */
    public function enviarAProduccion(): void
    {
        if (! $this->conforme || $this->enviado_produccion || $this->litros === null) {
            return;
        }

        $precioVigente = PrecioLeche::vigente();

        Insumo::deLeche()->registrarCompra((float) $this->litros, $precioVigente ? (float) $precioVigente->precio_litro : 0.0);

        $this->update(['enviado_produccion' => true]);
    }

    /**
     * "09:42 · 06/06/26", como lo imprime el ticket del analizador; null si no hay datos.
     */
    public function horaFechaAnalisis(): ?string
    {
        $partes = array_filter([
            $this->hora_analisis ? substr((string) $this->hora_analisis, 0, 5) : null,
            $this->fecha_analisis?->format('d/m/y'),
        ]);

        return $partes === [] ? null : implode(' · ', $partes);
    }

    /**
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'fecha' => 'date',
            'litros' => 'decimal:2',
            'densidad' => 'decimal:3',
            'acidez' => 'decimal:3',
            'temperatura' => 'decimal:2',
            'grasa' => 'decimal:2',
            'sng' => 'decimal:2',
            'proteina' => 'decimal:2',
            'lactosa' => 'decimal:2',
            'sales' => 'decimal:2',
            'total_solidos' => 'decimal:2',
            'agua_anadida' => 'decimal:2',
            'punto_congelacion' => 'decimal:3',
            'ph' => 'decimal:2',
            'fecha_analisis' => 'date',
            'conforme' => 'boolean',
            'enviado_produccion' => 'boolean',
        ];
    }
}

<?php

namespace App\Models;

use App\Enums\EstadoJustificacion;
use Carbon\CarbonImmutable;
use Database\Factories\JustificacionFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Support\Str;

/**
 * @property int $id
 * @property int $asistencia_id
 * @property string $motivo
 * @property string|null $archivo
 * @property EstadoJustificacion $estado
 * @property int|null $revisado_por
 * @property CarbonImmutable|null $revisado_at
 * @property CarbonImmutable|null $created_at
 * @property-read Asistencia $asistencia
 */
#[Fillable(['asistencia_id', 'motivo', 'archivo', 'estado', 'revisado_por', 'revisado_at'])]
class Justificacion extends Model
{
    /** @use HasFactory<JustificacionFactory> */
    use HasFactory;

    protected $table = 'justificaciones';

    /**
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'estado' => EstadoJustificacion::class,
            'revisado_at' => 'datetime',
        ];
    }

    /**
     * @return BelongsTo<Asistencia, $this>
     */
    public function asistencia(): BelongsTo
    {
        return $this->belongsTo(Asistencia::class);
    }

    public function archivoEsPdf(): bool
    {
        return $this->archivo !== null && Str::lower(pathinfo($this->archivo, PATHINFO_EXTENSION)) === 'pdf';
    }

    /**
     * Marca la justificación como aceptada o rechazada por quien toma asistencia.
     */
    public function revisar(EstadoJustificacion $estado, User $revisor): void
    {
        $this->update([
            'estado' => $estado,
            'revisado_por' => $revisor->id,
            'revisado_at' => now(),
        ]);
    }
}

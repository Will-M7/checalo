<?php

namespace App\Models;

use App\Enums\EstadoAsistencia;
use App\Enums\EstadoJustificacion;
use Carbon\CarbonImmutable;
use Database\Factories\AsistenciaFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasOne;

/**
 * @property int $id
 * @property int $user_id
 * @property CarbonImmutable $fecha
 * @property EstadoAsistencia $estado
 * @property int|null $horario_asistencia_id
 * @property string|null $hora_marcada
 * @property int|null $registrado_por
 * @property-read User $user
 * @property-read Justificacion|null $justificacion
 * @property-read HorarioAsistencia|null $horario
 */
#[Fillable(['user_id', 'fecha', 'horario_asistencia_id', 'estado', 'hora_marcada', 'registrado_por'])]
class Asistencia extends Model
{
    /** @use HasFactory<AsistenciaFactory> */
    use HasFactory;

    /**
     * Días que tiene el trabajador, desde la fecha de la falta o tardanza, para justificarla.
     */
    public const DIAS_PARA_JUSTIFICAR = 7;

    /**
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'fecha' => 'date',
            'estado' => EstadoAsistencia::class,
        ];
    }

    /**
     * @return BelongsTo<User, $this>
     */
    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }

    /**
     * @return BelongsTo<HorarioAsistencia, $this>
     */
    public function horario(): BelongsTo
    {
        return $this->belongsTo(HorarioAsistencia::class, 'horario_asistencia_id');
    }

    /**
     * Asistencia general (la que se toma desde la tabla), sin las marcas por horario de los
     * acopiadores.
     *
     * @param  Builder<Asistencia>  $query
     * @return Builder<Asistencia>
     */
    public function scopeGeneral(Builder $query): Builder
    {
        return $query->whereNull('horario_asistencia_id');
    }

    /**
     * Fecha y hora actual en la zona horaria de la planta.
     */
    public static function ahora(): CarbonImmutable
    {
        return CarbonImmutable::now(config('app.zona_horaria_local'));
    }

    /**
     * Día de hoy en la zona horaria de la planta (a medianoche, para comparar con `fecha`).
     */
    public static function hoy(): CarbonImmutable
    {
        return CarbonImmutable::parse(self::ahora()->toDateString());
    }

    public function horaMarcada(): ?string
    {
        return $this->hora_marcada ? substr($this->hora_marcada, 0, 5) : null;
    }

    /**
     * @return HasOne<Justificacion, $this>
     */
    public function justificacion(): HasOne
    {
        return $this->hasOne(Justificacion::class);
    }

    /**
     * Último día (inclusive) en que todavía se puede justificar.
     */
    public function fechaLimiteJustificacion(): CarbonImmutable
    {
        return $this->fecha->addDays(self::DIAS_PARA_JUSTIFICAR);
    }

    /**
     * Días que le quedan al trabajador para justificar (0 si ya venció el plazo).
     */
    public function diasRestantesParaJustificar(): int
    {
        return (int) max(0, self::hoy()->diffInDays($this->fechaLimiteJustificacion(), false));
    }

    /**
     * Una falta o tardanza se puede justificar dentro del plazo de una semana, siempre que no
     * tenga ya una justificación pendiente o aceptada (si fue rechazada, puede volver a enviarla).
     */
    public function puedeJustificarse(): bool
    {
        if (! $this->estado->esJustificable()) {
            return false;
        }

        if (self::hoy()->gt($this->fechaLimiteJustificacion())) {
            return false;
        }

        return $this->justificacion === null
            || $this->justificacion->estado === EstadoJustificacion::Rechazada;
    }
}

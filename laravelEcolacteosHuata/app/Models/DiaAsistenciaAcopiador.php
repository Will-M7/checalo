<?php

namespace App\Models;

use Carbon\CarbonImmutable;
use Database\Factories\DiaAsistenciaAcopiadorFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;

/**
 * Día en que se toma asistencia a los acopiadores. Solo en estos días pueden marcar y, si no
 * marcan, quedan con falta.
 *
 * @property int $id
 * @property CarbonImmutable $fecha
 */
#[Fillable(['fecha'])]
class DiaAsistenciaAcopiador extends Model
{
    /** @use HasFactory<DiaAsistenciaAcopiadorFactory> */
    use HasFactory;

    protected $table = 'dias_asistencia_acopiador';

    /**
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'fecha' => 'date',
        ];
    }

    public static function esDiaDeAsistencia(string $fecha): bool
    {
        return self::query()->whereDate('fecha', $fecha)->exists();
    }
}

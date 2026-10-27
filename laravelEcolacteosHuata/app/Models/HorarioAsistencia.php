<?php

namespace App\Models;

use App\Enums\EstadoAsistencia;
use App\Enums\Rol;
use Carbon\CarbonImmutable;
use Database\Factories\HorarioAsistenciaFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Storage;

/**
 * Horario en que los acopiadores marcan su asistencia. Ej. "Mañana": de 05:00 a 05:10 es
 * presente, de 05:11 a 05:20 es tardanza y quien no marcó hasta entonces queda con falta.
 *
 * @property int $id
 * @property string $nombre
 * @property string $hora_inicio
 * @property string $hora_limite_presente
 * @property string $hora_limite_tardanza
 * @property bool $activo
 * @property CarbonImmutable|null $created_at
 */
#[Fillable(['nombre', 'hora_inicio', 'hora_limite_presente', 'hora_limite_tardanza', 'activo'])]
class HorarioAsistencia extends Model
{
    /** @use HasFactory<HorarioAsistenciaFactory> */
    use HasFactory;

    protected $table = 'horarios_asistencia';

    /**
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'activo' => 'boolean',
        ];
    }

    /**
     * Horas en formato HH:MM (la base de datos puede devolverlas con segundos).
     */
    public function inicio(): string
    {
        return substr($this->hora_inicio, 0, 5);
    }

    public function limitePresente(): string
    {
        return substr($this->hora_limite_presente, 0, 5);
    }

    public function limiteTardanza(): string
    {
        return substr($this->hora_limite_tardanza, 0, 5);
    }

    /**
     * Primer minuto que ya cuenta como tardanza (HH:MM).
     */
    public function inicioTardanza(): string
    {
        return CarbonImmutable::createFromFormat('H:i', $this->limitePresente())->addMinute()->format('H:i');
    }

    /**
     * Estado que le corresponde a quien marca a la hora indicada (HH:MM), o null si el horario
     * todavía no abre o ya cerró.
     */
    public function estadoParaHora(string $hora): ?EstadoAsistencia
    {
        return match (true) {
            $hora < $this->inicio() => null,
            $hora <= $this->limitePresente() => EstadoAsistencia::Presente,
            $hora <= $this->limiteTardanza() => EstadoAsistencia::Tardanza,
            default => null,
        };
    }

    /**
     * Si en la fecha dada este horario ya cerró (pasó la hora límite de tardanza).
     */
    public function yaCerro(string $fecha, CarbonImmutable $ahora): bool
    {
        $hoy = $ahora->toDateString();

        return $fecha < $hoy || ($fecha === $hoy && $ahora->format('H:i') > $this->limiteTardanza());
    }

    /**
     * Elimina el horario junto con las marcas de asistencia de los acopiadores en ese horario
     * (y sus justificaciones y archivos), para no dejar registros sin horario.
     */
    public function eliminarConMarcas(): void
    {
        $archivos = Justificacion::query()
            ->whereNotNull('archivo')
            ->whereHas('asistencia', fn ($q) => $q->where('horario_asistencia_id', $this->id))
            ->pluck('archivo');

        DB::transaction(function () {
            Asistencia::query()->where('horario_asistencia_id', $this->id)->delete();
            $this->delete();
        });

        Storage::disk('local')->delete($archivos->all());
    }

    /**
     * Horario activo en el que se puede marcar en este momento. Solo hay horarios abiertos en
     * los días de asistencia elegidos en el calendario.
     */
    public static function abiertoA(CarbonImmutable $ahora): ?self
    {
        if (! DiaAsistenciaAcopiador::esDiaDeAsistencia($ahora->toDateString())) {
            return null;
        }

        $hora = $ahora->format('H:i');

        return self::query()
            ->where('activo', true)
            ->orderBy('hora_inicio')
            ->get()
            ->first(fn (self $horario) => $horario->estadoParaHora($hora) !== null);
    }

    /**
     * Registra falta a los acopiadores que no marcaron en un horario que ya cerró. Revisa solo
     * los días de asistencia elegidos en el calendario que todavía se pueden justificar, así no importa si el programador de tareas
     * estuvo apagado un rato. Devuelve cuántas faltas se registraron.
     */
    public static function registrarFaltasVencidas(): int
    {
        $ahora = Asistencia::ahora();
        $zona = $ahora->getTimezone();
        $acopiadores = User::query()->where('activo', true)->conAlgunRol([Rol::Acopiador])->get(['id', 'created_at']);
        $horarios = self::query()->where('activo', true)->get();
        $primerDia = $ahora->subDays(Asistencia::DIAS_PARA_JUSTIFICAR)->toDateString();
        $diasDeAsistencia = DiaAsistenciaAcopiador::query()
            ->whereDate('fecha', '>=', $primerDia)
            ->whereDate('fecha', '<=', $ahora->toDateString())
            ->get()
            ->map(fn (DiaAsistenciaAcopiador $dia) => $dia->fecha->toDateString());
        $creadas = 0;

        if ($acopiadores->isEmpty() || $diasDeAsistencia->isEmpty()) {
            return 0;
        }

        foreach ($horarios as $horario) {
            $desde = max($primerDia, $horario->created_at?->setTimezone($zona)->toDateString() ?? $primerDia);

            for ($dia = CarbonImmutable::parse($desde); $dia->toDateString() <= $ahora->toDateString(); $dia = $dia->addDay()) {
                $fecha = $dia->toDateString();

                if (! $diasDeAsistencia->contains($fecha) || ! $horario->yaCerro($fecha, $ahora)) {
                    continue;
                }

                $marcaron = Asistencia::query()
                    ->whereDate('fecha', $fecha)
                    ->where('horario_asistencia_id', $horario->id)
                    ->pluck('user_id');

                foreach ($acopiadores as $acopiador) {
                    if ($marcaron->contains($acopiador->id) || $acopiador->created_at?->setTimezone($zona)->toDateString() > $fecha) {
                        continue;
                    }

                    Asistencia::create([
                        'user_id' => $acopiador->id,
                        'fecha' => $fecha,
                        'horario_asistencia_id' => $horario->id,
                        'estado' => EstadoAsistencia::Falta,
                    ]);
                    $creadas++;
                }
            }
        }

        return $creadas;
    }
}

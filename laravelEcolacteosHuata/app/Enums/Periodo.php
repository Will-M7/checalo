<?php

namespace App\Enums;

use Illuminate\Support\Carbon;

enum Periodo: string
{
    case Turno = 'turno';
    case Dia = 'dia';
    case Semana = 'semana';
    case Mes = 'mes';
    case Anio = 'anio';

    public function label(): string
    {
        return match ($this) {
            self::Turno => 'Turno',
            self::Dia => 'Día',
            self::Semana => 'Semana',
            self::Mes => 'Mes',
            self::Anio => 'Año',
        };
    }

    /**
     * Rango [inicio, fin] de fecha para el período que contiene $fecha (no aplica a Turno).
     *
     * @return array{0: Carbon, 1: Carbon}
     */
    public function rangoParaFecha(Carbon $fecha): array
    {
        return match ($this) {
            self::Dia => [$fecha->copy()->startOfDay(), $fecha->copy()->endOfDay()],
            self::Semana => [$fecha->copy()->startOfWeek(), $fecha->copy()->endOfWeek()],
            self::Mes => [$fecha->copy()->startOfMonth(), $fecha->copy()->endOfMonth()],
            self::Anio => [$fecha->copy()->startOfYear(), $fecha->copy()->endOfYear()],
            self::Turno => [$fecha->copy()->startOfDay(), $fecha->copy()->endOfDay()],
        };
    }

    /**
     * @return array<int, array{value: string, label: string}>
     */
    public static function options(): array
    {
        return collect(self::cases())
            ->map(fn (self $periodo) => ['value' => $periodo->value, 'label' => $periodo->label()])
            ->values()
            ->toArray();
    }
}

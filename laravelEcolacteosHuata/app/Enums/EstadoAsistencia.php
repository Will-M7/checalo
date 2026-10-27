<?php

namespace App\Enums;

enum EstadoAsistencia: string
{
    case Presente = 'presente';
    case Tardanza = 'tardanza';
    case Falta = 'falta';

    public function label(): string
    {
        return match ($this) {
            self::Presente => 'Presente',
            self::Tardanza => 'Tardanza',
            self::Falta => 'Falta',
        };
    }

    /**
     * Color de Flux (badge) con el que se muestra el estado.
     */
    public function color(): string
    {
        return match ($this) {
            self::Presente => 'green',
            self::Tardanza => 'amber',
            self::Falta => 'red',
        };
    }

    public function icono(): string
    {
        return match ($this) {
            self::Presente => 'check-circle',
            self::Tardanza => 'clock',
            self::Falta => 'x-circle',
        };
    }

    /**
     * Estados que el trabajador puede justificar desde "Asistencia".
     */
    public function esJustificable(): bool
    {
        return $this !== self::Presente;
    }
}

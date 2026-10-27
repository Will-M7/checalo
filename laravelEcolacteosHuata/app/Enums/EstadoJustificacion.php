<?php

namespace App\Enums;

enum EstadoJustificacion: string
{
    case Pendiente = 'pendiente';
    case Aceptada = 'aceptada';
    case Rechazada = 'rechazada';

    public function label(): string
    {
        return match ($this) {
            self::Pendiente => 'Pendiente',
            self::Aceptada => 'Aceptada',
            self::Rechazada => 'Rechazada',
        };
    }

    /**
     * Color de Flux (badge) con el que se muestra el estado.
     */
    public function color(): string
    {
        return match ($this) {
            self::Pendiente => 'amber',
            self::Aceptada => 'green',
            self::Rechazada => 'red',
        };
    }
}

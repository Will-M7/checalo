<?php

namespace App\Enums;

enum EstadoPedidoWeb: string
{
    case Pendiente = 'pendiente';
    case Confirmado = 'confirmado';
    case Cancelado = 'cancelado';

    public function label(): string
    {
        return match ($this) {
            self::Pendiente => 'Pendiente',
            self::Confirmado => 'Confirmado',
            self::Cancelado => 'Cancelado',
        };
    }

    /**
     * Color de Flux (badge) con el que se muestra el estado.
     */
    public function color(): string
    {
        return match ($this) {
            self::Pendiente => 'amber',
            self::Confirmado => 'green',
            self::Cancelado => 'zinc',
        };
    }
}

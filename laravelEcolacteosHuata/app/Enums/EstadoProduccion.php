<?php

namespace App\Enums;

enum EstadoProduccion: string
{
    case Planificada = 'planificada';
    case Completada = 'completada';

    public function label(): string
    {
        return match ($this) {
            self::Planificada => 'Pendiente',
            self::Completada => 'Publicado',
        };
    }

    /**
     * @return array<int, array{value: string, label: string}>
     */
    public static function options(): array
    {
        return collect(self::cases())
            ->map(fn (self $estado) => ['value' => $estado->value, 'label' => $estado->label()])
            ->values()
            ->toArray();
    }
}

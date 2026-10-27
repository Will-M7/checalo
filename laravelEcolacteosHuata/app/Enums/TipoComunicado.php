<?php

namespace App\Enums;

enum TipoComunicado: string
{
    case Comunicado = 'comunicado';
    case Asesoramiento = 'asesoramiento';

    public function label(): string
    {
        return match ($this) {
            self::Comunicado => 'Comunicado',
            self::Asesoramiento => 'Asesoramiento',
        };
    }

    /**
     * @return array<int, array{value: string, label: string}>
     */
    public static function options(): array
    {
        return collect(self::cases())
            ->map(fn (self $tipo) => ['value' => $tipo->value, 'label' => $tipo->label()])
            ->values()
            ->toArray();
    }
}

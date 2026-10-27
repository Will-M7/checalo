<?php

namespace App\Enums;

enum EstadoLeche: string
{
    case Normal = 'normal';
    case Soleado = 'soleado';
    case Acidificado = 'acidificado';
    case Aguado = 'aguado';
    case Otro = 'otro';

    public function label(): string
    {
        return match ($this) {
            self::Normal => 'Normal',
            self::Soleado => 'Soleado',
            self::Acidificado => 'Acidificado',
            self::Aguado => 'Aguado',
            self::Otro => 'Otro',
        };
    }

    /**
     * Estados que cuentan como alteración de calidad para el bloqueo automático del proveedor.
     */
    public function esAlteracion(): bool
    {
        return $this !== self::Normal;
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

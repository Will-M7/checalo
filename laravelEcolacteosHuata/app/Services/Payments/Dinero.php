<?php

namespace App\Services\Payments;

class Dinero
{
    public static function minor(mixed $value): int
    {
        $value = (string) $value;
        if (! preg_match('/^\d{1,7}(?:\.(\d{1,2}))?$/', $value, $match)) {
            throw new \InvalidArgumentException('Importe inválido.');
        }

        return (int) explode('.', $value)[0] * 100 + (int) str_pad($match[1] ?? '', 2, '0');
    }

    public static function decimal(int $minor): string
    {
        return sprintf('%d.%02d', intdiv($minor, 100), $minor % 100);
    }
}

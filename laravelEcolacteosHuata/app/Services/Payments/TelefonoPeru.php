<?php

namespace App\Services\Payments;

use Illuminate\Validation\ValidationException;

class TelefonoPeru
{
    public static function coinciden(string $a, string $b): bool
    {
        try {
            return self::normalizar($a) === self::normalizar($b);
        } catch (ValidationException) {
            return false;
        }
    }

    public static function normalizar(string $value): string
    {
        $digits = preg_replace('/\D/', '', $value);
        if (str_starts_with($digits, '51') && strlen($digits) === 11) {
            $digits = substr($digits, 2);
        }
        if (! preg_match('/^9\d{8}$/', $digits)) {
            throw ValidationException::withMessages(['celular' => 'Escribe un celular peruano válido de nueve dígitos.']);
        }

        return '51'.$digits;
    }
}

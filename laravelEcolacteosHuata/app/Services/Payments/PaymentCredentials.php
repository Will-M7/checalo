<?php

namespace App\Services\Payments;

use Illuminate\Validation\ValidationException;

/** Optional private key file permits coordinated server-key rotation without restarting PHP. */
final class PaymentCredentials
{
    public static function key(): string
    {
        $file = config('payments.key_file');
        if (! $file && ! app()->environment('testing')) {
            $file = storage_path('app/private/payment-core.key');
        }
        if ($file && (file_exists($file) || is_link($file))) {
            if (is_link($file) || ! is_file($file) || (fileperms($file) & 0077) !== 0) {
                throw ValidationException::withMessages(['pago' => 'La credencial privada del motor requiere permisos exclusivos del servidor.']);
            }
            $key = trim((string) file_get_contents($file));
            if (strlen($key) < 32 || strlen($key) > 512) {
                throw ValidationException::withMessages(['pago' => 'Credencial privada del motor incompleta.']);
            }

            return $key;
        }
        if (config('payments.key_file')) {
            throw ValidationException::withMessages(['pago' => 'No se encuentra la credencial privada configurada.']);
        }

        return (string) config('payments.key');
    }
}

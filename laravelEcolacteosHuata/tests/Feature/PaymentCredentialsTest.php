<?php

use App\Services\Payments\PaymentCredentials;
use Illuminate\Validation\ValidationException;

test('rotación por archivo privado usa la clave actual sin revelar ni conservar la anterior', function () {
    $file = tempnam(sys_get_temp_dir(), 'huata-key-fixture-');
    try {
        chmod($file, 0600);
        config(['payments.key_file' => $file, 'payments.key' => str_repeat('o', 40)]);
        file_put_contents($file, str_repeat('n', 40));
        expect(PaymentCredentials::key())->toBe(str_repeat('n', 40));
        file_put_contents($file, str_repeat('r', 40));
        expect(PaymentCredentials::key())->toBe(str_repeat('r', 40));
        chmod($file, 0644);
        clearstatcache(true, $file);
        expect(fn () => PaymentCredentials::key())->toThrow(ValidationException::class);
    } finally {
        unlink($file);
    }
});

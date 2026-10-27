<?php

namespace App\Services\Payments;

use App\Models\IntentoPago;

interface PaymentCore
{
    public function receivers(): array;

    public function bridgeDevices(): array;

    public function provision(string $path, array $command): array;

    public function administerReceiver(string $id, array $command): array;

    public function create(IntentoPago $intento): array;

    public function get(string $id): array;

    public function cancel(string $id): array;

    public function resolve(string $id, string $code): array;
}

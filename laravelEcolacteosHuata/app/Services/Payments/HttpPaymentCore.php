<?php

namespace App\Services\Payments;

use App\Models\IntentoPago;
use Illuminate\Http\Client\ConnectionException;
use Illuminate\Support\Facades\Http;
use Illuminate\Validation\ValidationException;

class HttpPaymentCore implements PaymentCore
{
    private function request(string $method, string $path, array $data = []): array
    {
        $url = rtrim((string) config('payments.url'), '/');
        $parts = parse_url($url);
        $local = in_array($parts['host'] ?? '', ['localhost', '127.0.0.1', '::1'], true);
        if (! config('payments.enabled') || strlen((string) PaymentCredentials::key()) < 32 || isset($parts['user']) || isset($parts['query']) || isset($parts['fragment']) ||
            (($parts['scheme'] ?? '') !== 'https' && ! ($local && config('payments.allow_local_http')))) {
            throw ValidationException::withMessages(['pago' => 'Yape no está disponible: la vinculación comercial con Payment Core está pendiente o incompleta. No realices el pago.']);
        }
        try {
            $response = Http::withToken(PaymentCredentials::key())->withoutRedirecting()->acceptJson()->timeout(5)->connectTimeout(2)->send($method, $url.$path, ['json' => $data]);
        } catch (ConnectionException $e) {
            throw ValidationException::withMessages(['pago' => 'Verificación temporalmente no disponible. Conservamos la operación; vuelve a consultar.']);
        }
        if (str_starts_with($path, '/api/admin/') && ! $response->successful()) {
            $messages = [
                'receiver-admin-forbidden' => 'La credencial de aplicación no tiene alcance receiverAdministration autorizado en Core. Debe provisionarlo el operador local.',
                'NAME_MISMATCH' => 'Nombre del receptor no coincide.',
                'PHONE_MISMATCH' => 'Número del receptor no coincide.',
                'QR_MISMATCH' => 'Huella QR no coincide.',
                'PHYSICAL_CONFIRMATION_REQUIRED' => 'Falta la verificación física explícita del receptor.',
                'DEVICE_NOT_LINKED' => 'No hay dispositivo asociado a este receptor.',
                'BRIDGE_OFFLINE' => 'Bridge no comunicado recientemente o listener inactivo.',
                'VERSION_CONFLICT' => 'El receptor cambió en Core. Consulta y repite la operación.',
            ];
            throw ValidationException::withMessages(['receptor' => $messages[$response->json('error') ?? ''] ?? 'Core no confirmó el cambio administrativo. Recepción local bloqueada; consulta y reconcilia el estado.']);
        }
        if ($response->status() === 429) {
            throw ValidationException::withMessages(['codigo' => 'Máximo cinco intentos por minuto. Espera un minuto.']);
        }
        if (! $response->successful() || ! is_array($response->json())) {
            throw ValidationException::withMessages(['pago' => 'El motor no confirmó la operación. Consúltala nuevamente.']);
        }

        return $response->json();
    }

    public function provision(string $path, array $command): array
    {
        if (! preg_match('~\A/api/admin/(?:receivers|devices(?:/[a-zA-Z0-9_-]{1,100}/(?:state|grant))?)\z~', $path)) {
            throw ValidationException::withMessages(['receptor' => 'Operación administrativa no permitida.']);
        }
        if (($this->request('GET', '/health')['mode'] ?? '') !== 'commercial') {
            throw ValidationException::withMessages(['receptor' => 'Se requiere Core comercial.']);
        }

        return $this->request('POST', $path, $command);
    }

    public function administerReceiver(string $id, array $command): array
    {
        $health = $this->request('GET', '/health');
        if (($health['mode'] ?? null) !== 'commercial') {
            throw ValidationException::withMessages(['receptor' => 'La administración exige Core en modo commercial.']);
        }

        return $this->request('POST', '/api/admin/receivers/'.rawurlencode($id).'/state', $command);
    }

    public function bridgeDevices(): array
    {
        return $this->request('GET', '/api/admin/devices');
    }

    public function receivers(): array
    {
        return $this->request('GET', '/api/receivers');
    }

    public function create(IntentoPago $i): array
    {
        return $this->request('POST', '/api/payments', ['reference' => $i->referencia, 'amountMinor' => $i->amount_minor,
            'currency' => '604', 'method' => 'YAPE_NOTIFICATION', 'receiverId' => $i->receptor_snapshot['external_reference'],
            'subject' => $i->cliente_web_id ? 'customer_'.$i->cliente_web_id : 'staff_'.$i->user_id]);
    }

    public function get(string $id): array
    {
        return $this->request('GET', '/api/payments/'.rawurlencode($id));
    }

    public function cancel(string $id): array
    {
        return $this->request('POST', '/api/payments/'.rawurlencode($id).'/cancel');
    }

    public function resolve(string $id, string $code): array
    {
        return $this->request('POST', '/api/payments/'.rawurlencode($id).'/resolve-yape', ['securityCode' => $code]);
    }
}

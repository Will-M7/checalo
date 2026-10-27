<?php

namespace App\Services\Payments;

use Illuminate\Http\Client\ConnectionException;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Http;

/** Ingreso de evidencias, sin estado financiero local. Core autentica la credencial del dispositivo. */
final class BridgeIngress
{
    public function recibir(Request $request, bool $heartbeat): JsonResponse
    {
        $token = $request->bearerToken();
        if (! is_string($token) || ! preg_match('/\A[A-Za-z0-9._~+\/-]{32,512}=*\z/D', $token)) {
            return $this->fallo(401, 'DEVICE_AUTH_REQUIRED', 'Falta una credencial de dispositivo válida.');
        }
        if (strlen($request->getContent()) > 16384) {
            return $this->fallo(413, 'EVENT_TOO_LARGE', 'El evento excede el tamaño permitido.');
        }
        if (! $request->isJson()) {
            return $this->fallo(415, 'JSON_REQUIRED', 'Envía únicamente JSON.');
        }
        $data = $heartbeat ? $request->validate(['listenerConnected' => 'required|boolean', 'identityProof' => 'nullable|string|max:128', 'identityNonce' => 'nullable|string|max:128']) : $request->validate([
            'identityProof' => 'nullable|string|max:128', 'identityNonce' => 'nullable|string|max:128',
            'bridgeEventId' => ['required', 'string', 'regex:/\A[a-zA-Z0-9_-]{16,128}\z/D'],
            'receiverId' => ['required', 'string', 'regex:/\A[a-zA-Z0-9_-]{1,100}\z/D'],
            'packageName' => 'required|in:com.bcp.innovacxion.yapeapp',
            'postedAt' => ['required', 'date', 'regex:/\A\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d{1,9})?(?:Z|[+-]\d\d:\d\d)\z/D'],
            'title' => 'nullable|string|max:8192', 'text' => 'nullable|string|max:8192',
            'bigText' => 'nullable|string|max:8192', 'subText' => 'nullable|string|max:8192',
            'summaryText' => 'nullable|string|max:8192', 'securityCode' => ['nullable', 'string', 'regex:/\A[0-9]{3}\z/D'],
        ]);
        if ($heartbeat) {
            $data['listenerConnected'] = (bool) $data['listenerConnected'];
        } elseif (collect(['title', 'text', 'bigText', 'subText', 'summaryText'])->sum(fn ($p) => mb_strlen($data[$p] ?? '')) > 8192) {
            return $this->fallo(413, 'EVENT_TOO_LARGE', 'El evento excede el tamaño permitido.');
        }
        try {
            $data = app(BridgeProvisioning::class)->authorizeIngress($token, $data, $heartbeat);
        } catch (\Symfony\Component\HttpKernel\Exception\HttpExceptionInterface) {
            return $this->fallo(403, 'DEVICE_SESSION_SUSPENDED', 'Dispositivo revocado o sesión Huata incompatible. Conserva la cola.');
        } catch (\Illuminate\Validation\ValidationException) {
            return $this->fallo(503, 'DEVICE_AUTHORITY_UNAVAILABLE', 'No se pudo validar la autorización del dispositivo. Conserva la cola.');
        }
        $base = rtrim((string) config('payments.url'), '/');
        $url = parse_url($base);
        $local = in_array($url['host'] ?? '', ['localhost', '127.0.0.1', '::1', '[::1]'], true);
        if (! config('payments.enabled') || strlen((string) PaymentCredentials::key()) < 32 || ! isset($url['host']) ||
            isset($url['user']) || isset($url['pass']) || isset($url['query']) || isset($url['fragment']) ||
            (($url['scheme'] ?? '') !== 'https' && ! (($url['scheme'] ?? '') === 'http' && $local && config('payments.allow_local_http')))) {
            return $this->fallo(503, 'COMMERCIAL_LINK_PENDING', 'Falta habilitar la vinculación comercial de Huata con Payment Core y su clave de aplicación. Conserva la cola; no acredita ningún pago.');
        }
        try {
            $http = fn () => Http::withoutRedirecting()->acceptJson()->connectTimeout(2)->timeout(5);
            // El antiguo laboratorio tiene una ruta con el mismo nombre. Nunca se envía evidencia allí.
            $health = $http()->get($base.'/health');
            if (! $health->successful() || $health->json('mode') !== 'commercial') {
                return $this->fallo(503, 'COMMERCIAL_CORE_REQUIRED', 'Se requiere Payment Core en modo comercial; no se admite el motor de laboratorio.');
            }
            // Nunca sustituir por payments.key: cliente y dispositivo son identidades distintas.
            $response = $http()->withToken($token)->post($base.($heartbeat ? '/api/bridge/heartbeat' : '/api/bridge/notifications'), $data);
        } catch (ConnectionException) {
            return $this->fallo(503, 'CORE_UNREACHABLE', 'Payment Core no está disponible. El evento debe conservarse para reintento.');
        } catch (\Throwable) {
            // No propagar excepciones HTTP, cuerpos ni cabeceras con credenciales a logs o al cliente.
            return $this->fallo(502, 'CORE_INVALID_RESPONSE', 'No se pudo verificar la respuesta de Payment Core. Conserva el evento.');
        }
        if (! $response->successful()) {
            $status = in_array($response->status(), [400, 401, 403, 409, 429], true) ? $response->status() : 503;
            $mensaje = match ($status) {
                401 => 'Credencial de dispositivo rechazada. Revisa la vinculación técnica.',
                403 => 'El motor rechazó el acceso o el transporte. Revisa la configuración privada.',
                400 => 'El motor rechazó el formato, receptor o aplicación del evento. Requiere revisión.',
                409 => 'Evento en conflicto. Se conserva para revisión; no se confirma dinero.',
                429 => 'Demasiados intentos. Conserva el evento y reintenta más tarde.',
                default => 'Motor temporalmente no disponible. Conserva el evento para reintento.',
            };

            return $this->fallo($status, 'CORE_REJECTED', $mensaje);
        }
        $ack = $response->json();
        if (! is_array($ack) || ($ack['accepted'] ?? null) !== true || (! $heartbeat && (($ack['eventId'] ?? null) !== $data['bridgeEventId'] ||
            ! in_array($ack['result'] ?? null, ['OBSERVED', 'DUPLICATE', 'REVIEW_RECEIVER_UNVERIFIED', 'REVIEW_CODE_UNAVAILABLE', 'REVIEW_AMBIGUOUS', 'REVIEW_LATE', 'REVIEW_FORMAT_UNRECOGNIZED'], true)))) {
            return $this->fallo(502, 'CORE_ACK_INVALID', 'No existe una confirmación válida de recepción. Conserva el evento.');
        }

        return response()->json([
            'accepted' => true, 'coreMode' => 'commercial', 'receiptOnly' => true,
            ...($heartbeat ? [] : ['eventId' => $data['bridgeEventId'], 'result' => $ack['result'], 'reviewRequired' => ($ack['reviewRequired'] ?? false) === true || str_starts_with($ack['result'], 'REVIEW_')]),
        ], $heartbeat ? 200 : 202)->header('Cache-Control', 'no-store');
    }

    private function fallo(int $status, string $code, string $message): JsonResponse
    {
        return response()->json(['accepted' => false, 'code' => $code, 'message' => $message], $status)->header('Cache-Control', 'no-store');
    }
}

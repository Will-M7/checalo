<?php

namespace App\Services\Payments;

use App\Models\ReceptorPago;
use App\Models\User;
use Illuminate\Support\Carbon;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Storage;
use Illuminate\Support\Str;
use Illuminate\Validation\ValidationException;

class ReceptoresService
{
    public function __construct(private PaymentCore $core) {}

    public function identidad(ReceptorPago $r, ?array $remote): ?string
    {
        if (! $r->qr_path || ! $r->qr_sha256 || ! Storage::disk('public')->exists($r->qr_path)) {
            return 'QR inexistente. Guarda la imagen del receptor.';
        }
        if (! hash_equals($r->qr_sha256, hash_file('sha256', Storage::disk('public')->path($r->qr_path)))) {
            return 'Huella QR no coincide con el archivo guardado.';
        }
        if (! $remote) {
            return 'Receptor no registrado en Core o no autorizado para esta aplicación.';
        }
        if (! hash_equals($r->qr_sha256, (string) ($remote['qrSha256'] ?? ''))) {
            return 'Huella QR no coincide con Core.';
        }
        if ($r->nombre_receptor !== ($remote['name'] ?? null)) {
            return 'Nombre del receptor no coincide.';
        }
        try {
            if (TelefonoPeru::normalizar($r->celular) !== TelefonoPeru::normalizar((string) ($remote['phone'] ?? ''))) {
                return 'Número del receptor no coincide.';
            }
        } catch (ValidationException) {
            return 'Número del receptor no coincide o no es válido.';
        }

        return null;
    }

    public function remoto(ReceptorPago $r): ?array
    {
        return collect($this->core->receivers())->firstWhere('id', $r->referencia_externa);
    }

    public function diagnostico(ReceptorPago $r): string
    {
        try {
            $remote = $this->remoto($r);
            if ($error = $this->identidad($r, $remote)) {
                return $error;
            }
            $states = ['Identidad y QR coinciden técnicamente.'];
            $states[] = ($remote['verified'] ?? false) ? 'Core registra verificación del receptor.' : 'Receptor pendiente de verificación física por administrador.';
            $states[] = ($remote['deviceLinked'] ?? true) ? (($remote['online'] ?? false) ? 'Bridge comunicado recientemente con listener activo.' : 'Bridge no comunicado recientemente o listener inactivo.') : 'No hay dispositivo asociado a este receptor.';
            $states[] = ($remote['enabled'] ?? false) && $r->enabled && $r->verified_at ? 'Recepción comercial habilitada.' : 'Recepción deshabilitada; no realizar pagos.';
            if (($remote['verified'] ?? false) && ! $r->verified_at) {
                $states[] = 'Falta reconciliar la verificación autorizada en Laravel.';
            }

            return implode(' ', $states);
        } catch (ValidationException $e) {
            return 'Core no disponible o vinculación incompleta. '.$e->validator->errors()->first();
        }
    }

    public function cambiar(User $actor, ReceptorPago $r, string $action, bool $physical = false): void
    {
        abort_unless($actor->activo && $actor->puedePantalla('vendedor.configuracion-pagos') && $actor->puedeAdministracionCritica(), 403);
        abort_unless(in_array($action, ['verify', 'enable', 'disable'], true), 422);
        if ($action === 'verify' && ! $physical) {
            $this->error('Confirma físicamente titular, número, cuenta Yape y QR antes de verificar.');
        }
        // Commit the local interlock BEFORE any network mutation. A timeout must never roll it back.
        $version = DB::transaction(function () use ($r, $action) {
            DB::table('integracion_secuencias')->where('clave', 'reparto')->increment('valor', 0);
            $current = ReceptorPago::findOrFail($r->id);
            if ((int) $current->version !== (int) ($r->version ?? 1)) {
                $this->error('La configuración cambió. Actualiza y revisa el receptor.');
            }
            $values = ['enabled' => false, 'version' => (int) $current->version + 1];
            if ($action === 'verify') {
                $values += ['verified_at' => null, 'verified_by' => null];
            }
            $current->update($values);

            return (int) $current->version;
        }, 5);
        $remote = $this->remoto($r);
        if (! $remote && $action === 'verify') {
            app(BridgeProvisioning::class)->registrar($actor, $r);
            $remote = $this->remoto($r);
        }
        if ($action !== 'disable' && ($error = $this->identidad($r, $remote))) {
            $this->error($error);
        }
        if (! $remote || ! isset($remote['version'])) {
            $this->error('Core requiere la actualización de administración de receptores. La recepción permanece bloqueada.');
        }
        if ($action === 'enable' && app(BridgeProvisioning::class)->ready() && ! DB::table('payment_bridge_devices')->where('receiver_id', $r->id)->where('status', 'active')->exists()) {
            $this->error('Actualiza y vincula Bridge desde Huata para sustituir su credencial anterior antes de habilitar recepción.');
        }
        if ($action === 'enable' && (! $r->verified_at || ! ($remote['verified'] ?? false))) {
            $this->error('Receptor pendiente de verificación física y técnica autorizada.');
        }
        if ($action === 'enable' && ! ($remote['online'] ?? false)) {
            $this->error('Bridge no comunicado recientemente con este receptor y listener activo.');
        }
        $result = $this->core->administerReceiver((string) $r->referencia_externa, [
            'operationId' => (string) Str::uuid(), 'expectedVersion' => (int) $remote['version'],
            'actor' => 'staff_'.$actor->id, 'action' => $action, 'name' => $r->nombre_receptor,
            'phone' => $action === 'disable' ? $r->celular : TelefonoPeru::normalizar($r->celular), 'qrSha256' => $r->qr_sha256 ?? '',
            'physicalConfirmed' => $action === 'verify' && $physical,
        ]);
        // Re-read authority after acknowledgement; do not trust a stale or incomplete ACK.
        $confirmed = $this->remoto($r);
        if (! $confirmed || ($confirmed['version'] ?? null) !== ($result['version'] ?? null) || ($result['id'] ?? null) !== $r->referencia_externa ||
            ($action !== 'disable' && $this->identidad($r, $confirmed)) ||
            ($action === 'verify' && (! ($confirmed['verified'] ?? false) || ($confirmed['enabled'] ?? true) || ($confirmed['verifiedBy'] ?? '') !== ($result['verifiedBy'] ?? null) || ! str_ends_with((string) ($confirmed['verifiedBy'] ?? ''), ':staff_'.$actor->id) || ! ($confirmed['verifiedAt'] ?? null))) ||
            ($action === 'enable' && (! ($confirmed['verified'] ?? false) || ! ($confirmed['enabled'] ?? false) || ! ($confirmed['online'] ?? false))) ||
            ($action === 'disable' && ($confirmed['enabled'] ?? true))) {
            $this->error('No se confirmó un estado coherente en Core. Recepción local bloqueada; consulta y repite la operación para reconciliar.');
        }
        $values = ['enabled' => $action === 'enable'];
        if ($action === 'verify') {
            $values += ['verified_at' => Carbon::parse($confirmed['verifiedAt']), 'verified_by' => $actor->id];
        }
        if (ReceptorPago::whereKey($r->id)->where('version', $version)->update($values) !== 1) {
            $this->error('La configuración cambió durante la operación. Revisa y reconcilia el estado antes de habilitar.');
        }
    }

    private function error(string $message): never
    {
        throw ValidationException::withMessages(['receptor' => $message]);
    }
}

<?php

namespace App\Services\Payments;

use App\Models\ReceptorPago;
use App\Models\User;
use Illuminate\Support\Facades\Crypt;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;
use Illuminate\Support\Facades\Storage;
use Illuminate\Support\Str;
use Illuminate\Validation\ValidationException;

final class BridgeProvisioning
{
    public function __construct(private PaymentCore $core, private AsignacionesReceptor $assignments) {}

    public function ready(): bool
    {
        return Schema::hasTable('payment_bridge_devices');
    }

    private function error(string $message): never
    {
        throw ValidationException::withMessages(['bridge' => $message]);
    }

    public function registrar(User $actor, ReceptorPago $r): void
    {
        abort_unless($actor->activo && $actor->puedePantalla('vendedor.configuracion-pagos') && ($actor->puedeAdministracionCritica() || ($r->user_id === $actor->id && $this->assignments->puedePersonal($actor))), 403);
        if (! $r->referencia_externa || ! $r->qr_sha256 || ! $r->qr_path || ! Storage::disk('public')->exists($r->qr_path) || ! hash_equals($r->qr_sha256, hash_file('sha256', Storage::disk('public')->path($r->qr_path)))) {
            $this->error('Guarda la identidad y el archivo QR íntegro antes de registrar el receptor.');
        }
        $this->core->provision('/api/admin/receivers', ['operationId' => (string) Str::uuid(), 'actor' => 'staff_'.$actor->id, 'id' => $r->referencia_externa,
            'name' => $r->nombre_receptor, 'phone' => TelefonoPeru::normalizar($r->celular), 'qrSha256' => $r->qr_sha256]);
    }

    public function ticket(User $actor, ReceptorPago $r, string $mode, bool $responsibleConfirmed): string
    {
        $this->registrar($actor, $r);
        if ($mode !== 'personal') {
            $this->assignments->administrar($actor);
            if (! in_array($mode, ['shared', 'direct'], true) || ! $responsibleConfirmed) {
                $this->error('Confirma la autorización del responsable para vinculación compartida o sin Huata.');
            }
        }
        $legacy = collect($this->core->bridgeDevices())->filter(fn ($d) => is_array($d) && ($d['receiverId'] ?? '') === $r->referencia_externa && ($d['mode'] ?? '') === 'legacy')->values();
        if ($legacy->count() > 1 && ! DB::table('payment_bridge_devices')->where('receiver_id', $r->id)->exists()) {
            $this->error('Existen varios dispositivos anteriores. El administrador debe coordinar cuál actualizar; no creamos otro automáticamente.');
        }
        $legacyId = $legacy->first()['id'] ?? null;

        return DB::transaction(function () use ($actor, $r, $mode, $responsibleConfirmed, $legacyId) {
            DB::table('integracion_secuencias')->where('clave', 'reparto')->increment('valor', 0);
            // Re-enrollment rotates the same device instead of silently creating a duplicate Bridge.
            $device = DB::table('payment_bridge_devices')->where('receiver_id', $r->id)->where('owner_id', $r->user_id)->whereIn('status', ['active', 'suspended', 'pending', 'legacy_pending'])->first();
            $id = $device?->id ?? $legacyId ?? 'bridge-'.Str::uuid();
            if (! $device) {
                DB::table('payment_bridge_devices')->insert(['id' => $id, 'receiver_id' => $r->id, 'owner_id' => $r->user_id, 'mode' => $mode, 'status' => 'pending', 'created_at' => now(), 'updated_at' => now()]);
            }
            // Mode is part of the server-issued invitation, never taken from APK input.
            DB::table('payment_bridge_devices')->where('id', $id)->update(['mode' => $mode, 'updated_at' => now()]);
            DB::table('payment_bridge_tickets')->where('device_id', $id)->whereNull('used_at')->update(['expires_at' => now()]);
            $token = Str::random(64);
            DB::table('payment_bridge_tickets')->insert(['token_hash' => hash('sha256', $token), 'device_id' => $id, 'expires_at' => now()->addMinutes(10), 'created_by' => $actor->id]);
            $this->assignments->auditar($actor, $r->user_id, $r->id, 'bridge_invitation', null, ['device_id' => $id, 'mode' => $mode, 'responsible_confirmed' => $responsibleConfirmed]);

            return $token;
        }, 5);
    }

    public function enroll(string $ticket, string $claim): array
    {
        $t = DB::table('payment_bridge_tickets')->where('token_hash', hash('sha256', $ticket))->first();
        if (! $t || now()->gte($t->expires_at)) {
            $this->error('Vinculación vencida o revocada. Obtén un enlace nuevo desde Huata.');
        }
        $claimHash = hash('sha256', $claim);
        $d = DB::table('payment_bridge_devices')->find($t->device_id);
        $r = ReceptorPago::findOrFail($d->receiver_id);
        $actor = User::findOrFail($t->created_by);
        abort_unless($actor->activo && $actor->puedePantalla('vendedor.configuracion-pagos'), 403);
        if ($d->status === 'revoked' || ($t->claim_hash && ! hash_equals($t->claim_hash, $claimHash))) {
            $this->error('Esta vinculación ya pertenece a otra instalación.');
        }
        if (! $actor->puedeAdministracionCritica() && (! $this->assignments->puedePersonal($actor) || $actor->id !== $d->owner_id)) {
            abort(403);
        }
        if (! $t->claim_hash) {
            $key = Str::random(64);
            DB::transaction(function () use ($t, $claimHash, $key, $d) {
                DB::table('integracion_secuencias')->where('clave', 'reparto')->increment('valor', 0);
                $current = DB::table('payment_bridge_tickets')->where('token_hash', $t->token_hash)->first();
                if ($current->claim_hash) {
                    $this->error('Vinculación concurrente; reintenta desde el mismo dispositivo.');
                }
                DB::table('payment_bridge_tickets')->where('token_hash', $t->token_hash)->update(['claim_hash' => $claimHash]);
                DB::table('payment_bridge_devices')->where('id', $d->id)->update(['secret_hash' => hash('sha256', $key), 'secret_encrypted' => Crypt::encryptString($key), 'installation_hash' => $claimHash, 'status' => 'pending', 'updated_at' => now()]);
            }, 5);
        }
        $d = DB::table('payment_bridge_devices')->find($d->id);
        $key = Crypt::decryptString($d->secret_encrypted);
        // A network retry repeats exactly the same Core operation and credential.
        $registered = $this->core->provision('/api/admin/devices', ['operationId' => 'enroll_'.substr($t->token_hash, 0, 48), 'actor' => 'staff_'.$actor->id,
            'id' => $d->id, 'receiverId' => $r->referencia_externa, 'key' => $key, 'mode' => $d->mode, 'owner' => 'staff_'.$d->owner_id]);
        DB::table('payment_bridge_devices')->where('id', $d->id)->update(['status' => ($registered['enabled'] ?? false) ? 'active' : 'suspended', 'updated_at' => now()]);
        DB::table('payment_bridge_tickets')->where('token_hash', $t->token_hash)->update(['used_at' => now()]);

        return ['deviceId' => $d->id, 'receiverId' => $r->referencia_externa, 'credential' => $key, 'mode' => $d->mode];
    }

    public function assertion(User $user, string $id, string $nonce, string $event, string $sessionUsername): array
    {
        $d = DB::table('payment_bridge_devices')->find($id);
        abort_unless($d && $d->mode === 'personal' && $d->status !== 'revoked' && $user->activo && $user->id === $d->owner_id &&
            hash_equals($user->usuario, $sessionUsername) && $user->puedePantalla('vendedor.configuracion-pagos') && $this->assignments->puedePersonal($user), 403);
        $proof = Str::random(64);
        DB::table('payment_bridge_assertions')->insert(['token_hash' => hash('sha256', $proof), 'device_id' => $id, 'user_id' => $user->id,
            'nonce_hash' => hash('sha256', $nonce), 'event_id' => $event, 'expires_at' => now()->addSeconds(30)]);

        return ['proof' => $proof, 'nonce' => $nonce];
    }

    public function authorizeIngress(string $key, array $data, bool $heartbeat): array
    {
        if (! $this->ready()) {
            return $data;
        }
        $d = DB::table('payment_bridge_devices')->where('secret_hash', hash('sha256', $key))->first();
        if (! $d) {
            if (! $heartbeat && DB::table('payment_bridge_devices')->join('receptores_pago', 'payment_bridge_devices.receiver_id', '=', 'receptores_pago.id')->where('receptores_pago.referencia_externa', $data['receiverId'] ?? '')->exists()) {
                abort(403, 'Credencial anterior no autorizada para este receptor administrado.');
            }
            return $data;
        }
        if (in_array($d->status, ['revoked', 'pending', 'legacy_pending'], true)) {
            abort(403, 'Dispositivo no autorizado.');
        }
        $r = ReceptorPago::findOrFail($d->receiver_id);
        if (! $heartbeat && ($data['receiverId'] ?? '') !== $r->referencia_externa) {
            abort(403);
        }
        if ($d->mode === 'personal') {
            $proof = DB::table('payment_bridge_assertions')->where('token_hash', hash('sha256', $data['identityProof'] ?? ''))->first();
            $owner = User::find($d->owner_id);
            $event = $heartbeat ? 'heartbeat' : $data['bridgeEventId'];
            $valid = $proof && ! $proof->used_at && now()->lt($proof->expires_at) && $proof->device_id === $d->id && $proof->event_id === $event &&
                hash_equals($proof->nonce_hash, hash('sha256', $data['identityNonce'] ?? '')) && $proof->user_id === $d->owner_id && $owner?->activo && $owner->puedePantalla('vendedor.configuracion-pagos') && $this->assignments->puedePersonal($owner);
            if (! $valid) {
                $this->change($owner ?? User::findOrFail($d->owner_id), $d->id, false, 'suspended');
                abort(403, 'Sesión Huata incompatible o no verificada. Cola conservada.');
            }
            if (DB::table('payment_bridge_assertions')->where('token_hash', $proof->token_hash)->whereNull('used_at')->update(['used_at' => now()]) !== 1) {
                abort(403);
            }
            if ($d->status === 'suspended') {
                $this->change($owner, $d->id, true, 'active');
            }
            if (! $heartbeat) {
                $grant = $this->core->provision('/api/admin/devices/'.$d->id.'/grant', ['actor' => 'staff_'.$owner->id, 'eventId' => $event]);
                $data['authorizationGrant'] = $grant['token'] ?? '';
            }
        }
        unset($data['identityProof'], $data['identityNonce']);

        return $data;
    }

    public function revoke(User $actor, string $id): void
    {
        $d = DB::table('payment_bridge_devices')->find($id);
        abort_unless($d, 404);
        abort_unless($actor->activo && $actor->puedePantalla('vendedor.configuracion-pagos') && ($actor->puedeAdministracionCritica() || $actor->id === $d->owner_id), 403);
        $this->change($actor, $id, false, 'revoked');
        DB::table('payment_bridge_tickets')->where('device_id', $id)->update(['expires_at' => now()]);
    }

    private function change(User $actor, string $id, bool $enabled, string $status): void
    {
        $d = DB::table('payment_bridge_devices')->find($id);
        if (! $enabled) {
            DB::table('payment_bridge_devices')->where('id', $id)->update(['status' => $status, 'updated_at' => now()]);
            DB::table('payment_bridge_assertions')->where('device_id', $id)->whereNull('used_at')->update(['used_at' => now()]);
        }
        $result = $this->core->provision('/api/admin/devices/'.$id.'/state', ['operationId' => (string) Str::uuid(), 'actor' => 'staff_'.$actor->id, 'enabled' => $enabled]);
        if (($result['enabled'] ?? null) !== $enabled) {
            $this->error('Core no confirmó el estado del dispositivo.');
        }
        DB::table('payment_bridge_devices')->where('id', $id)->update(['status' => $status, 'updated_at' => now()]);
        $this->assignments->auditar($actor, $d->owner_id, $d->receiver_id, 'bridge_'.$status, null, ['device_id' => $id]);
    }
}

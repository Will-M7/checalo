<?php
namespace App\Services\Payments;

use App\Models\{ReceptorPago, User};
use Illuminate\Http\Request;
use Illuminate\Support\Facades\{Cache, Crypt, DB, Schema};
use Illuminate\Support\Str;
use Illuminate\Validation\ValidationException;

/** Adaptador de sesión Huata a la inscripción e ingreso existentes; Core sigue conciliando. */
final class HuataYape
{
    public function __construct(private BridgeProvisioning $bridge, private PaymentCore $core, private AsignacionesReceptor $assignments) {}
    public function ready(): bool { return Schema::hasTable('huata_yape_installations'); }
    private function error(string $message): never { throw ValidationException::withMessages(['recepcion' => $message]); }
    private function admin(User $actor): void { $this->assignments->administrar($actor); }
    private function compatible($d, User $user): bool {
        if (! $d || ! $user->activo || $d->user_id !== $user->id || ! in_array($d->status, ['authorized', 'active'], true)) return false;
        $r = ReceptorPago::find($d->receiver_id);
        return $r && (! Schema::hasTable('yape_reception_requests') || app(SolicitudesRecepcionYape::class)->permite($d, $user)) && ($d->mode !== 'personal' || ($r->user_id === $user->id && $user->puedePantalla('vendedor.configuracion-pagos') && $this->assignments->puedePersonal($user)));
    }
    private function publicState($d, User $user): array {
        if (! $this->compatible($d, $user)) return ['authorized' => false, 'status' => 'unauthorized'];
        $r = ReceptorPago::findOrFail($d->receiver_id);
        $device = $d->bridge_device_id ? DB::table('payment_bridge_devices')->find($d->bridge_device_id) : null;
        if ($device?->status === 'revoked') return ['authorized' => false, 'status' => 'revoked'];
        return ['authorized' => true, 'status' => $d->status, 'receiver' => $r->nombre_receptor, 'receiverId' => $r->referencia_externa,
            'serverAccepted' => Schema::hasTable('yape_reception_requests') && app(SolicitudesRecepcionYape::class)->actual($r->id)?->status === 'accepted', 'phone' => $r->celular, 'mode' => $d->mode, 'lastCommunication' => $d->last_communication_at];
    }
    public function status(User $user, string $installation, string $label, bool $listener, bool $consent, string $token, ?bool $permission = null): array {
        if (! $this->ready()) return ['authorized' => false, 'status' => 'unavailable'];
        return DB::transaction(function () use ($user, $installation, $label, $listener, $consent, $token, $permission) {
            DB::table('integracion_secuencias')->where('clave', 'reparto')->increment('valor', 0);
            $hash = hash('sha256', $installation);
            $d = DB::table('huata_yape_installations')->where('installation_hash', $hash)->first();
            if (! $d) {
                DB::table('huata_yape_installations')->insert(['id' => (string) Str::uuid(), 'installation_hash' => $hash, 'user_id' => $user->id,
                    'label' => $label, 'created_at' => now(), 'updated_at' => now()]);
            }
            DB::table('huata_yape_installations')->where('installation_hash', $hash)->update(['session_user_id' => $user->id,
                'session_token_hash' => hash('sha256', $token), 'listener_ready' => $listener && $consent, 'consent' => $consent, 'notification_permission' => $permission ?? $listener, 'last_seen_at' => now(), 'updated_at' => now()]);
            $state = $this->publicState(DB::table('huata_yape_installations')->where('installation_hash', $hash)->first(), $user);
            $state['requests'] = Schema::hasTable('yape_reception_requests') ? app(SolicitudesRecepcionYape::class)->pendientes($user) : [];
            return $state;
        }, 5);
    }
    public function approve(User $actor, string $id, int $receiver, string $mode, bool $transfer): void {
        $this->admin($actor);
        abort_unless(in_array($mode, ['personal', 'shared'], true), 422);
        $r = ReceptorPago::findOrFail($receiver);
        if (! $r->qr_path || ! $r->qr_sha256) $this->error('Guarda primero el titular, número y QR del receptor.');
        DB::transaction(function () use ($actor, $id, $r, $mode, $transfer) {
            DB::table('integracion_secuencias')->where('clave', 'reparto')->increment('valor', 0);
            $d = DB::table('huata_yape_installations')->find($id); abort_unless($d, 404);
            if ($d->status === 'revoked' || (($d->bridge_device_id || $d->ticket_encrypted) && ($d->receiver_id !== $r->id || $d->mode !== $mode))) $this->error('No se reutiliza una instalación revocada ni se cambia un receptor ya vinculado. Conserva su historial.');
            $user = User::findOrFail($d->user_id);
            if (Schema::hasTable('yape_reception_requests') && app(SolicitudesRecepcionYape::class)->actual($r->id) && ! app(SolicitudesRecepcionYape::class)->permite((object) [...(array) $d, 'receiver_id' => $r->id], $user)) $this->error('Primero debe aceptar el responsable desde su sesión Android.');
            if (! $user->activo || ($mode === 'personal' && ($r->user_id !== $user->id || ! $user->puedePantalla('vendedor.configuracion-pagos') || ! $this->assignments->puedePersonal($user)))) $this->error('La modalidad personal requiere al titular autorizado; para la cuenta central o compartida usa dispositivo compartido.');
            if ($transfer && ! $d->listener_ready) $this->error('Primero verifica físicamente el permiso y el listener de Huata en ese dispositivo. El Bridge anterior permanece intacto.');
            if (DB::table('huata_yape_installations')->where('receiver_id', $r->id)->where('id', '!=', $id)->whereIn('status', ['authorized', 'active'])->exists()) $this->error('El receptor ya tiene otro dispositivo Huata autorizado. Coordina su retirada antes de transferir.');
            if (! $r->referencia_externa) $r->update(['referencia_externa' => 'receptor_'.Str::uuid()]);
            DB::table('huata_yape_installations')->where('id', $id)->update(['receiver_id' => $r->id, 'mode' => $mode, 'approved_by' => $actor->id,
                'status' => $d->status === 'active' ? 'active' : 'authorized', 'replacement_confirmed' => $transfer, 'updated_at' => now()]);
            $this->assignments->auditar($actor, $user->id, $r->id, 'huata_reception_approved', null, ['installation' => $id, 'mode' => $mode, 'controlled_transfer' => $transfer]);
        }, 5);
    }
    public function activate(User $user, string $installation): array {
        abort_unless($this->ready(), 503);
        $candidate = DB::table('huata_yape_installations')->where('installation_hash', hash('sha256', $installation))->first();
        abort_unless($this->compatible($candidate, $user), 403);
        // Core IO must not be wrapped by a DB rollback: failed HTTP ACKs retain the same ticket/key for retry.
        return Cache::store('file')->lock('huata-yape-receiver:'.$candidate->receiver_id, 120)->block(5, function () use ($user, $installation) {
            $d = DB::table('huata_yape_installations')->where('installation_hash', hash('sha256', $installation))->first();
            abort_unless($this->compatible($d, $user) && $d->session_user_id === $user->id && $d->consent && $d->listener_ready, 403);
            if ($d->status === 'active') return $this->publicState($d, $user);
            $r = ReceptorPago::findOrFail($d->receiver_id);
            $devices = collect($this->core->bridgeDevices())->filter(fn ($x) => ($x['receiverId'] ?? '') === $r->referencia_externa);
            $known = DB::table('payment_bridge_devices')->where('receiver_id', $r->id)->where('owner_id', $r->user_id)->whereIn('status', ['active','suspended','pending','legacy_pending', 'revoked'])->first();
            $expected = $known?->id ?? ($devices->count() === 1 && ($devices->first()['mode'] ?? '') === 'legacy' ? $devices->first()['id'] : null);
            if ($devices->contains(fn ($x) => ($x['id'] ?? '') !== $expected)) $this->error('Hay colectores anteriores sin una correspondencia única. Revisa su transferencia en mantenimiento; no se creó otro dispositivo.');
            if (($devices->isNotEmpty() || ($known?->secret_hash && ! hash_equals($known->installation_hash ?? '', hash('sha256', $installation)))) && ! $d->replacement_confirmed && ! $d->ticket_encrypted) $this->error('Hay un Bridge anterior. El administrador debe confirmar la prueba física y la transferencia controlada; no se modificó el dispositivo anterior.');
            $actor = User::findOrFail($d->approved_by); $this->admin($actor);
            $ticket = $d->ticket_encrypted ? Crypt::decryptString($d->ticket_encrypted) : $this->bridge->ticket($actor, $r, $d->mode, true);
            if (! $d->ticket_encrypted) DB::table('huata_yape_installations')->where('id', $d->id)->update(['ticket_encrypted' => Crypt::encryptString($ticket)]);
            $invitation = DB::table('payment_bridge_tickets')->where('token_hash', hash('sha256', $ticket))->first();
            if ($invitation && now()->gte($invitation->expires_at) && (! $invitation->claim_hash || hash_equals($invitation->claim_hash, hash('sha256', $installation)))) {
                // Same approved installation may recover a lost ACK without rotating its established key.
                DB::table('payment_bridge_tickets')->where('token_hash', $invitation->token_hash)->update(['expires_at' => now()->addMinutes(10)]);
            }
            $cfg = $this->bridge->enroll($ticket, $installation);
            $current = DB::table('huata_yape_installations')->find($d->id);
            if (! $this->compatible($current, $user) || $current->receiver_id !== $d->receiver_id || $current->session_user_id !== $user->id || ! $current->consent) {
                // Revocation won a concurrent race. Do not expose or enable this binding in Huata.
                $this->bridge->revoke($actor, $cfg['deviceId']);
                abort(403);
            }
            $changed = DB::table('huata_yape_installations')->where('id', $d->id)->where('status', 'authorized')->where('consent', true)->where('session_user_id', $user->id)
                ->update(['bridge_device_id' => $cfg['deviceId'], 'status' => 'active', 'updated_at' => now()]);
            if (! $changed) { $this->bridge->revoke($actor, $cfg['deviceId']); abort(403); }
            return $this->publicState(DB::table('huata_yape_installations')->find($d->id), $user);
        });
    }
    public function relay(Request $request, bool $heartbeat): \Illuminate\Http\JsonResponse {
        abort_unless($this->ready(), 503);
        $installation = (string) $request->header('X-Huata-Installation');
        abort_unless(preg_match('/^[a-f0-9]{64}$/', $installation), 403);
        $d = DB::table('huata_yape_installations')->where('installation_hash', hash('sha256', $installation))->first();
        $user = $request->user();
        abort_unless($this->compatible($d, $user) && $d->status === 'active' && $d->session_user_id === $user->id && $d->consent &&
            hash_equals($d->session_token_hash ?? '', hash('sha256', $request->bearerToken() ?? '')), 403);
        $device = DB::table('payment_bridge_devices')->find($d->bridge_device_id);
        abort_unless($device && in_array($device->status, ['active', 'suspended'], true), 403);
        abort_unless($request->isJson(), 415);
        abort_if(strlen($request->getContent()) > 16384, 413);
        $payload = $request->json()->all();
        unset($payload['identityProof'], $payload['identityNonce'], $payload['authorizationGrant']);
        if ($d->mode === 'personal') {
            $proof = $this->bridge->assertion($user, $device->id, Str::random(64), $heartbeat ? 'heartbeat' : (string) ($payload['bridgeEventId'] ?? ''), $user->usuario);
            $payload['identityProof'] = $proof['proof']; $payload['identityNonce'] = $proof['nonce'];
        }
        $relay = Request::create('/api/bridge/'.($heartbeat ? 'heartbeat' : 'notifications'), 'POST', [], [], [],
            ['CONTENT_TYPE' => 'application/json', 'HTTP_AUTHORIZATION' => 'Bearer '.Crypt::decryptString($device->secret_encrypted)], json_encode($payload, JSON_THROW_ON_ERROR));
        $result = app(BridgeIngress::class)->recibir($relay, $heartbeat);
        if ($result->isSuccessful()) DB::table('huata_yape_installations')->where('id', $d->id)->update(['last_communication_at' => now()]);
        return $result;
    }
    public function revoke(User $actor, string $id): void {
        $this->admin($actor); $d = DB::table('huata_yape_installations')->find($id); abort_unless($d, 404);
        DB::table('huata_yape_installations')->where('id', $id)->update(['status' => 'revoked', 'consent' => false, 'updated_at' => now()]);
        $this->assignments->auditar($actor, $d->user_id, $d->receiver_id, 'huata_reception_revoked', null, ['installation' => $id]);
        if ($d->bridge_device_id) $this->bridge->revoke($actor, $d->bridge_device_id);
    }
}

<?php

namespace App\Services\Payments;

use App\Models\{ReceptorPago, User};
use Illuminate\Support\Facades\{DB, Storage};
use Illuminate\Support\Str;
use Illuminate\Validation\ValidationException;

/** Consentimiento del responsable del teléfono. No verifica identidad bancaria ni acredita pagos. */
final class SolicitudesRecepcionYape
{
    public static function identidad(ReceptorPago $r): string
    {
        return hash('sha256', json_encode([$r->referencia_externa, $r->nombre_receptor, $r->celular, $r->qr_sha256], JSON_THROW_ON_ERROR));
    }

    public function actual(int $receiver): ?object
    {
        return DB::table('yape_reception_requests')->where('receiver_id', $receiver)->orderByDesc('sequence')->first();
    }

    private function error(string $message): never
    {
        throw ValidationException::withMessages(['recepcion' => $message]);
    }

    public function guardar(User $actor, ?int $id, string $nombre, string $numero, int $responsable, ?string $path, ?string $hash, bool $reenviar = false): ReceptorPago
    {
        app(AsignacionesReceptor::class)->administrar($actor);
        $phone = TelefonoPeru::normalizar($numero);
        validator(['nombre' => $nombre], ['nombre' => 'required|string|max:100'])->validate();
        return DB::transaction(function () use ($actor, $id, $nombre, $phone, $responsable, $path, $hash, $reenviar) {
            DB::table('integracion_secuencias')->where('clave', 'reparto')->increment('valor', 0);
            $user = User::whereKey($responsable)->where('activo', true)->lockForUpdate()->firstOrFail();
            $r = $id ? ReceptorPago::lockForUpdate()->findOrFail($id) : ReceptorPago::whereIn('celular', [$phone, substr($phone, -9)])->lockForUpdate()->first();
            if (! $id && $r && ($r->nombre_receptor !== $nombre || ($hash && $r->qr_sha256 !== $hash))) {
                $this->error('Ese número ya tiene receptor. Edita el registro existente para revisar y confirmar su identidad.');
            }
            $duplicate = ReceptorPago::whereIn('celular', [$phone, substr($phone, -9)])->when($r, fn ($q) => $q->whereKeyNot($r->id))->exists();
            if ($duplicate) $this->error('El número ya está registrado en otro receptor. Revisa el registro existente.');
            $r ??= new ReceptorPago(['user_id' => $user->id, 'whatsapp' => '', 'referencia_externa' => 'receptor_'.Str::uuid()]);
            $before = $r->exists ? $r->getAttributes() : null;
            $old = $r->exists ? $this->actual($r->id) : null;
            $identityChanged = ! $r->exists || $r->nombre_receptor !== $nombre || $r->celular !== $phone || ($hash && $r->qr_sha256 !== $hash);
            $r->fill(['nombre_receptor' => $nombre, 'celular' => $phone]);
            if (! $r->referencia_externa) $r->referencia_externa = 'receptor_'.Str::uuid();
            if ($path) $r->fill(['qr_path' => $path, 'qr_sha256' => $hash]);
            if (! $r->qr_path || ! $r->qr_sha256 || ! Storage::disk('public')->exists($r->qr_path)) $this->error('Guarda una imagen QR válida del receptor.');
            $newRequest = $identityChanged || ! $old || $old->responsible_id !== $user->id || $reenviar;
            if ($identityChanged) $r->fill(['enabled' => false, 'verified_at' => null, 'verified_by' => null, 'version' => ($r->version ?? 0) + 1]);
            if ($newRequest) {
                $r->enabled = false;
                if (! $identityChanged) $r->version = (int) $r->version + 1;
            }
            $r->save();
            if ($newRequest) {
                // Primero interbloqueo local. No se borra ni rota una credencial anterior desde aquí.
                DB::table('yape_reception_requests')->where('receiver_id', $r->id)->whereIn('status', ['pending', 'accepted'])->update(['status' => 'revoked', 'updated_at' => now()]);
                DB::table('huata_yape_installations')->where('receiver_id', $r->id)->whereIn('status', ['authorized', 'active'])->update(['status' => 'revoked', 'consent' => false, 'updated_at' => now()]);
                DB::table('yape_reception_requests')->insert(['id' => (string) Str::uuid(), 'receiver_id' => $r->id, 'responsible_id' => $user->id,
                    'assigned_by' => $actor->id, 'identity_hash' => self::identidad($r), 'receiver_snapshot' => json_encode($r->snapshot(), JSON_THROW_ON_ERROR),
                    'status' => 'pending', 'created_at' => now(), 'updated_at' => now()]);
            }
            app(AsignacionesReceptor::class)->auditar($actor, $user->id, $r->id, 'receiver_request_saved', $before,
                ['identity_changed' => $identityChanged, 'new_request' => $newRequest, 'responsible_id' => $user->id]);
            return $r;
        }, 5);
    }

    public function pendientes(User $user): array
    {
        return DB::table('yape_reception_requests')->where('responsible_id', $user->id)->where('status', 'pending')->orderBy('created_at')->get()
            ->filter(fn ($q) => $this->actual($q->receiver_id)?->id === $q->id)
            ->map(function ($q) {
                $r = ReceptorPago::findOrFail($q->receiver_id);
                return ['id' => $q->id, 'name' => $r->nombre_receptor, 'phone' => $r->celular,
                    'qrUrl' => '/storage/'.$r->qr_path, 'identity' => $q->identity_hash];
            })->values()->all();
    }

    public function decidir(User $user, string $requestId, string $installation, string $token, bool $accept): void
    {
        DB::transaction(function () use ($user, $requestId, $installation, $token, $accept) {
            DB::table('integracion_secuencias')->where('clave', 'reparto')->increment('valor', 0);
            $q = DB::table('yape_reception_requests')->where('id', $requestId)->lockForUpdate()->first();
            abort_unless($q && $user->activo && $q->responsible_id === $user->id, 403);
            $d = DB::table('huata_yape_installations')->where('installation_hash', hash('sha256', $installation))->lockForUpdate()->first();
            abort_unless($d && $d->session_user_id === $user->id && hash_equals($d->session_token_hash ?? '', hash('sha256', $token)), 403);
            $target = $accept ? 'accepted' : 'rejected';
            if ($q->status === $target && (! $accept || $q->installation_id === $d->id)) return; // ACK perdido.
            if ($q->status !== 'pending' || $this->actual($q->receiver_id)?->id !== $q->id) $this->error('La solicitud cambió. Consulta nuevamente.');
            $r = ReceptorPago::findOrFail($q->receiver_id);
            if (! hash_equals($q->identity_hash, self::identidad($r))) $this->error('La identidad del receptor cambió. Administración debe renovar la solicitud.');
            if ($accept) {
                app(AsignacionesReceptor::class)->auditar($user, $user->id, $r->id, 'installation_binding_replaced', null, ['installation' => $d->id, 'previous_receiver' => $d->receiver_id, 'previous_device' => $d->bridge_device_id, 'previous_status' => $d->status]);
                if (in_array($d->status, ['authorized', 'active'], true)) $this->error('Este teléfono conserva otra vinculación. Administración debe revocarla y coordinar su transferencia.');
                if (DB::table('huata_yape_installations')->where('receiver_id', $r->id)->whereIn('status', ['authorized', 'active'])->exists()) $this->error('El receptor ya tiene un teléfono autorizado. Requiere transferencia administrativa.');
                DB::table('huata_yape_installations')->where('id', $d->id)->update(['user_id' => $user->id, 'receiver_id' => $r->id, 'approved_by' => $q->assigned_by,
                    'mode' => 'shared', 'status' => 'authorized', 'ticket_encrypted' => null, 'bridge_device_id' => null, 'consent' => true, 'replacement_confirmed' => false, 'updated_at' => now()]);
            }
            DB::table('yape_reception_requests')->where('id', $q->id)->update(['status' => $target, 'installation_id' => $accept ? $d->id : null, 'decided_at' => now(), 'updated_at' => now()]);
            app(AsignacionesReceptor::class)->auditar($user, $user->id, $r->id, 'receiver_request_'.$target, null, ['request' => $q->id, 'installation' => $d->id, 'bank_identity_verified' => false]);
        }, 5);
    }

    /** Valida también cada transmisión; una variable local de Android nunca concede esta capacidad. */
    public function permite(object $d, User $user): bool
    {
        $q = $this->actual($d->receiver_id);
        if (! $q) return true; // Autorización anterior conservada hasta transferencia explícita.
        $r = ReceptorPago::find($d->receiver_id);
        return $r && $q->status === 'accepted' && $q->responsible_id === $user->id && $q->installation_id === $d->id && hash_equals($q->identity_hash, self::identidad($r));
    }

    public function asignar(User $actor, User $seller, ReceptorPago $r): void
    {
        app(AsignacionesReceptor::class)->administrar($actor);
        abort_unless($seller->activo && ($seller->puedeVender('vendedor.ventas') || $seller->puedeVender('vendedor.pedidos-web')), 422);
        DB::transaction(function () use ($actor, $seller, $r) {
            DB::table('integracion_secuencias')->where('clave', 'reparto')->increment('valor', 0);
            $q = $this->actual($r->id);
            if ($q && in_array($q->status, ['rejected', 'revoked'], true)) $this->error('El receptor fue rechazado o revocado. Renueva su autorización antes de asignarlo.');
            $old = DB::table('payment_seller_assignments')->where('user_id', $seller->id)->first();
            $values = ['mode' => 'shared', 'receiver_id' => $r->id, 'personal_allowed' => false, 'assigned_by' => $actor->id,
                'owner_authorized_at' => now(), 'version' => ($old?->version ?? 0) + 1, 'created_at' => $old?->created_at ?? now(), 'updated_at' => now()];
            DB::table('payment_seller_assignments')->updateOrInsert(['user_id' => $seller->id], $values);
            app(AsignacionesReceptor::class)->auditar($actor, $seller->id, $r->id, 'seller_assignment', $old, $values);
        }, 5);
    }

    public function revocar(User $actor, ReceptorPago $r): void
    {
        app(AsignacionesReceptor::class)->administrar($actor);
        DB::transaction(function () use ($actor, $r) {
            DB::table('integracion_secuencias')->where('clave', 'reparto')->increment('valor', 0);
            $r->update(['enabled' => false, 'version' => $r->version + 1]);
            DB::table('yape_reception_requests')->where('receiver_id', $r->id)->whereIn('status', ['pending', 'accepted'])->update(['status' => 'revoked', 'updated_at' => now()]);
            DB::table('huata_yape_installations')->where('receiver_id', $r->id)->update(['status' => 'revoked', 'consent' => false, 'updated_at' => now()]);
            app(AsignacionesReceptor::class)->auditar($actor, null, $r->id, 'receiver_revoked', null, ['enabled' => false]);
        }, 5);
        // El bloqueo local anterior persiste incluso si Core está sin conexión.
        foreach (DB::table('payment_bridge_devices')->where('receiver_id', $r->id)->where('status', '!=', 'revoked')->get() as $d) app(BridgeProvisioning::class)->revoke($actor, $d->id);
    }
}

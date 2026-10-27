<?php

namespace App\Services\Payments;

use App\Models\ReceptorPago;
use App\Models\User;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;
use Illuminate\Support\Str;
use Illuminate\Validation\ValidationException;

final class AsignacionesReceptor
{
    public function configurada(): bool
    {
        return Schema::hasTable('payment_seller_assignments');
    }

    public function receptor(User $seller): ?ReceptorPago
    {
        if (! $this->configurada()) {
            return ReceptorPago::where('user_id', $seller->id)->first();
        }
        $assignment = DB::table('payment_seller_assignments')->where('user_id', $seller->id)->first();
        if (! $assignment) {
            // Compatibility for existing accounts; new sharing is always an explicit server assignment.
            return ReceptorPago::where('user_id', $seller->id)->first();
        }
        if ($assignment->mode === 'personal') {
            return $assignment->personal_allowed ? ReceptorPago::where('user_id', $seller->id)->first() : null;
        }
        $id = $assignment->mode === 'central' ? DB::table('payment_receiver_policy')->where('id', 1)->value('central_receiver_id') : $assignment->receiver_id;

        return $assignment->owner_authorized_at && $id ? ReceptorPago::find($id) : null;
    }

    public function puedePersonal(User $seller): bool
    {
        if (! $this->configurada()) {
            return ReceptorPago::where('user_id', $seller->id)->exists();
        }
        $a = DB::table('payment_seller_assignments')->where('user_id', $seller->id)->first();

        return $a ? $a->mode === 'personal' && (bool) $a->personal_allowed : ReceptorPago::where('user_id', $seller->id)->exists();
    }

    public function administrar(User $actor): void
    {
        abort_unless($actor->activo && $actor->puedePantalla('vendedor.configuracion-pagos') && $actor->puedeAdministracionCritica(), 403);
    }

    public function central(User $actor, int $id, bool $consent): void
    {
        $this->administrar($actor);
        $this->autorizado($id, $consent);
        DB::transaction(function () use ($actor, $id) {
            DB::table('integracion_secuencias')->where('clave', 'reparto')->increment('valor', 0);
            $old = DB::table('payment_receiver_policy')->where('id', 1)->first();
            DB::table('payment_receiver_policy')->updateOrInsert(['id' => 1], ['central_receiver_id' => $id, 'updated_by' => $actor->id, 'created_at' => $old?->created_at ?? now(), 'updated_at' => now()]);
            $this->auditar($actor, null, $id, 'central_receiver', $old, ['central_receiver_id' => $id, 'owner_consent' => true]);
        }, 5);
    }

    public function asignar(User $actor, User $seller, string $mode, ?int $receiver, bool $allowPersonal, bool $consent): void
    {
        $this->administrar($actor);
        abort_unless($seller->activo && $seller->puedePantalla('vendedor.configuracion-pagos'), 422);
        if (! in_array($mode, ['personal', 'central', 'shared'], true)) {
            throw ValidationException::withMessages(['modalidad' => 'Selecciona una modalidad existente.']);
        }
        if ($mode === 'central') {
            $receiver = DB::table('payment_receiver_policy')->where('id', 1)->value('central_receiver_id');
        }
        if ($mode !== 'personal') {
            $this->autorizado((int) $receiver, $consent);
        }
        DB::transaction(function () use ($actor, $seller, $mode, $receiver, $allowPersonal, $consent) {
            DB::table('integracion_secuencias')->where('clave', 'reparto')->increment('valor', 0);
            $old = DB::table('payment_seller_assignments')->where('user_id', $seller->id)->first();
            $state = ['mode' => $mode, 'receiver_id' => $mode === 'personal' ? null : $receiver, 'personal_allowed' => $mode === 'personal' && $allowPersonal,
                'assigned_by' => $actor->id, 'owner_authorized_at' => $consent ? now() : null, 'version' => ($old?->version ?? 0) + 1, 'created_at' => $old?->created_at ?? now(), 'updated_at' => now()];
            DB::table('payment_seller_assignments')->updateOrInsert(['user_id' => $seller->id], $state);
            $this->auditar($actor, $seller->id, $receiver, 'seller_assignment', $old, $state);
        }, 5);
    }

    private function autorizado(int $id, bool $consent): void
    {
        $r = ReceptorPago::find($id);
        if (! $consent || ! $r?->verified_at || ! $r->referencia_externa) {
            throw ValidationException::withMessages(['receptor' => 'Se requiere receptor verificado y autorización expresa de su responsable.']);
        }
    }

    public function auditar(User $actor, ?int $subject, ?int $receiver, string $action, mixed $before, array $after): void
    {
        DB::table('payment_authorization_events')->insert(['id' => (string) Str::uuid(), 'actor_id' => $actor->id, 'subject_id' => $subject, 'receiver_id' => $receiver,
            'action' => $action, 'before_state' => $before ? json_encode($before, JSON_THROW_ON_ERROR) : null, 'after_state' => json_encode($after, JSON_THROW_ON_ERROR), 'created_at' => now()]);
    }
}

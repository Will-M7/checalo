<?php

namespace Tests\Support;

use App\Models\ReceptorPago;
use Illuminate\Http\Client\ConnectionException;
use Illuminate\Http\Client\Factory;
use Illuminate\Support\Facades\Http;

/** Isolated Core contract stub; financial reconciliation remains covered by canonical circuit tests. */
class ReceiverCoreFixture
{
    public array $state;

    public bool $failAfterMutation = false;

    public bool $unavailable = false;

    public bool $denyAdmin = false;

    public int $mutations = 0;

    public function __construct(ReceptorPago $r)
    {
        if (\Illuminate\Support\Facades\Schema::hasTable('payment_bridge_devices')) {
            \Illuminate\Support\Facades\DB::table('payment_bridge_devices')->insertOrIgnore(['id' => 'fixture-'.$r->id, 'receiver_id' => $r->id, 'owner_id' => $r->user_id, 'mode' => 'direct', 'status' => 'active', 'created_at' => now(), 'updated_at' => now()]);
        }
        $this->state = ['id' => $r->referencia_externa, 'name' => $r->nombre_receptor, 'phone' => $r->celular,
            'qrSha256' => $r->qr_sha256, 'verified' => false, 'enabled' => false, 'online' => true, 'deviceLinked' => true, 'version' => 0];
        Http::swap(new Factory);
        Http::preventStrayRequests();
        Http::fake(function ($request) {
            if ($this->unavailable) {
                throw new ConnectionException('fixture disconnected');
            }
            if (str_ends_with($request->url(), '/health')) {
                return Http::response(['mode' => 'commercial']);
            }
            if (str_ends_with($request->url(), '/api/receivers')) {
                if ($this->failAfterMutation && $this->mutations) {
                    throw new ConnectionException('fixture interrupted after commit');
                }

                return Http::response([$this->state]);
            }
            if (str_contains($request->url(), '/api/admin/receivers/')) {
                if ($this->denyAdmin) {
                    return Http::response(['error' => 'receiver-admin-forbidden'], 403);
                }
                if ($request['expectedVersion'] !== $this->state['version']) {
                    return Http::response(['error' => 'VERSION_CONFLICT'], 409);
                }
                $this->mutations++;
                $this->state['version']++;
                if ($request['action'] === 'verify') {
                    $this->state['verified'] = true;
                    $this->state['enabled'] = false;
                    $this->state['verifiedBy'] = 'huata:'.$request['actor'];
                    $this->state['verifiedAt'] = now()->toIso8601String();
                } else {
                    $this->state['enabled'] = $request['action'] === 'enable';
                }

                return Http::response($this->state);
            }

            return Http::response([], 404);
        });
    }
}

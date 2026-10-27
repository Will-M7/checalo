<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Services\Payments\BridgeIngress;
use Illuminate\Http\Request;

class YapeBridgeController extends Controller
{
    public function heartbeat(Request $request, BridgeIngress $ingress)
    {
        return $ingress->recibir($request, true);
    }

    public function notification(Request $request, BridgeIngress $ingress)
    {
        return $ingress->recibir($request, false);
    }
}

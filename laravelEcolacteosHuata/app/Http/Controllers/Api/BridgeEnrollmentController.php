<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Services\Payments\BridgeProvisioning;
use Illuminate\Http\Request;

final class BridgeEnrollmentController extends Controller
{
    public function enroll(Request $r, BridgeProvisioning $service)
    {
        $data = $r->validate(['ticket' => 'required|string|size:64', 'claim' => 'required|string|min:32|max:128']);

        return response()->json($service->enroll($data['ticket'], $data['claim']))->header('Cache-Control', 'no-store');
    }

    public function assertion(Request $r, BridgeProvisioning $service)
    {
        $data = $r->validate(['deviceId' => 'required|string|max:100', 'nonce' => 'required|string|min:32|max:128', 'eventId' => 'required|string|max:128', 'sessionUsername' => 'required|string|max:100']);

        return response()->json($service->assertion($r->user(), $data['deviceId'], $data['nonce'], $data['eventId'], $data['sessionUsername']))->header('Cache-Control', 'no-store');
    }

    public function download()
    {
        $user = auth('web')->user();
        abort_unless($user && $user->activo && $user->puedePantalla('vendedor.configuracion-pagos'), 403);
        $file = storage_path('app/private/bridge/yape-bridge.apk');
        abort_unless(is_file($file), 503, 'La APK firmada aún no ha sido publicada por el operador.');

        return response()->download($file, 'Huata-Yape-Bridge.apk', ['Cache-Control' => 'private, no-store', 'X-Content-Type-Options' => 'nosniff']);
    }
}

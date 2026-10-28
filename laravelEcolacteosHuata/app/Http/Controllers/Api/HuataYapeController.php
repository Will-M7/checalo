<?php
namespace App\Http\Controllers\Api;
use App\Http\Controllers\Controller;
use App\Services\Payments\HuataYape;
use Illuminate\Http\Request;
final class HuataYapeController extends Controller {
    public function status(Request $r, HuataYape $s) {
        $d = $r->validate(['installation' => 'required|regex:/^[a-f0-9]{64}$/', 'label' => 'required|string|max:100', 'listener' => 'required|boolean', 'consent' => 'required|boolean', 'permission' => 'sometimes|boolean']);
        return response()->json($s->status($r->user(), $d['installation'], $d['label'], $d['listener'], $d['consent'], $r->bearerToken(), $d['permission'] ?? null))->header('Cache-Control', 'no-store');
    }
    public function activate(Request $r, HuataYape $s) {
        $d = $r->validate(['installation' => 'required|regex:/^[a-f0-9]{64}$/']);
        return response()->json($s->activate($r->user(), $d['installation']))->header('Cache-Control', 'no-store');
    }
    public function decision(Request $r, \App\Services\Payments\SolicitudesRecepcionYape $s) {
        $d = $r->validate(['installation' => 'required|regex:/^[a-f0-9]{64}$/', 'requestId' => 'required|uuid', 'accept' => 'required|boolean']);
        $s->decidir($r->user(), $d['requestId'], $d['installation'], $r->bearerToken(), $d['accept']);
        return response()->json(['saved' => true])->header('Cache-Control', 'no-store');
    }
    public function heartbeat(Request $r, HuataYape $s) { return $s->relay($r, true); }
    public function evidence(Request $r, HuataYape $s) { return $s->relay($r, false); }
}

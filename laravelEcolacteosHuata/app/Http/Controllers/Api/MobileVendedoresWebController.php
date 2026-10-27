<?php
namespace App\Http\Controllers\Api;
use App\Http\Controllers\Controller;
use App\Models\User;
use App\Services\VendedoresWeb;
use Illuminate\Http\Request;
final class MobileVendedoresWebController extends Controller
{
    public function index(Request $r, VendedoresWeb $s)
    {
        abort_unless($r->user()->puedePantalla('admin.usuarios'), 403);
        return response()->json(User::orderBy('nombres')->get()->map(fn ($u) => $s->estado($u)));
    }
    public function propio(Request $r, VendedoresWeb $s)
    {
        abort_unless($r->user()->puedePantalla('vendedor.pedidos-web'), 403);
        return response()->json($s->estado($r->user()));
    }
    public function pausar(Request $r, VendedoresWeb $s)
    {
        $d = $r->validate(['pausado' => 'required|boolean']);
        return response()->json($s->cambiar($r->user(), $r->user(), $d['pausado'], false));
    }
    public function autorizar(Request $r, int $usuario, VendedoresWeb $s)
    {
        $d = $r->validate(['autorizado' => 'required|boolean']);
        return response()->json($s->cambiar($r->user(), User::findOrFail($usuario), $d['autorizado'], true));
    }
}

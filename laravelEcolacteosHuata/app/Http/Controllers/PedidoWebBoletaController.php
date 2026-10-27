<?php

namespace App\Http\Controllers;

use App\Models\Empresa;
use App\Models\PedidoWeb;
use Barryvdh\DomPDF\Facade\Pdf;
use Illuminate\Http\Response;
use Illuminate\Support\Facades\Auth;

class PedidoWebBoletaController extends Controller
{
    /**
     * Comprobante del pedido para el cliente, sin iniciar sesión (se abre con el token secreto del
     * pedido). Mientras el pago no se confirme es un comprobante del pedido; cuando el vendedor
     * confirma el pago, la misma dirección muestra la boleta de venta oficial.
     */
    public function __invoke(string $token): Response
    {
        $pedido = PedidoWeb::query()->with('items', 'venta.items', 'venta.vendedor')->where('token', $token)->firstOrFail();
        if ($pedido->cliente_web_id) {
            abort_unless(Auth::guard('cliente')->id() === $pedido->cliente_web_id, 404);
        }
        $empresa = Empresa::actual();

        if ($pedido->venta) {
            $pdf = Pdf::loadView('ventas.boleta', ['venta' => $pedido->venta, 'empresa' => $empresa])->setPaper('a5', 'portrait');

            return $pdf->stream('boleta-'.$pedido->venta->numero_boleta.'.pdf');
        }

        $pdf = Pdf::loadView('ventas.comprobante-pedido', ['pedido' => $pedido, 'empresa' => $empresa])->setPaper('a5', 'portrait');

        return $pdf->stream('pedido-'.$pedido->id.'.pdf');
    }
}

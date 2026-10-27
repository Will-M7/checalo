<?php

namespace App\Http\Controllers;

use App\Models\Empresa;
use App\Models\PedidoWeb;
use App\Models\Venta;
use Barryvdh\DomPDF\Facade\Pdf;
use Illuminate\Http\Response;

class VentaBoletaPdfController extends Controller
{
    public function __invoke(string $current_team, Venta $venta): Response
    {
        $actor = auth('web')->user();
        abort_unless($actor && ($actor->puedePantalla('vendedor.historial') || ($actor->puedePantalla('vendedor.ventas') && $venta->vendedor_id === $actor->id) || ($actor->puedePantalla('vendedor.pedidos-web') && PedidoWeb::where('venta_id', $venta->id)->exists())), 403);
        $venta->load('items', 'cliente', 'vendedor');

        $pdf = Pdf::loadView('ventas.boleta', [
            'venta' => $venta,
            'empresa' => Empresa::actual(),
        ])->setPaper('a5', 'portrait');

        return $pdf->stream('boleta-'.$venta->numero_boleta.'.pdf');
    }
}

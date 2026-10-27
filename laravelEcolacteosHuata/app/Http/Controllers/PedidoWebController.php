<?php

namespace App\Http\Controllers;

use App\Services\Payments\CobrosService;
use App\Services\Payments\StockInsuficiente;
use App\Services\Payments\TelefonoPeru;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Auth;
use Illuminate\Validation\Rule;
use Illuminate\Validation\ValidationException;

class PedidoWebController extends Controller
{
    public function store(Request $request, CobrosService $service): JsonResponse
    {
        $client = Auth::guard('cliente')->user();
        if ($request->has('celular')) {
            $request->validate(['celular' => 'required|string|max:20']);
            $request->merge(['celular' => TelefonoPeru::normalizar($request->input('celular'))]);
        }
        $data = $request->validate(['items' => 'required|array|min:1|max:30', 'idempotency_key' => 'required|uuid',
            'celular' => ['sometimes', 'required', Rule::unique('clientes_web')->ignore($client->id)]]);
        // El contacto declarado no interviene en la identidad bancaria ni en la conciliación.
        if (isset($data['celular']) && $client->celular !== $data['celular']) {
            $client->update(['celular' => $data['celular']]);
        }
        try {
            $order = $service->pedido($client, $data['items'], 'pendiente', $data['idempotency_key']);
        } catch (StockInsuficiente $e) {
            throw ValidationException::withMessages(['items' => $e->getMessage()]);
        }
        return response()->json(['id' => $order->id, 'total' => $order->total, 'metodo_pago' => $order->metodo_pago, 'url_pedido' => route('cliente.cuenta', ['pedido' => $order->id]).'#pedido-'.$order->id,
            'url_boleta' => $order->venta_id ? $order->urlBoleta() : null, 'message' => 'Pedido guardado. Pendiente de pago.'], 201);
    }
}

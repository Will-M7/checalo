<?php

namespace App\Http\Controllers;

use App\Models\IntentoPago;
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
        $data = $request->validate(['items' => 'required|array|min:1|max:30', 'metodo_pago' => 'required|in:yape,yape_despues,plin,efectivo,saldo', 'idempotency_key' => 'required|uuid',
            'celular' => ['sometimes', 'required', Rule::unique('clientes_web')->ignore($client->id)]]);
        // El contacto declarado no interviene en la identidad bancaria ni en la conciliación.
        if (isset($data['celular']) && $client->celular !== $data['celular']) {
            $client->update(['celular' => $data['celular']]);
        }
        try {
            $order = $service->pedido($client, $data['items'], $data['metodo_pago'] === 'yape_despues' ? 'yape' : $data['metodo_pago'], $data['idempotency_key'], $data['metodo_pago'] === 'yape_despues');
        } catch (StockInsuficiente $e) {
            throw ValidationException::withMessages(['items' => $e->getMessage()]);
        }
        $payment = IntentoPago::where('pedido_web_id', $order->id)->first();
        $message = null;
        if ($payment) {
            try {
                $service->iniciar($payment);
            } catch (ValidationException $e) {
                $message = 'Pedido conservado; la verificación está temporalmente no disponible.';
            }
        }

        return response()->json(['id' => $order->id, 'total' => $order->total, 'metodo_pago' => $order->metodo_pago, 'url_pedido' => $data['metodo_pago'] === 'yape_despues' ? route('cliente.cuenta') : route('cliente.pedido', $order->id),
            'url_boleta' => $order->venta_id ? $order->urlBoleta() : null, 'message' => $message], 201);
    }
}

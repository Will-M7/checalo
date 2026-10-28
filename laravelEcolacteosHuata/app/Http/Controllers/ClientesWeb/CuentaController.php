<?php

namespace App\Http\Controllers\ClientesWeb;

use App\Http\Controllers\Controller;
use App\Models\IntentoPago;
use App\Models\MovimientoSaldo;
use App\Models\PedidoWeb;
use App\Services\Payments\CobrosService;
use App\Services\Payments\Dinero;
use App\Services\Payments\TelefonoPeru;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Facades\Hash;
use Illuminate\Validation\Rule;
use Illuminate\Validation\ValidationException;

class CuentaController extends Controller
{
    public function index()
    {
        $client = Auth::guard('cliente')->user();

        return view('clientes-web.cuenta', ['client' => $client, 'orders' => PedidoWeb::with('items', 'vendedor')->where('cliente_web_id', $client->id)->latest()->orderByDesc('id')->paginate(10),
            'payments' => IntentoPago::where('cliente_web_id', $client->id)->latest()->limit(20)->get(), 'movements' => MovimientoSaldo::where('cliente_web_id', $client->id)->latest()->limit(30)->get()]);
    }

    public function perfil(Request $r)
    {
        $client = Auth::guard('cliente')->user();
        if (! Hash::check((string) $r->input('current_password'), $client->password)) {
            throw ValidationException::withMessages(['current_password' => 'Confirma tu contraseña para modificar el perfil.']);
        }
        $r->merge(['celular' => TelefonoPeru::normalizar((string) $r->input('celular')), 'email' => mb_strtolower(trim((string) $r->input('email')))]);
        $client->update($r->validate(['nombres' => 'required|string|max:80', 'apellidos' => 'required|string|max:80', 'celular' => ['required', Rule::unique('clientes_web')->ignore($client->id)], 'email' => ['required', 'email', Rule::unique('clientes_web')->ignore($client->id)]]));

        return back()->with('status', 'Perfil actualizado. El celular es un contacto declarado, no una identidad bancaria verificada.');
    }

    public function pedido(int $id, CobrosService $service)
    {
        $order = PedidoWeb::with('items', 'venta')->where('cliente_web_id', Auth::guard('cliente')->id())->findOrFail($id);
        $payment = IntentoPago::where('pedido_web_id', $order->id)->latest('id')->first();
        $paymentError = null;
        if ($order->metodo_pago === 'yape' && ! $order->venta_id && $order->estado === \App\Enums\EstadoPedidoWeb::Pendiente) {
            try { $payment = $service->prepararPedido($order, Auth::guard('cliente')->user()); $order->refresh()->load('items', 'venta'); }
            catch (ValidationException $e) { $paymentError = $e->validator->errors()->first(); }
        }
        if ($payment && ! $payment->external_id && ! $paymentError) {
            try {
                $payment = $service->iniciar($payment);
            } catch (ValidationException $e) {
                $paymentError = $e->validator->errors()->first();
            }
        }
        if ($payment && ! $payment->applied_at && ! in_array($payment->estado, ['EXPIRED', 'CANCELLED']) && ! $paymentError) {
            $paymentError = $service->indisponibilidad($payment);
        }

        return view('clientes-web.pago', ['order' => $order, 'payment' => $payment, 'paymentError' => $paymentError]);
    }

    public function pagarSaldo(int $id, CobrosService $service)
    {
        $client = Auth::guard('cliente')->user();
        $order = PedidoWeb::where('cliente_web_id', $client->id)->findOrFail($id);
        try {
            $service->pagarConSaldo($client, $order);
        } catch (\App\Services\Payments\StockInsuficiente $e) {
            throw ValidationException::withMessages(['pedido' => $e->getMessage().' No se descontó tu saldo.']);
        }
        return redirect(route('cliente.cuenta', ['pedido' => $id]).'#pedido-'.$id)->with('status', 'Pedido pagado con saldo. Ya puedes abrir WhatsApp para avisar al vendedor.');
    }

    public function recarga(Request $r, CobrosService $service)
    {
        $client = Auth::guard('cliente')->user();
        if ($r->has('celular')) {
            $r->merge(['celular' => TelefonoPeru::normalizar((string) $r->input('celular'))]);
        }
        $data = $r->validate(['importe' => ['required', 'regex:/^\d{1,6}(?:\.\d{1,2})?$/'], 'referencia' => 'required|uuid',
            'celular' => ['sometimes', 'required', Rule::unique('clientes_web')->ignore($client->id)]]);
        if (isset($data['celular']) && $data['celular'] !== $client->celular) {
            $client->update(['celular' => $data['celular']]);
        }
        $payment = $service->recarga($client, Dinero::minor($data['importe']), $data['referencia']);
        if (! $r->expectsJson()) {
            return redirect()->route('cliente.pago', $payment->id);
        }
        $payment = $service->iniciar($payment);
        $error = $payment->applied_at || in_array($payment->estado, ['EXPIRED', 'CANCELLED'], true) ? null : $service->indisponibilidad($payment);
        $active = ! $error && $payment->external_id && $payment->estado === 'PENDING' && ! $payment->applied_at;
        return response()->json([...$this->resultado($payment), 'id' => $payment->id,
            'receptor' => $active ? ['nombre' => $payment->receptor_snapshot['nombre'], 'celular' => $payment->receptor_snapshot['celular'], 'qr' => asset('storage/'.$payment->receptor_snapshot['qr_path'])] : null,
            'disponibilidad' => $error, 'url_estado' => route('cliente.pago.estado', $payment->id), 'url_validar' => route('cliente.pago.validar', $payment->id), 'url_pago' => route('cliente.pago', $payment->id)]);
    }

    public function pago(int $id, CobrosService $service)
    {
        $payment = $this->own($id);
        $paymentError = null;
        if (! $payment->external_id) {
            try {
                $payment = $service->iniciar($payment);
            } catch (ValidationException $e) {
                $paymentError = $e->validator->errors()->first();
            }
        }
        if (! $payment->applied_at && ! in_array($payment->estado, ['EXPIRED', 'CANCELLED']) && ! $paymentError) {
            $paymentError = $service->indisponibilidad($payment);
        }

        return view('clientes-web.pago', ['payment' => $payment, 'order' => $payment->pedido, 'paymentError' => $paymentError]);
    }

    public function consultar(int $id, CobrosService $service)
    {
        $i = $service->consultar($this->own($id));

        return response()->json([...$this->resultado($i), 'disponibilidad' => $i->estado === 'PENDING' ? $service->indisponibilidad($i) : null]);
    }

    public function validar(Request $r, int $id, CobrosService $service)
    {
        $data = $r->validate(['codigo' => ['required', 'regex:/^\d{3}$/']]);
        $i = $service->validar($this->own($id), $data['codigo'], 'cliente:'.Auth::guard('cliente')->id(), (string) $r->ip());

        return response()->json([...$this->resultado($i), 'disponibilidad' => $i->estado === 'PENDING' ? $service->indisponibilidad($i) : null]);
    }

    private function own(int $id): IntentoPago
    {
        return IntentoPago::where('cliente_web_id', Auth::guard('cliente')->id())->findOrFail($id);
    }

    private function resultado(IntentoPago $i): array
    {
        $order = $i->pedido;

        return ['saldo_minor' => Auth::guard('cliente')->user()->saldoMinor(), 'estado' => $i->estado, 'aplicado' => $i->applied_at !== null, 'estado_comercial' => $order?->pago_estado,
            'url_boleta' => $order?->venta_id ? route('pedidos-web.boleta', $order->token) : null,
            'resultado' => $i->ultimo_resultado, 'mensaje' => $i->ultimo_resultado === 'AMBIGUOUS' ? 'Pago ambiguo. Se conserva pendiente de revisión; no se aplicó dinero.' : ($i->ultimo_resultado === 'CODE_NOT_MATCHED' ? 'Código incorrecto. Comprueba los tres dígitos de tu Yape.' : (in_array($i->ultimo_resultado, ['REVIEW_CODE_UNAVAILABLE', 'REVIEW_FORMAT_UNRECOGNIZED'], true) ? 'La notificación no contiene un código verificable. Se requiere revisión.' : ($i->estado === 'PAID' ? ($order?->pago_estado === 'saldo_acreditado' ? 'Pago confirmado sin stock. Se acreditó el importe a tu saldo.' : 'Pago confirmado.') : ($i->estado === 'EXPIRED' ? 'Operación vencida. Si pagaste, solicita revisión a la tienda.' : ($i->estado === 'CANCELLED' ? 'Operación cancelada.' : 'Pago no encontrado o código incorrecto. Si hay ambigüedad se conserva pendiente de revisión.')))))];
    }
}

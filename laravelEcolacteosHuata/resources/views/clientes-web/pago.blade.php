<x-layouts::auth title="Pedido y pago">
    <div class="flex max-h-[75dvh] flex-col gap-5 overflow-y-auto pr-2 *:shrink-0" role="region" aria-label="Estado del pedido y pago" tabindex="0">
        <x-ui.titulo :titulo="$order ? 'Pedido #'.$order->id : 'Recarga #'.$payment->id">Consulta tu operación sin crear otro cobro.</x-ui.titulo>
        <flux:link :href="route('cliente.cuenta')">Mi cuenta · pedido guardado para pagar después</flux:link>
        @if($order)
            @foreach($order->items as $item) <p>{{ $item->cantidad }} × {{ $item->producto }} · S/ {{ $item->subtotal }}</p> @endforeach
        @endif
        @php $snapshot = $payment?->receptor_snapshot ?? $order?->receptor_snapshot; @endphp
        <p class="text-xl font-bold">Total: S/ {{ $order?->total ?? number_format($payment->amount_minor / 100, 2, '.', '') }}</p>
        <p>Contacto de tu cuenta: {{ auth('cliente')->user()->celular }}</p>
        @if($payment && $snapshot)
            <p>Vendedor: {{ $snapshot['vendedor_nombre'] }} · Receptor: {{ $snapshot['nombre'] }}</p>
            <p>Yape receptor: {{ $snapshot['celular'] }}</p>
        @endif
        @if($errors->any()) <p role="alert">{{ $errors->first() }}</p> @endif
        <p id="payment-message" role="status" aria-live="polite">{{ $paymentError ?? ($order?->pago_estado === 'saldo_acreditado' ? 'Pago confirmado sin stock: importe acreditado a tu saldo.' : ($payment?->estado === 'PENDING' ? 'Pendiente de pago. Aún no se ha confirmado ningún ingreso.' : ($payment?->estado ?? $order?->estado->label()))) }}</p>
        @if($payment && !$payment->applied_at && !in_array($payment->estado, ['EXPIRED', 'CANCELLED']))
            @if($paymentError)
                <flux:button as="a" :href="url()->current()">Volver a consultar disponibilidad</flux:button>
            @elseif($payment->external_id)
                <div class="overflow-hidden rounded-2xl text-center text-white" style="background:#742284">
                    <p class="px-4 pt-4 text-sm font-semibold">Escanea con Yape y paga</p>
                    <p class="font-display text-3xl font-extrabold tabular">S/ {{ number_format($payment->amount_minor / 100, 2, '.', '') }}</p>
                    <img src="{{ asset('storage/'.$snapshot['qr_path']) }}" alt="QR del receptor asignado" class="mx-auto mb-4 mt-2 w-full max-w-[13rem] rounded-xl object-contain">
                </div>
                <p class="text-sm">Paga el importe exacto. Escribe el código de tres dígitos solo si Yape lo muestra; sin código se requiere revisión, no se confirma por importe y hora. No se reserva stock: si falta cuando se confirme el ingreso, recibirás saldo a favor.</p>
                <form id="payment-code-form" class="flex flex-col gap-3">
                    <flux:input name="codigo" label="Código de seguridad Yape" inputmode="numeric" pattern="[0-9]{3}" minlength="3" maxlength="3" autocomplete="off" required />
                    <flux:button type="submit" variant="primary">Verificar pago</flux:button>
                </form>
            @else <p>No realices el pago todavía. Estamos preparando la intención con el receptor.</p> @endif
            @unless($paymentError) <flux:button id="payment-refresh">Consultar estado</flux:button> @endunless
        @endif
        @if($order?->venta_id)
            <flux:button as="a" :href="$order->urlBoleta()" target="_blank" variant="primary">Ver mi boleta</flux:button>
        @endif
        @if($order && ($whatsapp = $order->whatsappPagado()))
            <flux:button as="a" :href="$whatsapp" target="_blank" rel="noopener">ABRIR WHATSAPP</flux:button>
        @endif
        @if($payment && !$paymentError && !$payment->applied_at && !in_array($payment->estado, ['EXPIRED', 'CANCELLED']))
            <script>
                (() => {
                    const message = document.getElementById('payment-message');
                    let busy = false;
                    const consult = async (code) => {
                        if (busy) return; busy = true;
                        message.textContent = code ? 'Verificando…' : 'Esperando pago: consultando estado…';
                        try {
                            const response = await fetch(code ? @json(route('cliente.pago.validar', $payment->id)) : @json(route('cliente.pago.estado', $payment->id)), {
                                method: code ? 'POST' : 'GET', headers: {'Accept': 'application/json', 'Content-Type': 'application/json', 'X-CSRF-TOKEN': @json(csrf_token())}, ...(code ? {body: JSON.stringify({codigo: code})} : {})
                            });
                            const data = await response.json();
                            if (!response.ok) throw new Error(data.errors ? Object.values(data.errors).flat()[0] : 'Verificación temporalmente no disponible.');
                            message.textContent = data.mensaje;
                            if (data.aplicado || ['EXPIRED','CANCELLED'].includes(data.estado) || @json(!$payment->external_id)) location.reload();
                        } catch (error) { message.textContent = error.message || 'Verificación temporalmente no disponible.'; }
                        finally { busy = false; }
                    };
                    document.getElementById('payment-code-form')?.addEventListener('submit', e => { e.preventDefault(); const input = e.target.elements.codigo; const code = input.value; input.value = ''; consult(code); });
                    document.getElementById('payment-refresh')?.addEventListener('click', () => consult());
                    const timer = setInterval(() => { if (!document.hidden) consult(); }, 5000);
                    window.addEventListener('pagehide', () => clearInterval(timer), {once: true});
                })();
            </script>
        @endif
    </div>
</x-layouts::auth>

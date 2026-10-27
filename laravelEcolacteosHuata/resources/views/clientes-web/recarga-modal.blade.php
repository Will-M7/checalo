<flux:modal name="recargar-saldo" class="md:w-[32rem]">
    <div class="flex flex-col gap-4">
        <x-ui.titulo titulo="Recargar saldo">Saldo actual: <span id="recarga-saldo">S/ {{ \App\Services\Payments\Dinero::decimal($client->saldoMinor()) }}</span></x-ui.titulo>
        <form id="recarga-form" class="flex flex-col gap-3">
            <flux:input name="importe" label="Importe (S/)" type="number" min="1" max="999999.99" step="0.01" required />
            <flux:input name="celular" label="Tu celular de contacto" type="tel" :value="$client->celular" required maxlength="20" />
            <flux:select name="metodo" label="Método habilitado"><flux:select.option value="yape">Yape · sujeto a disponibilidad</flux:select.option></flux:select>
            <flux:text>El celular es un contacto declarado. Plin y efectivo no tienen un flujo de recarga habilitado aquí.</flux:text>
            <flux:button type="submit" variant="primary" id="recarga-iniciar">Preparar recarga</flux:button>
        </form>
        <p id="recarga-estado" role="status" aria-live="polite">Primero se comprobará el receptor asignado. No pagues antes de ver su QR autorizado.</p>
        <section id="recarga-receptor" hidden class="flex flex-col gap-3">
            <p id="recarga-cuenta" class="font-semibold"></p>
            <img id="recarga-qr" alt="QR autorizado del receptor asignado" class="mx-auto w-full max-w-52 rounded-xl" />
            <flux:text>Paga el importe exacto. Introduce los tres dígitos solo si Yape los muestra. Sin código verificable o con una notificación ambigua, la recarga queda pendiente de revisión.</flux:text>
            <form id="recarga-codigo-form" class="flex flex-col gap-3">
                <flux:input name="codigo" label="Código Yape, si existe" inputmode="numeric" pattern="[0-9]{3}" minlength="3" maxlength="3" autocomplete="off" required />
                <flux:button type="submit">Verificar pago</flux:button>
            </form>
        </section>
        <flux:button id="recarga-consultar" hidden>Consultar estado</flux:button>
        <flux:button as="a" :href="route('cliente.cuenta')" id="recarga-actualizar" hidden>Actualizar Mi cuenta</flux:button>
    </div>
</flux:modal>
<script>
(() => {
    const form = document.getElementById('recarga-form'), message = document.getElementById('recarga-estado');
    const panel = document.getElementById('recarga-receptor'), refresh = document.getElementById('recarga-consultar');
    let current = null, busy = false;
    const newReference = () => {
        const bytes = crypto.getRandomValues(new Uint8Array(16));
        bytes[6] = (bytes[6] & 15) | 64; bytes[8] = (bytes[8] & 63) | 128;
        const hex = [...bytes].map(b => b.toString(16).padStart(2, '0')).join('');
        return hex.slice(0,8)+'-'+hex.slice(8,12)+'-'+hex.slice(12,16)+'-'+hex.slice(16,20)+'-'+hex.slice(20);
    };
    const send = async (url, body) => {
        const response = await fetch(url, {method: body ? 'POST' : 'GET', headers: {'Accept':'application/json', 'Content-Type':'application/json', 'X-CSRF-TOKEN':@json(csrf_token())}, ...(body ? {body:JSON.stringify(body)} : {})});
        const data = await response.json();
        if (!response.ok) throw new Error(data.errors ? Object.values(data.errors).flat()[0] : 'No se pudo consultar la operación. Conservamos su referencia.');
        return data;
    };
    const show = data => {
        message.textContent = data.disponibilidad || data.mensaje;
        document.getElementById('recarga-saldo').textContent = 'S/ '+(data.saldo_minor / 100).toFixed(2);
        panel.hidden = !(data.estado === 'PENDING' && !data.disponibilidad && current?.receptor);
        if (data.aplicado) window.setTimeout(() => { location.href = @json(route('cliente.cuenta')); }, 1200);
        if (data.aplicado || ['EXPIRED','CANCELLED'].includes(data.estado)) {
            refresh.hidden = true;
            document.getElementById('recarga-actualizar').hidden = false;
        }
    };
    form.addEventListener('submit', async e => {
        e.preventDefault(); if (busy) return; busy = true;
        document.getElementById('recarga-iniciar').disabled = true;
        try {
            const amount = Number(form.elements.importe.value).toFixed(2);
            const key = 'huata-recarga-'+@json($client->id)+'-'+amount;
            const id = sessionStorage.getItem(key) || newReference();
            sessionStorage.setItem(key, id);
            const data = await send(@json(route('cliente.recarga')), {importe:amount, celular:form.elements.celular.value, referencia:id});
            current = data; show(data);
            form.elements.importe.readOnly = true;
            form.elements.celular.readOnly = true;
            if (data.receptor) {
                document.getElementById('recarga-cuenta').textContent = data.receptor.nombre+' · '+data.receptor.celular+' · S/ '+amount;
                document.getElementById('recarga-qr').src = data.receptor.qr;
                panel.hidden = false;
                refresh.hidden = false;
            }
        } catch (e) { panel.hidden = true; message.textContent = e.message; document.getElementById('recarga-iniciar').disabled = false; }
        finally { busy = false; }
    });
    const consult = async code => {
        if (!current || busy) return; busy = true;
        try { show(await send(code ? current.url_validar : current.url_estado, code ? {codigo:code} : null)); }
        catch (e) { panel.hidden = true; message.textContent = e.message; }
        finally { busy = false; }
    };
    document.getElementById('recarga-codigo-form').addEventListener('submit', e => { e.preventDefault(); const input = e.target.elements.codigo; const code = input.value; input.value = ''; consult(code); });
    refresh.addEventListener('click', () => consult());
})();
</script>

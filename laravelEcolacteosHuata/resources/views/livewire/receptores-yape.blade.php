<div class="flex flex-col gap-6" x-data x-on:toast-show.stop="$el.querySelector('ui-toast').showToast($event.detail)">
    <flux:toast />
    <x-ui.titulo titulo="Receptores Yape">Responsables de recepción, dispositivos y asignación comercial.</x-ui.titulo>
    <form wire:submit="guardar" class="huata-cuadro flex flex-col gap-4 rounded-xl border p-5">
        <div class="flex items-center justify-between gap-3"><flux:heading>{{ $editando ? 'Editar receptor y responsable' : 'Crear receptor' }}</flux:heading><flux:button wire:click="nuevo" type="button">Nuevo receptor</flux:button></div>
        <flux:input label="Nombre del titular/receptor" wire:model="nombre" required />
        <flux:input label="Número Yape" wire:model="numero" required />
        <flux:input label="Buscar usuario Huata responsable" wire:model.live.debounce.300ms="buscarUsuario" placeholder="Nombre o usuario de cualquier rol activo" />
        <flux:select label="Responsable de escuchar notificaciones" wire:model="responsable" required><flux:select.option value="">Seleccionar usuario activo</flux:select.option>@foreach($usuarios as $u)<flux:select.option :value="$u->id">{{ $u->nombreCompleto() }} · {{ $u->usuario }}</flux:select.option>@endforeach</flux:select>
        <flux:input type="file" label="Imagen QR (máximo 2 MB)" wire:model="qr" accept="image/png,image/jpeg,image/webp" />
        @if($qr)<img src="{{ $qr->temporaryUrl() }}" alt="QR propuesto" class="w-40 rounded-xl">@elseif($editado?->qr_path)<img src="{{ asset('storage/'.$editado->qr_path) }}" alt="QR guardado" class="w-40 rounded-xl">@endif
        <flux:text>La aceptación autoriza el uso del teléfono. Administración debe comprobar físicamente titular, número, cuenta y QR antes de habilitar cobros. Cambiar la identidad requiere nueva verificación y aceptación.</flux:text>
        @error('recepcion')<flux:text role="alert">{{ $message }}</flux:text>@enderror
        <flux:button type="submit" variant="primary" wire:loading.attr="disabled" wire:target="guardar,qr">Guardar y solicitar aceptación</flux:button>
    </form>
    <div class="huata-cuadro flex flex-col gap-4 rounded-xl border p-5">
        <flux:heading>Asignar receptor a vendedores</flux:heading>
        <flux:select label="Vendedor autorizado" wire:model="vendedor"><flux:select.option value="">Seleccionar vendedor</flux:select.option>@foreach($vendedores as $v)<flux:select.option :value="$v->id">{{ $v->nombreCompleto() }}</flux:select.option>@endforeach</flux:select>
        <flux:select label="Receptor para cobros nuevos" wire:model="receptorAsignado"><flux:select.option value="">Seleccionar receptor</flux:select.option>@foreach($opcionesReceptor as $r)<flux:select.option :value="$r->id">{{ $r->nombre_receptor }} · {{ $r->celular }} · {{ $r->enabled ? 'Habilitado' : 'Pendiente / bloqueado' }}</flux:select.option>@endforeach</flux:select>
        <flux:button wire:click="asignar" wire:loading.attr="disabled">Guardar asignación</flux:button>
        <flux:text>Varios vendedores pueden usar el mismo receptor. Una asignación pendiente no ofrece su QR para cobros. Los pedidos e intenciones anteriores conservan su receptor.</flux:text>
    </div>
    <flux:input label="Buscar receptor" wire:model.live.debounce.300ms="buscar" />
    @foreach($receptores as $r)
        @php($s = $estados[$r->id])
        <div wire:key="receptor-{{ $r->id }}" class="huata-cuadro flex flex-col gap-3 rounded-xl border p-5">
            <flux:heading>{{ $r->nombre_receptor }} · {{ $r->celular }}</flux:heading>
            @if($r->qr_path)<img src="{{ asset('storage/'.$r->qr_path) }}" alt="QR de {{ $r->nombre_receptor }}" class="w-32 rounded-xl">@endif
            <flux:text>Responsable: {{ $s['responsible']?->nombreCompleto() ?? 'Sin asignar' }}</flux:text>
            <flux:badge>{{ $s['label'] }}</flux:badge>
            <flux:text>Android: {{ $s['device']?->label ?? 'Pendiente de aceptación en el teléfono' }} · Permiso: {{ $s['device']?->notification_permission ? 'Concedido' : 'Pendiente' }} · Listener: {{ $s['device']?->listener_ready ? 'Conectado' : 'Sin confirmar' }}</flux:text>
            <flux:text>Última conexión: {{ $s['device']?->last_seen_at ?? 'Sin conexión registrada' }} · Última transmisión: {{ $s['device']?->last_communication_at ?? 'Sin comunicación' }}</flux:text>
            <flux:text>Habilitación comercial: {{ $r->enabled && $r->verified_at ? 'Habilitada; cada cobro comprobará disponibilidad en Core' : 'Bloqueada' }} · Verificación: {{ $r->verified_at ? 'Registrada por administración' : 'Pendiente' }}</flux:text>
            <flux:text>Vendedores: {{ $s['sellers']->map(fn ($u) => $u->nombreCompleto())->implode(', ') ?: 'Sin asignaciones explícitas' }}</flux:text>
            @if(isset($diagnosticos[$r->id]))<flux:text>{{ $diagnosticos[$r->id] }}</flux:text>@endif
            <div class="flex flex-wrap gap-2">
                <flux:button wire:click="editar({{ $r->id }})">Editar / cambiar responsable</flux:button>
                <flux:button wire:click="consultar({{ $r->id }})" wire:loading.attr="disabled">Consultar estado</flux:button>
                @if(!$s['request'] || in_array($s['request']->status, ['rejected', 'revoked']))<flux:button wire:click="reenviar({{ $r->id }})">Solicitar aceptación</flux:button>@endif
                <flux:button wire:click="revocar({{ $r->id }})" wire:confirm="¿Revocar la recepción y bloquear cobros nuevos? Se conserva el historial." wire:loading.attr="disabled">Revocar receptor / dispositivo</flux:button>
            </div>
            <details><summary>Verificación y habilitación comercial</summary><div class="flex flex-col gap-3 pt-3">
                <flux:checkbox wire:model="confirmacionFisica.{{ $r->id }}" label="Comprobé físicamente titular, número, cuenta Yape y QR de este receptor" />
                <div class="flex flex-wrap gap-2"><flux:button wire:click="verificar({{ $r->id }})" wire:loading.attr="disabled">Verificar cuenta y QR</flux:button><flux:button wire:click="central({{ $r->id }})" wire:loading.attr="disabled">Usar como receptor central</flux:button><flux:button wire:click="habilitar({{ $r->id }})" wire:loading.attr="disabled">Habilitar cobros</flux:button><flux:button wire:click="deshabilitar({{ $r->id }})" wire:loading.attr="disabled">Deshabilitar cobros</flux:button></div>
            </div></details>
            @if($s['request']?->status === 'accepted')<details><summary>Transferencia de Bridge anterior</summary><div class="flex flex-col gap-3 pt-3">
                <flux:text>Comprueba físicamente el listener de Huata, revisa la cola anterior y detén el listener anterior. La transferencia conserva las evidencias y sustituye su credencial al completar la vinculación.</flux:text>
                <flux:checkbox wire:model="confirmacionTransferencia.{{ $r->id }}" label="Verifiqué Huata, revisé la cola y detuve el listener anterior de este receptor" />
                <flux:button wire:click="transferir({{ $r->id }})" wire:loading.attr="disabled">Autorizar transferencia controlada</flux:button>
            </div></details>@endif
        </div>
    @endforeach
    {{ $receptores->links() }}
</div>

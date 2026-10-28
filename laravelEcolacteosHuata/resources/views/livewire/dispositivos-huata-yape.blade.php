<section class="huata-cuadro flex flex-col gap-3 rounded-xl border p-5">
    <flux:heading>Dispositivos Huata autorizados</flux:heading>
    @if(!$listo)<flux:text>Falta aplicar la migración incremental de dispositivos Huata.</flux:text>
    @else
        <flux:text>El usuario inicia sesión en Huata. Autoriza aquí su teléfono para el receptor {{ $receptor?->nombre_receptor ?? 'que debes guardar primero' }}. No se necesita una segunda APK ni escribir claves.</flux:text>
        <flux:select label="Modalidad del dispositivo" wire:model="modo"><flux:select.option value="personal">Personal: sesión del titular y receptor propio</flux:select.option><flux:select.option value="shared">Central o compartido: responsable autorizado</flux:select.option></flux:select>
        <details><summary>Transferencia desde un Bridge anterior</summary>
            <flux:text>Antes de transferir, verifica físicamente que el listener de Huata recibe eventos en ese teléfono, detén el Bridge anterior y revisa su cola pendiente. La transferencia conserva su identidad técnica y rota su credencial; las APK anteriores dejarán de transmitir.</flux:text>
            <flux:checkbox wire:model="transferencia" label="Confirmo la prueba física de Huata, la retirada coordinada del colector anterior y la revisión de su cola" />
        </details>
        @forelse($dispositivos as $d)
            <div wire:key="huata-yape-{{ $d->id }}"><flux:text>{{ $d->nombre_usuario }} · {{ $d->label }} · {{ $d->status }} · {{ $d->listener_ready ? 'Permiso y listener comunicados' : 'Esperando permiso/listener' }}</flux:text>
                @unless($d->status === 'revoked')<flux:button size="sm" wire:click="aprobar('{{ $d->id }}')">Autorizar este teléfono</flux:button><flux:button size="sm" wire:click="revocar('{{ $d->id }}')">Revocar recepción</flux:button>@endunless
            </div>
        @empty<flux:text>Sin solicitudes. El usuario debe iniciar sesión en su Huata actualizada y consultar su configuración con conexión.</flux:text>@endforelse
    @endif
</section>

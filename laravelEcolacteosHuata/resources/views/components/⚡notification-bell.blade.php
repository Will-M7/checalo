<?php

use Livewire\Attributes\On;
use Livewire\Component;

new class extends Component
{
    public function marcarTodasLeidas(): void
    {
        auth()->user()->unreadNotifications->markAsRead();
    }

    #[On('notificaciones-actualizadas')]
    public function refrescar(): void {}
};
?>

<div>
    @php
        $notificaciones = auth()->user()->notificacionesNoLeidas();
        $total = auth()->user()->unreadNotifications()->count();
    @endphp

    <flux:dropdown position="bottom" align="end">
        <flux:button variant="ghost" square icon="bell" data-test="notification-bell" class="relative" aria-label="Notificaciones{{ $total > 0 ? ', '.$total.' sin leer' : '' }}">
            @if ($total > 0)
                <span class="absolute -right-0.5 -top-0.5 flex min-w-4 items-center justify-center rounded-full bg-[var(--hh-peligro)] px-1 text-[10px] font-bold leading-4 text-white ring-2 ring-[var(--hh-superficie)]">
                    {{ $total > 9 ? '9+' : $total }}
                </span>
            @endif
        </flux:button>

        <flux:menu class="w-[22rem] max-w-[92vw]">
            <div class="flex items-center justify-between px-2 py-1.5">
                <flux:heading size="sm">Notificaciones</flux:heading>
                @if ($total > 0)
                    <flux:link wire:click="marcarTodasLeidas" class="text-xs cursor-pointer">
                        Marcar todas como leídas
                    </flux:link>
                @endif
            </div>

            <flux:menu.separator />

            @forelse ($notificaciones as $notificacion)
                <div class="hh-entra flex gap-3 px-2 py-2.5 text-sm {{ $notificacion->read_at ? 'opacity-60' : '' }}" style="--i: {{ $loop->index }}">
                    <span class="mt-1.5 size-2 shrink-0 rounded-full {{ $notificacion->read_at ? 'bg-zinc-300 dark:bg-zinc-600' : 'bg-[var(--hh-marca)]' }}" aria-hidden="true"></span>
                    <div class="flex min-w-0 flex-col gap-0.5">
                        <span class="font-semibold text-[var(--hh-texto)]">{{ $notificacion->data['titulo'] ?? 'Notificación' }}</span>
                        @if (! empty($notificacion->data['mensaje']))
                            <span class="text-xs text-[var(--hh-texto-2)]">{{ $notificacion->data['mensaje'] }}</span>
                        @endif
                        <span class="text-xs text-[var(--hh-texto-2)]/80">{{ $notificacion->created_at->diffForHumans() }}</span>
                    </div>
                </div>
            @empty
                <div class="px-2 py-4 text-center text-sm text-zinc-500">
                    No tienes notificaciones.
                </div>
            @endforelse
        </flux:menu>
    </flux:dropdown>
</div>

<div class="flex flex-wrap items-center gap-3">
    <flux:text>Recepción de pedidos web: {{ ucfirst($disponibilidad['estado']) }}</flux:text>
    @if($disponibilidad['capacidad'])<flux:button size="sm" wire:click="pausar({{ $disponibilidad['pausado'] ? 'false' : 'true' }})" :disabled="! $disponibilidad['autorizado']">{{ $disponibilidad['pausado'] ? 'Activar recepción web' : 'Pausar recepción web' }}</flux:button>@endif
    @error('disponibilidad')<flux:text role="alert">{{ $message }}</flux:text>@enderror
</div>

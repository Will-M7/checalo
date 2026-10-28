<div class="flex flex-wrap items-center gap-3">
    <flux:text>Recepción de pedidos web: {{ ucfirst($disponibilidad['estado']) }}</flux:text>
    @error('disponibilidad')<flux:text role="alert">{{ $message }}</flux:text>@enderror
</div>

<?php

use App\Models\PrecioLeche;
use Flux\Flux;
use Livewire\Component;

new class extends Component
{
    public string $precio_litro = '';

    public string $vigente_desde = '';

    public function mount(): void
    {
        $this->vigente_desde = now()->toDateString();
    }

    public function with(): array
    {
        return [
            'precioVigente' => PrecioLeche::vigente(),
            'historial' => PrecioLeche::query()->orderByDesc('vigente_desde')->limit(10)->get(),
        ];
    }

    public function registrarPrecio(): void
    {
        $data = $this->validate([
            'precio_litro' => 'required|numeric|min:0',
            'vigente_desde' => 'required|date',
        ]);

        PrecioLeche::create($data);

        $this->reset(['precio_litro']);
        $this->vigente_desde = now()->toDateString();

        Flux::toast('Precio de leche actualizado.', variant: 'success');
    }
};
?>

<div class="flex flex-col gap-8">
    <x-ui.titulo titulo="Precios">Precio de la leche.</x-ui.titulo>

    <div class="max-w-md rounded-xl border border-huata-verde-100 p-5 dark:border-zinc-700 huata-cuadro">
        <flux:heading size="lg">Precio de la leche</flux:heading>

        <div class="mt-3 rounded-lg bg-huata-verde-50 p-4 dark:bg-zinc-900">
            <flux:text>Precio vigente</flux:text>
            <p class="text-2xl font-bold text-huata-verde-700">
                S/ {{ number_format((float) ($precioVigente->precio_litro ?? 0), 2) }} / litro
            </p>
            <flux:text class="text-xs">Desde {{ $precioVigente?->vigente_desde?->format('d/m/Y') ?? '—' }}</flux:text>
        </div>

        <form wire:submit="registrarPrecio" class="mt-4 flex flex-col gap-4">
            <flux:input label="Nuevo precio por litro (S/)" type="number" step="0.01" min="0" wire:model="precio_litro" />
            <flux:input label="Vigente desde" type="date" wire:model="vigente_desde" />
            <flux:button type="submit" variant="primary">Registrar nuevo precio</flux:button>
        </form>

        @if ($historial->isNotEmpty())
            <div class="mt-4">
                <flux:heading size="sm" class="mb-2">Historial</flux:heading>
                <ul class="flex flex-col gap-1 text-sm text-zinc-600 dark:text-zinc-300">
                    @foreach ($historial as $item)
                        <li>S/ {{ number_format((float) $item->precio_litro, 2) }} — desde {{ $item->vigente_desde->format('d/m/Y') }}</li>
                    @endforeach
                </ul>
            </div>
        @endif
    </div>
</div>

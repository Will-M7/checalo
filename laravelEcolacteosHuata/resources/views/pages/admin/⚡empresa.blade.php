<?php

use App\Models\Empresa;
use Flux\Flux;
use Livewire\Component;

new class extends Component
{
    public string $razon_social = '';

    public string $ruc = '';

    public string $telefono = '';

    public string $direccion = '';

    public function mount(): void
    {
        $empresa = Empresa::actual();

        $this->razon_social = $empresa->razon_social;
        $this->ruc = $empresa->ruc;
        $this->telefono = $empresa->telefono;
        $this->direccion = $empresa->direccion;
    }

    public function guardar(): void
    {
        $data = $this->validate([
            'razon_social' => 'required|string|max:255',
            'ruc' => 'required|string|max:20',
            'telefono' => 'nullable|string|max:20',
            'direccion' => 'nullable|string|max:255',
        ]);

        Empresa::actual()->update($data);

        Flux::toast('Datos de la empresa actualizados.', variant: 'success');
    }
};
?>

<div class="flex flex-col gap-6">
    <x-ui.titulo titulo="Empresa">Datos que aparecen en boletas y reportes.</x-ui.titulo>

    <form wire:submit="guardar" class="flex max-w-lg flex-col gap-4 rounded-xl border border-huata-verde-100 p-5 dark:border-zinc-700 huata-cuadro">
        <flux:input label="Razón social" wire:model="razon_social" />
        <flux:input label="RUC" wire:model="ruc" />
        <flux:input label="Teléfono" wire:model="telefono" />
        <flux:input label="Dirección" wire:model="direccion" />

        <div class="flex justify-end">
            <flux:button type="submit" variant="primary">Guardar</flux:button>
        </div>
    </form>
</div>

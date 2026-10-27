<?php

use App\Models\UnidadMedida;
use Flux\Flux;
use Livewire\Component;

new class extends Component
{
    public ?int $unidadId = null;

    public string $unidadNombre = '';

    public string $unidadSimbolo = '';

    public ?int $eliminarUnidadId = null;

    public function with(): array
    {
        return [
            'unidades' => UnidadMedida::orderBy('nombre')->get(),
        ];
    }

    public function nuevaUnidad(): void
    {
        $this->reset(['unidadId', 'unidadNombre', 'unidadSimbolo']);
        Flux::modal('unidad-form')->show();
    }

    public function editarUnidad(int $id): void
    {
        $u = UnidadMedida::findOrFail($id);
        $this->unidadId = $u->id;
        $this->unidadNombre = $u->nombre;
        $this->unidadSimbolo = $u->simbolo;
        Flux::modal('unidad-form')->show();
    }

    public function guardarUnidad(): void
    {
        $data = $this->validate([
            'unidadNombre' => 'required|string|max:255',
            'unidadSimbolo' => 'required|string|max:4',
        ]);

        UnidadMedida::updateOrCreate(['id' => $this->unidadId], [
            'nombre' => $data['unidadNombre'],
            'simbolo' => $data['unidadSimbolo'],
        ]);

        Flux::modal('unidad-form')->close();
        Flux::toast('Unidad de medida guardada.', variant: 'success');
    }

    public function pedirEliminarUnidad(int $id): void
    {
        $this->eliminarUnidadId = $id;
        Flux::modal('confirmar-eliminar-unidad')->show();
    }

    public function eliminarUnidad(): void
    {
        UnidadMedida::whereKey($this->eliminarUnidadId)->delete();
        Flux::modal('confirmar-eliminar-unidad')->close();
        Flux::toast('Unidad eliminada.', variant: 'success');
    }
};
?>

<div class="flex flex-col gap-6">
    <div class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <x-ui.titulo titulo="Unidades de medida">Unidades disponibles para tus insumos y recetas.</x-ui.titulo>
        <flux:modal.trigger name="unidad-form">
            <flux:button size="sm" variant="primary" icon="plus" wire:click="nuevaUnidad">Nueva unidad</flux:button>
        </flux:modal.trigger>
    </div>

    <div class="overflow-x-auto rounded-xl border border-huata-verde-100 px-3 dark:border-zinc-700 huata-cuadro">
        <flux:table>
            <flux:table.columns class="bg-white/80 dark:bg-zinc-800/80">
                <flux:table.column>Nombre</flux:table.column>
                <flux:table.column>Símbolo</flux:table.column>
                <flux:table.column class="w-px whitespace-nowrap">Acciones</flux:table.column>
            </flux:table.columns>
            <flux:table.rows>
                @forelse ($unidades as $unidad)
                    <flux:table.row wire:key="unidad-{{ $unidad->id }}">
                        <flux:table.cell class="font-medium">{{ $unidad->nombre }}</flux:table.cell>
                        <flux:table.cell>{{ $unidad->simbolo }}</flux:table.cell>
                        <flux:table.cell>
                            <div class="flex gap-2">
                                <flux:modal.trigger name="unidad-form">
                                    <flux:button size="sm" variant="ghost" icon="pencil" wire:click="editarUnidad({{ $unidad->id }})" />
                                </flux:modal.trigger>
                                <flux:modal.trigger name="confirmar-eliminar-unidad">
                                    <flux:button size="sm" variant="ghost" icon="trash" wire:click="pedirEliminarUnidad({{ $unidad->id }})" />
                                </flux:modal.trigger>
                            </div>
                        </flux:table.cell>
                    </flux:table.row>
                @empty
                    <flux:table.row><flux:table.cell colspan="3" class="text-center text-zinc-500">Sin unidades registradas.</flux:table.cell></flux:table.row>
                @endforelse
            </flux:table.rows>
        </flux:table>
    </div>

    <flux:modal name="unidad-form" class="md:w-80">
        <form wire:submit="guardarUnidad" class="flex flex-col gap-6">
            <flux:heading size="lg">{{ $unidadId ? 'Editar unidad de medida' : 'Nueva unidad de medida' }}</flux:heading>
            <flux:input label="Nombre" wire:model="unidadNombre" placeholder="Kilogramo" />
            <flux:input label="Símbolo" wire:model="unidadSimbolo" placeholder="kg" />
            <div class="flex justify-end gap-2">
                <flux:modal.close><flux:button variant="ghost">Cancelar</flux:button></flux:modal.close>
                <flux:button type="submit" variant="primary">Guardar</flux:button>
            </div>
        </form>
    </flux:modal>

    <x-confirm-modal
        name="confirmar-eliminar-unidad"
        accion="eliminarUnidad"
        titulo="¿Eliminar esta unidad?"
        mensaje="Esta acción no se puede deshacer."
    />
</div>

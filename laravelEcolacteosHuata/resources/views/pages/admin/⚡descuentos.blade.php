<?php

use App\Models\Descuento;
use Flux\Flux;
use Livewire\Attributes\Validate;
use Livewire\Component;
use Livewire\WithPagination;

new class extends Component
{
    use WithPagination;

    public string $busqueda = '';

    public ?int $descuentoId = null;

    #[Validate('required|string|max:255')]
    public string $nombre = '';

    #[Validate('required|numeric|min:0.01|max:100')]
    public string $porcentaje = '';

    public bool $activo = true;

    public ?int $eliminarId = null;

    public function with(): array
    {
        return [
            'descuentos' => Descuento::query()
                ->when($this->busqueda, fn ($q) => $q->where('nombre', 'like', "%{$this->busqueda}%"))
                ->orderBy('nombre')
                ->paginate(10),
        ];
    }

    public function nuevo(): void
    {
        $this->reset(['descuentoId', 'nombre', 'porcentaje']);
        $this->activo = true;
        $this->resetValidation();
        Flux::modal('descuento-form')->show();
    }

    public function editar(int $id): void
    {
        $descuento = Descuento::findOrFail($id);
        $this->descuentoId = $descuento->id;
        $this->nombre = $descuento->nombre;
        $this->porcentaje = (string) $descuento->porcentaje;
        $this->activo = $descuento->activo;
        $this->resetValidation();
        Flux::modal('descuento-form')->show();
    }

    public function guardar(): void
    {
        $data = $this->validate();

        Descuento::updateOrCreate(['id' => $this->descuentoId], [
            'nombre' => $data['nombre'],
            'porcentaje' => $data['porcentaje'],
            'activo' => $this->activo,
        ]);

        Flux::modal('descuento-form')->close();
        Flux::toast('Descuento guardado correctamente.', variant: 'success');
    }

    public function alternarActivo(int $id): void
    {
        $descuento = Descuento::findOrFail($id);
        $descuento->update(['activo' => ! $descuento->activo]);
    }

    public function pedirEliminar(int $id): void
    {
        $this->eliminarId = $id;
        Flux::modal('confirmar-eliminar-descuento')->show();
    }

    public function eliminar(): void
    {
        Descuento::whereKey($this->eliminarId)->delete();

        Flux::modal('confirmar-eliminar-descuento')->close();
        Flux::toast('Descuento eliminado.', variant: 'success');
    }
};
?>

<div class="flex flex-col gap-6">
    <div class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <x-ui.titulo titulo="Descuentos">Descuentos que el vendedor puede activar en el punto de venta.</x-ui.titulo>

        <flux:modal.trigger name="descuento-form">
            <flux:button variant="primary" icon="plus" wire:click="nuevo">Nuevo descuento</flux:button>
        </flux:modal.trigger>
    </div>

    <flux:input
        wire:model.live.debounce.300ms="busqueda"
        icon="magnifying-glass"
        placeholder="Buscar descuento..."
        class="max-w-sm"
    />

    <div class="overflow-x-auto rounded-xl border border-huata-verde-100 px-3 dark:border-zinc-700 huata-cuadro">
    <flux:table :paginate="$descuentos">
        <flux:table.columns class="bg-white/80 dark:bg-zinc-800/80">
            <flux:table.column>Nombre</flux:table.column>
            <flux:table.column>Porcentaje</flux:table.column>
            <flux:table.column>Disponible</flux:table.column>
            <flux:table.column class="w-px whitespace-nowrap">Acciones</flux:table.column>
        </flux:table.columns>

        <flux:table.rows>
            @forelse ($descuentos as $descuento)
                <flux:table.row wire:key="descuento-{{ $descuento->id }}">
                    <flux:table.cell class="font-medium">{{ $descuento->nombre }}</flux:table.cell>
                    <flux:table.cell>{{ number_format((float) $descuento->porcentaje, 2) }}%</flux:table.cell>
                    <flux:table.cell>
                        <flux:badge
                            :color="$descuento->activo ? 'green' : 'zinc'"
                            size="sm"
                            class="cursor-pointer"
                            wire:click="alternarActivo({{ $descuento->id }})"
                        >
                            {{ $descuento->activo ? 'Disponible' : 'Oculto' }}
                        </flux:badge>
                    </flux:table.cell>
                    <flux:table.cell>
                        <div class="flex gap-2">
                            <flux:modal.trigger name="descuento-form">
                                <flux:button size="sm" variant="ghost" icon="pencil" wire:click="editar({{ $descuento->id }})" />
                            </flux:modal.trigger>
                            <flux:modal.trigger name="confirmar-eliminar-descuento">
                                <flux:button size="sm" variant="ghost" icon="trash" wire:click="pedirEliminar({{ $descuento->id }})" />
                            </flux:modal.trigger>
                        </div>
                    </flux:table.cell>
                </flux:table.row>
            @empty
                <flux:table.row>
                    <flux:table.cell colspan="4" class="text-center text-zinc-500">
                        No hay descuentos registrados todavía.
                    </flux:table.cell>
                </flux:table.row>
            @endforelse
        </flux:table.rows>
    </flux:table>
    </div>

    <flux:modal name="descuento-form" class="md:w-96">
        <form wire:submit="guardar" class="flex flex-col gap-6">
            <flux:heading size="lg">{{ $descuentoId ? 'Editar descuento' : 'Nuevo descuento' }}</flux:heading>

            <flux:input label="Nombre" wire:model="nombre" placeholder="Descuento por temporada" />
            <flux:input label="Porcentaje (%)" type="number" step="0.01" min="0" max="100" wire:model="porcentaje" />
            <flux:switch label="Disponible para el vendedor" wire:model="activo" />

            <div class="flex justify-end gap-2">
                <flux:modal.close>
                    <flux:button variant="ghost">Cancelar</flux:button>
                </flux:modal.close>
                <flux:button type="submit" variant="primary">Guardar</flux:button>
            </div>
        </form>
    </flux:modal>

    <x-confirm-modal
        name="confirmar-eliminar-descuento"
        accion="eliminar"
        titulo="¿Eliminar este descuento?"
        mensaje="Esta acción no se puede deshacer."
    />
</div>

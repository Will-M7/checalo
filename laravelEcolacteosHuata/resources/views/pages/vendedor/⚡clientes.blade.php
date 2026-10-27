<?php

use App\Models\Cliente;
use Flux\Flux;
use Livewire\Attributes\Validate;
use Livewire\Component;
use Livewire\WithPagination;

new class extends Component
{
    use WithPagination;

    public string $busqueda = '';

    public ?int $clienteId = null;

    public ?int $eliminarId = null;

    #[Validate('required|string|max:255')]
    public string $nombres = '';

    #[Validate('nullable|string|max:255')]
    public string $apellidos = '';

    #[Validate('required|string|max:15')]
    public string $documento = '';

    public function with(): array
    {
        $clientes = Cliente::query()
            ->when($this->busqueda, fn ($q) => $q->where('nombres', 'like', "%{$this->busqueda}%")
                ->orWhere('apellidos', 'like', "%{$this->busqueda}%")
                ->orWhere('documento', 'like', "%{$this->busqueda}%"))
            ->with('ventas')
            ->orderByDesc('compras_count')
            ->paginate(10);

        // Porcentaje de descuento efectivamente recibido, calculado a partir del
        // historial real de ventas de cada cliente.
        $descuentoPctPorCliente = collect($clientes->items())->mapWithKeys(function (Cliente $cliente) {
            $subtotal = (float) $cliente->ventas->sum('subtotal');
            $descuento = (float) $cliente->ventas->sum('descuento');

            return [$cliente->id => $subtotal > 0 ? round($descuento / $subtotal * 100, 2) : 0.0];
        });

        return [
            'clientes' => $clientes,
            'descuentoPctPorCliente' => $descuentoPctPorCliente,
            'clienteFrecuente' => Cliente::query()->orderByDesc('compras_count')->first(),
        ];
    }

    public function nuevo(): void
    {
        $this->reset(['clienteId', 'nombres', 'apellidos', 'documento']);
        $this->resetValidation();
        Flux::modal('cliente-form')->show();
    }

    public function editar(int $id): void
    {
        $cliente = Cliente::findOrFail($id);
        $this->clienteId = $cliente->id;
        $this->nombres = $cliente->nombres;
        $this->apellidos = $cliente->apellidos;
        $this->documento = $cliente->documento;
        $this->resetValidation();
        Flux::modal('cliente-form')->show();
    }

    public function guardar(): void
    {
        $data = $this->validate([
            'nombres' => 'required|string|max:255',
            'apellidos' => 'nullable|string|max:255',
            'documento' => 'required|string|max:15|unique:clientes,documento,'.$this->clienteId,
        ]);

        Cliente::updateOrCreate(['id' => $this->clienteId], $data);

        Flux::modal('cliente-form')->close();
        Flux::toast('Cliente guardado correctamente.', variant: 'success');
    }

    public function pedirEliminar(int $id): void
    {
        $this->eliminarId = $id;
        Flux::modal('confirmar-eliminar-cliente')->show();
    }

    public function eliminar(): void
    {
        Cliente::whereKey($this->eliminarId)->delete();

        Flux::modal('confirmar-eliminar-cliente')->close();
        Flux::toast('Cliente eliminado.', variant: 'success');
    }
};
?>

<div class="flex flex-col gap-6">
    <div class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <x-ui.titulo titulo="Clientes">Clientes que compran productos en el punto de venta.</x-ui.titulo>

        <flux:modal.trigger name="cliente-form">
            <flux:button variant="primary" icon="plus" wire:click="nuevo">Nuevo cliente</flux:button>
        </flux:modal.trigger>
    </div>

    @if ($clienteFrecuente && $clienteFrecuente->compras_count > 0)
        <div class="flex items-center gap-2 rounded-lg border border-huata-amarillo-400/60 bg-huata-amarillo-400/10 px-4 py-2 text-sm">
            <flux:icon name="star" class="size-4 text-huata-amarillo-600" />
            <span>Cliente más frecuente: <strong>{{ trim("{$clienteFrecuente->nombres} {$clienteFrecuente->apellidos}") }}</strong> ({{ $clienteFrecuente->compras_count }} compras)</span>
        </div>
    @endif

    <flux:input
        wire:model.live.debounce.300ms="busqueda"
        icon="magnifying-glass"
        placeholder="Buscar cliente..."
        class="max-w-sm"
    />

    <div class="overflow-x-auto rounded-xl border border-huata-verde-100 px-3 dark:border-zinc-700 huata-cuadro">
        <flux:table :paginate="$clientes">
            <flux:table.columns class="bg-white/80 dark:bg-zinc-800/80">
                <flux:table.column>Cliente</flux:table.column>
                <flux:table.column>DNI/RUC</flux:table.column>
                <flux:table.column>Compras</flux:table.column>
                <flux:table.column>% Descuento</flux:table.column>
                <flux:table.column class="w-px whitespace-nowrap">Acciones</flux:table.column>
            </flux:table.columns>

            <flux:table.rows>
                @forelse ($clientes as $cliente)
                    <flux:table.row wire:key="cliente-{{ $cliente->id }}">
                        <flux:table.cell class="font-medium">{{ trim("{$cliente->nombres} {$cliente->apellidos}") }}</flux:table.cell>
                        <flux:table.cell>{{ $cliente->documento }}</flux:table.cell>
                        <flux:table.cell>{{ $cliente->compras_count }}</flux:table.cell>
                        <flux:table.cell>{{ number_format($descuentoPctPorCliente[$cliente->id] ?? 0, 2) }}%</flux:table.cell>
                        <flux:table.cell>
                            <div class="flex gap-2">
                                <flux:modal.trigger name="cliente-form">
                                    <flux:button size="sm" variant="ghost" icon="pencil" wire:click="editar({{ $cliente->id }})" />
                                </flux:modal.trigger>
                                <flux:modal.trigger name="confirmar-eliminar-cliente">
                                    <flux:button size="sm" variant="ghost" icon="trash" wire:click="pedirEliminar({{ $cliente->id }})" />
                                </flux:modal.trigger>
                            </div>
                        </flux:table.cell>
                    </flux:table.row>
                @empty
                    <flux:table.row>
                        <flux:table.cell colspan="5" class="text-center text-zinc-500">
                            No hay clientes registrados todavía.
                        </flux:table.cell>
                    </flux:table.row>
                @endforelse
            </flux:table.rows>
        </flux:table>
    </div>

    <flux:modal name="cliente-form" class="md:w-96">
        <form wire:submit="guardar" class="flex flex-col gap-6">
            <flux:heading size="lg">{{ $clienteId ? 'Editar cliente' : 'Nuevo cliente' }}</flux:heading>

            <flux:input label="Nombres" wire:model="nombres" />
            <flux:input label="Apellidos" wire:model="apellidos" />
            <flux:input label="DNI/RUC" wire:model="documento" />

            <div class="flex justify-end gap-2">
                <flux:modal.close>
                    <flux:button variant="ghost">Cancelar</flux:button>
                </flux:modal.close>
                <flux:button type="submit" variant="primary">Guardar</flux:button>
            </div>
        </form>
    </flux:modal>

    <x-confirm-modal
        name="confirmar-eliminar-cliente"
        accion="eliminar"
        titulo="¿Eliminar este cliente?"
        mensaje="Esta acción no se puede deshacer."
    />
</div>

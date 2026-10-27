<?php

use App\Models\Compra;
use App\Models\Insumo;
use App\Models\UnidadMedida;
use Flux\Flux;
use Livewire\Component;

new class extends Component
{
    // --- Insumo ---
    public ?int $insumoId = null;

    public string $insumoNombre = '';

    public bool $insumoEsLeche = false;

    public ?int $insumoUnidadMedidaId = null;

    public string $insumoCostoUnitario = '';

    public string $insumoStockActual = '0';

    public ?int $insumoCompraId = null;

    public string $compraCantidad = '';

    public string $compraCosto = '';

    public ?int $eliminarInsumoId = null;

    public function with(): array
    {
        // Se asegura de que el insumo de leche siempre exista, aunque nunca se haya enviado
        // ningún resultado de laboratorio a producción todavía.
        Insumo::deLeche();

        return [
            'unidades' => UnidadMedida::orderBy('nombre')->get(),
            'insumos' => Insumo::with('unidadMedida')->orderByDesc('es_leche')->orderBy('nombre')->get(),
        ];
    }

    // ----- Insumo -----
    public function nuevoInsumo(): void
    {
        $this->reset(['insumoId', 'insumoNombre', 'insumoUnidadMedidaId', 'insumoCostoUnitario', 'insumoEsLeche']);
        $this->insumoStockActual = '0';
        Flux::modal('insumo-form')->show();
    }

    public function editarInsumo(int $id): void
    {
        $i = Insumo::findOrFail($id);
        $this->insumoId = $i->id;
        $this->insumoNombre = $i->nombre;
        $this->insumoEsLeche = $i->es_leche;
        $this->insumoUnidadMedidaId = $i->unidad_medida_id;
        $this->insumoCostoUnitario = (string) $i->costo_unitario;
        $this->insumoStockActual = (string) $i->stock_actual;
        Flux::modal('insumo-form')->show();
    }

    public function guardarInsumo(): void
    {
        $data = $this->validate([
            'insumoNombre' => 'required|string|max:255',
            'insumoUnidadMedidaId' => 'required|exists:unidades_medida,id',
            'insumoCostoUnitario' => 'required|numeric|min:0',
            'insumoStockActual' => 'required|numeric|min:0',
        ]);

        if ($this->insumoEsLeche) {
            Insumo::where('es_leche', true)->where('id', '!=', $this->insumoId)->update(['es_leche' => false]);
        }

        Insumo::updateOrCreate(['id' => $this->insumoId], [
            'nombre' => $data['insumoNombre'],
            'es_leche' => $this->insumoEsLeche,
            'unidad_medida_id' => $data['insumoUnidadMedidaId'],
            'costo_unitario' => $data['insumoCostoUnitario'],
            'stock_actual' => $data['insumoStockActual'],
        ]);

        Flux::modal('insumo-form')->close();
        Flux::toast('Insumo guardado.', variant: 'success');
    }

    public function pedirEliminarInsumo(int $id): void
    {
        $this->eliminarInsumoId = $id;
        Flux::modal('confirmar-eliminar-insumo')->show();
    }

    public function eliminarInsumo(): void
    {
        $insumo = Insumo::findOrFail($this->eliminarInsumoId);

        if ($insumo->es_leche) {
            Flux::modal('confirmar-eliminar-insumo')->close();
            Flux::toast('El insumo de leche está bloqueado y no se puede eliminar.', variant: 'danger');

            return;
        }

        $insumo->delete();
        Flux::modal('confirmar-eliminar-insumo')->close();
        Flux::toast('Insumo eliminado.', variant: 'success');
    }

    public function abrirCompra(int $id): void
    {
        $this->insumoCompraId = $id;
        $this->reset(['compraCantidad', 'compraCosto']);
        Flux::modal('compra-form')->show();
    }

    public function registrarCompra(): void
    {
        $data = $this->validate([
            'compraCantidad' => 'required|numeric|min:0.001',
            'compraCosto' => 'required|numeric|min:0',
        ]);

        $insumo = Insumo::findOrFail($this->insumoCompraId);
        $cantidad = (float) $data['compraCantidad'];
        $costoUnitario = (float) $data['compraCosto'];

        $insumo->registrarCompra($cantidad, $costoUnitario);

        $compra = Compra::create([
            'fecha' => now()->toDateString(),
            'descripcion' => "Compra de {$insumo->nombre}",
            'registrado_por' => auth()->id(),
        ]);

        $compra->detalles()->create([
            'insumo_id' => $insumo->id,
            'cantidad' => $cantidad,
            'costo_unitario' => $costoUnitario,
            'subtotal' => round($cantidad * $costoUnitario, 2),
        ]);

        Flux::modal('compra-form')->close();
        Flux::toast('Compra registrada, stock actualizado.', variant: 'success');
    }
};
?>

<div class="flex flex-col gap-10">
    <div class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <x-ui.titulo titulo="Insumos">Insumos y compras.</x-ui.titulo>
        <div class="flex gap-2">
            <flux:button
                size="sm"
                variant="ghost"
                icon="credit-card"
                :href="route('produccion.compras')"
                wire:navigate
            >
                Historial de compras
            </flux:button>
            <flux:button
                size="sm"
                variant="ghost"
                icon="scale"
                :href="route('produccion.unidades')"
                wire:navigate
            >
                Unidades de medida
            </flux:button>
        </div>
    </div>

    {{-- Insumos --}}
    <div>
        <div class="mb-3 flex items-center justify-between">
            <flux:heading size="lg">Insumos</flux:heading>
            <flux:modal.trigger name="insumo-form">
                <flux:button size="sm" variant="primary" icon="plus" wire:click="nuevoInsumo">Nuevo insumo</flux:button>
            </flux:modal.trigger>
        </div>

        <div class="overflow-x-auto rounded-xl border border-huata-verde-100 px-3 dark:border-zinc-700 huata-cuadro">
            <flux:table>
                <flux:table.columns class="bg-white/80 dark:bg-zinc-800/80">
                    <flux:table.column>Insumo</flux:table.column>
                    <flux:table.column>Fecha</flux:table.column>
                    <flux:table.column>Unidad</flux:table.column>
                    <flux:table.column>Costo unitario</flux:table.column>
                    <flux:table.column>Stock</flux:table.column>
                    <flux:table.column class="w-px whitespace-nowrap">Acciones</flux:table.column>
                </flux:table.columns>
                <flux:table.rows>
                    @forelse ($insumos as $insumo)
                        <flux:table.row wire:key="insumo-{{ $insumo->id }}">
                            <flux:table.cell class="font-medium">
                                {{ $insumo->nombre }}
                                @if ($insumo->es_leche)
                                    <flux:badge color="blue" size="sm" class="ml-1">Automático (litraje que llegó a planta)</flux:badge>
                                @endif
                            </flux:table.cell>
                            <flux:table.cell>{{ $insumo->updated_at->format('d/m/Y') }}</flux:table.cell>
                            <flux:table.cell>{{ $insumo->unidadMedida->simbolo }}</flux:table.cell>
                            <flux:table.cell>S/ {{ number_format((float) $insumo->costo_unitario, 4) }}</flux:table.cell>
                            <flux:table.cell>{{ number_format((float) $insumo->stock_actual, 2) }} {{ $insumo->unidadMedida->simbolo }}</flux:table.cell>
                            <flux:table.cell>
                                @if ($insumo->es_leche)
                                    <flux:badge color="zinc" size="sm" icon="lock-closed">Bloqueado</flux:badge>
                                @else
                                    <div class="flex gap-2">
                                        <flux:modal.trigger name="compra-form">
                                            <flux:button size="sm" variant="ghost" icon="arrow-down-tray" wire:click="abrirCompra({{ $insumo->id }})" />
                                        </flux:modal.trigger>
                                        <flux:modal.trigger name="insumo-form">
                                            <flux:button size="sm" variant="ghost" icon="pencil" wire:click="editarInsumo({{ $insumo->id }})" />
                                        </flux:modal.trigger>
                                        <flux:modal.trigger name="confirmar-eliminar-insumo">
                                            <flux:button size="sm" variant="ghost" icon="trash" wire:click="pedirEliminarInsumo({{ $insumo->id }})" />
                                        </flux:modal.trigger>
                                    </div>
                                @endif
                            </flux:table.cell>
                        </flux:table.row>
                    @empty
                        <flux:table.row><flux:table.cell colspan="6" class="text-center text-zinc-500">Sin insumos registrados.</flux:table.cell></flux:table.row>
                    @endforelse
                </flux:table.rows>
            </flux:table>
        </div>
    </div>

    {{-- Modales --}}
    <flux:modal name="insumo-form" class="md:w-96">
        <form wire:submit="guardarInsumo" class="flex flex-col gap-6">
            <flux:heading size="lg">{{ $insumoId ? 'Editar insumo' : 'Nuevo insumo' }}</flux:heading>
            <flux:input label="Nombre" wire:model="insumoNombre" placeholder="Leche fresca" />
            <flux:select label="Unidad de medida" wire:model="insumoUnidadMedidaId">
                <flux:select.option value="">Selecciona...</flux:select.option>
                @foreach ($unidades as $u)
                    <flux:select.option value="{{ $u->id }}">{{ $u->nombre }} ({{ $u->simbolo }})</flux:select.option>
                @endforeach
            </flux:select>
            <flux:input type="number" step="0.0001" min="0" label="Costo unitario (S/)" wire:model="insumoCostoUnitario" />
            <flux:input type="number" step="0.001" min="0" label="Stock actual" wire:model="insumoStockActual" />
            <div class="flex justify-end gap-2">
                <flux:modal.close><flux:button variant="ghost">Cancelar</flux:button></flux:modal.close>
                <flux:button type="submit" variant="primary">Guardar</flux:button>
            </div>
        </form>
    </flux:modal>

    <flux:modal name="compra-form" class="md:w-80">
        <form wire:submit="registrarCompra" class="flex flex-col gap-6">
            <flux:heading size="lg">Registrar compra de insumo</flux:heading>
            <flux:input type="number" step="0.001" min="0" label="Cantidad" wire:model="compraCantidad" />
            <flux:input type="number" step="0.0001" min="0" label="Costo unitario pagado (S/)" wire:model="compraCosto" />
            <div class="flex justify-end gap-2">
                <flux:modal.close><flux:button variant="ghost">Cancelar</flux:button></flux:modal.close>
                <flux:button type="submit" variant="primary">Registrar</flux:button>
            </div>
        </form>
    </flux:modal>

    <x-confirm-modal
        name="confirmar-eliminar-insumo"
        accion="eliminarInsumo"
        titulo="¿Eliminar este insumo?"
        mensaje="Esta acción no se puede deshacer."
    />
</div>

<?php

use App\Models\Insumo;
use App\Models\Receta;
use App\Models\RecetaDetalle;
use Flux\Flux;
use Livewire\Component;

new class extends Component
{
    // --- Receta ---
    public ?int $recetaId = null;

    public string $recetaNombre = '';

    public string $recetaPrecioVenta = '';

    public array $recetaDetalles = [];

    public ?int $eliminarRecetaId = null;

    public function with(): array
    {
        return [
            'insumos' => Insumo::with('unidadMedida')->orderBy('nombre')->get(),
            'recetas' => Receta::with('detalles.insumo.unidadMedida')->orderBy('nombre')->get(),
        ];
    }

    public function nuevaReceta(): void
    {
        $this->reset(['recetaId', 'recetaNombre', 'recetaPrecioVenta']);
        $this->recetaDetalles = [];
        Flux::modal('receta-form')->show();
    }

    public function editarReceta(int $id): void
    {
        $r = Receta::with('detalles')->findOrFail($id);
        $this->recetaId = $r->id;
        $this->recetaNombre = $r->nombre;
        $this->recetaPrecioVenta = $r->precio_venta !== null ? (string) $r->precio_venta : '';
        $this->recetaDetalles = $r->detalles->map(fn (RecetaDetalle $d) => [
            'insumo_id' => $d->insumo_id,
            'cantidad' => (string) $d->cantidad,
        ])->toArray();
        Flux::modal('receta-form')->show();
    }

    public function agregarIngrediente(): void
    {
        $this->recetaDetalles[] = ['insumo_id' => null, 'cantidad' => ''];
    }

    public function quitarIngrediente(int $index): void
    {
        unset($this->recetaDetalles[$index]);
        $this->recetaDetalles = array_values($this->recetaDetalles);
    }

    public function guardarReceta(): void
    {
        $data = $this->validate([
            'recetaNombre' => 'required|string|max:255',
            'recetaPrecioVenta' => 'nullable|numeric|min:0|max:99999999',
        ]);

        if (collect($this->recetaDetalles)->filter(fn (array $l) => ! empty($l['insumo_id']) && $l['cantidad'] !== '')->isEmpty()) {
            Flux::toast('Agrega al menos un insumo a la receta.', variant: 'danger');

            return;
        }

        $receta = Receta::updateOrCreate(['id' => $this->recetaId], [
            'nombre' => $data['recetaNombre'],
            'precio_venta' => $data['recetaPrecioVenta'] === null || $data['recetaPrecioVenta'] === '' ? null : $data['recetaPrecioVenta'],
        ]);

        $receta->detalles()->delete();

        foreach ($this->recetaDetalles as $linea) {
            if (empty($linea['insumo_id']) || $linea['cantidad'] === '') {
                continue;
            }

            RecetaDetalle::create([
                'receta_id' => $receta->id,
                'insumo_id' => $linea['insumo_id'],
                'cantidad' => $linea['cantidad'],
                'unidad_medida_id' => Insumo::find($linea['insumo_id'])->unidad_medida_id,
            ]);
        }

        Flux::modal('receta-form')->close();
        Flux::toast('Receta guardada.', variant: 'success');
    }

    public function pedirEliminarReceta(int $id): void
    {
        $this->eliminarRecetaId = $id;
        Flux::modal('confirmar-eliminar-receta')->show();
    }

    public function eliminarReceta(): void
    {
        Receta::whereKey($this->eliminarRecetaId)->delete();
        Flux::modal('confirmar-eliminar-receta')->close();
        Flux::toast('Receta eliminada.', variant: 'success');
    }
};
?>

<div class="flex flex-col gap-10">
    <div class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <x-ui.titulo titulo="Recetas">Arma cada receta con sus insumos y mira cuánto cuesta producirla.</x-ui.titulo>
        <flux:modal.trigger name="receta-form">
            <flux:button size="sm" variant="primary" icon="plus" wire:click="nuevaReceta">Nueva receta</flux:button>
        </flux:modal.trigger>
    </div>

    <div class="flex flex-col gap-4">
        @forelse ($recetas as $receta)
            <div class="rounded-xl border border-huata-verde-100 p-5 dark:border-zinc-700 huata-cuadro">
                <div class="mb-3 flex items-start justify-between">
                    <flux:heading size="md">{{ $receta->nombre }}</flux:heading>
                    <div class="flex gap-2">
                        <flux:modal.trigger name="receta-form">
                            <flux:button size="sm" variant="ghost" icon="pencil" wire:click="editarReceta({{ $receta->id }})" />
                        </flux:modal.trigger>
                        <flux:modal.trigger name="confirmar-eliminar-receta">
                            <flux:button size="sm" variant="ghost" icon="trash" wire:click="pedirEliminarReceta({{ $receta->id }})" />
                        </flux:modal.trigger>
                    </div>
                </div>

                <div class="grid gap-4 md:grid-cols-3">
                    <div class="md:col-span-2">
                        <div class="max-h-64 overflow-x-auto overflow-y-auto rounded-xl border border-huata-verde-100 dark:border-zinc-700">
                            <flux:table>
                                <flux:table.columns sticky class="bg-white dark:bg-zinc-800">
                                    <flux:table.column>Fecha</flux:table.column>
                                    <flux:table.column>Insumos</flux:table.column>
                                    <flux:table.column>Cantidad</flux:table.column>
                                    <flux:table.column>Costo unit.</flux:table.column>
                                    <flux:table.column>% del costo</flux:table.column>
                                    <flux:table.column>Costo total</flux:table.column>
                                </flux:table.columns>
                                <flux:table.rows>
                                    @php $costoTotalReceta = $receta->costoTotal(); @endphp
                                    @forelse ($receta->detalles as $detalle)
                                        <flux:table.row wire:key="rd-{{ $detalle->id }}">
                                            <flux:table.cell>{{ $receta->created_at->format('d/m/Y') }}</flux:table.cell>
                                            <flux:table.cell>{{ $detalle->insumo->nombre }}</flux:table.cell>
                                            <flux:table.cell>{{ number_format((float) $detalle->cantidad, 2) }} {{ $detalle->unidadMedida->simbolo }}</flux:table.cell>
                                            <flux:table.cell>S/ {{ number_format((float) $detalle->insumo->costo_unitario, 4) }}</flux:table.cell>
                                            <flux:table.cell>{{ $costoTotalReceta > 0 ? number_format($detalle->costo() / $costoTotalReceta * 100, 1) : '0,0' }}%</flux:table.cell>
                                            <flux:table.cell>S/ {{ number_format($detalle->costo(), 2) }}</flux:table.cell>
                                        </flux:table.row>
                                    @empty
                                        <flux:table.row><flux:table.cell colspan="6" class="text-center text-zinc-500">Sin insumos.</flux:table.cell></flux:table.row>
                                    @endforelse
                                </flux:table.rows>
                            </flux:table>
                        </div>
                    </div>

                    <div class="flex flex-col gap-2 rounded-lg bg-huata-verde-50 p-4 text-sm dark:bg-huata-verde-950/30">
                        <div class="flex justify-between">
                            <span>Costo total receta</span>
                            <span class="font-semibold">S/ {{ number_format($receta->costoTotal(), 2) }}</span>
                        </div>
                        <div class="flex justify-between">
                            <span>Precio de venta (por unidad)</span>
                            <span class="font-semibold">S/ {{ number_format($receta->precioVenta(), 2) }}</span>
                        </div>
                        @if ((float) $receta->precio_venta <= 0)
                            <p class="text-xs text-zinc-500">Sin precio propio: se vende al costo. Edita la receta para fijarlo.</p>
                        @endif
                    </div>
                </div>
            </div>
        @empty
            <div class="rounded-xl border border-huata-verde-100 p-6 text-center text-zinc-500 dark:border-zinc-700 huata-cuadro">
                No hay recetas registradas.
            </div>
        @endforelse
    </div>

    <flux:modal name="receta-form" class="md:w-[36rem]">
        <form wire:submit="guardarReceta" class="flex flex-col gap-6">
            <flux:heading size="lg">{{ $recetaId ? 'Editar receta' : 'Nueva receta' }}</flux:heading>

            <flux:input label="Nombre de la receta" wire:model="recetaNombre" placeholder="Queso fresco artesanal" />

            <flux:input label="Precio de venta por unidad (S/)" type="number" step="0.01" min="0" wire:model="recetaPrecioVenta" placeholder="Vacío = se vende al costo" />

            <div>
                <div class="mb-2 flex items-center justify-between">
                    <flux:heading size="sm">Insumos de la receta</flux:heading>
                    <flux:button size="sm" variant="ghost" icon="plus" wire:click="agregarIngrediente">Agregar insumo</flux:button>
                </div>

                <div class="flex flex-col gap-3">
                    @forelse ($recetaDetalles as $index => $linea)
                        @php $insumoSeleccionado = $insumos->firstWhere('id', (int) ($linea['insumo_id'] ?: 0)); @endphp
                        <div
                            class="flex flex-col gap-3 rounded-lg border border-huata-verde-100 bg-huata-verde-50/40 p-3 sm:flex-row sm:items-start dark:border-zinc-700 dark:bg-zinc-900/40"
                            wire:key="detalle-{{ $index }}"
                        >
                            <flux:select label="Insumo" wire:model.live="recetaDetalles.{{ $index }}.insumo_id" class="flex-1">
                                <flux:select.option value="">Selecciona...</flux:select.option>
                                @foreach ($insumos as $ins)
                                    <flux:select.option value="{{ $ins->id }}">{{ $ins->nombre }} ({{ $ins->unidadMedida->simbolo }})</flux:select.option>
                                @endforeach
                            </flux:select>
                            <flux:input.group label="Cantidad" class="sm:w-40">
                                <flux:input
                                    type="number" step="0.001" min="0"
                                    wire:model="recetaDetalles.{{ $index }}.cantidad"
                                />
                                <flux:input.group.suffix>{{ $insumoSeleccionado?->unidadMedida->simbolo ?? 'u.' }}</flux:input.group.suffix>
                            </flux:input.group>
                            <flux:button
                                size="sm"
                                variant="ghost"
                                icon="trash"
                                class="self-end sm:mt-6 sm:self-start"
                                wire:click="quitarIngrediente({{ $index }})"
                            />
                        </div>
                    @empty
                        <p class="rounded-lg border border-dashed border-huata-verde-200 p-4 text-center text-sm text-zinc-500 dark:border-zinc-700">
                            Aún no agregaste insumos. Presiona "Agregar insumo" para añadir el primero.
                        </p>
                    @endforelse
                </div>
            </div>

            <div class="flex justify-end gap-2">
                <flux:modal.close><flux:button variant="ghost">Cancelar</flux:button></flux:modal.close>
                <flux:button type="submit" variant="primary">Guardar receta</flux:button>
            </div>
        </form>
    </flux:modal>

    <x-confirm-modal
        name="confirmar-eliminar-receta"
        accion="eliminarReceta"
        titulo="¿Eliminar esta receta?"
        mensaje="Esta acción no se puede deshacer."
    />
</div>

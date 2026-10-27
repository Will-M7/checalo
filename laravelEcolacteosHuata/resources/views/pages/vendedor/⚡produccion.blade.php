<?php

use App\Enums\EstadoProduccion;
use App\Models\Produccion;
use Flux\Flux;
use Livewire\Component;
use Livewire\WithFileUploads;

new class extends Component
{
    use \App\Livewire\Concerns\FiltrosListado;
    use WithFileUploads;

    public string $busqueda = '';

    public ?int $fotoProduccionId = null;

    public string $fotoProduccionNombre = '';

    public $foto = null;

    public function with(): array
    {
        return [
            'producciones' => Produccion::query()
                ->when($this->filtro('stock') === 'disponible', fn ($q) => $q->whereRaw('(cantidad_producida - cantidad_vendida) > 0'))
                ->when($this->filtro('stock') === 'bajo', fn ($q) => $q->whereRaw('(cantidad_producida - cantidad_vendida) > 0 AND (cantidad_producida - cantidad_vendida) <= 5'))
                ->when($this->filtro('stock') === 'agotado', fn ($q) => $q->whereRaw('(cantidad_producida - cantidad_vendida) <= 0'))
                ->when($this->filtro('desde'), fn ($q, $v) => $q->whereDate('fecha', '>=', $v))
                ->when($this->filtro('hasta'), fn ($q, $v) => $q->whereDate('fecha', '<=', $v))
                
                ->with('receta.detalles.insumo')
                ->where('estado', EstadoProduccion::Completada)
                ->when($this->busqueda, fn ($q) => $q->whereHas('receta', fn ($r) => $r->where('nombre', 'like', "%{$this->busqueda}%")))
                ->orderByDesc('fecha')
                ->get(),
        ];
    }

    public function abrirFoto(int $id): void
    {
        $produccion = Produccion::findOrFail($id);
        $this->fotoProduccionId = $produccion->id;
        $this->fotoProduccionNombre = $produccion->nombre ?: $produccion->receta->nombre;
        $this->foto = null;
        $this->resetValidation();
        Flux::modal('foto-form')->show();
    }

    public function guardarFoto(): void
    {
        $this->validate([
            'foto' => 'required|file|mimes:jpg,jpeg,png,gif,bmp,webp,heic,heif|max:5120',
        ]);

        $produccion = Produccion::findOrFail($this->fotoProduccionId);
        $ruta = $this->foto->store('producciones', 'public');

        $produccion->update(['imagen' => $ruta]);

        Flux::modal('foto-form')->close();
        Flux::toast('Foto actualizada.', variant: 'success');
    }
};
?>

<div class="flex flex-col gap-6">
    <div class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <x-ui.titulo titulo="Productos">Lo que ya se terminó de producir, listo para vender.</x-ui.titulo>
        <div class="flex flex-wrap items-center gap-3"><flux:input
            wire:model.live.debounce.300ms="busqueda"
            icon="magnifying-glass"
            placeholder="Buscar por receta..."
            class="max-w-sm"
        /><x-ui.filtros :activos="$filtros"><flux:select label="Disponibilidad" wire:model.live="filtros.stock"><flux:select.option value="">Todos</flux:select.option><flux:select.option value="disponible">Con stock</flux:select.option><flux:select.option value="bajo">Cantidad baja (hasta 5)</flux:select.option><flux:select.option value="agotado">Sin stock</flux:select.option></flux:select><flux:input label="Desde" type="date" wire:model.live="filtros.desde" /><flux:input label="Hasta" type="date" wire:model.live="filtros.hasta" /></x-ui.filtros></div>
    </div>

    <div class="overflow-x-auto rounded-xl border border-huata-verde-100 px-3 dark:border-zinc-700 huata-cuadro">
        <flux:table>
            <flux:table.columns class="bg-white/80 dark:bg-zinc-800/80">
                <flux:table.column>Nombre</flux:table.column>
                <flux:table.column>Cantidad</flux:table.column>
                <flux:table.column>Precio unidad</flux:table.column>
                <flux:table.column>Estado</flux:table.column>
                <flux:table.column class="w-px whitespace-nowrap">Acciones</flux:table.column>
            </flux:table.columns>
            <flux:table.rows>
                @forelse ($producciones as $produccion)
                    @php $precioUnidad = $produccion->receta->precioVenta(); $cantidad = $produccion->cantidadDisponible(); @endphp
                    <flux:table.row wire:key="prod-{{ $produccion->id }}">
                        <flux:table.cell class="font-medium">
                            <div class="flex items-center gap-2">
                                @if ($produccion->imagenUrl())
                                    <img src="{{ $produccion->imagenUrl() }}" alt="{{ $produccion->nombre }}" class="size-8 rounded-md object-cover" />
                                @endif
                                {{ $produccion->nombre ?? $produccion->receta->nombre }}
                            </div>
                        </flux:table.cell>
                        <flux:table.cell>{{ number_format($cantidad, 2) }}</flux:table.cell>
                        <flux:table.cell>S/ {{ number_format($precioUnidad, 2) }}</flux:table.cell>
                        <flux:table.cell>
                            @if ($cantidad <= 0)
                                <flux:badge color="red" size="sm">Sin stock</flux:badge>
                            @elseif ($cantidad <= 5)
                                <flux:badge color="amber" size="sm">Cantidad baja</flux:badge>
                            @else
                                <flux:badge color="green" size="sm">Disponible</flux:badge>
                            @endif
                        </flux:table.cell>
                        <flux:table.cell>
                            <flux:modal.trigger name="foto-form">
                                <flux:button size="sm" variant="ghost" icon="camera" wire:click="abrirFoto({{ $produccion->id }})">
                                    Foto
                                </flux:button>
                            </flux:modal.trigger>
                        </flux:table.cell>
                    </flux:table.row>
                @empty
                    <flux:table.row><flux:table.cell colspan="5" class="text-center text-zinc-500">No se encontraron producciones.</flux:table.cell></flux:table.row>
                @endforelse
            </flux:table.rows>
        </flux:table>
    </div>

    <flux:modal name="foto-form" class="md:w-96">
        <form wire:submit="guardarFoto" class="flex flex-col gap-6">
            <flux:heading size="lg">Foto de {{ $fotoProduccionNombre }}</flux:heading>

            <flux:input type="file" label="Selecciona una imagen" wire:model="foto" accept="image/*" />

            @if ($foto)
                @if ($foto->isPreviewable())
                    <img src="{{ $foto->temporaryUrl() }}" alt="Vista previa" class="h-32 w-32 rounded-lg object-cover" />
                @else
                    <p class="text-sm text-zinc-500">{{ $foto->getClientOriginalName() }}</p>
                @endif
            @endif

            <div class="flex justify-end gap-2">
                <flux:modal.close><flux:button variant="ghost">Cancelar</flux:button></flux:modal.close>
                <flux:button type="submit" variant="primary">Guardar foto</flux:button>
            </div>
        </form>
    </flux:modal>
</div>

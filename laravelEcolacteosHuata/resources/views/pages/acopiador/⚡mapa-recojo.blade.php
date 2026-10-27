<?php

use App\Models\Recojo;
use App\Models\Ruta;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Support\Collection;
use Livewire\Component;

new class extends Component
{
    public ?int $rutaId = null;

    public function mount(): void
    {
        $this->rutaId = $this->misRutas()->orderBy('nombre')->value('id');
    }

    public function with(): array
    {
        $ruta = $this->rutaSeleccionada();
        $recogidosIds = $this->recogidosHoyIds();

        return [
            'rutas' => $this->misRutas()->orderBy('nombre')->get(),
            'ruta' => $ruta,
            'recogidosIds' => $recogidosIds,
            'datosMapa' => $ruta?->datosMapa($recogidosIds) ?? ['rutaId' => null, 'trazo' => [], 'paradas' => []],
        ];
    }

    public function updatedRutaId(): void
    {
        $ruta = $this->rutaSeleccionada();

        $this->dispatch('mapa-ruta', datos: $ruta?->datosMapa($this->recogidosHoyIds()) ?? ['rutaId' => null, 'trazo' => [], 'paradas' => []]);
    }

    /**
     * Solo las rutas activas asignadas al acopiador que inició sesión.
     *
     * @return Builder<Ruta>
     */
    private function misRutas(): Builder
    {
        return Ruta::query()
            ->where('acopiador_id', auth()->user()->acopiador?->id)
            ->where('activa', true);
    }

    private function rutaSeleccionada(): ?Ruta
    {
        return $this->rutaId
            ? $this->misRutas()->with(['turnos', 'proveedores.user'])->find($this->rutaId)
            : null;
    }

    /**
     * Proveedores ya recogidos en la ronda actual (mismo criterio que la pantalla de Recojo:
     * al entregar a planta, los recojos de hoy dejan de contar para el siguiente turno).
     *
     * @return Collection<int, int>
     */
    private function recogidosHoyIds(): Collection
    {
        return Recojo::query()
            ->where('acopiador_id', auth()->user()->acopiador?->id)
            ->whereDate('fecha', now()->toDateString())
            ->where('entregado', false)
            ->pluck('proveedor_id')
            ->unique()
            ->values();
    }
};
?>

<div class="flex flex-col gap-6">
    <div class="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <x-ui.titulo titulo="Mapa de recojo">Sigue el camino designado por planta y recoge la leche en cada parada.</x-ui.titulo>
        @if ($rutas->count() > 1)
            <div class="w-full sm:w-72">
                <flux:select wire:model.live="rutaId" label="Ruta">
                    @foreach ($rutas as $r)
                        <flux:select.option value="{{ $r->id }}" wire:key="opcion-ruta-{{ $r->id }}">{{ $r->nombre }}</flux:select.option>
                    @endforeach
                </flux:select>
            </div>
        @endif
    </div>

    @if (! $ruta)
        <div class="rounded-xl border border-huata-verde-100 p-6 text-center text-zinc-500 dark:border-zinc-700 huata-cuadro">
            Todavía no tienes una ruta activa asignada. Pide a planta que te asigne una.
        </div>
    @else
        <div class="flex flex-wrap items-center gap-2">
            <flux:badge color="green" icon="map">{{ $ruta->nombre }}</flux:badge>
            @foreach ($ruta->turnos as $turno)
                <flux:badge color="amber" size="sm" icon="clock" wire:key="turno-{{ $turno->id }}">{{ $turno->nombre }}: {{ $turno->hora_inicio }} - {{ $turno->hora_fin }}</flux:badge>
            @endforeach
            <flux:badge color="zinc" size="sm">
                {{ $ruta->proveedores->whereIn('id', $recogidosIds)->count() }} / {{ $ruta->proveedores->count() }} recogidos
            </flux:badge>
            @if (empty($ruta->trazo))
                <flux:badge color="red" size="sm" icon="exclamation-triangle">Planta aún no trazó el camino</flux:badge>
            @endif
        </div>
    @endif

    <div @class(['grid gap-6 lg:grid-cols-3', 'hidden' => ! $ruta])>
        <div class="lg:col-span-2">
            <x-mapa-ruta :datos="$datosMapa" />
        </div>

        <div class="flex flex-col gap-3">
            <div class="flex items-center justify-between">
                <flux:heading size="lg">Paradas</flux:heading>
                <flux:button size="sm" variant="primary" icon="truck" :href="route('acopiador.recojo')" wire:navigate>Registrar recojo</flux:button>
            </div>

            <div class="flex max-h-[34rem] flex-col gap-2 overflow-y-auto pr-1">
                @forelse ($ruta?->proveedores ?? [] as $proveedor)
                    @php $recogido = $recogidosIds->contains($proveedor->id); @endphp
                    <div
                        wire:key="parada-{{ $proveedor->id }}"
                        @class([
                            'flex items-center gap-3 rounded-lg border p-2',
                            'border-green-300 bg-green-50 dark:border-green-800 dark:bg-green-950/30' => $recogido,
                            'border-zinc-200 bg-white dark:border-zinc-700 dark:bg-zinc-800' => ! $recogido,
                        ])
                    >
                        <div @class([
                            'flex size-8 shrink-0 items-center justify-center rounded-full text-sm font-bold text-white',
                            'bg-green-600' => $recogido,
                            'bg-red-600' => ! $recogido,
                        ])>
                            {{ $loop->iteration }}
                        </div>

                        <div class="min-w-0 flex-1">
                            <p class="truncate text-sm font-semibold text-zinc-900 dark:text-white">{{ $proveedor->user->nombreCompleto() }}</p>
                            <p class="truncate text-xs text-zinc-500">{{ $proveedor->finca_ubicacion ?: 'Sin ubicación registrada' }}</p>
                            <p @class(['text-xs', 'text-green-700' => $recogido, 'text-red-600' => ! $recogido])>
                                {{ $recogido ? 'Recogido' : 'Pendiente' }}
                            </p>
                        </div>

                        @if ($proveedor->latitud !== null && $proveedor->longitud !== null)
                            <flux:button
                                size="xs"
                                variant="ghost"
                                icon="map"
                                x-on:click="$dispatch('como-llegar', { id: {{ $proveedor->id }} })"
                            >
                                Cómo llegar
                            </flux:button>
                        @endif
                    </div>
                @empty
                    <p class="py-6 text-center text-sm text-zinc-500">Tu ruta todavía no tiene proveedores asignados.</p>
                @endforelse
            </div>
        </div>
    </div>
</div>

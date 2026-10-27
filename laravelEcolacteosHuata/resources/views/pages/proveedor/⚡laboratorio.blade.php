<?php

use App\Enums\Periodo;
use App\Models\ResultadoLaboratorio;
use Illuminate\Support\Carbon;
use Livewire\Component;

new class extends Component
{
    public string $periodo = 'turno';

    public string $fecha = '';

    public function mount(): void
    {
        $this->fecha = now()->toDateString();
    }

    public function with(): array
    {
        $proveedor = auth()->user()->proveedor;
        $periodo = Periodo::from($this->periodo);
        [$desde, $hasta] = $periodo->rangoParaFecha(Carbon::parse($this->fecha));

        return [
            'periodos' => Periodo::options(),
            'resultados' => ResultadoLaboratorio::query()
                ->with('turno')
                ->where('proveedor_id', $proveedor?->id)
                ->whereBetween('fecha', [$desde->toDateString(), $hasta->toDateString()])
                ->orderByDesc('fecha')
                ->get(),
        ];
    }
};
?>

<div class="flex flex-col gap-6">
    <x-ui.titulo titulo="Resultados de laboratorio">Calidad de la leche que has entregado.</x-ui.titulo>

    <div class="flex flex-wrap items-end gap-4">
        <flux:select label="Período" wire:model.live="periodo" class="max-w-xs">
            @foreach ($periodos as $p)
                <flux:select.option value="{{ $p['value'] }}">{{ $p['label'] }}</flux:select.option>
            @endforeach
        </flux:select>

        <flux:input type="date" label="Fecha de referencia" wire:model.live="fecha" class="max-w-xs" />
    </div>

    <div class="flex flex-col gap-4">
        @forelse ($resultados as $resultado)
            <div
                class="rounded-xl border p-4 {{ $resultado->conforme ? 'border-huata-verde-100 dark:border-zinc-700 dark:bg-zinc-800' : 'border-huata-rojo-500/40 bg-huata-rojo-50 dark:bg-huata-rojo-500/10' }} huata-cuadro"
            >
                <div class="flex flex-wrap items-center justify-between gap-2">
                    <div>
                        <p class="font-semibold">{{ $resultado->fecha->format('d/m/Y') }} &middot; {{ $resultado->turno?->nombre ?? 'Sin turno' }}</p>
                        <p class="text-sm text-zinc-500">Resultado: {{ ucfirst($resultado->resultado) }}</p>
                    </div>
                    <flux:badge :color="$resultado->conforme ? 'green' : 'red'">
                        {{ $resultado->conforme ? 'Conforme' : 'No conforme' }}
                    </flux:badge>
                </div>

                <div class="mt-3 grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
                    <div>
                        <p class="text-zinc-500">Densidad</p>
                        <p class="font-medium">{{ $resultado->densidad ?? '—' }}</p>
                    </div>
                    <div>
                        <p class="text-zinc-500">Hora y fecha del análisis</p>
                        <p class="font-medium">{{ $resultado->horaFechaAnalisis() ?? '—' }}</p>
                    </div>
                    <div>
                        <p class="text-zinc-500">Temperatura</p>
                        <p class="font-medium">{{ $resultado->temperatura ?? '—' }}°C</p>
                    </div>
                </div>

                @if ($resultado->observaciones)
                    <p class="mt-3 text-sm text-zinc-600 dark:text-zinc-300">{{ $resultado->observaciones }}</p>
                @endif
            </div>
        @empty
            <div class="rounded-xl border border-huata-verde-100 p-6 text-center text-zinc-500 dark:border-zinc-700 huata-cuadro">
                No hay resultados de laboratorio en este período.
            </div>
        @endforelse
    </div>

    <div>
        <flux:heading size="lg" class="mb-3">Resultados de laboratorio</flux:heading>
        <div class="overflow-x-auto rounded-xl border border-huata-verde-100 px-3 dark:border-zinc-700 huata-cuadro">
            <flux:table>
                <flux:table.columns class="bg-white/80 dark:bg-zinc-800/80">
                    <flux:table.column>Fecha</flux:table.column>
                    <flux:table.column>Turno</flux:table.column>
                    <flux:table.column>Litros</flux:table.column>
                    <flux:table.column>Temp.</flux:table.column>
                    <flux:table.column>Grasa</flux:table.column>
                    <flux:table.column>SNG</flux:table.column>
                    <flux:table.column>Densidad</flux:table.column>
                    <flux:table.column>Proteína</flux:table.column>
                    <flux:table.column>Lactosa</flux:table.column>
                    <flux:table.column>Sales</flux:table.column>
                    <flux:table.column>Total sólidos</flux:table.column>
                    <flux:table.column>Agua añadida</flux:table.column>
                    <flux:table.column>Punto cong.</flux:table.column>
                    <flux:table.column>pH</flux:table.column>
                    <flux:table.column>Resultado</flux:table.column>
                    <flux:table.column class="w-px whitespace-nowrap">Conforme</flux:table.column>
                </flux:table.columns>
                <flux:table.rows>
                    @forelse ($resultados as $resultado)
                        <flux:table.row wire:key="tabla-{{ $resultado->id }}">
                            <flux:table.cell>{{ $resultado->fecha->format('d/m/Y') }}</flux:table.cell>
                            <flux:table.cell>{{ $resultado->turno?->nombre ?? '—' }}</flux:table.cell>
                            <flux:table.cell>{{ number_format((float) $resultado->litros, 2) }}</flux:table.cell>
                            <flux:table.cell>{{ $resultado->temperatura !== null ? number_format((float) $resultado->temperatura, 2) : '—' }}</flux:table.cell>
                            <flux:table.cell>{{ $resultado->grasa !== null ? number_format((float) $resultado->grasa, 2) : '—' }}</flux:table.cell>
                            <flux:table.cell>{{ $resultado->sng !== null ? number_format((float) $resultado->sng, 2) : '—' }}</flux:table.cell>
                            <flux:table.cell>{{ $resultado->densidad !== null ? number_format((float) $resultado->densidad, 3) : '—' }}</flux:table.cell>
                            <flux:table.cell>{{ $resultado->proteina !== null ? number_format((float) $resultado->proteina, 2) : '—' }}</flux:table.cell>
                            <flux:table.cell>{{ $resultado->lactosa !== null ? number_format((float) $resultado->lactosa, 2) : '—' }}</flux:table.cell>
                            <flux:table.cell>{{ $resultado->sales !== null ? number_format((float) $resultado->sales, 2) : '—' }}</flux:table.cell>
                            <flux:table.cell>{{ $resultado->total_solidos !== null ? number_format((float) $resultado->total_solidos, 2) : '—' }}</flux:table.cell>
                            <flux:table.cell>{{ $resultado->agua_anadida !== null ? number_format((float) $resultado->agua_anadida, 2) : '—' }}</flux:table.cell>
                            <flux:table.cell>{{ $resultado->punto_congelacion !== null ? number_format((float) $resultado->punto_congelacion, 3) : '—' }}</flux:table.cell>
                            <flux:table.cell>{{ $resultado->ph !== null ? number_format((float) $resultado->ph, 2) : '—' }}</flux:table.cell>
                            <flux:table.cell>{{ ucfirst($resultado->resultado) }}</flux:table.cell>
                            <flux:table.cell>
                                <flux:badge :color="$resultado->conforme ? 'green' : 'red'" size="sm">
                                    {{ $resultado->conforme ? 'Conforme' : 'No conforme' }}
                                </flux:badge>
                            </flux:table.cell>
                        </flux:table.row>
                    @empty
                        <flux:table.row>
                            <flux:table.cell colspan="16" class="text-center text-zinc-500">Sin resultados registrados en este período.</flux:table.cell>
                        </flux:table.row>
                    @endforelse
                </flux:table.rows>
            </flux:table>
        </div>
    </div>
</div>

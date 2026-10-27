<?php

use App\Enums\Periodo;
use App\Models\Recojo;
use Illuminate\Support\Carbon;
use Livewire\Component;

new class extends Component
{
    public string $periodo = 'semana';

    public string $fecha = '';

    public function mount(): void
    {
        $this->fecha = now()->toDateString();
    }

    public function with(): array
    {
        $acopiador = auth()->user()->acopiador;
        $periodo = Periodo::from($this->periodo);
        [$desde, $hasta] = $periodo->rangoParaFecha(Carbon::parse($this->fecha));

        $recojos = Recojo::query()
            ->with('proveedor.user', 'turno')
            ->where('acopiador_id', $acopiador?->id)
            ->whereBetween('fecha', [$desde->toDateString(), $hasta->toDateString()])
            ->orderByDesc('fecha')
            ->get();

        $proveedoresAsignados = $acopiador?->proveedores()->with('user')->get() ?? collect();
        $recogidosIds = $recojos->pluck('proveedor_id')->unique();
        $noRecogidos = $proveedoresAsignados->reject(fn ($p) => $recogidosIds->contains($p->id));

        return [
            'periodos' => Periodo::options(),
            'recojos' => $recojos,
            'noRecogidos' => $noRecogidos,
            'litrosTotal' => $recojos->sum('litros'),
            'desde' => $desde,
            'hasta' => $hasta,
        ];
    }
};
?>

<div class="flex flex-col gap-6">
    <div class="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <x-ui.titulo titulo="Inventario">Leche recogida por proveedor y turno.</x-ui.titulo>

        <flux:button
            as="a"
            target="_blank"
            :href="route('acopiador.inventario.pdf', ['periodo' => $periodo, 'fecha' => $fecha])"
            icon="arrow-down-tray"
            variant="subtle"
        >
            Exportar PDF
        </flux:button>
    </div>

    <div class="flex flex-wrap gap-4">
        <flux:select label="Período" wire:model.live="periodo" class="max-w-xs">
            @foreach ($periodos as $p)
                <flux:select.option value="{{ $p['value'] }}">{{ $p['label'] }}</flux:select.option>
            @endforeach
        </flux:select>

        <flux:input type="date" label="Fecha de referencia" wire:model.live="fecha" class="max-w-xs" />
    </div>

    <flux:text class="text-zinc-500">
        {{ $desde->format('d/m/Y') }} — {{ $hasta->format('d/m/Y') }} · Total: {{ number_format((float) $litrosTotal, 2) }} L
    </flux:text>

    <div class="overflow-x-auto rounded-xl border border-huata-verde-100 px-3 dark:border-zinc-700 huata-cuadro">
        <flux:table>
            <flux:table.columns class="bg-white/80 dark:bg-zinc-800/80">
                <flux:table.column>Fecha</flux:table.column>
                <flux:table.column>Proveedor</flux:table.column>
                <flux:table.column>Turno</flux:table.column>
                <flux:table.column>Litros</flux:table.column>
                <flux:table.column>Estado</flux:table.column>
                <flux:table.column class="w-px whitespace-nowrap">Total</flux:table.column>
            </flux:table.columns>
            <flux:table.rows>
                @forelse ($recojos as $recojo)
                    <flux:table.row wire:key="recojo-{{ $recojo->id }}">
                        <flux:table.cell>{{ $recojo->fecha->format('d/m/Y') }}</flux:table.cell>
                        <flux:table.cell>{{ $recojo->proveedor->user->nombreCompleto() }}</flux:table.cell>
                        <flux:table.cell>{{ $recojo->turno->nombre }}</flux:table.cell>
                        <flux:table.cell>{{ number_format((float) $recojo->litros, 2) }}</flux:table.cell>
                        <flux:table.cell>{{ ucfirst($recojo->estado_leche) }}</flux:table.cell>
                        <flux:table.cell>S/ {{ number_format((float) $recojo->total, 2) }}</flux:table.cell>
                    </flux:table.row>
                @empty
                    <flux:table.row>
                        <flux:table.cell colspan="6" class="text-center text-zinc-500">Sin recojos en este período.</flux:table.cell>
                    </flux:table.row>
                @endforelse
            </flux:table.rows>
        </flux:table>
    </div>

    @if ($noRecogidos->isNotEmpty())
        <div>
            <flux:heading size="lg" class="mb-2 text-huata-rojo-600">No recogidos en este período</flux:heading>
            <div class="flex flex-wrap gap-2">
                @foreach ($noRecogidos as $proveedor)
                    <flux:badge color="red">{{ $proveedor->user->nombreCompleto() }}</flux:badge>
                @endforeach
            </div>
        </div>
    @endif
</div>

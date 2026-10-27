<?php

use App\Enums\Periodo;
use App\Models\Recojo;
use Illuminate\Support\Carbon;
use Livewire\Component;

new class extends Component
{
    public string $periodo = 'semana';

    public string $fecha = '';

    public ?int $recojoId = null;

    public function mount(): void
    {
        $this->fecha = now()->toDateString();
    }

    public function with(): array
    {
        $proveedor = auth()->user()->proveedor;
        $periodo = Periodo::from($this->periodo);
        [$desde, $hasta] = $periodo->rangoParaFecha(Carbon::parse($this->fecha));

        $recojos = Recojo::query()
            ->with('turno')
            ->where('proveedor_id', $proveedor?->id)
            ->whereBetween('fecha', [$desde->toDateString(), $hasta->toDateString()])
            ->orderByDesc('fecha')
            ->get();

        return [
            'periodos' => Periodo::options(),
            'recojos' => $recojos,
            'recojoSeleccionado' => $this->recojoId ? $recojos->firstWhere('id', $this->recojoId) : null,
        ];
    }

    public function verDetalle(int $id): void
    {
        $this->recojoId = $id;
    }

    public function cerrarDetalle(): void
    {
        $this->recojoId = null;
    }
};
?>

<div class="flex flex-col gap-6">
    <x-ui.titulo titulo="Mis entregas">Historial de leche entregada y estado de pago.</x-ui.titulo>

    <div class="flex flex-wrap items-end gap-4">
        <flux:select label="Período" wire:model.live="periodo" class="max-w-xs">
            @foreach ($periodos as $p)
                <flux:select.option value="{{ $p['value'] }}">{{ $p['label'] }}</flux:select.option>
            @endforeach
        </flux:select>

        <flux:input type="date" label="Fecha de referencia" wire:model.live="fecha" class="max-w-xs" />
    </div>

    <div class="overflow-x-auto rounded-xl border border-huata-verde-100 px-3 dark:border-zinc-700 huata-cuadro">
        <flux:table>
            <flux:table.columns class="bg-white/80 dark:bg-zinc-800/80">
                <flux:table.column>Fecha</flux:table.column>
                <flux:table.column>Turno</flux:table.column>
                <flux:table.column>Litros entregados</flux:table.column>
                <flux:table.column>Litraje llegado (a pagar)</flux:table.column>
                <flux:table.column>Monto a pagar</flux:table.column>
                <flux:table.column>Pago</flux:table.column>
                <flux:table.column class="w-px whitespace-nowrap">Acciones</flux:table.column>
            </flux:table.columns>

            <flux:table.rows>
                @forelse ($recojos as $recojo)
                    <flux:table.row wire:key="recojo-{{ $recojo->id }}">
                        <flux:table.cell>{{ $recojo->fecha->format('d/m/Y') }}</flux:table.cell>
                        <flux:table.cell>{{ $recojo->turno?->nombre ?? '—' }}</flux:table.cell>
                        <flux:table.cell>{{ number_format((float) ($recojo->litros_entregados ?? $recojo->litros), 2) }} L</flux:table.cell>
                        <flux:table.cell>{{ number_format((float) $recojo->litros, 2) }} L</flux:table.cell>
                        <flux:table.cell>S/ {{ number_format((float) $recojo->total, 2) }}</flux:table.cell>
                        <flux:table.cell>
                            <flux:badge :color="$recojo->pagado ? 'green' : 'amber'" size="sm">
                                {{ $recojo->pagado ? 'Cobrado' : 'Pendiente' }}
                            </flux:badge>
                        </flux:table.cell>
                        <flux:table.cell>
                            <flux:button size="sm" variant="ghost" icon="eye" wire:click="verDetalle({{ $recojo->id }})">
                                Entrar
                            </flux:button>
                        </flux:table.cell>
                    </flux:table.row>
                @empty
                    <flux:table.row>
                        <flux:table.cell colspan="7" class="text-center text-zinc-500">
                            No tienes entregas registradas en este período.
                        </flux:table.cell>
                    </flux:table.row>
                @endforelse
            </flux:table.rows>
        </flux:table>
    </div>

    @if ($recojoSeleccionado)
        <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" wire:click.self="cerrarDetalle">
            <div class="w-full max-w-md rounded-xl bg-white p-6 shadow-lg dark:bg-zinc-800">
                <div class="flex items-center justify-between">
                    <flux:heading size="lg">Detalle de la entrega</flux:heading>
                    <flux:button size="sm" variant="ghost" icon="x-mark" wire:click="cerrarDetalle" />
                </div>

                <div class="mt-4 flex flex-col gap-3">
                    <div class="flex justify-between">
                        <flux:text>Fecha</flux:text>
                        <flux:text class="font-medium">{{ $recojoSeleccionado->fecha->format('d/m/Y') }}</flux:text>
                    </div>
                    <div class="flex justify-between">
                        <flux:text>Turno</flux:text>
                        <flux:text class="font-medium">{{ $recojoSeleccionado->turno?->nombre ?? '—' }}</flux:text>
                    </div>
                    <div class="flex justify-between">
                        <flux:text>Litros entregados</flux:text>
                        <flux:text class="font-medium">{{ number_format((float) ($recojoSeleccionado->litros_entregados ?? $recojoSeleccionado->litros), 2) }} L</flux:text>
                    </div>
                    <div class="flex justify-between">
                        <flux:text>Litraje llegado (a pagar)</flux:text>
                        <flux:text class="font-medium">{{ number_format((float) $recojoSeleccionado->litros, 2) }} L</flux:text>
                    </div>
                    <div class="flex justify-between">
                        <flux:text>Precio por litro</flux:text>
                        <flux:text class="font-medium">S/ {{ number_format((float) $recojoSeleccionado->precio_litro, 2) }}</flux:text>
                    </div>
                    <div class="flex justify-between">
                        <flux:text>Estado de la leche</flux:text>
                        <flux:text class="font-medium">{{ ucfirst($recojoSeleccionado->estado_leche) }}</flux:text>
                    </div>
                    <flux:separator />
                    <div class="flex justify-between text-lg font-bold">
                        <flux:text>Monto a pagar</flux:text>
                        <flux:text>S/ {{ number_format((float) $recojoSeleccionado->total, 2) }}</flux:text>
                    </div>
                    <div class="flex items-center justify-between">
                        <flux:text>Estado de pago</flux:text>
                        <flux:badge :color="$recojoSeleccionado->pagado ? 'green' : 'amber'">
                            {{ $recojoSeleccionado->pagado ? 'Cobrado' : 'Pendiente' }}
                        </flux:badge>
                    </div>
                </div>
            </div>
        </div>
    @endif
</div>

<?php

use App\Models\Acopiador;
use App\Models\Recojo;
use Livewire\Component;

new class extends Component
{
    public string $fecha = '';

    public function mount(): void
    {
        $this->fecha = now()->toDateString();
    }

    public function with(): array
    {
        $recojosPorProveedor = Recojo::whereDate('fecha', $this->fecha)->get()->groupBy('proveedor_id');

        return [
            'acopiadores' => Acopiador::with(['user', 'proveedores.user'])->orderBy('id')->get(),
            'recogidosHoyIds' => $recojosPorProveedor->keys(),
            'litrosHoyPorProveedor' => $recojosPorProveedor->map(fn ($recojos) => $recojos->sum('litros')),
        ];
    }
};
?>

<div class="flex flex-col gap-6">
    <div class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <x-ui.titulo titulo="Recojo de ruta">Estado del recojo de leche de cada acopiador, individualmente.</x-ui.titulo>
        <flux:input type="date" wire:model.live="fecha" class="max-w-xs" />
    </div>

    @forelse ($acopiadores as $acopiador)
        <div class="rounded-xl border border-huata-verde-100 dark:border-zinc-700 huata-cuadro">
            <div class="flex flex-wrap items-center justify-between gap-3 border-b border-huata-verde-100 px-4 py-3 dark:border-zinc-700">
                <flux:heading size="lg">{{ $acopiador->user->nombreCompleto() }}</flux:heading>
                <flux:badge color="zinc" size="sm">{{ $acopiador->ruta_nombre }}</flux:badge>
            </div>

            <div class="max-h-80 overflow-x-auto overflow-y-auto">
                <flux:table>
                    <flux:table.columns sticky class="bg-white dark:bg-zinc-800">
                        <flux:table.column>Nombres</flux:table.column>
                        <flux:table.column>Apellidos</flux:table.column>
                        <flux:table.column>Ubicación</flux:table.column>
                        <flux:table.column>Litros recogidos hoy</flux:table.column>
                        <flux:table.column>Estado</flux:table.column>
                    </flux:table.columns>
                    <flux:table.rows>
                        @forelse ($acopiador->proveedores as $proveedor)
                            <flux:table.row wire:key="recojo-prov-{{ $proveedor->id }}">
                                <flux:table.cell class="font-medium">{{ $proveedor->user->nombres }}</flux:table.cell>
                                <flux:table.cell>{{ $proveedor->user->apellidos }}</flux:table.cell>
                                <flux:table.cell class="text-lg font-bold text-huata-verde-800 dark:text-huata-verde-200">
                                    {{ $proveedor->finca_ubicacion ?: 'Sin registrar' }}
                                </flux:table.cell>
                                <flux:table.cell>{{ number_format((float) ($litrosHoyPorProveedor[$proveedor->id] ?? 0), 2) }} L</flux:table.cell>
                                <flux:table.cell>
                                    @if ($proveedor->bloqueado)
                                        <flux:badge color="red" size="sm">Bloqueado por calidad</flux:badge>
                                    @elseif ($recogidosHoyIds->contains($proveedor->id))
                                        <flux:badge color="green" size="sm">Ya recogido hoy</flux:badge>
                                    @else
                                        <flux:badge color="zinc" size="sm">Pendiente hoy</flux:badge>
                                    @endif
                                </flux:table.cell>
                            </flux:table.row>
                        @empty
                            <flux:table.row>
                                <flux:table.cell colspan="5" class="text-center text-zinc-500">Este acopiador no tiene proveedores asignados.</flux:table.cell>
                            </flux:table.row>
                        @endforelse
                    </flux:table.rows>
                </flux:table>
            </div>
        </div>
    @empty
        <div class="rounded-xl border border-huata-verde-100 p-6 text-center text-zinc-500 dark:border-zinc-700 huata-cuadro">
            No hay acopiadores registrados.
        </div>
    @endforelse
</div>

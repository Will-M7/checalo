<?php

use App\Enums\EstadoLeche;
use App\Models\EntregaPlanta;
use App\Models\Recojo;
use App\Models\Turno;
use Flux\Flux;
use Livewire\Component;

new class extends Component
{
    public ?int $turnoId = null;

    public ?int $recojoEditId = null;

    public string $editLitros = '';

    public string $editEstadoLeche = 'normal';

    public string $editEstadoOtro = '';

    public ?int $editTurnoId = null;

    public function mount(): void
    {
        $this->turnoId = Turno::where('es_produccion', false)->orderBy('hora_inicio')->value('id');
    }

    public function with(): array
    {
        $acopiador = auth()->user()->acopiador;

        $recojosPendientes = Recojo::query()
            ->where('acopiador_id', $acopiador?->id)
            ->whereDate('fecha', now()->toDateString())
            ->where('entregado', false);

        $totalProveedores = $acopiador?->proveedores()->count() ?? 0;
        $proveedoresRecogidos = (clone $recojosPendientes)->distinct('proveedor_id')->count('proveedor_id');

        // Igual que $recojosPendientes: una vez entregado a planta, ya no cuenta como "recogido
        // hoy" en la tabla de proveedores, para que el estado y los litros se restablezcan.
        $recojosHoyPorProveedor = Recojo::query()
            ->where('acopiador_id', $acopiador?->id)
            ->whereDate('fecha', now()->toDateString())
            ->where('entregado', false)
            ->get()
            ->groupBy('proveedor_id');

        return [
            'acopiador' => $acopiador,
            'turnos' => Turno::where('es_produccion', false)->orderBy('hora_inicio')->get(),
            'litrosTotal' => (clone $recojosPendientes)->sum('litros'),
            'totalProveedores' => $totalProveedores,
            'proveedoresNoRecogidos' => max($totalProveedores - $proveedoresRecogidos, 0),
            'entregasHoy' => EntregaPlanta::query()
                ->where('acopiador_id', $acopiador?->id)
                ->whereDate('fecha', now()->toDateString())
                ->latest()
                ->get(),
            'proveedores' => $acopiador ? $acopiador->proveedores()->with('user')->orderBy('id')->get() : collect(),
            'recogidosHoyIds' => $recojosHoyPorProveedor->keys(),
            'litrosHoyPorProveedor' => $recojosHoyPorProveedor->map(fn ($recojos) => $recojos->sum('litros')),
            'ultimoRecojoPorProveedor' => $recojosHoyPorProveedor->map(fn ($recojos) => $recojos->sortByDesc('id')->first()),
            'estados' => EstadoLeche::options(),
        ];
    }

    public function editar(int $recojoId): void
    {
        $recojo = Recojo::findOrFail($recojoId);

        $this->recojoEditId = $recojo->id;
        $this->editLitros = (string) $recojo->litros;
        $this->editEstadoLeche = $recojo->estado_leche;
        $this->editEstadoOtro = $recojo->estado_otro ?? '';
        $this->editTurnoId = $recojo->turno_id;
        $this->resetValidation();

        Flux::modal('recojo-editar-form')->show();
    }

    public function guardarEdicion(): void
    {
        $data = $this->validate([
            'editLitros' => 'required|numeric|min:0.01',
            'editEstadoLeche' => 'required|string',
            'editEstadoOtro' => $this->editEstadoLeche === 'otro' ? 'required|string|max:255' : 'nullable|string|max:255',
            'editTurnoId' => 'required|exists:turnos,id',
        ]);

        $recojo = Recojo::findOrFail($this->recojoEditId);

        $recojo->update([
            'turno_id' => $data['editTurnoId'],
            'litros' => $data['editLitros'],
            'estado_leche' => $data['editEstadoLeche'],
            'estado_otro' => $data['editEstadoOtro'] ?? '',
            'total' => round((float) $data['editLitros'] * (float) $recojo->precio_litro, 2),
        ]);

        Flux::modal('recojo-editar-form')->close();
        Flux::toast('Recojo actualizado correctamente.', variant: 'success');
        $this->reset(['recojoEditId', 'editLitros', 'editEstadoOtro']);
    }

    public function entregar(): void
    {
        $acopiador = auth()->user()->acopiador;

        abort_if(! $acopiador, 404);

        $recojos = Recojo::query()
            ->where('acopiador_id', $acopiador->id)
            ->whereDate('fecha', now()->toDateString())
            ->where('entregado', false)
            ->get();

        if ($recojos->isEmpty()) {
            Flux::modal('confirmar-entrega')->close();
            Flux::toast('No hay recojos pendientes de entregar.', variant: 'warning');

            return;
        }

        $totalProveedores = $acopiador->proveedores()->count();
        $proveedoresRecogidos = $recojos->pluck('proveedor_id')->unique()->count();

        EntregaPlanta::create([
            'acopiador_id' => $acopiador->id,
            'turno_id' => $this->turnoId ?? $recojos->first()->turno_id,
            'fecha' => now()->toDateString(),
            'litros_total' => $recojos->sum('litros'),
            'total_pagado' => 0,
            'total_deuda' => $recojos->sum('total'),
            'proveedores_no_recogidos' => max($totalProveedores - $proveedoresRecogidos, 0),
            'guia' => 'G-'.now()->format('YmdHis'),
        ]);

        Recojo::whereIn('id', $recojos->pluck('id'))->update(['entregado' => true]);

        Flux::modal('confirmar-entrega')->close();
        Flux::toast('Entrega registrada en planta.', variant: 'success');
    }
};
?>

<div class="flex flex-col gap-6">
    <x-ui.titulo titulo="Entrega a planta">Cierra el recojo del día y entrega la leche a planta.</x-ui.titulo>

    <div class="grid gap-4 sm:grid-cols-3">
        <div class="rounded-xl border border-huata-verde-100 p-5 dark:border-zinc-700 huata-cuadro">
            <flux:text>Litros por entregar</flux:text>
            <p class="mt-1 text-3xl font-bold text-huata-verde-700">{{ number_format((float) $litrosTotal, 2) }} L</p>
        </div>
        <div class="rounded-xl border border-huata-verde-100 p-5 dark:border-zinc-700 huata-cuadro">
            <flux:text>Proveedores asignados</flux:text>
            <p class="mt-1 text-3xl font-bold text-huata-verde-700">{{ $totalProveedores }}</p>
        </div>
        <div class="rounded-xl border border-huata-rojo-500/30 p-5 dark:border-zinc-700 huata-cuadro">
            <flux:text>No recogidos hoy</flux:text>
            <p class="mt-1 text-3xl font-bold {{ $proveedoresNoRecogidos > 0 ? 'text-huata-rojo-600' : 'text-huata-verde-700' }}">
                {{ $proveedoresNoRecogidos }}
            </p>
        </div>
    </div>

    @if ($turnos->count() > 1)
        <flux:select label="Turno a entregar" wire:model="turnoId" class="max-w-xs">
            @foreach ($turnos as $turno)
                <flux:select.option value="{{ $turno->id }}">{{ $turno->nombre }}</flux:select.option>
            @endforeach
        </flux:select>
    @endif

    <div>
        <flux:modal.trigger name="confirmar-entrega">
            <flux:button variant="primary" icon="truck">
                Entregar a planta
            </flux:button>
        </flux:modal.trigger>
    </div>

    @if ($entregasHoy->isNotEmpty())
        <div>
            <flux:heading size="lg" class="mb-3">Entregas de hoy</flux:heading>
            <div class="max-h-72 overflow-x-auto overflow-y-auto rounded-xl border border-huata-verde-100 dark:border-zinc-700">
                <flux:table>
                    <flux:table.columns sticky class="bg-white dark:bg-zinc-800">
                        <flux:table.column>Fecha</flux:table.column>
                        <flux:table.column>Guía</flux:table.column>
                        <flux:table.column>Litros</flux:table.column>
                        <flux:table.column>No recogidos</flux:table.column>
                    </flux:table.columns>
                    <flux:table.rows>
                        @foreach ($entregasHoy as $entrega)
                            <flux:table.row wire:key="entrega-{{ $entrega->id }}">
                                <flux:table.cell>{{ $entrega->created_at->format('d/m/Y H:i') }}</flux:table.cell>
                                <flux:table.cell>{{ $entrega->guia }}</flux:table.cell>
                                <flux:table.cell>{{ number_format((float) $entrega->litros_total, 2) }} L</flux:table.cell>
                                <flux:table.cell>{{ $entrega->proveedores_no_recogidos }}</flux:table.cell>
                            </flux:table.row>
                        @endforeach
                    </flux:table.rows>
                </flux:table>
            </div>
        </div>
    @endif

    <div>
        <flux:heading size="lg" class="mb-3">Proveedores de la ruta</flux:heading>
        <div class="overflow-x-auto rounded-xl border border-huata-verde-100 px-3 dark:border-zinc-700 huata-cuadro">
            <flux:table>
                <flux:table.columns class="bg-white/80 dark:bg-zinc-800/80">
                    <flux:table.column>Nombres</flux:table.column>
                    <flux:table.column>Apellidos</flux:table.column>
                    <flux:table.column>DNI/RUC</flux:table.column>
                    <flux:table.column>Celular</flux:table.column>
                    <flux:table.column>Ubicación</flux:table.column>
                    <flux:table.column>Fecha</flux:table.column>
                    <flux:table.column>Litros recogidos hoy</flux:table.column>
                    <flux:table.column>Estado</flux:table.column>
                    <flux:table.column class="w-px whitespace-nowrap">Acciones</flux:table.column>
                </flux:table.columns>
                <flux:table.rows>
                    @forelse ($proveedores as $proveedor)
                        <flux:table.row wire:key="entrega-proveedor-{{ $proveedor->id }}">
                            <flux:table.cell class="font-medium">{{ $proveedor->user->nombres }}</flux:table.cell>
                            <flux:table.cell>{{ $proveedor->user->apellidos }}</flux:table.cell>
                            <flux:table.cell>{{ $proveedor->user->documento ?? '—' }}</flux:table.cell>
                            <flux:table.cell>{{ $proveedor->user->celular ?? '—' }}</flux:table.cell>
                            <flux:table.cell class="text-lg font-bold text-huata-verde-800 dark:text-huata-verde-200">
                                {{ $proveedor->finca_ubicacion ?: 'Sin registrar' }}
                            </flux:table.cell>
                            <flux:table.cell>
                                {{ $ultimoRecojoPorProveedor->get($proveedor->id)?->created_at?->format('d/m/Y H:i') ?? now()->format('d/m/Y H:i') }}
                            </flux:table.cell>
                            <flux:table.cell>
                                {{ number_format((float) ($litrosHoyPorProveedor[$proveedor->id] ?? 0), 2) }} L
                            </flux:table.cell>
                            <flux:table.cell>
                                @if ($proveedor->bloqueado)
                                    <flux:badge color="red" size="sm">Bloqueado por calidad</flux:badge>
                                @elseif ($recogidosHoyIds->contains($proveedor->id))
                                    <flux:badge color="green" size="sm">Ya recogido hoy</flux:badge>
                                @else
                                    <flux:badge color="zinc" size="sm">Pendiente hoy</flux:badge>
                                @endif
                            </flux:table.cell>
                            <flux:table.cell>
                                @if ($ultimoRecojoPorProveedor->has($proveedor->id))
                                    <flux:button
                                        size="sm"
                                        variant="ghost"
                                        icon="pencil"
                                        wire:click="editar({{ $ultimoRecojoPorProveedor[$proveedor->id]->id }})"
                                    />
                                @endif
                            </flux:table.cell>
                        </flux:table.row>
                    @empty
                        <flux:table.row>
                            <flux:table.cell colspan="9" class="text-center text-zinc-500">No tienes proveedores asignados todavía.</flux:table.cell>
                        </flux:table.row>
                    @endforelse
                </flux:table.rows>
            </flux:table>
        </div>
    </div>

    <flux:modal name="recojo-editar-form" class="md:w-96">
        <form wire:submit="guardarEdicion" class="flex flex-col gap-6">
            <flux:heading size="lg">Editar recojo</flux:heading>

            <flux:input label="Litros" type="number" step="0.01" min="0" wire:model="editLitros" />

            <flux:select label="Turno" wire:model="editTurnoId">
                @foreach ($turnos as $turno)
                    <flux:select.option value="{{ $turno->id }}">{{ $turno->nombre }} ({{ $turno->hora_inicio }} - {{ $turno->hora_fin }})</flux:select.option>
                @endforeach
            </flux:select>

            <flux:select label="Estado de la leche" wire:model.live="editEstadoLeche">
                @foreach ($estados as $estado)
                    <flux:select.option value="{{ $estado['value'] }}">{{ $estado['label'] }}</flux:select.option>
                @endforeach
            </flux:select>

            @if ($editEstadoLeche === 'otro')
                <flux:input label="Describe el estado" wire:model="editEstadoOtro" />
            @endif

            <div class="flex justify-end gap-2">
                <flux:modal.close><flux:button variant="ghost">Cancelar</flux:button></flux:modal.close>
                <flux:button type="submit" variant="primary">Guardar</flux:button>
            </div>
        </form>
    </flux:modal>

    <x-confirm-modal
        name="confirmar-entrega"
        accion="entregar"
        titulo="¿Confirmas la entrega de leche a planta?"
        mensaje="Se cerrará el recojo del día y se registrará la entrega en planta."
        textoConfirmar="Entregar"
        variante="primary"
        icono="truck"
    />
</div>

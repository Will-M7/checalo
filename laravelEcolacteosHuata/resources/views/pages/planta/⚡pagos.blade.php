<?php

use App\Enums\MetodoPago;
use App\Enums\Periodo;
use App\Models\PagoProveedor;
use App\Models\Proveedor;
use App\Models\Recojo;
use Flux\Flux;
use Illuminate\Support\Carbon;
use Livewire\Component;

new class extends Component
{
    public string $periodo = 'semana';

    public string $fecha = '';

    public ?int $proveedorPagarId = null;

    public float $montoPagar = 0;

    public string $metodoPago = 'efectivo';

    public ?int $historialProveedorId = null;

    public function mount(): void
    {
        $this->fecha = now()->toDateString();
    }

    public function with(): array
    {
        $periodo = Periodo::from($this->periodo);
        [$inicio, $fin] = $periodo->rangoParaFecha(Carbon::parse($this->fecha));

        $filas = Proveedor::with('user')->get()->map(function (Proveedor $proveedor) use ($inicio, $fin) {
            // El litraje acumulado es el confirmado por el laboratorio (recojo.litros), que es
            // el mismo que determina el monto a pagar; no el litraje entregado originalmente.
            $totalLitros = Recojo::query()
                ->where('proveedor_id', $proveedor->id)
                ->whereBetween('fecha', [$inicio->toDateString(), $fin->toDateString()])
                ->sum('litros');

            $totalRecojos = Recojo::query()
                ->where('proveedor_id', $proveedor->id)
                ->whereBetween('fecha', [$inicio->toDateString(), $fin->toDateString()])
                ->sum('total');

            $totalPagado = PagoProveedor::query()
                ->where('proveedor_id', $proveedor->id)
                ->whereBetween('fecha', [$inicio->toDateString(), $fin->toDateString()])
                ->sum('monto');

            return [
                'proveedor' => $proveedor,
                'totalLitros' => (float) $totalLitros,
                'totalRecojos' => (float) $totalRecojos,
                'totalPagado' => (float) $totalPagado,
                'pendiente' => round((float) $totalRecojos - (float) $totalPagado, 2),
            ];
        });

        return [
            'periodos' => Periodo::cases(),
            'metodos' => MetodoPago::options(),
            'inicio' => $inicio,
            'fin' => $fin,
            'filas' => $filas,
            'historialProveedor' => $this->historialProveedorId
                ? Proveedor::with('user')->find($this->historialProveedorId)
                : null,
            'historialPagos' => $this->historialProveedorId
                ? PagoProveedor::where('proveedor_id', $this->historialProveedorId)->orderByDesc('fecha')->orderByDesc('id')->get()
                : collect(),
        ];
    }

    public function abrirPago(int $proveedorId, float $pendiente): void
    {
        $this->proveedorPagarId = $proveedorId;
        $this->montoPagar = max($pendiente, 0);
        $this->metodoPago = 'efectivo';
        Flux::modal('pago-form')->show();
    }

    public function verHistorialPago(int $proveedorId): void
    {
        $this->historialProveedorId = $proveedorId;
        Flux::modal('historial-pagos')->show();
    }

    public function pagar(): void
    {
        if (! $this->proveedorPagarId || $this->montoPagar <= 0) {
            return;
        }

        $periodo = Periodo::from($this->periodo);
        [$inicio, $fin] = $periodo->rangoParaFecha(Carbon::parse($this->fecha));

        PagoProveedor::create([
            'proveedor_id' => $this->proveedorPagarId,
            'fecha' => now()->toDateString(),
            'monto' => $this->montoPagar,
            'metodo_pago' => $this->metodoPago,
            'periodo' => $this->periodo,
        ]);

        Recojo::query()
            ->where('proveedor_id', $this->proveedorPagarId)
            ->whereBetween('fecha', [$inicio->toDateString(), $fin->toDateString()])
            ->update(['pagado' => true, 'metodo_pago' => $this->metodoPago]);

        Flux::modal('pago-form')->close();
        Flux::toast('Pago registrado correctamente.', variant: 'success');
    }
};
?>

<div class="flex flex-col gap-6">
    <x-ui.titulo titulo="Pago a proveedores">Revisa quiénes están pagados y quiénes faltan por período.</x-ui.titulo>

    <div class="flex flex-wrap items-end gap-3 rounded-xl border border-huata-verde-100 p-4 dark:border-zinc-700 huata-cuadro">
        <flux:select label="Período" wire:model.live="periodo" class="max-w-xs">
            @foreach ($periodos as $p)
                <flux:select.option value="{{ $p->value }}">{{ $p->label() }}</flux:select.option>
            @endforeach
        </flux:select>
        <flux:input type="date" label="Fecha de referencia" wire:model.live="fecha" />
        <flux:text class="text-sm text-zinc-500">
            Rango: {{ $inicio->format('d/m/Y') }} - {{ $fin->format('d/m/Y') }}
        </flux:text>
    </div>

    <div class="max-h-80 overflow-x-auto overflow-y-auto rounded-xl border border-huata-verde-100 px-3 dark:border-zinc-700 huata-cuadro">
        <flux:table>
            <flux:table.columns sticky class="bg-white/80 dark:bg-zinc-800/80">
                <flux:table.column>Proveedor</flux:table.column>
                <flux:table.column>Fecha</flux:table.column>
                <flux:table.column>Litraje de laboratorio</flux:table.column>
                <flux:table.column>Total recogido</flux:table.column>
                <flux:table.column>Pagado</flux:table.column>
                <flux:table.column>Pendiente</flux:table.column>
                <flux:table.column class="w-px whitespace-nowrap">Acciones</flux:table.column>
            </flux:table.columns>
            <flux:table.rows>
                @forelse ($filas as $fila)
                    <flux:table.row wire:key="pago-{{ $fila['proveedor']->id }}">
                        <flux:table.cell class="font-medium">{{ $fila['proveedor']->user->nombreCompleto() }}</flux:table.cell>
                        <flux:table.cell>{{ $inicio->format('d/m/Y') }} - {{ $fin->format('d/m/Y') }}</flux:table.cell>
                        <flux:table.cell>{{ number_format($fila['totalLitros'], 2) }} L</flux:table.cell>
                        <flux:table.cell>S/ {{ number_format($fila['totalRecojos'], 2) }}</flux:table.cell>
                        <flux:table.cell>S/ {{ number_format($fila['totalPagado'], 2) }}</flux:table.cell>
                        <flux:table.cell>
                            <flux:badge :color="$fila['pendiente'] > 0 ? 'amber' : 'green'" size="sm">
                                S/ {{ number_format($fila['pendiente'], 2) }}
                            </flux:badge>
                        </flux:table.cell>
                        <flux:table.cell class="whitespace-nowrap">
                            <div class="flex flex-nowrap items-center gap-2">
                                <flux:modal.trigger name="historial-pagos">
                                    <flux:button size="sm" variant="ghost" icon="table-cells" wire:click="verHistorialPago({{ $fila['proveedor']->id }})" />
                                </flux:modal.trigger>
                                @if ($fila['pendiente'] > 0)
                                    <flux:modal.trigger name="pago-form">
                                        <flux:button
                                            size="sm"
                                            variant="primary"
                                            wire:click="abrirPago({{ $fila['proveedor']->id }}, {{ $fila['pendiente'] }})"
                                        >
                                            Pagar
                                        </flux:button>
                                    </flux:modal.trigger>
                                @endif
                            </div>
                        </flux:table.cell>
                    </flux:table.row>
                @empty
                    <flux:table.row>
                        <flux:table.cell colspan="7" class="text-center text-zinc-500">No hay proveedores registrados.</flux:table.cell>
                    </flux:table.row>
                @endforelse
            </flux:table.rows>
        </flux:table>
    </div>

    <flux:modal name="historial-pagos" class="md:w-[36rem]">
        <div class="flex flex-col gap-5">
            <flux:heading size="lg">Historial de pagos &middot; {{ $historialProveedor?->user?->nombreCompleto() }}</flux:heading>

            <div class="max-h-80 overflow-x-auto overflow-y-auto rounded-xl border border-huata-verde-100 px-3 dark:border-zinc-700 huata-cuadro">
                <flux:table>
                    <flux:table.columns sticky class="bg-white/80 dark:bg-zinc-800/80">
                        <flux:table.column>Fecha</flux:table.column>
                        <flux:table.column>Monto</flux:table.column>
                        <flux:table.column>Método</flux:table.column>
                        <flux:table.column>Período</flux:table.column>
                    </flux:table.columns>
                    <flux:table.rows>
                        @forelse ($historialPagos as $pago)
                            <flux:table.row wire:key="pago-hist-{{ $pago->id }}">
                                <flux:table.cell>{{ $pago->fecha->format('d/m/Y') }}</flux:table.cell>
                                <flux:table.cell>S/ {{ number_format((float) $pago->monto, 2) }}</flux:table.cell>
                                <flux:table.cell>{{ \App\Enums\MetodoPago::from($pago->metodo_pago)->label() }}</flux:table.cell>
                                <flux:table.cell>{{ \App\Enums\Periodo::from($pago->periodo)->label() }}</flux:table.cell>
                            </flux:table.row>
                        @empty
                            <flux:table.row>
                                <flux:table.cell colspan="4" class="text-center text-zinc-500">Este proveedor todavía no tiene pagos registrados.</flux:table.cell>
                            </flux:table.row>
                        @endforelse
                    </flux:table.rows>
                </flux:table>
            </div>

            <div class="flex justify-end">
                <flux:modal.close>
                    <flux:button variant="ghost">Cerrar</flux:button>
                </flux:modal.close>
            </div>
        </div>
    </flux:modal>

    <flux:modal name="pago-form" class="md:w-96">
        <form wire:submit="pagar" class="flex flex-col gap-6">
            <flux:heading size="lg">Registrar pago</flux:heading>

            <flux:input type="number" step="0.01" min="0" label="Monto a pagar (S/)" wire:model="montoPagar" />

            <flux:select label="Método de pago" wire:model="metodoPago">
                @foreach ($metodos as $m)
                    <flux:select.option value="{{ $m['value'] }}">{{ $m['label'] }}</flux:select.option>
                @endforeach
            </flux:select>

            <div class="flex justify-end gap-2">
                <flux:modal.close>
                    <flux:button variant="ghost">Cancelar</flux:button>
                </flux:modal.close>
                <flux:button type="submit" variant="primary">Confirmar pago</flux:button>
            </div>
        </form>
    </flux:modal>
</div>

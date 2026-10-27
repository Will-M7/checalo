<?php

use App\Enums\EstadoPedidoWeb;
use App\Enums\MetodoPago;
use App\Models\PedidoWeb;
use Flux\Flux;
use Livewire\Component;
use Livewire\WithPagination;

new class extends Component
{
    use WithPagination;

    /** Estado que se está viendo: pendiente, confirmado, cancelado o todos. */
    public string $filtro = 'pendiente';

    public ?int $confirmarId = null;

    public string $metodoPago = 'efectivo';

    public function with(): array
    {
        $pedidos = PedidoWeb::query()
            ->with('items')
            ->when($this->filtro !== 'todos', fn ($q) => $q->where('estado', $this->filtro))
            ->latest()
            ->paginate(10);

        return [
            'pedidos' => $pedidos,
            'pendientes' => PedidoWeb::query()->where('estado', EstadoPedidoWeb::Pendiente)->count(),
            'metodos' => MetodoPago::options(),
            'pedidoPorConfirmar' => $this->confirmarId ? PedidoWeb::with('items')->find($this->confirmarId) : null,
        ];
    }

    public function updatedFiltro(): void
    {
        $this->resetPage();
    }

    public function pedirConfirmar(int $id): void
    {
        $this->confirmarId = $id;
        $this->metodoPago = PedidoWeb::findOrFail($id)->metodo_pago;
        Flux::modal('confirmar-pedido')->show();
    }

    /**
     * Confirma el pedido registrando la venta y abre la boleta.
     */
    public function confirmar(): void
    {
        $pedido = PedidoWeb::findOrFail($this->confirmarId);

        try {
            $venta = $pedido->confirmarComoVenta(auth()->user(), MetodoPago::from($this->metodoPago));
        } catch (RuntimeException $e) {
            Flux::modal('confirmar-pedido')->close();
            Flux::toast($e->getMessage(), variant: 'danger');

            return;
        }

        $this->reset('confirmarId');
        Flux::modal('confirmar-pedido')->close();
        Flux::toast('Pedido confirmado. Boleta '.$venta->numero_boleta.' lista.', variant: 'success');

        $this->js("window.open('".route('vendedor.ventas.boleta', $venta->id)."', '_blank')");
    }

    public function cancelar(int $id): void
    {
        $pedido = PedidoWeb::findOrFail($id);

        if ($pedido->estado !== EstadoPedidoWeb::Pendiente) {
            Flux::toast('Ese pedido ya fue atendido.', variant: 'warning');

            return;
        }

        $pedido->update(['estado' => EstadoPedidoWeb::Cancelado]);
        Flux::toast('Pedido cancelado.', variant: 'success');
    }
};
?>

<div class="flex flex-col gap-6">
    <div class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <x-ui.titulo titulo="Compras por web">Compras que los clientes hacen en la página de inicio, sin iniciar sesión. Verifica el pago (Yape, Plin o efectivo) y confirma para generar la boleta.</x-ui.titulo>
        <flux:badge :color="$pendientes > 0 ? 'amber' : 'zinc'">{{ $pendientes }} {{ $pendientes === 1 ? 'pendiente' : 'pendientes' }}</flux:badge>
    </div>

    <flux:radio.group wire:model.live="filtro" variant="segmented" class="max-w-lg">
        <flux:radio value="pendiente" label="Pendientes" />
        <flux:radio value="confirmado" label="Confirmados" />
        <flux:radio value="cancelado" label="Cancelados" />
        <flux:radio value="todos" label="Todos" />
    </flux:radio.group>

    <div class="overflow-x-auto rounded-xl border border-huata-verde-100 px-3 dark:border-zinc-700 huata-cuadro">
        <flux:table :paginate="$pedidos">
            <flux:table.columns class="bg-white/80 dark:bg-zinc-800/80">
                <flux:table.column>Fecha</flux:table.column>
                <flux:table.column>Cliente</flux:table.column>
                <flux:table.column>Pedido</flux:table.column>
                <flux:table.column>Total</flux:table.column>
                <flux:table.column>Pago</flux:table.column>
                <flux:table.column>Estado</flux:table.column>
                <flux:table.column class="w-px whitespace-nowrap"></flux:table.column>
            </flux:table.columns>
            <flux:table.rows>
                @forelse ($pedidos as $pedido)
                    <flux:table.row wire:key="pedido-{{ $pedido->id }}">
                        <flux:table.cell class="whitespace-nowrap">{{ $pedido->created_at->timezone(config('app.zona_horaria_local'))->format('d/m/Y H:i') }}</flux:table.cell>
                        <flux:table.cell>
                            <div class="font-medium">{{ $pedido->nombre }}</div>
                            @if ($pedido->celular)
                                <a href="https://wa.me/{{ strlen(preg_replace('/\D+/', '', $pedido->celular)) === 9 ? '51' : '' }}{{ preg_replace('/\D+/', '', $pedido->celular) }}" target="_blank" rel="noopener" class="text-sm text-zinc-500 underline">{{ $pedido->celular }}</a>
                            @endif
                        </flux:table.cell>
                        <flux:table.cell>
                            <ul class="flex flex-col gap-0.5 text-sm">
                                @foreach ($pedido->items as $item)
                                    <li>{{ $item->cantidad }} × {{ $item->producto }}</li>
                                @endforeach
                            </ul>
                        </flux:table.cell>
                        <flux:table.cell class="whitespace-nowrap">S/ {{ number_format((float) $pedido->total, 2) }}</flux:table.cell>
                        <flux:table.cell>
                            <div class="text-sm">{{ ucfirst($pedido->metodo_pago) }}</div>
                            @if ($pedido->codigo_operacion)
                                <div class="text-xs text-zinc-500">Op. {{ $pedido->codigo_operacion }}</div>
                            @endif
                        </flux:table.cell>
                        <flux:table.cell><flux:badge :color="$pedido->estado->color()" size="sm">{{ $pedido->estado->label() }}</flux:badge></flux:table.cell>
                        <flux:table.cell>
                            @if ($pedido->estado === \App\Enums\EstadoPedidoWeb::Pendiente)
                                <div class="flex gap-2">
                                    <flux:button size="sm" variant="primary" icon="check" wire:click="pedirConfirmar({{ $pedido->id }})">Confirmar y facturar</flux:button>
                                    <flux:button size="sm" variant="ghost" icon="x-mark" wire:click="cancelar({{ $pedido->id }})">Cancelar</flux:button>
                                </div>
                            @elseif ($pedido->venta_id)
                                <flux:button size="sm" variant="ghost" icon="document-text" :href="route('vendedor.ventas.boleta', $pedido->venta_id)" target="_blank">Boleta</flux:button>
                            @endif
                        </flux:table.cell>
                    </flux:table.row>
                @empty
                    <flux:table.row><flux:table.cell colspan="7" class="text-center text-zinc-500">No hay pedidos en esta lista.</flux:table.cell></flux:table.row>
                @endforelse
            </flux:table.rows>
        </flux:table>
    </div>

    <flux:modal name="confirmar-pedido" class="md:w-[28rem]">
        <form wire:submit="confirmar" class="flex flex-col gap-6">
            <div>
                <flux:heading size="lg">Confirmar y generar boleta</flux:heading>
                @if ($pedidoPorConfirmar)
                    <flux:subheading>Compra de {{ $pedidoPorConfirmar->nombre }}. Total S/ {{ number_format((float) $pedidoPorConfirmar->total, 2) }}. Se descontará del stock.</flux:subheading>
                    <ul class="mt-3 flex flex-col gap-1 rounded-lg bg-zinc-50 p-3 text-sm dark:bg-zinc-800">
                        @foreach ($pedidoPorConfirmar->items as $item)
                            <li class="flex justify-between gap-3"><span>{{ $item->cantidad }} × {{ $item->producto }}</span><span class="tabular-nums">S/ {{ number_format((float) $item->subtotal, 2) }}</span></li>
                        @endforeach
                    </ul>
                @endif
            </div>

            @if ($pedidoPorConfirmar?->codigo_operacion)
                <flux:text class="text-sm">El cliente indicó el N.º de operación <strong>{{ $pedidoPorConfirmar->codigo_operacion }}</strong>. Compruébalo en tu {{ ucfirst($pedidoPorConfirmar->metodo_pago) }} antes de confirmar.</flux:text>
            @endif

            <flux:select label="Método de pago" wire:model="metodoPago">
                @foreach ($metodos as $m)
                    <flux:select.option value="{{ $m['value'] }}">{{ $m['label'] }}</flux:select.option>
                @endforeach
            </flux:select>

            <div class="flex justify-end gap-2">
                <flux:modal.close><flux:button variant="ghost">Volver</flux:button></flux:modal.close>
                <flux:button type="submit" variant="primary" icon="document-check">Registrar venta</flux:button>
            </div>
        </form>
    </flux:modal>
</div>

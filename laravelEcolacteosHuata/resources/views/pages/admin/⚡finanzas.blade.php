<?php

use App\Models\{MovimientoFinanciero, ReceptorPago, User};
use App\Services\Finanzas\FinanzasService;
use App\Services\Payments\Dinero;
use Flux\Flux;
use Illuminate\Support\Str;
use Livewire\Component;
use Livewire\WithPagination;

new class extends Component {
    use \App\Livewire\Concerns\FiltrosListado;
    public string $busqueda = '';
    use WithPagination;

    public string $busquedaCliente = '';
    public ?int $clienteSeleccionado = null;

    public function verCliente(int $id): void { $this->clienteSeleccionado = $id; $this->resetPage('pedidosClientePage'); }
    public function updatedBusquedaCliente(): void { $this->resetPage('clientesPage'); }
    public function updatedFiltros(): void { $this->resetPage(); $this->resetPage('clientesPage'); }

    public string $desde = '';
    public string $hasta = '';
    public string $vendedor = '';
    public string $receptor = '';
    public string $tipo = '';
    public ?int $ingreso = null;
    public string $importe = '';
    public string $referencia = '';
    public string $clave = '';
    public bool $recibido = false;

    public function limpiarFiltros(): void
    {
        $this->filtros = []; $this->busqueda = ''; $this->busquedaCliente = ''; $this->resetPage('clientesPage');
        $this->desde = $this->hasta = $this->vendedor = $this->receptor = $this->tipo = '';
        $this->resetPage();
    }

    public function updated($property): void
    {
        if (in_array($property, ['desde', 'hasta', 'vendedor', 'receptor', 'tipo'], true)) $this->resetPage();
    }

    public function with(): array
    {
        $this->validate(['desde' => 'nullable|date_format:Y-m-d', 'hasta' => 'nullable|date_format:Y-m-d',
            'vendedor' => 'nullable|integer', 'receptor' => 'nullable|integer', 'tipo' => 'nullable|string|max:40']);
        $consultas = app(\App\Services\Finanzas\ConsultasFinanzas::class);
        $query = $consultas->movimientos(auth('web')->user(), ['busqueda' => $this->busqueda, 'estado' => $this->filtro('estado'),
            'desde' => $this->desde, 'hasta' => $this->hasta, 'vendedor' => $this->vendedor, 'receptor' => $this->receptor, 'tipo' => $this->tipo]);
        $activity = $consultas->actividad($query);
        return ['resumen' => app(FinanzasService::class)->resumen(), 'actividad' => $activity,
            'movimientos' => $query->latest('id')->paginate(25), 'usuarios' => User::orderBy('id')->get()->keyBy('id'),
            'receptores' => ReceptorPago::orderBy('id')->get()->keyBy('id'),
            'clientes' => $consultas->clientes(auth('web')->user(), ['busquedaCliente' => $this->busquedaCliente, 'saldoCliente' => $this->filtro('saldoCliente'), 'vendedorCliente' => $this->filtro('vendedorCliente')])->paginate(25, ['*'], 'clientesPage'),
            'pedidosCliente' => $this->clienteSeleccionado ? $consultas->pedidosCliente(auth('web')->user(), $this->clienteSeleccionado)->paginate(15, ['*'], 'pedidosClientePage') : null,
            'tipos' => MovimientoFinanciero::distinct()->pluck('tipo')];
    }

    public function prepararRendicion(int $id): void
    {
        abort_unless(auth('web')->user()?->puedePantalla('admin.finanzas'), 403);
        $movement = MovimientoFinanciero::findOrFail($id);
        if ($movement->pendienteRendir() <= 0) {
            $this->addError('finanzas', 'Ese ingreso no tiene fondos pendientes de rendición.'); return;
        }
        $this->resetValidation();
        $this->ingreso = $id;
        $this->importe = Dinero::decimal($movement->pendienteRendir());
        $this->referencia = '';
        $this->recibido = false;
        $this->clave = (string) Str::uuid();
        Flux::modal('rendicion')->show();
    }

    public function rendir(): void
    {
        $this->validate(['ingreso' => 'required|integer', 'importe' => ['required', 'regex:/^\d{1,7}(?:\.\d{1,2})?$/'],
            'referencia' => 'required|string|min:10|max:180', 'clave' => 'required|uuid', 'recibido' => 'accepted']);
        app(FinanzasService::class)->rendir(auth('web')->user(), $this->ingreso, Dinero::minor($this->importe), $this->clave, $this->referencia);
        Flux::modal('rendicion')->close();
        Flux::toast('Entrega a la empresa registrada una sola vez.', variant: 'success');
    }
};
?>

<div class="flex flex-col gap-6">
    <x-ui.titulo titulo="Finanzas">Control operativo de fondos y obligaciones.</x-ui.titulo>
    <div class="flex flex-wrap items-center gap-3"><flux:input wire:model.live.debounce.300ms="busqueda" icon="magnifying-glass" placeholder="Buscar origen o referencia..." class="max-w-sm" /><x-ui.filtros :activos="$filtros"><flux:select label="Conciliación" wire:model.live="filtros.estado"><flux:select.option value="">Todos</flux:select.option><flux:select.option value="confirmado">Confirmado</flux:select.option><flux:select.option value="no_conciliado">No conciliado</flux:select.option></flux:select></x-ui.filtros></div>

    <flux:text>Posición registrada actual, sin filtros. No certifica saldos bancarios. Los ingresos recibidos en cuentas receptoras permanecen bajo custodia hasta registrar su entrega a Huata.</flux:text>
    <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        @foreach(['saldos_clientes' => 'Saldos pendientes de clientes', 'custodia' => 'Fondos bajo custodia / por rendir', 'empresa' => 'Fondos netos entregados a Huata', 'disponible' => 'Disponible tras obligaciones'] as $key => $label)
            <section class="huata-cuadro rounded-xl border border-huata-verde-100 p-5 dark:border-zinc-700"><flux:text>{{ $label }}</flux:text><p class="text-2xl font-bold">S/ {{ ($resumen[$key] < 0 ? '-' : '').Dinero::decimal(abs($resumen[$key])) }}</p></section>
        @endforeach
    </div>
    @if($resumen['faltante_cobertura'])<flux:text>Fondos aún por cubrir en la empresa para cubrir las obligaciones: S/ {{ Dinero::decimal($resumen['faltante_cobertura']) }}. La custodia no se considera disponible.</flux:text>@endif
    <flux:text>Obligaciones con proveedores por recojos entregados y pendientes: S/ {{ Dinero::decimal($resumen['proveedores_pendientes']) }}. Abonos de saldo sin ingreso conciliado: S/ {{ Dinero::decimal($resumen['abonos_no_conciliados']) }}.</flux:text>
    <details class="huata-cuadro rounded-xl border p-4"><summary class="cursor-pointer font-semibold">Conciliación y alcance de las cifras</summary>
        <flux:text>Este registro comienza con la activación del módulo. No se inventaron movimientos anteriores. Ventas sin asiento: {{ $resumen['ventas_sin_registro'] }}; pagos a proveedores sin asiento: {{ $resumen['pagos_proveedor_sin_registro'] }}; pagos Core PAID sin asiento: {{ $resumen['pagos_core_sin_registro'] }}. Diferencia de saldos heredados sin conciliación: S/ {{ ($resumen['saldo_no_conciliado'] < 0 ? '-' : '').Dinero::decimal(abs($resumen['saldo_no_conciliado'])) }}.</flux:text>
        <flux:text>El disponible es conservador: entregas registradas menos egresos confirmados y todos los saldos de clientes y obligaciones registradas por recojos entregados pendientes de pago. El flujo existente de pagos a proveedores no identifica cuenta de origen: esos egresos se descuentan conservadoramente, sin afirmar su procedencia bancaria. No incorpora saldos bancarios iniciales, facturas futuras ni compras sin confirmación de pago. Los métodos declarados sin conciliación se identifican como no conciliados. Una recarga no constituye una venta; la rendición tampoco genera otro ingreso externo.</flux:text>
    </details>
    <div class="huata-cuadro flex flex-wrap items-end gap-3 rounded-xl border p-4">
        <flux:input type="date" label="Desde" wire:model.live="desde" />
        <flux:input type="date" label="Hasta" wire:model.live="hasta" />
        <flux:select label="Vendedor" wire:model.live="vendedor"><flux:select.option value="">Todos</flux:select.option>@foreach($usuarios as $u)<flux:select.option :value="$u->id">{{ $u->nombreCompleto() }}</flux:select.option>@endforeach</flux:select>
        <flux:select label="Receptor" wire:model.live="receptor"><flux:select.option value="">Todos</flux:select.option>@foreach($receptores as $r)<flux:select.option :value="$r->id">{{ $r->nombre_receptor }}</flux:select.option>@endforeach</flux:select>
        <flux:select label="Tipo" wire:model.live="tipo"><flux:select.option value="">Todos</flux:select.option>@foreach($tipos as $t)<flux:select.option :value="$t">{{ str_replace('_', ' ', $t) }}</flux:select.option>@endforeach</flux:select>
    </div>
    <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
        @foreach(['recaudado' => 'Total recaudado', 'ventas' => 'Ingresos por ventas', 'recargas' => 'Recargas confirmadas', 'rendido' => 'Entregado a la empresa', 'egresos' => 'Egresos confirmados'] as $key => $label)
            <section class="huata-cuadro rounded-xl border p-4"><flux:text>{{ $label }} · filtro actual</flux:text><p class="text-xl font-bold">S/ {{ Dinero::decimal($actividad[$key]) }}</p></section>
        @endforeach
    </div>
    @error('finanzas')<flux:text role="alert">{{ $message }}</flux:text>@enderror
    <div class="huata-cuadro overflow-x-auto rounded-xl border p-3">
        <flux:table>
            <flux:table.columns><flux:table.column>Fecha / operación</flux:table.column><flux:table.column>Origen / estado</flux:table.column><flux:table.column>Vendedor / custodio</flux:table.column><flux:table.column>Receptor</flux:table.column><flux:table.column>Importe</flux:table.column><flux:table.column>Acción</flux:table.column></flux:table.columns>
            <flux:table.rows>
                @forelse($movimientos as $m)
                    <flux:table.row :key="$m->id">
                        <flux:table.cell>{{ $m->created_at->format('d/m/Y H:i') }}<br>{{ str_replace('_', ' ', $m->tipo) }} #{{ $m->id }}</flux:table.cell>
                        <flux:table.cell>{{ $m->origen }}<br>{{ str_replace('_', ' ', $m->estado) }}@if($m->originado_por_id) · origen #{{ $m->originado_por_id }}@endif</flux:table.cell>
                        <flux:table.cell>{{ $usuarios->get($m->vendedor_id)?->nombreCompleto() ?? '—' }}<br>{{ $usuarios->get($m->custodio_id)?->nombreCompleto() ?? '—' }}</flux:table.cell>
                        <flux:table.cell>{{ $receptores->get($m->receptor_pago_id)?->nombre_receptor ?? '—' }}<br>{{ $m->metodo }}</flux:table.cell>
                        <flux:table.cell>S/ {{ Dinero::decimal($m->amount_minor) }}</flux:table.cell>
                        <flux:table.cell>@if($m->custodia_minor > 0 && $m->pendienteRendir() > 0)<flux:button size="sm" wire:click="prepararRendicion({{ $m->id }})">Registrar entrega</flux:button><flux:text>Pendiente S/ {{ Dinero::decimal($m->pendienteRendir()) }}</flux:text>@endif</flux:table.cell>
                    </flux:table.row>
                @empty<flux:table.row><flux:table.cell colspan="6">Sin movimientos para este filtro.</flux:table.cell></flux:table.row>@endforelse
            </flux:table.rows>
        </flux:table>
    </div>
    {{ $movimientos->links() }}
    <section class="huata-cuadro rounded-xl border p-4 flex flex-col gap-4">
        <flux:heading size="lg">Clientes y saldos</flux:heading>
        <flux:text>El saldo pertenece al comprador. Consulta quién atendió cada pedido en su historial.</flux:text>
        <div class="flex flex-wrap gap-3">
            <flux:input wire:model.live.debounce.300ms="busquedaCliente" label="Buscar cliente o contacto" />
            <x-ui.filtros :activos="$filtros">
                <flux:select label="Saldo del cliente" wire:model.live="filtros.saldoCliente"><flux:select.option value="">Todos</flux:select.option><flux:select.option value="positivo">Con saldo</flux:select.option><flux:select.option value="cero">Sin saldo</flux:select.option></flux:select>
                <flux:select label="Atendido por vendedor" wire:model.live="filtros.vendedorCliente"><flux:select.option value="">Todos</flux:select.option>@foreach($usuarios as $u)<flux:select.option :value="$u->id">{{ $u->nombreCompleto() }}</flux:select.option>@endforeach</flux:select>
            </x-ui.filtros>
        </div>
        <div class="overflow-x-auto"><flux:table><flux:table.columns>
            @foreach(['Cliente / contacto', 'Saldo actual', 'Recargado / consumido', 'Pedidos / pendientes / pagados', 'Relaciones por pedido'] as $t)<flux:table.column>{{ $t }}</flux:table.column>@endforeach
        </flux:table.columns><flux:table.rows>
            @forelse($clientes as $c)<flux:table.row :key="'cliente-'.$c->id">
                <flux:table.cell>{{ $c->nombreCompleto() }}<br>{{ $c->celular }}<br>{{ $c->email }}</flux:table.cell>
                <flux:table.cell>S/ {{ Dinero::decimal((int) $c->saldo_minor) }}</flux:table.cell>
                <flux:table.cell>S/ {{ Dinero::decimal((int) $c->recargado_minor) }} / S/ {{ Dinero::decimal(-(int) $c->consumido_negativo_minor) }}</flux:table.cell>
                <flux:table.cell>{{ $c->pedidos_total }} / {{ $c->pedidos_pendientes }} / {{ $c->pedidos_pagados }}</flux:table.cell>
                <flux:table.cell><flux:button size="sm" wire:click="verCliente({{ $c->id }})">Ver pedidos y vendedores</flux:button></flux:table.cell>
            </flux:table.row>@empty<flux:table.row><flux:table.cell colspan="5">Sin clientes para este filtro.</flux:table.cell></flux:table.row>@endforelse
        </flux:table.rows></flux:table></div>
        {{ $clientes->links() }}
        @if($pedidosCliente)
            <flux:heading>Historial del cliente #{{ $clienteSeleccionado }}</flux:heading>
            @forelse($pedidosCliente as $p)<flux:text>Pedido #{{ $p->id }} · {{ $p->created_at->format('d/m/Y') }} · {{ $p->vendedor?->nombreCompleto() ?? ($p->receptor_snapshot['vendedor_nombre'] ?? 'Sin registro') }} · S/ {{ $p->total }} · {{ $p->estado->label() }} · {{ $p->pago_estado }}</flux:text>@empty<flux:text>Sin pedidos.</flux:text>@endforelse
            {{ $pedidosCliente->links() }}
        @endif
    </section>
    <flux:modal name="rendicion" class="md:w-96"><form wire:submit="rendir" class="flex flex-col gap-4">
        <x-ui.titulo titulo="Entrega de fondos">Ingreso #{{ $ingreso }}. Registra únicamente una entrega realmente recibida por Huata.</x-ui.titulo>
        <flux:input label="Importe entregado (S/)" wire:model="importe" type="number" min="0.01" step="0.01" required />
        <flux:input label="Referencia / constancia de recepción" wire:model="referencia" minlength="10" maxlength="180" required />
        <flux:checkbox wire:model="recibido" label="Confirmo que la empresa recibió estos fondos." />
        <flux:button type="submit" variant="primary" wire:loading.attr="disabled">Confirmar entrega</flux:button>
    </form></flux:modal>
</div>

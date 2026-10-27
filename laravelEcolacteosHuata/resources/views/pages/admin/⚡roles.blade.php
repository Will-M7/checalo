<?php
use App\Authorization\Autorizacion;
use App\Authorization\Pantallas;
use App\Models\RolDinamico;
use Livewire\Component;
use Flux\Flux;

new class extends Component {
    use \App\Livewire\Concerns\FiltrosListado;
    public string $busqueda = '';
    public ?int $rolId = null;
    public string $nombre = '';
    public string $codigo = '';
    public bool $activo = true;
    public array $pantallas = [];
    public function editar(?int $id = null): void {
        abort_unless(auth('web')->user()?->puedePantalla('admin.roles'), 403);
        $r = $id ? RolDinamico::findOrFail($id) : null;
        $this->rolId = $r?->id; $this->nombre = $r?->nombre ?? ''; $this->codigo = $r?->codigo ?? '';
        $this->activo = $r?->activo ?? true; $this->pantallas = $r?->pantallas ?? [];
        $this->resetValidation(); Flux::modal('rol-funciones')->show();
    }
    public function guardar(): void {
        Autorizacion::guardarRol(auth('web')->user(), $this->rolId, $this->nombre, $this->codigo, $this->activo, $this->pantallas);
        Flux::modal('rol-funciones')->close(); Flux::toast('Rol actualizado.', variant: 'success');
    }
    public function with(): array { return ['roles' => RolDinamico::query()->when($this->busqueda !== '', fn ($q) => $q->where(fn ($t) => $t->where('nombre', 'like', '%'.$this->busqueda.'%')->orWhere('codigo', 'like', '%'.$this->busqueda.'%')))->when($this->filtro('estado') !== '', fn ($q) => $q->where('activo', $this->filtro('estado') === '1'))->when($this->filtro('pantalla'), fn ($q, $v) => $q->whereJsonContains('pantallas', $v))->orderBy('id')->get(), 'catalogo' => Pantallas::todas()]; }
}; ?>
<div class="flex flex-col gap-6">
    <div class="flex flex-wrap items-center justify-between gap-3">
        <x-ui.titulo titulo="Roles y funciones">Una asignación para web y Android.</x-ui.titulo>
        <div class="flex gap-2"><flux:button wire:click="$dispatch('abrir-distribuciones')">Distribuciones</flux:button><flux:button variant="primary" icon="plus" wire:click="editar">Crear rol</flux:button></div>
    </div>
    <div class="flex flex-wrap items-center gap-3"><flux:input wire:model.live.debounce.300ms="busqueda" icon="magnifying-glass" placeholder="Buscar rol o identificador..." class="max-w-sm" /><x-ui.filtros :activos="$filtros"><flux:select label="Estado" wire:model.live="filtros.estado"><flux:select.option value="">Todos</flux:select.option><flux:select.option value="1">Activos</flux:select.option><flux:select.option value="0">Inactivos</flux:select.option></flux:select><flux:select label="Pantalla asignada" wire:model.live="filtros.pantalla"><flux:select.option value="">Todas</flux:select.option>@foreach($catalogo as $id => $c)<flux:select.option :value="$id">{{ $c["label"] }}</flux:select.option>@endforeach</flux:select></x-ui.filtros></div>

    <div class="overflow-x-auto rounded-xl border border-huata-verde-100 px-3 dark:border-zinc-700 huata-cuadro">
        <flux:table><flux:table.columns><flux:table.column>Rol</flux:table.column><flux:table.column>Funciones</flux:table.column><flux:table.column>Estado</flux:table.column><flux:table.column>Acciones</flux:table.column></flux:table.columns>
        <flux:table.rows>@forelse($roles as $r)<flux:table.row wire:key="rol-{{ $r->id }}">
            <flux:table.cell>{{ $r->nombre }}</flux:table.cell>
            <flux:table.cell>{{ count($r->pantallas) }}</flux:table.cell>
            <flux:table.cell><flux:badge size="sm" :color="$r->activo ? 'green' : 'zinc'">{{ $r->activo ? 'Activo' : 'Inactivo' }}</flux:badge></flux:table.cell>
            <flux:table.cell><flux:button size="sm" wire:click="editar({{ $r->id }})">Editar funciones</flux:button></flux:table.cell>
        </flux:table.row>@empty<flux:table.row><flux:table.cell colspan="4">No se encontraron roles.</flux:table.cell></flux:table.row>@endforelse</flux:table.rows></flux:table>
    </div>
    <livewire:distribuciones-roles />
    <flux:modal name="rol-funciones" class="md:w-[40rem]">
        <form wire:submit="guardar" class="flex flex-col gap-5">
            <flux:heading size="lg">Rol y funciones</flux:heading>
            <flux:input label="Nombre" wire:model="nombre" />
            <flux:input label="Identificador estable" wire:model="codigo" :readonly="$rolId !== null" />
            <flux:checkbox label="Rol activo" wire:model="activo" />
            <flux:text>Las funciones ordinarias de cada pantalla se habilitan juntas. Se conservan las reglas de negocio y propiedad.</flux:text>
            <div class="grid gap-3 sm:grid-cols-2">@foreach($catalogo as $id => $p)
                <flux:checkbox wire:key="func-{{ $id }}" wire:model="pantallas" :value="$id" :label="$p['area'].' · '.$p['label']" :description="$p['android'] ? 'Web y Android' : 'Solo web: no existe pantalla Android'" />
            @endforeach</div>
            <flux:error name="pantallas" />
            <div class="flex justify-end gap-2"><flux:modal.close><flux:button variant="ghost">Cancelar</flux:button></flux:modal.close><flux:button variant="primary" type="submit">Guardar</flux:button></div>
        </form>
    </flux:modal>
</div>

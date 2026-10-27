<?php

use App\Enums\Rol;
use App\Models\Acopiador;
use App\Models\Proveedor;
use App\Models\User;
use Flux\Flux;
use Livewire\Attributes\Validate;
use Livewire\Component;
use Livewire\WithPagination;

new class extends Component
{
    use \App\Livewire\Concerns\FiltrosListado;
    use WithPagination;

    public string $busqueda = '';

    public ?int $userId = null;

    public string $nombreUsuario = '';

    /** @var array<int, string> */
    #[Validate('present|array')]
    public array $rolesSeleccionados = [];


    public string $finca_ubicacion = '';

    public string $finca_referencia = '';

    public ?float $latitud = null;

    public ?float $longitud = null;

    public ?int $proveedorAcopiadorId = null;

    public function with(): array
    {
        return [
            'rolesFiltro' => \App\Models\RolDinamico::orderBy('nombre')->get(),
            'usuarios' => User::query()->with('rolesDinamicos')
                ->when($this->filtro('rol'), fn ($q, $rol) => $q->whereHas('rolesDinamicos', fn ($r) => $r->where('codigo', $rol)))
                ->when($this->filtro('estado') !== '', fn ($q) => $q->where('activo', $this->filtro('estado') === '1'))
                ->when($this->filtro('cantidad') === 'varios', fn ($q) => $q->has('rolesDinamicos', '>', 1))
                ->when($this->filtro('cantidad') === 'ninguno', fn ($q) => $q->doesntHave('rolesDinamicos'))
                ->when($this->busqueda, fn ($q) => $q->where(function ($q) {
                    $q->where('nombres', 'like', "%{$this->busqueda}%")
                        ->orWhere('apellidos', 'like', "%{$this->busqueda}%")
                        ->orWhere('usuario', 'like', "%{$this->busqueda}%")
                        ->orWhere('documento', 'like', "%{$this->busqueda}%");
                }))
                ->orderBy('nombres')
                ->paginate(10),
            'roles' => \App\Authorization\Autorizacion::catalogoRoles(auth('web')->user(), $this->userId ? User::find($this->userId) : null)->where('codigo', '!=', 'tomar_asistencia'),
            'acopiadores' => Acopiador::with('user')->orderBy('id')->get(),
        ];
    }

    public function abrirAsignar(int $userId): void
    {
        $user = User::findOrFail($userId);

        $this->userId = $user->id;
        $this->nombreUsuario = $user->nombreCompleto();
        $this->rolesSeleccionados = $user->rolesDinamicos()->pluck('codigo')->all();

        $proveedor = $user->proveedor;
        $this->finca_ubicacion = $proveedor?->finca_ubicacion ?? '';
        $this->finca_referencia = $proveedor?->finca_referencia ?? '';
        $this->latitud = $proveedor?->latitud ? (float) $proveedor->latitud : null;
        $this->longitud = $proveedor?->longitud ? (float) $proveedor->longitud : null;
        $this->proveedorAcopiadorId = $proveedor?->acopiador_id;

        $this->resetValidation();
        Flux::modal('asignar-rol-form')->show();
    }

    public function guardar(): void
    {
        $rules = ['rolesSeleccionados' => 'present|array'];

        if (in_array(Rol::Proveedor->value, $this->rolesSeleccionados, true)) {
            $rules['finca_ubicacion'] = 'required|string|max:255';
            $rules['proveedorAcopiadorId'] = 'required|exists:acopiadores,id';
        }

        $data = $this->validate($rules);

        $user = User::findOrFail($this->userId);
        $codigos = $data['rolesSeleccionados'];
        \App\Authorization\Autorizacion::asignar(auth('web')->user(), $user, $codigos);
        $roles = collect($codigos)->map(fn ($v) => Rol::tryFrom($v))->filter();

        if ($roles->contains(Rol::Acopiador)) {
            Acopiador::firstOrCreate(['user_id' => $user->id], ['ruta_nombre' => '']);
        }

        if ($roles->contains(Rol::Proveedor)) {
            Proveedor::guardarConUbicacion($user->id, [
                'finca_ubicacion' => $data['finca_ubicacion'] ?? '',
                'finca_referencia' => $this->finca_referencia,
                'latitud' => $this->latitud,
                'longitud' => $this->longitud,
                'acopiador_id' => $data['proveedorAcopiadorId'] ?? null,
            ]);
        }

        Flux::modal('asignar-rol-form')->close();
        Flux::toast('Roles actualizados correctamente.', variant: 'success');
    }
};
?>

<div class="flex flex-col gap-6">
    <x-ui.titulo titulo="Asignar rol a usuarios">Un usuario puede tener uno o varios roles a la vez.</x-ui.titulo>

    <div class="flex flex-wrap items-center gap-3"><flux:input
        wire:model.live.debounce.300ms="busqueda"
        icon="magnifying-glass"
        placeholder="Buscar por nombre, usuario o documento..."
        class="max-w-sm"
    /><x-ui.filtros :activos="$filtros"><flux:select label="Rol actual" wire:model.live="filtros.rol"><flux:select.option value="">Todos</flux:select.option>@foreach($rolesFiltro as $r)<flux:select.option :value="$r->codigo">{{ $r->nombre }}{{ $r->activo ? "" : " (Inactivo)" }}</flux:select.option>@endforeach</flux:select><flux:select label="Estado" wire:model.live="filtros.estado"><flux:select.option value="">Todos</flux:select.option><flux:select.option value="1">Activos</flux:select.option><flux:select.option value="0">Inactivos</flux:select.option></flux:select><flux:select label="Asignaciones" wire:model.live="filtros.cantidad"><flux:select.option value="">Todos</flux:select.option><flux:select.option value="varios">Varios roles</flux:select.option><flux:select.option value="ninguno">Sin roles</flux:select.option></flux:select></x-ui.filtros></div>

    <div class="overflow-x-auto rounded-xl border border-huata-verde-100 px-3 dark:border-zinc-700 huata-cuadro">
        <flux:table :paginate="$usuarios">
            <flux:table.columns class="bg-white/80 dark:bg-zinc-800/80">
                <flux:table.column>Nombre</flux:table.column>
                <flux:table.column>Rol</flux:table.column>
                <flux:table.column>Documento</flux:table.column>
                <flux:table.column>Usuario</flux:table.column>
                <flux:table.column>Estado</flux:table.column>
                <flux:table.column class="w-px whitespace-nowrap">Acciones</flux:table.column>
            </flux:table.columns>

            <flux:table.rows>
                @forelse ($usuarios as $user)
                    <flux:table.row wire:key="asignar-rol-{{ $user->id }}">
                        <flux:table.cell class="font-medium">{{ $user->nombreCompleto() }}</flux:table.cell>
                        <flux:table.cell>
                            <div class="flex flex-wrap gap-1">
                                @foreach ($user->rolesDinamicos as $r)
                                    <flux:badge size="sm" :color="$r->codigo === $user->rol?->value ? 'green' : 'zinc'">{{ $r->label() }}</flux:badge>
                                @endforeach
                                @if ($user->toma_asistencia)
                                    <flux:badge size="sm" color="blue" icon="calendar-days">Toma asistencia</flux:badge>
                                @endif
                            </div>
                        </flux:table.cell>
                        <flux:table.cell>{{ $user->documento }}</flux:table.cell>
                        <flux:table.cell>{{ $user->usuario }}</flux:table.cell>
                        <flux:table.cell>
                            <flux:badge :color="$user->activo ? 'green' : 'zinc'" size="sm">
                                {{ $user->activo ? 'Activo' : 'Inactivo' }}
                            </flux:badge>
                        </flux:table.cell>
                        <flux:table.cell>
                            <flux:modal.trigger name="asignar-rol-form">
                                <flux:button size="sm" variant="primary" icon="identification" wire:click="abrirAsignar({{ $user->id }})">
                                    Asignar
                                </flux:button>
                            </flux:modal.trigger>
                        </flux:table.cell>
                    </flux:table.row>
                @empty
                    <flux:table.row>
                        <flux:table.cell colspan="6" class="text-center text-zinc-500">
                            {{ $busqueda !== '' || count($filtros) ? 'No se encontraron usuarios con estos filtros.' : 'No hay usuarios registrados todavía.' }}
                        </flux:table.cell>
                    </flux:table.row>
                @endforelse
            </flux:table.rows>
        </flux:table>
    </div>

    <flux:modal name="asignar-rol-form" class="md:w-[28rem]">
        <form wire:submit="guardar" class="flex flex-col gap-6">
            <div>
                <flux:heading size="lg">Asignar roles</flux:heading>
                <flux:subheading>{{ $nombreUsuario }}</flux:subheading>
            </div>



            <div>
                <flux:text class="mb-2 text-xs text-zinc-500">
                    Selecciona uno o varios roles. Vuelve a marcar un rol ya seleccionado para quitárselo.
                </flux:text>
                <div class="grid gap-2 sm:grid-cols-2">
                    @foreach ($roles as $rol)
                        <flux:checkbox
                            wire:key="rolopt-{{ $rol->value }}"
                            value="{{ $rol->value }}"
                            wire:model="rolesSeleccionados"
                            :label="$rol->label().($rol->activo ? '' : ' (Inactivo)')"
                        />
                    @endforeach
                </div>
                @error('rolesSeleccionados')
                    <flux:text class="mt-1 text-huata-rojo-600">{{ $message }}</flux:text>
                @enderror
            </div>

            @if (in_array(\App\Enums\Rol::Proveedor->value, $rolesSeleccionados, true))
                <div class="flex flex-col gap-4 rounded-lg border border-huata-verde-100 p-3">
                    <flux:select label="Acopiador" wire:model="proveedorAcopiadorId" placeholder="Selecciona un acopiador...">
                        @foreach ($acopiadores as $a)
                            <flux:select.option value="{{ $a->id }}" wire:key="prov-acop-opt-{{ $a->id }}">{{ $a->user->nombreCompleto() }}</flux:select.option>
                        @endforeach
                    </flux:select>
                    <flux:heading size="sm">Ubicación de la finca</flux:heading>
                    <flux:input label="Ubicación / dirección" wire:model="finca_ubicacion" placeholder="Comunidad de Huata" />
                    <flux:input label="Referencia" wire:model="finca_referencia" placeholder="A 200m de la escuela..." />
                    <x-mapa-ubicacion :lat="$latitud" :lng="$longitud" :editable="true" lat-model="latitud" lng-model="longitud" height="200px" />
                </div>
            @endif

            <div class="flex justify-end gap-2">
                <flux:modal.close>
                    <flux:button variant="ghost">Cancelar</flux:button>
                </flux:modal.close>
                <flux:button type="submit" variant="primary">Guardar</flux:button>
            </div>
        </form>
    </flux:modal>
</div>

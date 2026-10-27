<?php

use App\Models\Turno;
use Flux\Flux;
use Livewire\Component;

new class extends Component
{
    // --- Formulario de turno de ruta ---
    public ?int $turnoRutaId = null;

    public string $turnoRutaNombre = '';

    public string $turnoRutaInicio = '';

    public string $turnoRutaFin = '';

    // --- Formulario de turno de producción ---
    public ?int $turnoProduccionId = null;

    public string $turnoProduccionNombre = '';

    public string $turnoProduccionInicio = '';

    public string $turnoProduccionFin = '';

    public ?int $eliminarTurnoRutaId = null;

    public ?int $eliminarTurnoProduccionId = null;

    public function with(): array
    {
        return [
            'turnosRuta' => Turno::where('es_produccion', false)->orderBy('hora_inicio')->get(),
            'turnosProduccion' => Turno::where('es_produccion', true)->orderBy('hora_inicio')->get(),
        ];
    }

    // --- Turnos de ruta ---
    public function nuevoTurnoRuta(): void
    {
        $this->reset(['turnoRutaId', 'turnoRutaNombre', 'turnoRutaInicio', 'turnoRutaFin']);
        Flux::modal('turno-ruta-form')->show();
    }

    public function editarTurnoRuta(int $id): void
    {
        $turno = Turno::findOrFail($id);
        $this->turnoRutaId = $turno->id;
        $this->turnoRutaNombre = $turno->nombre;
        $this->turnoRutaInicio = $turno->hora_inicio;
        $this->turnoRutaFin = $turno->hora_fin;
        Flux::modal('turno-ruta-form')->show();
    }

    public function guardarTurnoRuta(): void
    {
        $data = $this->validate([
            'turnoRutaNombre' => 'required|string|max:255',
            'turnoRutaInicio' => 'required',
            'turnoRutaFin' => 'required',
        ]);

        Turno::updateOrCreate(['id' => $this->turnoRutaId], [
            'nombre' => $data['turnoRutaNombre'],
            'hora_inicio' => $data['turnoRutaInicio'],
            'hora_fin' => $data['turnoRutaFin'],
            'es_produccion' => false,
        ]);

        Flux::modal('turno-ruta-form')->close();
        Flux::toast('Turno de ruta guardado.', variant: 'success');
    }

    public function pedirEliminarTurnoRuta(int $id): void
    {
        $this->eliminarTurnoRutaId = $id;
        Flux::modal('confirmar-eliminar-turno-ruta')->show();
    }

    public function eliminarTurnoRuta(): void
    {
        Turno::whereKey($this->eliminarTurnoRutaId)->delete();
        Flux::modal('confirmar-eliminar-turno-ruta')->close();
        Flux::toast('Turno de ruta eliminado.', variant: 'success');
    }

    // --- Turnos de producción ---
    public function nuevoTurnoProduccion(): void
    {
        $this->reset(['turnoProduccionId', 'turnoProduccionNombre', 'turnoProduccionInicio', 'turnoProduccionFin']);
        Flux::modal('turno-produccion-form')->show();
    }

    public function editarTurnoProduccion(int $id): void
    {
        $turno = Turno::findOrFail($id);
        $this->turnoProduccionId = $turno->id;
        $this->turnoProduccionNombre = $turno->nombre;
        $this->turnoProduccionInicio = $turno->hora_inicio;
        $this->turnoProduccionFin = $turno->hora_fin;
        Flux::modal('turno-produccion-form')->show();
    }

    public function guardarTurnoProduccion(): void
    {
        $data = $this->validate([
            'turnoProduccionNombre' => 'required|string|max:255',
            'turnoProduccionInicio' => 'required',
            'turnoProduccionFin' => 'required',
        ]);

        Turno::updateOrCreate(['id' => $this->turnoProduccionId], [
            'nombre' => $data['turnoProduccionNombre'],
            'hora_inicio' => $data['turnoProduccionInicio'],
            'hora_fin' => $data['turnoProduccionFin'],
            'es_produccion' => true,
        ]);

        Flux::modal('turno-produccion-form')->close();
        Flux::toast('Turno de producción guardado.', variant: 'success');
    }

    public function pedirEliminarTurnoProduccion(int $id): void
    {
        $this->eliminarTurnoProduccionId = $id;
        Flux::modal('confirmar-eliminar-turno-produccion')->show();
    }

    public function eliminarTurnoProduccion(): void
    {
        Turno::whereKey($this->eliminarTurnoProduccionId)->delete();
        Flux::modal('confirmar-eliminar-turno-produccion')->close();
        Flux::toast('Turno de producción eliminado.', variant: 'success');
    }
};
?>

<div class="flex flex-col gap-6">
    <x-ui.titulo titulo="Turnos de rutas y turnos de producción">Horarios que usan los acopiadores en sus rutas y los que puede elegir Producción para cada lote.</x-ui.titulo>

    <div class="grid gap-6 lg:grid-cols-2">
        <div class="rounded-xl border border-huata-verde-100 p-5 dark:border-zinc-700 huata-cuadro">
            <div class="mb-3 flex flex-wrap items-center justify-between gap-3">
                <div>
                    <flux:heading size="lg" class="flex items-center gap-2">
                        <flux:icon name="map" class="size-5 text-huata-verde-600" />
                        Turnos de ruta
                    </flux:heading>
                    <flux:subheading>Horarios de recojo de los acopiadores.</flux:subheading>
                </div>
                <flux:modal.trigger name="turno-ruta-form">
                    <flux:button size="sm" icon="plus" wire:click="nuevoTurnoRuta">
                        Agregar turno
                    </flux:button>
                </flux:modal.trigger>
            </div>

            <div class="max-h-72 overflow-x-auto overflow-y-auto rounded-lg border border-huata-verde-100 dark:border-zinc-700">
                <flux:table>
                    <flux:table.columns sticky class="bg-white dark:bg-zinc-800">
                        <flux:table.column>Nombre</flux:table.column>
                        <flux:table.column>Hora de inicio</flux:table.column>
                        <flux:table.column>Hora de fin</flux:table.column>
                        <flux:table.column>Acciones</flux:table.column>
                    </flux:table.columns>
                    <flux:table.rows>
                        @forelse ($turnosRuta as $turno)
                            <flux:table.row wire:key="turno-ruta-{{ $turno->id }}">
                                <flux:table.cell class="font-medium">{{ $turno->nombre }}</flux:table.cell>
                                <flux:table.cell>{{ $turno->hora_inicio }}</flux:table.cell>
                                <flux:table.cell>{{ $turno->hora_fin }}</flux:table.cell>
                                <flux:table.cell>
                                    <div class="flex gap-1">
                                        <flux:modal.trigger name="turno-ruta-form">
                                            <flux:button size="sm" variant="ghost" icon="pencil" wire:click="editarTurnoRuta({{ $turno->id }})" />
                                        </flux:modal.trigger>
                                        <flux:modal.trigger name="confirmar-eliminar-turno-ruta">
                                            <flux:button size="sm" variant="ghost" icon="trash" wire:click="pedirEliminarTurnoRuta({{ $turno->id }})" />
                                        </flux:modal.trigger>
                                    </div>
                                </flux:table.cell>
                            </flux:table.row>
                        @empty
                            <flux:table.row>
                                <flux:table.cell colspan="4" class="text-center text-zinc-500">No hay turnos de ruta configurados.</flux:table.cell>
                            </flux:table.row>
                        @endforelse
                    </flux:table.rows>
                </flux:table>
            </div>
        </div>

        <div class="rounded-xl border border-huata-amarillo-400/40 p-5 dark:border-zinc-700 huata-cuadro">
            <div class="mb-3 flex flex-wrap items-center justify-between gap-3">
                <div>
                    <flux:heading size="lg" class="flex items-center gap-2">
                        <flux:icon name="beaker" class="size-5 text-huata-amarillo-600" />
                        Turnos de producción
                    </flux:heading>
                    <flux:subheading>Disponibles para cualquier lote de producción.</flux:subheading>
                </div>
                <flux:modal.trigger name="turno-produccion-form">
                    <flux:button size="sm" icon="plus" wire:click="nuevoTurnoProduccion">
                        Agregar turno
                    </flux:button>
                </flux:modal.trigger>
            </div>

            <div class="max-h-72 overflow-x-auto overflow-y-auto rounded-lg border border-huata-verde-100 dark:border-zinc-700">
                <flux:table>
                    <flux:table.columns sticky class="bg-white dark:bg-zinc-800">
                        <flux:table.column>Nombre</flux:table.column>
                        <flux:table.column>Hora de inicio</flux:table.column>
                        <flux:table.column>Hora de fin</flux:table.column>
                        <flux:table.column>Acciones</flux:table.column>
                    </flux:table.columns>
                    <flux:table.rows>
                        @forelse ($turnosProduccion as $turno)
                            <flux:table.row wire:key="turno-produccion-{{ $turno->id }}">
                                <flux:table.cell class="font-medium">{{ $turno->nombre }}</flux:table.cell>
                                <flux:table.cell>{{ $turno->hora_inicio }}</flux:table.cell>
                                <flux:table.cell>{{ $turno->hora_fin }}</flux:table.cell>
                                <flux:table.cell>
                                    <div class="flex gap-1">
                                        <flux:modal.trigger name="turno-produccion-form">
                                            <flux:button size="sm" variant="ghost" icon="pencil" wire:click="editarTurnoProduccion({{ $turno->id }})" />
                                        </flux:modal.trigger>
                                        <flux:modal.trigger name="confirmar-eliminar-turno-produccion">
                                            <flux:button size="sm" variant="ghost" icon="trash" wire:click="pedirEliminarTurnoProduccion({{ $turno->id }})" />
                                        </flux:modal.trigger>
                                    </div>
                                </flux:table.cell>
                            </flux:table.row>
                        @empty
                            <flux:table.row>
                                <flux:table.cell colspan="4" class="text-center text-zinc-500">Todavía no hay turnos de producción.</flux:table.cell>
                            </flux:table.row>
                        @endforelse
                    </flux:table.rows>
                </flux:table>
            </div>
        </div>
    </div>

    <flux:modal name="turno-ruta-form" class="md:w-80">
        <form wire:submit="guardarTurnoRuta" class="flex flex-col gap-6">
            <flux:heading size="lg">{{ $turnoRutaId ? 'Editar turno de ruta' : 'Nuevo turno de ruta' }}</flux:heading>
            <flux:input label="Nombre" wire:model="turnoRutaNombre" placeholder="Mañana, Tarde..." />
            <flux:input label="Hora de inicio" type="time" wire:model="turnoRutaInicio" />
            <flux:input label="Hora de fin" type="time" wire:model="turnoRutaFin" />
            <div class="flex justify-end gap-2">
                <flux:modal.close><flux:button variant="ghost">Cancelar</flux:button></flux:modal.close>
                <flux:button type="submit" variant="primary">Guardar</flux:button>
            </div>
        </form>
    </flux:modal>

    <flux:modal name="turno-produccion-form" class="md:w-80">
        <form wire:submit="guardarTurnoProduccion" class="flex flex-col gap-6">
            <flux:heading size="lg">{{ $turnoProduccionId ? 'Editar turno de producción' : 'Nuevo turno de producción' }}</flux:heading>
            <flux:input label="Nombre" wire:model="turnoProduccionNombre" placeholder="Mañana, Tarde..." />
            <flux:input label="Hora de inicio" type="time" wire:model="turnoProduccionInicio" />
            <flux:input label="Hora de fin" type="time" wire:model="turnoProduccionFin" />
            <div class="flex justify-end gap-2">
                <flux:modal.close><flux:button variant="ghost">Cancelar</flux:button></flux:modal.close>
                <flux:button type="submit" variant="primary">Guardar</flux:button>
            </div>
        </form>
    </flux:modal>

    <x-confirm-modal
        name="confirmar-eliminar-turno-ruta"
        accion="eliminarTurnoRuta"
        titulo="¿Eliminar este turno de ruta?"
        mensaje="Esta acción no se puede deshacer."
    />

    <x-confirm-modal
        name="confirmar-eliminar-turno-produccion"
        accion="eliminarTurnoProduccion"
        titulo="¿Eliminar este turno de producción?"
        mensaje="Esta acción no se puede deshacer."
    />
</div>

<?php

use App\Models\Acopiador;
use App\Models\Proveedor;
use App\Models\Recojo;
use App\Models\Ruta;
use App\Models\Turno;
use Flux\Flux;
use Illuminate\Support\Collection;
use Livewire\Component;

new class extends Component
{
    public ?int $rutaAsignarId = null;

    public string $buscarProveedorAsignar = '';

    public ?int $rutaTurnoId = null;

    public ?int $rutaFormId = null;

    public string $rutaFormNombre = '';

    public ?int $rutaFormAcopiadorId = null;

    public bool $rutaFormActiva = true;

    public ?int $eliminarRutaId = null;

    public ?int $rutaDetalleId = null;

    /**
     * Ruta elegida en el mapa del recorrido: se dibuja resaltada con sus paradas numeradas.
     */
    public ?int $rutaMapaId = null;

    public ?int $ubicandoProveedorId = null;

    public string $buscarProveedorMapa = '';

    public function mount(): void
    {
        $this->rutaMapaId = Ruta::query()->orderBy('nombre')->value('id');
    }

    public function with(): array
    {
        $rutaAsignar = $this->rutaAsignarId
            ? Ruta::with(['proveedores', 'acopiador'])->find($this->rutaAsignarId)
            : null;

        $proveedoresAsignar = collect();

        if ($rutaAsignar) {
            $idsAsignados = $rutaAsignar->proveedores->pluck('id');

            // Candidatos: los proveedores propios de este acopiador, más los que todavía no
            // están asignados a ninguna ruta (aunque sean de otro acopiador), más los que ya
            // están asignados a ESTA ruta (aunque sean de otro acopiador) — para que, una vez
            // asignado un proveedor "Sin ruta" de otro acopiador, siga apareciendo aquí y se
            // pueda quitar en vez de desaparecer de la lista.
            $proveedoresAsignar = Proveedor::with(['user', 'acopiador.user'])
                ->where(fn ($q) => $q->where('acopiador_id', $rutaAsignar->acopiador_id)
                    ->orWhereDoesntHave('rutas')
                    ->orWhereHas('rutas', fn ($r) => $r->where('rutas.id', $rutaAsignar->id)))
                ->when($this->buscarProveedorAsignar, fn ($q) => $q->whereHas('user', fn ($u) => $u
                    ->where('nombres', 'like', "%{$this->buscarProveedorAsignar}%")
                    ->orWhere('apellidos', 'like', "%{$this->buscarProveedorAsignar}%")))
                ->get()
                ->sortBy(fn (Proveedor $p) => $p->user->nombres)
                ->map(fn (Proveedor $p) => [
                    'proveedor' => $p,
                    'asignado' => $idsAsignados->contains($p->id),
                    'deOtroAcopiador' => $p->acopiador_id !== $rutaAsignar->acopiador_id,
                ])
                ->values();
        }

        $rutaTurno = $this->rutaTurnoId
            ? Ruta::with(['turnos', 'acopiador'])->find($this->rutaTurnoId)
            : null;

        $turnosAsignar = collect();

        if ($rutaTurno) {
            $idsAsignados = $rutaTurno->turnos->pluck('id');

            // Los turnos de ruta son una lista compartida (no pertenecen a un acopiador
            // específico), así que cualquier ruta puede usar cualquiera de ellos.
            $turnosAsignar = Turno::where('es_produccion', false)
                ->orderBy('hora_inicio')
                ->get()
                ->map(fn (Turno $t) => ['turno' => $t, 'asignado' => $idsAsignados->contains($t->id)])
                ->values();
        }

        $rutaMapa = $this->rutaMapa();

        return [
            'rutas' => Ruta::with(['acopiador.user', 'turnos', 'proveedores'])->orderBy('nombre')->get(),
            'acopiadores' => Acopiador::with('user')->get(),
            'proveedoresRecogidosHoy' => Recojo::whereDate('fecha', now()->toDateString())->pluck('proveedor_id')->unique(),
            'rutaDetalle' => $this->rutaDetalleId
                ? Ruta::with(['acopiador.user', 'turnos', 'proveedores.user'])->find($this->rutaDetalleId)
                : null,
            'rutaAsignar' => $rutaAsignar,
            'proveedoresAsignar' => $proveedoresAsignar,
            'rutaTurno' => $rutaTurno,
            'turnosAsignar' => $turnosAsignar,
            'datosMapaGeneral' => $this->datosMapaGeneral(),
            'rutaMapa' => $rutaMapa,
            'proveedoresMapa' => $this->proveedoresMapa($rutaMapa),
            'ubicandoProveedor' => $this->ubicandoProveedorId ? Proveedor::with('user')->find($this->ubicandoProveedorId) : null,
        ];
    }

    private function rutaMapa(): ?Ruta
    {
        return $this->rutaMapaId ? Ruta::with(['acopiador.user', 'proveedores.user'])->find($this->rutaMapaId) : null;
    }

    /**
     * Todos los proveedores para el panel junto al mapa: primero los de la ruta elegida en su orden
     * de recojo (con su número de parada) y después el resto por nombre.
     *
     * @return Collection<int, array{proveedor: Proveedor, parada: int|null}>
     */
    private function proveedoresMapa(?Ruta $rutaMapa): Collection
    {
        $orden = $rutaMapa?->proveedores->pluck('id')->flip() ?? collect();

        return Proveedor::with(['user', 'rutas'])
            ->when($this->buscarProveedorMapa, fn ($q) => $q->whereHas('user', fn ($u) => $u
                ->where('nombres', 'like', "%{$this->buscarProveedorMapa}%")
                ->orWhere('apellidos', 'like', "%{$this->buscarProveedorMapa}%")))
            ->get()
            ->map(fn (Proveedor $proveedor) => [
                'proveedor' => $proveedor,
                'parada' => $orden->has($proveedor->id) ? $orden[$proveedor->id] + 1 : null,
            ])
            ->sortBy([
                fn (array $a, array $b) => ($a['parada'] ?? PHP_INT_MAX) <=> ($b['parada'] ?? PHP_INT_MAX),
                fn (array $a, array $b) => $a['proveedor']->user->nombreCompleto() <=> $b['proveedor']->user->nombreCompleto(),
            ])
            ->values();
    }

    /**
     * Datos del mapa general (<x-mapa-rutas-general>): el camino de cada ruta, los proveedores
     * con ubicación (indicando en qué rutas están), la ruta elegida con el orden de sus paradas y
     * el proveedor que se está ubicando.
     *
     * @return array{rutaSel: int|null, ubicandoId: int|null, paradas: array<int, int>, rutas: array<int, array{id: int, nombre: string, acopiador: string, activa: bool, rango: int, trazo: array<int, array{0: float, 1: float}>}>, proveedores: array<int, array{id: int, nombre: string, ubicacion: string, lat: float, lng: float, rutaIds: array<int, int>}>}
     */
    private function datosMapaGeneral(): array
    {
        return [
            'rutaSel' => $this->rutaMapaId,
            'ubicandoId' => $this->ubicandoProveedorId,
            'paradas' => $this->rutaMapa()?->proveedores->pluck('id')->all() ?? [],
            'rutas' => Ruta::with('acopiador.user')->orderBy('nombre')->get()
                ->map(fn (Ruta $ruta) => [
                    'id' => $ruta->id,
                    'nombre' => $ruta->nombre,
                    'acopiador' => $ruta->acopiador->user->nombreCompleto(),
                    'activa' => $ruta->activa,
                    'rango' => $ruta->rango,
                    'trazo' => $ruta->trazo ?? [],
                ])->values()->all(),
            'proveedores' => Proveedor::with(['user', 'rutas' => fn ($q) => $q->select('rutas.id')])
                ->whereNotNull('latitud')
                ->whereNotNull('longitud')
                ->get()
                ->map(fn (Proveedor $proveedor) => [
                    'id' => $proveedor->id,
                    'nombre' => $proveedor->user->nombreCompleto(),
                    'ubicacion' => $proveedor->finca_ubicacion ?: 'Sin ubicación registrada',
                    'lat' => (float) $proveedor->latitud,
                    'lng' => (float) $proveedor->longitud,
                    'rutaIds' => $proveedor->rutas->pluck('id')->all(),
                ])->values()->all(),
        ];
    }

    private function refrescarMapa(): void
    {
        $this->dispatch('mapa-rutas-general', datos: $this->datosMapaGeneral());
    }

    /**
     * Guarda el camino del camioncito de una ruta, trazado en el mapa general, como lista de puntos [lat, lng].
     *
     * @param  array<int, array{0: float, 1: float}>  $puntos
     */
    public function guardarTrazoRuta(int $rutaId, array $puntos): void
    {
        $ruta = Ruta::findOrFail($rutaId);

        validator(['puntos' => $puntos], [
            'puntos' => 'array|max:1000',
            'puntos.*' => 'array|size:2',
            'puntos.*.0' => 'required|numeric|between:-90,90',
            'puntos.*.1' => 'required|numeric|between:-180,180',
        ])->validate();

        $ruta->update([
            'trazo' => collect($puntos)->map(fn (array $punto) => [(float) $punto[0], (float) $punto[1]])->values()->all(),
        ]);

        $ruta->numerarParadasPorTrazo();

        $this->refrescarMapa();
        Flux::toast('Camino de la ruta guardado. Las paradas se numeraron de inicio a fin.', variant: 'success');
    }

    public function updatedRutaMapaId(): void
    {
        $this->ubicandoProveedorId = null;
        $this->refrescarMapa();
    }

    /**
     * Genera el camino de la ruta elegida uniendo en línea recta las paradas en su orden de recojo.
     */
    public function trazarPorParadas(): void
    {
        $ruta = Ruta::with('proveedores')->findOrFail($this->rutaMapaId);

        $ruta->update([
            'trazo' => $ruta->proveedores
                ->filter(fn (Proveedor $proveedor) => $proveedor->latitud !== null && $proveedor->longitud !== null)
                ->map(fn (Proveedor $proveedor) => [(float) $proveedor->latitud, (float) $proveedor->longitud])
                ->values()
                ->all(),
        ]);

        $this->refrescarMapa();
        Flux::toast('Camino generado según el orden de las paradas.', variant: 'success');
    }

    public function limpiarTrazo(): void
    {
        Ruta::findOrFail($this->rutaMapaId)->update(['trazo' => null]);

        $this->refrescarMapa();
        Flux::toast('Camino borrado.', variant: 'success');
    }

    /**
     * Sube o baja una parada de la ruta elegida en el orden de recojo.
     */
    public function moverParada(int $proveedorId, int $direccion): void
    {
        $ruta = Ruta::with('proveedores')->findOrFail($this->rutaMapaId);

        $ids = $ruta->proveedores->pluck('id')->values();
        $posicion = $ids->search($proveedorId);
        $destino = $posicion === false ? false : $posicion + ($direccion < 0 ? -1 : 1);

        if ($destino === false || $destino < 0 || $destino >= $ids->count()) {
            return;
        }

        $ordenados = $ids->all();
        [$ordenados[$posicion], $ordenados[$destino]] = [$ordenados[$destino], $ordenados[$posicion]];

        foreach ($ordenados as $indice => $id) {
            $ruta->proveedores()->updateExistingPivot($id, ['orden' => $indice + 1]);
        }

        // El pivot no toca `rutas.updated_at`; sin esto la sincronización incremental de la
        // app móvil no se enteraría del nuevo orden de paradas.
        $ruta->touch();

        $this->refrescarMapa();
    }

    public function ubicarProveedor(int $proveedorId): void
    {
        $this->ubicandoProveedorId = $this->ubicandoProveedorId === $proveedorId ? null : $proveedorId;
        $this->refrescarMapa();
    }

    /**
     * Guarda la ubicación de la finca del proveedor que se está ubicando, tocada en el mapa.
     */
    public function marcarUbicacion(float $lat, float $lng): void
    {
        abort_if($this->ubicandoProveedorId === null, 404);

        $this->guardarUbicacion($this->ubicandoProveedorId, $lat, $lng);
    }

    /**
     * Guarda la ubicación de un proveedor cuyo marcador se arrastró en el mapa.
     */
    public function moverUbicacionProveedor(int $proveedorId, float $lat, float $lng): void
    {
        $this->guardarUbicacion($proveedorId, $lat, $lng);
    }

    /**
     * Si la nueva ubicación queda dentro del rango (en metros, el guardado en cada ruta) del camino
     * de una ruta activa, el proveedor pasa a esa ruta (la más cercana), salvo que ya esté en una
     * de las rutas en rango.
     */
    private function guardarUbicacion(int $proveedorId, float $lat, float $lng): void
    {
        validator(['lat' => $lat, 'lng' => $lng], [
            'lat' => 'required|numeric|between:-90,90',
            'lng' => 'required|numeric|between:-180,180',
        ])->validate();

        // Se guarda directamente (sin Proveedor::guardarConUbicacion) porque aquí es planta quien
        // ubica al proveedor: no debe quitarse de sus rutas ni notificar por el cambio.
        $proveedor = Proveedor::findOrFail($proveedorId);
        $proveedor->update(['latitud' => $lat, 'longitud' => $lng]);

        $this->ubicandoProveedorId = null;

        $rutasEnRango = Ruta::query()
            ->where('activa', true)
            ->whereNotNull('trazo')
            ->get()
            ->map(fn (Ruta $ruta) => ['ruta' => $ruta, 'distancia' => $ruta->distanciaAlTrazoEnMetros([$lat, $lng])])
            ->filter(fn (array $item) => $item['distancia'] <= $item['ruta']->rango)
            ->sortBy('distancia')
            ->pluck('ruta');

        $idsActuales = $proveedor->rutas()->pluck('rutas.id');
        $rutaDestino = $rutasEnRango->first();

        if ($rutaDestino && $rutasEnRango->pluck('id')->intersect($idsActuales)->isEmpty()) {
            // agregarProveedor lo saca de su ruta anterior y la marca como modificada.
            $rutaDestino->agregarProveedor($proveedor->id);
            $proveedor->update(['acopiador_id' => $rutaDestino->acopiador_id]);

            $this->refrescarMapa();
            Flux::toast("Ubicación guardada. El proveedor quedó en el rango de {$rutaDestino->nombre} y se agregó a esa ruta.", variant: 'success');

            return;
        }

        // La nueva ubicación puede cambiar su número de parada; y aunque no cambie, la app móvil
        // debe enterarse de la nueva ubicación.
        Ruta::whereKey($idsActuales)->each(function (Ruta $ruta) {
            $ruta->numerarParadasPorTrazo();
            $ruta->touch();
        });

        $this->refrescarMapa();
        Flux::toast('Ubicación del proveedor guardada.', variant: 'success');
    }

    /**
     * Guarda el rango (en metros a cada lado del camino) en el que la ruta suma proveedores cercanos.
     */
    public function guardarRangoRuta(int $rutaId, int $rango): void
    {
        validator(['rango' => $rango], ['rango' => 'required|integer|between:100,5000'])->validate();

        Ruta::findOrFail($rutaId)->update(['rango' => $rango]);

        Flux::toast('Rango de la ruta guardado.', variant: 'success');
    }

    /**
     * Agrega a la ruta un proveedor que quedó dentro del rango de su camino (desde el mapa general).
     */
    public function agregarProveedorARuta(int $rutaId, int $proveedorId): void
    {
        $this->rutaAsignarId = $rutaId;
        $this->asignarProveedor($proveedorId);
    }

    public function quitarProveedorDeRuta(int $rutaId, int $proveedorId): void
    {
        $this->rutaAsignarId = $rutaId;
        $this->quitarProveedor($proveedorId);
    }

    public function verDetalle(int $rutaId): void
    {
        $this->rutaDetalleId = $rutaId;
        Flux::modal('detalle-ruta')->show();
    }

    public function nuevaRuta(): void
    {
        $this->reset(['rutaFormId', 'rutaFormNombre', 'rutaFormAcopiadorId']);
        $this->rutaFormActiva = true;
        $this->resetValidation();
        Flux::modal('ruta-form')->show();
    }

    public function editarRuta(int $id): void
    {
        $ruta = Ruta::findOrFail($id);
        $this->rutaFormId = $ruta->id;
        $this->rutaFormNombre = $ruta->nombre;
        $this->rutaFormAcopiadorId = $ruta->acopiador_id;
        $this->rutaFormActiva = $ruta->activa;
        $this->resetValidation();
        Flux::modal('ruta-form')->show();
    }

    public function guardarRuta(): void
    {
        $data = $this->validate([
            'rutaFormNombre' => 'required|string|max:255',
            'rutaFormAcopiadorId' => 'required|exists:acopiadores,id',
        ]);

        $ruta = Ruta::updateOrCreate(['id' => $this->rutaFormId], [
            'nombre' => $data['rutaFormNombre'],
            'acopiador_id' => $data['rutaFormAcopiadorId'],
            'activa' => $this->rutaFormActiva,
        ]);

        $this->rutaMapaId ??= $ruta->id;

        Flux::modal('ruta-form')->close();
        $this->refrescarMapa();
        Flux::toast($this->rutaFormId ? 'Ruta actualizada.' : 'Ruta creada.', variant: 'success');
    }

    public function pedirEliminarRuta(int $id): void
    {
        $this->eliminarRutaId = $id;
        Flux::modal('confirmar-eliminar-ruta')->show();
    }

    public function eliminarRuta(): void
    {
        Ruta::whereKey($this->eliminarRutaId)->delete();

        if ($this->rutaMapaId === $this->eliminarRutaId) {
            $this->rutaMapaId = Ruta::query()->orderBy('nombre')->value('id');
            $this->ubicandoProveedorId = null;
        }

        Flux::modal('confirmar-eliminar-ruta')->close();
        $this->refrescarMapa();
        Flux::toast('Ruta eliminada.', variant: 'success');
    }

    public function abrirAsignar(int $rutaId): void
    {
        $this->rutaAsignarId = $rutaId;
        $this->buscarProveedorAsignar = '';
        Flux::modal('asignar-proveedores')->show();
    }

    public function abrirAsignarDeAcopiador(int $acopiadorId): void
    {
        $this->abrirAsignar($this->rutaPrincipal($acopiadorId)->id);
    }

    public function asignarProveedor(int $proveedorId): void
    {
        $ruta = Ruta::findOrFail($this->rutaAsignarId);

        $ruta->agregarProveedor($proveedorId);

        // El proveedor pasa a pertenecer formalmente al acopiador de esta ruta, para que
        // también aparezca en sus propios paneles de Recojo/Entrega (que filtran por
        // Proveedor.acopiador_id, no por esta ruta).
        Proveedor::whereKey($proveedorId)->update(['acopiador_id' => $ruta->acopiador_id]);

        $this->refrescarMapa();
        Flux::toast('Proveedor asignado a la ruta.', variant: 'success');
    }

    public function quitarProveedor(int $proveedorId): void
    {
        $ruta = Ruta::findOrFail($this->rutaAsignarId);
        $ruta->proveedores()->detach($proveedorId);

        // El pivot no toca `rutas.updated_at`; la app móvil sincroniza por esa fecha.
        $ruta->touch();

        $this->refrescarMapa();
        Flux::toast('Proveedor quitado de la ruta.', variant: 'success');
    }

    public function abrirAsignarTurno(int $rutaId): void
    {
        $this->rutaTurnoId = $rutaId;
        Flux::modal('asignar-turno')->show();
    }

    public function abrirAsignarTurnoDeAcopiador(int $acopiadorId): void
    {
        $this->abrirAsignarTurno($this->rutaPrincipal($acopiadorId)->id);
    }

    /**
     * La ruta que usan las acciones "Asignar proveedores"/"Asignar turno" cuando se disparan
     * desde la cabecera de un acopiador (en vez de desde una ruta puntual): su primera ruta, o
     * una nueva creada al vuelo si todavía no tiene ninguna configurada.
     */
    private function rutaPrincipal(int $acopiadorId): Ruta
    {
        return Ruta::query()->where('acopiador_id', $acopiadorId)->orderBy('id')->first()
            ?? Ruta::create([
                'acopiador_id' => $acopiadorId,
                'nombre' => Acopiador::find($acopiadorId)?->ruta_nombre ?: 'Ruta principal',
                'activa' => true,
            ]);
    }

    public function asignarTurno(int $turnoId): void
    {
        Ruta::findOrFail($this->rutaTurnoId)->turnos()->syncWithoutDetaching([$turnoId]);
        Flux::toast('Turno asignado a la ruta.', variant: 'success');
    }

    public function quitarTurno(int $turnoId): void
    {
        Ruta::findOrFail($this->rutaTurnoId)->turnos()->detach($turnoId);
        Flux::toast('Turno quitado de la ruta.', variant: 'success');
    }
};
?>

<div class="flex flex-col gap-8">
    <div class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <x-ui.titulo titulo="Rutas">Acopiadores con sus proveedores asignados y si ya recogieron hoy.</x-ui.titulo>
        <flux:modal.trigger name="ruta-form">
            <flux:button size="sm" variant="primary" icon="plus" wire:click="nuevaRuta">Nueva ruta</flux:button>
        </flux:modal.trigger>
    </div>

    <div class="max-h-[36rem] overflow-x-auto overflow-y-auto rounded-xl border border-huata-verde-100 px-3 dark:border-zinc-700 huata-cuadro">
        <flux:table>
            <flux:table.columns sticky class="bg-white/80 dark:bg-zinc-800/80">
                <flux:table.column>Ruta</flux:table.column>
                <flux:table.column>Acopiador</flux:table.column>
                <flux:table.column>Turnos</flux:table.column>
                <flux:table.column>Proveedores</flux:table.column>
                <flux:table.column>Estado</flux:table.column>
                <flux:table.column class="w-px whitespace-nowrap">Acciones</flux:table.column>
            </flux:table.columns>
            <flux:table.rows>
                @forelse ($rutas as $ruta)
                    <flux:table.row wire:key="ruta-{{ $ruta->id }}">
                        <flux:table.cell class="font-medium">{{ $ruta->nombre }}</flux:table.cell>
                        <flux:table.cell>{{ $ruta->acopiador->user->nombreCompleto() }}</flux:table.cell>
                        <flux:table.cell>
                            <div class="flex flex-wrap gap-1">
                                @forelse ($ruta->turnos as $turno)
                                    <flux:badge color="amber" size="sm" icon="clock">{{ $turno->nombre }}: {{ $turno->hora_inicio }} - {{ $turno->hora_fin }}</flux:badge>
                                @empty
                                    <span class="text-sm text-zinc-400">Sin turno</span>
                                @endforelse
                            </div>
                        </flux:table.cell>
                        <flux:table.cell>{{ $ruta->proveedores->count() }}</flux:table.cell>
                        <flux:table.cell>
                            <flux:badge :color="$ruta->activa ? 'green' : 'zinc'" size="sm">{{ $ruta->activa ? 'Activa' : 'Inactiva' }}</flux:badge>
                        </flux:table.cell>
                        <flux:table.cell class="whitespace-nowrap">
                            <div class="flex flex-nowrap items-center gap-2">
                                <flux:modal.trigger name="detalle-ruta">
                                    <flux:button size="sm" variant="primary" icon="table-cells" wire:click="verDetalle({{ $ruta->id }})">
                                        Ver proveedores
                                    </flux:button>
                                </flux:modal.trigger>
                                <flux:modal.trigger name="ruta-form">
                                    <flux:button size="sm" variant="ghost" icon="pencil" wire:click="editarRuta({{ $ruta->id }})" />
                                </flux:modal.trigger>
                                <flux:modal.trigger name="confirmar-eliminar-ruta">
                                    <flux:button size="sm" variant="ghost" icon="trash" wire:click="pedirEliminarRuta({{ $ruta->id }})" />
                                </flux:modal.trigger>
                            </div>
                        </flux:table.cell>
                    </flux:table.row>
                @empty
                    <flux:table.row>
                        <flux:table.cell colspan="6" class="text-center text-zinc-500">
                            No hay rutas creadas todavía. Usa "Nueva ruta" para crear la primera.
                        </flux:table.cell>
                    </flux:table.row>
                @endforelse
            </flux:table.rows>
        </flux:table>
    </div>

    <div class="flex flex-col gap-4 rounded-xl border border-huata-verde-100 p-4 dark:border-zinc-700 huata-cuadro">
        <div class="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
            <div>
                <flux:heading size="lg">Recorrido del camioncito</flux:heading>
                <flux:subheading>Traza el camino de cada ruta, marca la ubicación de los proveedores, ordena las paradas y ajusta el rango del acopiador para sumar a los proveedores cercanos al caminito.</flux:subheading>
            </div>
            @if ($rutas->isNotEmpty())
                <div class="w-full sm:w-80">
                    <flux:select wire:model.live="rutaMapaId" label="Ruta">
                        @foreach ($rutas as $r)
                            <flux:select.option value="{{ $r->id }}" wire:key="opcion-ruta-mapa-{{ $r->id }}">
                                {{ $r->nombre }} · {{ $r->acopiador->user->nombreCompleto() }}
                            </flux:select.option>
                        @endforeach
                    </flux:select>
                </div>
            @endif
        </div>

        <div class="grid gap-6 lg:grid-cols-3">
            <div class="flex flex-col gap-3 lg:col-span-2">
                @if ($ubicandoProveedor)
                    <flux:callout icon="map-pin" color="amber">
                        <flux:callout.heading>Ubicando a {{ $ubicandoProveedor->user->nombreCompleto() }}</flux:callout.heading>
                        <flux:callout.text>Haz clic en el mapa en el lugar exacto de su finca (Esc para cancelar).</flux:callout.text>
                        <x-slot name="actions">
                            <flux:button size="sm" variant="ghost" wire:click="ubicarProveedor({{ $ubicandoProveedor->id }})">Cancelar</flux:button>
                        </x-slot>
                    </flux:callout>
                @endif

                <x-mapa-rutas-general :datos="$datosMapaGeneral" />
            </div>

            <div class="flex flex-col gap-3">
                <div class="flex items-center justify-between gap-2">
                    <flux:heading size="lg">Proveedores</flux:heading>
                    <flux:badge size="sm" color="zinc">{{ $proveedoresMapa->count() }}</flux:badge>
                </div>

                <flux:input
                    wire:model.live.debounce.300ms="buscarProveedorMapa"
                    icon="magnifying-glass"
                    size="sm"
                    placeholder="Buscar proveedor..."
                />

                <div class="flex max-h-[34rem] flex-col gap-2 overflow-y-auto pr-1">
                    @forelse ($proveedoresMapa as $item)
                        @php
                            $proveedor = $item['proveedor'];
                            $parada = $item['parada'];
                        @endphp
                        <div
                            wire:key="proveedor-mapa-{{ $proveedor->id }}"
                            @class([
                                'flex items-center gap-3 rounded-lg border p-2',
                                'border-huata-amarillo-400 bg-huata-amarillo-400/10' => $ubicandoProveedor?->id === $proveedor->id,
                                'border-huata-verde-300 bg-huata-verde-50 dark:border-huata-verde-700 dark:bg-huata-verde-950/30' => $ubicandoProveedor?->id !== $proveedor->id && $parada,
                                'border-zinc-200 bg-white dark:border-zinc-700 dark:bg-zinc-800' => $ubicandoProveedor?->id !== $proveedor->id && ! $parada,
                            ])
                        >
                            @if ($parada)
                                <div class="flex size-8 shrink-0 items-center justify-center rounded-full bg-huata-verde-600 text-sm font-bold text-white" title="Parada {{ $parada }} de {{ $rutaMapa->nombre }}">
                                    {{ $parada }}
                                </div>
                            @else
                                <div class="flex size-8 shrink-0 items-center justify-center rounded-full bg-zinc-200 text-xs font-bold text-zinc-600 dark:bg-zinc-700 dark:text-zinc-300">
                                    {{ mb_substr($proveedor->user->nombres, 0, 1) }}{{ mb_substr($proveedor->user->apellidos, 0, 1) }}
                                </div>
                            @endif

                            <div class="min-w-0 flex-1">
                                <p class="truncate text-sm font-semibold text-zinc-900 dark:text-white">{{ $proveedor->user->nombreCompleto() }}</p>
                                <p class="truncate text-xs text-zinc-500">{{ $proveedor->finca_ubicacion ?: 'Sin ubicación' }}</p>
                                @unless ($parada)
                                    <p class="truncate text-xs text-zinc-500">{{ $proveedor->rutas->pluck('nombre')->join(', ') ?: 'Sin ruta' }}</p>
                                @endunless
                                @if ($proveedor->latitud === null || $proveedor->longitud === null)
                                    <p class="text-xs text-red-600">Sin punto en el mapa</p>
                                @endif
                            </div>

                            <div class="flex items-center">
                                <flux:button size="xs" variant="ghost" icon="map-pin"wire:click="ubicarProveedor({{ $proveedor->id }})" title="Marcar ubicación en el mapa" />
                                <flux:button
                                    size="xs"
                                    variant="ghost"
                                    icon="eye"
                                    x-on:click="$dispatch('mapa-enfocar-proveedor', { id: {{ $proveedor->id }} })"
                                    :disabled="$proveedor->latitud === null || $proveedor->longitud === null"
                                    title="Ver dónde está respecto al camino"
                                />
                            </div>
                        </div>
                    @empty
                        <p class="py-6 text-center text-sm text-zinc-500">
                            {{ $buscarProveedorMapa ? 'Ningún proveedor coincide con la búsqueda.' : 'Todavía no hay proveedores registrados.' }}
                        </p>
                    @endforelse
                </div>
            </div>
        </div>
    </div>

    <flux:modal name="detalle-ruta" class="md:w-[52rem]">
        <div class="flex flex-col gap-5">
            <div class="flex flex-wrap items-center justify-between gap-3">
                <div>
                    <flux:heading size="lg">{{ $rutaDetalle?->nombre }}</flux:heading>
                    <flux:subheading>{{ $rutaDetalle?->acopiador?->user?->nombreCompleto() }}</flux:subheading>
                </div>
                <div class="flex flex-wrap gap-2">
                    <flux:modal.trigger name="asignar-proveedores">
                        <flux:button size="sm" variant="primary" icon="user-plus" wire:click="abrirAsignar({{ $rutaDetalle?->id }})">
                            Asignar proveedores
                        </flux:button>
                    </flux:modal.trigger>
                    <flux:modal.trigger name="asignar-turno">
                        <flux:button size="sm" variant="filled" icon="clock" wire:click="abrirAsignarTurno({{ $rutaDetalle?->id }})">
                            Asignar turno
                        </flux:button>
                    </flux:modal.trigger>
                </div>
            </div>

            <div class="flex flex-wrap gap-2">
                @forelse ($rutaDetalle?->turnos ?? [] as $turno)
                    <flux:badge color="amber" size="sm" icon="clock">{{ $turno->nombre }}: {{ $turno->hora_inicio }} - {{ $turno->hora_fin }}</flux:badge>
                @empty
                    <flux:badge color="zinc" size="sm" icon="clock">Sin turno asignado</flux:badge>
                @endforelse
            </div>

            <div class="rounded-xl border border-huata-verde-100 dark:border-zinc-700">
                <flux:table>
                    <flux:table.columns class="bg-white dark:bg-zinc-800">
                        <flux:table.column>Proveedor</flux:table.column>
                        <flux:table.column>DNI/RUC</flux:table.column>
                        <flux:table.column>Celular</flux:table.column>
                        <flux:table.column>Ubicación</flux:table.column>
                        <flux:table.column class="w-px whitespace-nowrap">Recojo de hoy</flux:table.column>
                    </flux:table.columns>
                    <flux:table.rows>
                        @forelse ($rutaDetalle?->proveedores ?? [] as $proveedor)
                            <flux:table.row wire:key="rp-{{ $proveedor->id }}">
                                <flux:table.cell class="font-medium">{{ $proveedor->user->nombreCompleto() }}</flux:table.cell>
                                <flux:table.cell>{{ $proveedor->user->documento ?? '—' }}</flux:table.cell>
                                <flux:table.cell>{{ $proveedor->user->celular ?? '—' }}</flux:table.cell>
                                <flux:table.cell>{{ $proveedor->finca_ubicacion ?: 'Sin registrar' }}</flux:table.cell>
                                <flux:table.cell class="whitespace-nowrap">
                                    <flux:badge :color="$proveedoresRecogidosHoy->contains($proveedor->id) ? 'green' : 'red'" size="sm">
                                        {{ $proveedoresRecogidosHoy->contains($proveedor->id) ? 'Recogido' : 'No recogido' }}
                                    </flux:badge>
                                </flux:table.cell>
                            </flux:table.row>
                        @empty
                            <flux:table.row>
                                <flux:table.cell colspan="5" class="text-center text-zinc-500">Esta ruta no tiene proveedores asignados.</flux:table.cell>
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

    <flux:modal name="asignar-proveedores" class="md:w-[44rem]">
        <div class="flex flex-col gap-5">
            <div>
                <flux:heading size="lg">Asignar proveedores</flux:heading>
                <flux:subheading>
                    {{ $rutaAsignar?->nombre }} &middot; {{ $rutaAsignar?->acopiador?->user?->nombreCompleto() }}
                </flux:subheading>
            </div>

            <flux:input
                wire:model.live.debounce.300ms="buscarProveedorAsignar"
                icon="magnifying-glass"
                placeholder="Buscar proveedor por nombre o apellido..."
            />

            <div class="grid max-h-96 gap-2 overflow-y-auto pr-1 sm:grid-cols-2">
                @forelse ($proveedoresAsignar as $item)
                    @php $proveedor = $item['proveedor']; @endphp
                    <div
                        wire:key="asig-{{ $proveedor->id }}"
                        class="flex items-center gap-3 rounded-lg border p-2 transition {{ $item['asignado'] ? 'border-huata-verde-300 bg-huata-verde-50 dark:border-huata-verde-700 dark:bg-huata-verde-950/30' : 'border-zinc-200 bg-white dark:border-zinc-700 dark:bg-zinc-800' }}"
                    >
                        <div class="flex size-9 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-huata-verde-400 to-huata-celeste-500 text-xs font-bold text-white">
                            {{ mb_substr($proveedor->user->nombres, 0, 1) }}{{ mb_substr($proveedor->user->apellidos, 0, 1) }}
                        </div>

                        <div class="min-w-0 flex-1">
                            <p class="truncate text-sm font-semibold leading-tight text-zinc-900 dark:text-white">
                                {{ $proveedor->user->nombres }} {{ $proveedor->user->apellidos }}
                            </p>
                            <p class="truncate text-xs text-zinc-500 dark:text-zinc-400">
                                {{ $proveedor->finca_ubicacion ?: 'Sin ubicación' }}
                            </p>
                            @if ($item['deOtroAcopiador'])
                                <p class="truncate text-xs text-huata-amarillo-600">Sin ruta &middot; {{ $proveedor->acopiador?->user?->nombreCompleto() ?? 'Sin acopiador' }}</p>
                            @endif
                        </div>

                        @if ($item['asignado'])
                            <flux:button size="sm" variant="ghost" icon="x-mark" wire:click="quitarProveedor({{ $proveedor->id }})" />
                        @else
                            <flux:button size="sm" variant="primary" icon="plus" wire:click="asignarProveedor({{ $proveedor->id }})" />
                        @endif
                    </div>
                @empty
                    <p class="col-span-full py-6 text-center text-sm text-zinc-500">Este acopiador no tiene proveedores registrados.</p>
                @endforelse
            </div>

            <div class="flex justify-end">
                <flux:modal.close>
                    <flux:button variant="ghost">Cerrar</flux:button>
                </flux:modal.close>
            </div>
        </div>
    </flux:modal>

    <flux:modal name="asignar-turno" class="md:w-[36rem]">
        <div class="flex flex-col gap-5">
            <div>
                <flux:heading size="lg">Asignar turno</flux:heading>
                <flux:subheading>
                    {{ $rutaTurno?->nombre }} &middot; {{ $rutaTurno?->acopiador?->user?->nombreCompleto() }}
                </flux:subheading>
            </div>

            <div class="grid gap-2 sm:grid-cols-2">
                @forelse ($turnosAsignar as $item)
                    @php $turno = $item['turno']; @endphp
                    <div
                        wire:key="turno-op-{{ $turno->id }}"
                        class="flex items-center gap-3 rounded-lg border p-2 transition {{ $item['asignado'] ? 'border-huata-amarillo-400 bg-huata-amarillo-400/10' : 'border-zinc-200 bg-white dark:border-zinc-700 dark:bg-zinc-800' }}"
                    >
                        <div class="flex size-9 shrink-0 items-center justify-center rounded-full bg-huata-amarillo-400/20">
                            <flux:icon name="clock" class="size-4 text-huata-amarillo-600" />
                        </div>

                        <div class="min-w-0 flex-1">
                            <p class="truncate text-sm font-semibold leading-tight text-zinc-900 dark:text-white">{{ $turno->nombre }}</p>
                            <p class="truncate text-xs text-zinc-500 dark:text-zinc-400">{{ $turno->hora_inicio }} &ndash; {{ $turno->hora_fin }}</p>
                        </div>

                        @if ($item['asignado'])
                            <flux:button size="sm" variant="ghost" icon="x-mark" wire:click="quitarTurno({{ $turno->id }})" />
                        @else
                            <flux:button size="sm" variant="primary" icon="plus" wire:click="asignarTurno({{ $turno->id }})" />
                        @endif
                    </div>
                @empty
                    <p class="col-span-full py-6 text-center text-sm text-zinc-500">
                        Todavía no hay turnos de ruta configurados.
                    </p>
                @endforelse
            </div>

            <div class="flex justify-end">
                <flux:modal.close>
                    <flux:button variant="ghost">Cerrar</flux:button>
                </flux:modal.close>
            </div>
        </div>
    </flux:modal>

    <flux:modal name="ruta-form" class="md:w-96">
        <form wire:submit="guardarRuta" class="flex flex-col gap-6">
            <flux:heading size="lg">{{ $rutaFormId ? 'Editar ruta' : 'Nueva ruta' }}</flux:heading>

            <flux:input label="Nombre de la ruta" wire:model="rutaFormNombre" placeholder="Ej: Ruta Huata Centro" />

            <flux:select label="Acopiador" wire:model="rutaFormAcopiadorId">
                <flux:select.option value="">Selecciona...</flux:select.option>
                @foreach ($acopiadores as $a)
                    <flux:select.option value="{{ $a->id }}">{{ $a->user->nombreCompleto() }}</flux:select.option>
                @endforeach
            </flux:select>

            <flux:switch label="Ruta activa" wire:model="rutaFormActiva" />

            <div class="flex justify-end gap-2">
                <flux:modal.close><flux:button variant="ghost">Cancelar</flux:button></flux:modal.close>
                <flux:button type="submit" variant="primary">Guardar</flux:button>
            </div>
        </form>
    </flux:modal>

    <x-confirm-modal
        name="confirmar-eliminar-ruta"
        accion="eliminarRuta"
        titulo="¿Eliminar esta ruta?"
        mensaje="Se quitarán sus proveedores y turnos asignados. Esta acción no se puede deshacer."
    />
</div>

<?php

use App\Enums\EstadoAsistencia;
use App\Enums\EstadoJustificacion;
use App\Enums\Rol;
use App\Models\Asistencia;
use App\Models\DiaAsistenciaAcopiador;
use App\Models\HorarioAsistencia;
use App\Models\Justificacion;
use App\Models\User;
use Carbon\CarbonImmutable;
use Flux\Flux;
use Illuminate\Support\Collection;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Storage;
use Livewire\Attributes\Url;
use Livewire\Component;

new class extends Component
{
    /** Día seleccionado en el calendario (Y-m-d). */
    public string $fecha = '';

    /** Mes que muestra el calendario (Y-m). */
    public string $mes = '';

    /** @var array<int|string, string> Estado elegido por usuario (id => presente|tardanza|falta). */
    public array $estados = [];

    public bool $editando = false;

    public string $busqueda = '';

    #[Url]
    public string $pestana = 'asistencia';

    public string $filtroJustificacion = 'pendiente';

    public ?int $justificacionId = null;

    public ?int $horarioId = null;

    public string $horarioNombre = '';

    public string $horaInicio = '';

    public string $horaLimitePresente = '';

    public string $horaLimiteTardanza = '';

    public bool $horarioActivo = true;

    public ?int $eliminarHorarioId = null;

    public ?int $acopiadorId = null;

    public string $acopiadorNombre = '';

    /** @var array<int|string, string> Estado del acopiador por horario (horario id => estado, '' = sin registro). */
    public array $estadosAcopiador = [];

    /** Mes que muestra el calendario de días de asistencia de los acopiadores (Y-m). */
    public string $mesAcopiadores = '';

    public function mount(): void
    {
        HorarioAsistencia::registrarFaltasVencidas();

        $this->seleccionarFecha(Asistencia::hoy()->toDateString());
        $this->mesAcopiadores = Asistencia::hoy()->format('Y-m');
    }

    public function with(): array
    {
        $fecha = CarbonImmutable::parse($this->fecha);
        $registrosDelDia = Asistencia::query()
            ->general()
            ->with('justificacion')
            ->whereDate('fecha', $fecha)
            ->get()
            ->keyBy('user_id');

        $horarios = HorarioAsistencia::query()->orderBy('hora_inicio')->get();

        return [
            'fechaSeleccionada' => $fecha,
            'fechaRegistrada' => $registrosDelDia->isNotEmpty(),
            'esFutura' => $fecha->isAfter(Asistencia::hoy()),
            'registrosDelDia' => $registrosDelDia,
            'semanas' => $this->semanasDelCalendario(),
            'usuarios' => $this->buscarUsuarios()
                ->filter(fn (User $u) => $u->participaEnAsistenciaGeneral())
                ->values(),
            'opciones' => EstadoAsistencia::cases(),
            'justificaciones' => $this->pestana === 'justificaciones'
                ? Justificacion::query()
                    ->with('asistencia.user', 'asistencia.horario')
                    ->when($this->filtroJustificacion !== 'todas', fn ($q) => $q->where('estado', $this->filtroJustificacion))
                    ->latest()
                    ->get()
                : collect(),
            'pendientes' => Justificacion::query()->where('estado', EstadoJustificacion::Pendiente)->count(),
            'justificacionDetalle' => $this->justificacionId
                ? Justificacion::with('asistencia.user', 'asistencia.horario')->find($this->justificacionId)
                : null,
            'horarios' => $horarios,
            'horariosActivos' => $horarios->where('activo', true)->values(),
            'acopiadores' => $this->pestana === 'acopiadores'
                ? $this->buscarUsuarios()->filter(fn (User $u) => $u->esAcopiador())->values()
                : collect(),
            'marcasAcopiadores' => $this->pestana === 'acopiadores'
                ? Asistencia::query()
                    ->whereNotNull('horario_asistencia_id')
                    ->whereDate('fecha', $fecha)
                    ->get()
                    ->groupBy('user_id')
                    ->map(fn (Collection $marcas) => $marcas->keyBy('horario_asistencia_id'))
                : collect(),
            'semanasAcopiadores' => $this->pestana === 'acopiadores' ? $this->semanasDiasAcopiadores() : [],
            'esDiaAcopiador' => $this->pestana === 'acopiadores' && DiaAsistenciaAcopiador::esDiaDeAsistencia($this->fecha),
        ];
    }

    /**
     * Semanas (lunes a domingo) del mes visible en el calendario de los acopiadores, indicando
     * qué días se toma asistencia.
     *
     * @return array<int, array<int, array{fecha: string, dia: int, delMes: bool, esHoy: bool, esPasado: bool, seleccionado: bool}>>
     */
    private function semanasDiasAcopiadores(): array
    {
        $hoy = Asistencia::hoy();
        $inicioMes = CarbonImmutable::parse("{$this->mesAcopiadores}-01");
        $inicio = $inicioMes->startOfWeek(CarbonImmutable::MONDAY);
        $fin = $inicioMes->endOfMonth()->endOfWeek(CarbonImmutable::SUNDAY);

        $seleccionados = $this->diasAcopiadoresEntre($inicio, $fin);

        $dias = [];

        for ($dia = $inicio; $dia->lte($fin); $dia = $dia->addDay()) {
            $dias[] = [
                'fecha' => $dia->toDateString(),
                'dia' => $dia->day,
                'delMes' => $dia->month === $inicioMes->month,
                'esHoy' => $dia->isSameDay($hoy),
                'esPasado' => $dia->lt($hoy),
                'seleccionado' => $seleccionados->contains($dia->toDateString()),
            ];
        }

        return array_chunk($dias, 7);
    }

    /**
     * @return Collection<int, string>
     */
    private function diasAcopiadoresEntre(CarbonImmutable $inicio, CarbonImmutable $fin): Collection
    {
        return DiaAsistenciaAcopiador::query()
            ->whereDate('fecha', '>=', $inicio)
            ->whereDate('fecha', '<=', $fin)
            ->get()
            ->map(fn (DiaAsistenciaAcopiador $dia) => $dia->fecha->toDateString());
    }

    public function cambiarMesAcopiadores(int $desplazamiento): void
    {
        $this->mesAcopiadores = CarbonImmutable::parse("{$this->mesAcopiadores}-01")->addMonths($desplazamiento)->format('Y-m');
    }

    /**
     * Agrega o quita un día de asistencia de los acopiadores. Los días pasados no se cambian
     * para no generar ni borrar faltas de forma retroactiva.
     */
    public function alternarDiaAcopiador(string $fecha): void
    {
        $this->autorizar();

        $dia = CarbonImmutable::parse($fecha);

        if ($dia->lt(Asistencia::hoy())) {
            Flux::toast('No se pueden cambiar días pasados.', variant: 'warning');

            return;
        }

        $existente = DiaAsistenciaAcopiador::query()->whereDate('fecha', $dia)->first();

        if ($existente) {
            $existente->delete();
        } else {
            DiaAsistenciaAcopiador::create(['fecha' => $dia->toDateString()]);
        }
    }

    /**
     * Selecciona de una vez los días restantes del mes visible (desde hoy).
     *
     * @param  string  $modo  laborables (lunes a sábado) | todos | ninguno
     */
    public function seleccionarDiasDelMes(string $modo): void
    {
        $this->autorizar();

        abort_unless(in_array($modo, ['laborables', 'todos', 'ninguno'], true), 422);

        $inicioMes = CarbonImmutable::parse("{$this->mesAcopiadores}-01");
        $desde = $inicioMes->max(Asistencia::hoy());
        $hasta = $inicioMes->endOfMonth()->startOfDay();

        if ($desde->gt($hasta)) {
            return;
        }

        DB::transaction(function () use ($modo, $desde, $hasta) {
            DiaAsistenciaAcopiador::query()
                ->whereDate('fecha', '>=', $desde)
                ->whereDate('fecha', '<=', $hasta)
                ->delete();

            if ($modo === 'ninguno') {
                return;
            }

            for ($dia = $desde; $dia->lte($hasta); $dia = $dia->addDay()) {
                if ($modo === 'laborables' && $dia->isSunday()) {
                    continue;
                }

                DiaAsistenciaAcopiador::create(['fecha' => $dia->toDateString()]);
            }
        });

        Flux::toast('Días de asistencia actualizados.', variant: 'success');
    }

    /**
     * Semanas (lunes a domingo) del mes visible, con el resumen de asistencia de cada día.
     *
     * @return array<int, array<int, array{fecha: string, dia: int, delMes: bool, esHoy: bool, esFuturo: bool, registrada: bool, faltas: int, tardanzas: int}>>
     */
    private function semanasDelCalendario(): array
    {
        $hoy = Asistencia::hoy();
        $inicioMes = CarbonImmutable::parse("{$this->mes}-01");
        $inicio = $inicioMes->startOfWeek(CarbonImmutable::MONDAY);
        $fin = $inicioMes->endOfMonth()->endOfWeek(CarbonImmutable::SUNDAY);

        $resumen = Asistencia::query()
            ->general()
            ->whereDate('fecha', '>=', $inicio)
            ->whereDate('fecha', '<=', $fin)
            ->get(['fecha', 'estado'])
            ->groupBy(fn (Asistencia $a) => $a->fecha->toDateString());

        $dias = [];

        for ($dia = $inicio; $dia->lte($fin); $dia = $dia->addDay()) {
            $registros = $resumen->get($dia->toDateString(), collect());

            $dias[] = [
                'fecha' => $dia->toDateString(),
                'dia' => $dia->day,
                'delMes' => $dia->month === $inicioMes->month,
                'esHoy' => $dia->isSameDay($hoy),
                'esFuturo' => $dia->isAfter($hoy),
                'registrada' => $registros->isNotEmpty(),
                'faltas' => $registros->where('estado', EstadoAsistencia::Falta)->count(),
                'tardanzas' => $registros->where('estado', EstadoAsistencia::Tardanza)->count(),
            ];
        }

        return array_chunk($dias, 7);
    }

    public function seleccionarFecha(string $fecha): void
    {
        $dia = CarbonImmutable::parse($fecha);

        if ($dia->isAfter(Asistencia::hoy())) {
            Flux::toast('No se puede tomar asistencia de un día futuro.', variant: 'warning');

            return;
        }

        $this->fecha = $dia->toDateString();
        $this->mes = $dia->format('Y-m');
        $this->editando = false;
        $this->cargarEstados();
    }

    public function irAHoy(): void
    {
        $this->seleccionarFecha(Asistencia::hoy()->toDateString());
    }

    public function cambiarDia(int $desplazamiento): void
    {
        $this->seleccionarFecha(CarbonImmutable::parse($this->fecha)->addDays($desplazamiento)->toDateString());
    }

    public function cambiarMes(int $desplazamiento): void
    {
        $this->mes = CarbonImmutable::parse("{$this->mes}-01")->addMonths($desplazamiento)->format('Y-m');
    }

    /**
     * Empieza a tomar asistencia del día seleccionado: todos los trabajadores como presentes.
     */
    public function nuevo(): void
    {
        $this->autorizar();

        if (CarbonImmutable::parse($this->fecha)->isAfter(Asistencia::hoy())) {
            return;
        }

        if (Asistencia::query()->general()->whereDate('fecha', $this->fecha)->exists()) {
            Flux::toast('Este día ya tiene asistencia registrada. Usa "Editar" para modificarla.', variant: 'warning');

            return;
        }

        $this->estados = $this->usuariosActivos()
            ->mapWithKeys(fn (int $id) => [$id => EstadoAsistencia::Presente->value])
            ->all();
        $this->editando = true;
    }

    public function editar(): void
    {
        $this->autorizar();

        if (! Asistencia::query()->general()->whereDate('fecha', $this->fecha)->exists()) {
            return;
        }

        $this->cargarEstados();

        // Quien se haya registrado después de tomar la asistencia aparece como presente.
        foreach ($this->usuariosActivos() as $id) {
            $this->estados[$id] ??= EstadoAsistencia::Presente->value;
        }

        $this->editando = true;
    }

    public function cancelar(): void
    {
        $this->editando = false;
        $this->cargarEstados();
    }

    public function marcarTodos(string $estado): void
    {
        $estado = EstadoAsistencia::from($estado)->value;

        foreach ($this->usuariosActivos() as $id) {
            $this->estados[$id] = $estado;
        }
    }

    public function guardar(): void
    {
        $this->autorizar();

        if (! $this->editando || CarbonImmutable::parse($this->fecha)->isAfter(Asistencia::hoy())) {
            return;
        }

        $this->validate([
            'estados' => 'required|array',
            'estados.*' => ['required', 'in:'.implode(',', array_column(EstadoAsistencia::cases(), 'value'))],
        ]);

        $existentes = Asistencia::query()->general()->whereDate('fecha', $this->fecha)->get()->keyBy('user_id');
        $activos = $this->usuariosActivos();

        foreach ($this->estados as $userId => $estado) {
            if (! $activos->contains((int) $userId)) {
                continue;
            }

            $asistencia = $existentes->get((int) $userId) ?? new Asistencia([
                'user_id' => (int) $userId,
                'fecha' => $this->fecha,
            ]);

            $asistencia->fill(['estado' => $estado, 'registrado_por' => auth()->id()])->save();
        }

        $this->editando = false;
        $this->cargarEstados();

        Flux::toast('Asistencia guardada correctamente.', variant: 'success');
    }

    public function verJustificacion(int $id): void
    {
        $this->justificacionId = $id;
        Flux::modal('justificacion-detalle')->show();
    }

    public function aceptar(int $id): void
    {
        $this->revisar($id, EstadoJustificacion::Aceptada);
        Flux::toast('Justificación aceptada.', variant: 'success');
    }

    public function rechazar(int $id): void
    {
        $this->revisar($id, EstadoJustificacion::Rechazada);
        Flux::toast('Justificación rechazada.', variant: 'warning');
    }

    private function revisar(int $id, EstadoJustificacion $estado): void
    {
        $this->autorizar();

        Justificacion::findOrFail($id)->revisar($estado, auth()->user());

        Flux::modal('justificacion-detalle')->close();
    }

    public function nuevoHorario(): void
    {
        $this->reset(['horarioId', 'horarioNombre', 'horaInicio', 'horaLimitePresente', 'horaLimiteTardanza']);
        $this->horarioActivo = true;
        $this->resetValidation();
        Flux::modal('horario-form')->show();
    }

    public function editarHorario(int $id): void
    {
        $horario = HorarioAsistencia::findOrFail($id);

        $this->horarioId = $horario->id;
        $this->horarioNombre = $horario->nombre;
        $this->horaInicio = $horario->inicio();
        $this->horaLimitePresente = $horario->limitePresente();
        $this->horaLimiteTardanza = $horario->limiteTardanza();
        $this->horarioActivo = $horario->activo;
        $this->resetValidation();
        Flux::modal('horario-form')->show();
    }

    public function confirmarEliminarHorario(int $id): void
    {
        $this->eliminarHorarioId = HorarioAsistencia::findOrFail($id)->id;
        Flux::modal('confirmar-eliminar-horario')->show();
    }

    /**
     * Elimina el horario junto con las marcas de asistencia de los acopiadores en ese horario
     * (y sus justificaciones y archivos), para no dejar registros sin horario.
     */
    public function eliminarHorario(): void
    {
        $this->autorizar();

        HorarioAsistencia::findOrFail($this->eliminarHorarioId)->eliminarConMarcas();

        $this->eliminarHorarioId = null;
        Flux::modal('confirmar-eliminar-horario')->close();
        Flux::toast('Horario eliminado.', variant: 'success');
    }

    public function guardarHorario(): void
    {
        $this->autorizar();

        $data = $this->validate([
            'horarioNombre' => 'required|string|max:60',
            'horaInicio' => 'required|date_format:H:i',
            'horaLimitePresente' => 'required|date_format:H:i|after:horaInicio',
            'horaLimiteTardanza' => 'required|date_format:H:i|after:horaLimitePresente',
        ], [], [
            'horarioNombre' => 'nombre',
            'horaInicio' => 'hora de inicio',
            'horaLimitePresente' => 'presente hasta',
            'horaLimiteTardanza' => 'tardanza hasta',
        ]);

        HorarioAsistencia::updateOrCreate(['id' => $this->horarioId], [
            'nombre' => $data['horarioNombre'],
            'hora_inicio' => $data['horaInicio'],
            'hora_limite_presente' => $data['horaLimitePresente'],
            'hora_limite_tardanza' => $data['horaLimiteTardanza'],
            'activo' => $this->horarioActivo,
        ]);

        Flux::modal('horario-form')->close();
        Flux::toast('Horario guardado correctamente.', variant: 'success');
    }

    public function editarAcopiador(int $userId): void
    {
        $this->autorizar();

        $acopiador = User::findOrFail($userId);
        $marcas = Asistencia::query()
            ->where('user_id', $acopiador->id)
            ->whereNotNull('horario_asistencia_id')
            ->whereDate('fecha', $this->fecha)
            ->get()
            ->keyBy('horario_asistencia_id');

        $this->acopiadorId = $acopiador->id;
        $this->acopiadorNombre = $acopiador->nombreCompleto();
        $this->estadosAcopiador = HorarioAsistencia::query()
            ->where('activo', true)
            ->pluck('id')
            ->mapWithKeys(fn (int $id) => [$id => $marcas->get($id)?->estado->value ?? ''])
            ->all();

        Flux::modal('acopiador-form')->show();
    }

    public function guardarAcopiador(): void
    {
        $this->autorizar();

        $this->validate([
            'estadosAcopiador.*' => ['nullable', 'in:'.implode(',', array_column(EstadoAsistencia::cases(), 'value'))],
        ]);

        foreach ($this->estadosAcopiador as $horarioId => $estado) {
            $marca = Asistencia::query()
                ->where('user_id', $this->acopiadorId)
                ->where('horario_asistencia_id', (int) $horarioId)
                ->whereDate('fecha', $this->fecha)
                ->first();

            if ($estado === '') {
                $marca?->delete();

                continue;
            }

            ($marca ?? new Asistencia([
                'user_id' => $this->acopiadorId,
                'fecha' => $this->fecha,
                'horario_asistencia_id' => (int) $horarioId,
            ]))->fill(['estado' => $estado, 'registrado_por' => auth()->id()])->save();
        }

        Flux::modal('acopiador-form')->close();
        Flux::toast('Asistencia del acopiador actualizada.', variant: 'success');
    }

    private function cargarEstados(): void
    {
        $this->estados = Asistencia::query()
            ->general()
            ->whereDate('fecha', $this->fecha)
            ->get()
            ->mapWithKeys(fn (Asistencia $a) => [$a->user_id => $a->estado->value])
            ->all();
    }

    /**
     * Usuarios activos que coinciden con la búsqueda.
     *
     * @return Collection<int, User>
     */
    private function buscarUsuarios(): Collection
    {
        return User::query()
            ->where('activo', true)
            ->when($this->busqueda, fn ($q) => $q->where(function ($q) {
                $q->where('nombres', 'like', "%{$this->busqueda}%")
                    ->orWhere('apellidos', 'like', "%{$this->busqueda}%")
                    ->orWhere('documento', 'like', "%{$this->busqueda}%");
            }))
            ->orderBy('nombres')
            ->orderBy('apellidos')
            ->get();
    }

    /**
     * Ids de los usuarios a los que se les toma asistencia desde la tabla general.
     *
     * @return Collection<int, int>
     */
    private function usuariosActivos(): Collection
    {
        return User::query()
            ->where('activo', true)
            ->get()
            ->filter(fn (User $u) => $u->participaEnAsistenciaGeneral())
            ->pluck('id')
            ->values();
    }

    private function autorizar(): void
    {
        abort_unless(auth()->user()->puedeTomarAsistencia(), 403);
    }
};
?>

<div class="flex flex-col gap-6">
    @php
        $estiloOpcion = [
            'presente' => 'peer-checked:bg-huata-verde-600 peer-checked:text-white',
            'tardanza' => 'peer-checked:bg-huata-amarillo-500 peer-checked:text-zinc-900',
            'falta' => 'peer-checked:bg-huata-rojo-500 peer-checked:text-white',
        ];
        $pestanas = [
            'asistencia' => ['icono' => 'calendar-days', 'titulo' => 'Asistencia', 'ayuda' => 'Registra quién vino, quién llegó tarde y quién faltó cada día.'],
            'justificaciones' => ['icono' => 'document-check', 'titulo' => 'Justificaciones', 'ayuda' => 'Revisa los motivos que enviaron los trabajadores por sus faltas o tardanzas.'],
            'acopiadores' => ['icono' => 'truck', 'titulo' => 'Acopiadores', 'ayuda' => 'Los acopiadores marcan su propia asistencia. Aquí eliges qué días y en qué horario.'],
        ];
    @endphp

    {{-- Encabezado y pestañas --}}
    <div class="flex flex-col gap-4">
        <x-ui.titulo titulo="Tomar asistencia">{{ $pestanas[$pestana]['ayuda'] ?? $pestanas['asistencia']['ayuda'] }}</x-ui.titulo>

        <div class="hh-panel grid grid-cols-3 gap-1 p-1 sm:inline-flex sm:w-fit">
            @foreach ($pestanas as $clave => $datos)
                <button
                    type="button"
                    wire:key="pestana-{{ $clave }}"
                    wire:click="$set('pestana', '{{ $clave }}')"
                    @class([
                        'flex items-center justify-center gap-2 rounded-lg px-3 py-2 text-sm font-medium transition sm:px-4',
                        'bg-huata-verde-600 text-white shadow' => $pestana === $clave,
                        'text-zinc-600 hover:bg-huata-verde-50 dark:text-zinc-300 dark:hover:bg-zinc-700' => $pestana !== $clave,
                    ])
                >
                    <flux:icon :name="$datos['icono']" variant="mini" class="shrink-0" />
                    <span class="truncate">{{ $datos['titulo'] }}</span>
                    @if ($clave === 'justificaciones' && $pendientes > 0)
                        <span class="rounded-full bg-huata-amarillo-400 px-2 text-xs font-bold text-zinc-900">{{ $pendientes }}</span>
                    @endif
                </button>
            @endforeach
        </div>
    </div>

    @if ($pestana === 'asistencia')
        {{-- Pasos --}}
        <ol class="grid gap-2 text-sm sm:grid-cols-2 xl:grid-cols-4 animate-huata-in">
            @foreach (['Elige el día en el calendario', 'Presiona "Tomar asistencia"', 'Cambia a quien llegó tarde o faltó', 'Presiona "Guardar"'] as $indice => $paso)
                <li wire:key="paso-{{ $indice }}" class="flex items-center gap-2 rounded-lg bg-white/60 px-3 py-2 dark:bg-zinc-800/60">
                    <span class="flex size-5 shrink-0 items-center justify-center rounded-full bg-huata-verde-600 text-[11px] font-bold text-white">{{ $indice + 1 }}</span>
                    <span class="text-zinc-600 dark:text-zinc-300">{{ $paso }}</span>
                </li>
            @endforeach
        </ol>

        <div class="grid items-start gap-6 lg:grid-cols-[20rem_1fr]">
            {{-- Calendario --}}
            <section class="flex flex-col gap-4 rounded-xl border border-huata-verde-100 p-4 dark:border-zinc-700 huata-cuadro animate-huata-in lg:sticky lg:top-4">
                <div class="flex items-center justify-between">
                    <flux:button size="sm" variant="ghost" icon="chevron-left" wire:click="cambiarMes(-1)" aria-label="Mes anterior" />
                    <flux:heading size="lg" class="capitalize">
                        {{ \Carbon\CarbonImmutable::parse($mes.'-01')->translatedFormat('F Y') }}
                    </flux:heading>
                    <flux:button size="sm" variant="ghost" icon="chevron-right" wire:click="cambiarMes(1)" aria-label="Mes siguiente" />
                </div>

                <div class="flex flex-col gap-1">
                    <div class="grid grid-cols-7 gap-1 text-center text-xs font-semibold text-zinc-500">
                        @foreach (['Lu', 'Ma', 'Mi', 'Ju', 'Vi', 'Sá', 'Do'] as $diaSemana)
                            <div>{{ $diaSemana }}</div>
                        @endforeach
                    </div>

                    <div class="grid grid-cols-7 gap-1">
                        @foreach ($semanas as $semana)
                            @foreach ($semana as $dia)
                                <button
                                    type="button"
                                    wire:key="dia-{{ $dia['fecha'] }}"
                                    wire:click="seleccionarFecha('{{ $dia['fecha'] }}')"
                                    @disabled($dia['esFuturo'])
                                    title="{{ $dia['registrada'] ? 'Asistencia registrada' : 'Sin registrar' }}"
                                    @class([
                                        'relative flex aspect-square flex-col items-center justify-center rounded-lg text-sm transition',
                                        'bg-huata-verde-600 font-bold text-white shadow-md' => $dia['fecha'] === $fecha,
                                        'ring-2 ring-huata-amarillo-400 font-semibold' => $dia['esHoy'] && $dia['fecha'] !== $fecha,
                                        'hover:bg-huata-verde-50 dark:hover:bg-zinc-700' => $dia['fecha'] !== $fecha && ! $dia['esFuturo'],
                                        'text-zinc-300 dark:text-zinc-600' => ! $dia['delMes'] && $dia['fecha'] !== $fecha,
                                        'cursor-not-allowed opacity-40' => $dia['esFuturo'],
                                    ])
                                >
                                    {{ $dia['dia'] }}
                                    @if ($dia['registrada'])
                                        <span class="absolute bottom-1 flex gap-0.5">
                                            <span @class(['size-1.5 rounded-full', 'bg-white' => $dia['fecha'] === $fecha, 'bg-huata-verde-500' => $dia['fecha'] !== $fecha])></span>
                                            @if ($dia['tardanzas'] > 0)
                                                <span class="size-1.5 rounded-full bg-huata-amarillo-500"></span>
                                            @endif
                                            @if ($dia['faltas'] > 0)
                                                <span class="size-1.5 rounded-full bg-huata-rojo-500"></span>
                                            @endif
                                        </span>
                                    @endif
                                </button>
                            @endforeach
                        @endforeach
                    </div>
                </div>

                <div class="grid grid-cols-2 gap-x-3 gap-y-1.5 text-xs text-zinc-500">
                    <span class="flex items-center gap-1.5"><span class="size-2 rounded-full bg-huata-verde-500"></span> Registrada</span>
                    <span class="flex items-center gap-1.5"><span class="size-2.5 rounded ring-2 ring-huata-amarillo-400"></span> Hoy</span>
                    <span class="flex items-center gap-1.5"><span class="size-2 rounded-full bg-huata-amarillo-500"></span> Hubo tardanzas</span>
                    <span class="flex items-center gap-1.5"><span class="size-2 rounded-full bg-huata-rojo-500"></span> Hubo faltas</span>
                </div>

                <flux:button size="sm" variant="subtle" icon="calendar" wire:click="irAHoy">Ir a hoy</flux:button>
            </section>

            {{-- Asistencia del día --}}
            <section
                class="flex flex-col gap-4 animate-huata-in-delay-1"
                x-data="{ contar(estado) { return Object.values($wire.estados ?? {}).filter(e => e === estado).length } }"
            >
                <div class="flex flex-col gap-3 rounded-xl border border-huata-verde-100 p-4 dark:border-zinc-700 huata-cuadro sm:flex-row sm:items-center sm:justify-between">
                    <div class="flex items-center gap-2">
                        <flux:button size="sm" variant="ghost" icon="chevron-left" wire:click="cambiarDia(-1)" aria-label="Día anterior" :disabled="$editando" />
                        <div>
                            <p class="text-base font-semibold capitalize text-huata-verde-700 dark:text-huata-verde-400">
                                {{ $fechaSeleccionada->translatedFormat('l, j \d\e F \d\e Y') }}
                            </p>
                            @if ($editando)
                                <flux:badge color="blue" size="sm" icon="pencil-square">Sin guardar</flux:badge>
                            @elseif ($fechaRegistrada)
                                <flux:badge color="green" size="sm" icon="check-badge">Asistencia registrada</flux:badge>
                            @else
                                <flux:badge color="zinc" size="sm" icon="clock">Sin registrar</flux:badge>
                            @endif
                        </div>
                        <flux:button size="sm" variant="ghost" icon="chevron-right" wire:click="cambiarDia(1)" aria-label="Día siguiente" :disabled="$editando || $fechaSeleccionada->gte(\App\Models\Asistencia::hoy())" />
                    </div>

                    <div class="flex flex-wrap gap-2">
                        @if ($editando)
                            <flux:button variant="ghost" wire:click="cancelar">Cancelar</flux:button>
                            <flux:button variant="primary" icon="check" wire:click="guardar">
                                <span wire:loading.remove wire:target="guardar">Guardar</span>
                                <span wire:loading wire:target="guardar">Guardando...</span>
                            </flux:button>
                        @elseif ($fechaRegistrada)
                            <flux:button icon="pencil-square" wire:click="editar">Editar asistencia</flux:button>
                        @elseif (! $esFutura)
                            <flux:button variant="primary" icon="plus" wire:click="nuevo">Tomar asistencia</flux:button>
                        @endif
                    </div>
                </div>

                @if ($fechaRegistrada || $editando)
                    <div class="grid grid-cols-3 gap-3">
                        <div class="flex items-center gap-3 rounded-xl border border-huata-verde-100 p-3 dark:border-zinc-700 huata-cuadro">
                            <flux:icon.check-circle class="hidden size-6 text-huata-verde-500 sm:block" />
                            <div>
                                <flux:text class="text-xs">Presentes</flux:text>
                                <p class="text-xl font-bold text-huata-verde-600" x-text="contar('presente')">0</p>
                            </div>
                        </div>
                        <div class="flex items-center gap-3 rounded-xl border border-huata-verde-100 p-3 dark:border-zinc-700 huata-cuadro">
                            <flux:icon.clock class="hidden size-6 text-huata-amarillo-500 sm:block" />
                            <div>
                                <flux:text class="text-xs">Tardanzas</flux:text>
                                <p class="text-xl font-bold text-huata-amarillo-600" x-text="contar('tardanza')">0</p>
                            </div>
                        </div>
                        <div class="flex items-center gap-3 rounded-xl border border-huata-verde-100 p-3 dark:border-zinc-700 huata-cuadro">
                            <flux:icon.x-circle class="hidden size-6 text-huata-rojo-500 sm:block" />
                            <div>
                                <flux:text class="text-xs">Faltas</flux:text>
                                <p class="text-xl font-bold text-huata-rojo-500" x-text="contar('falta')">0</p>
                            </div>
                        </div>
                    </div>
                @endif

                @if (! $fechaRegistrada && ! $editando)
                    <div class="flex flex-col items-center gap-3 rounded-xl border border-dashed border-huata-verde-200 p-10 text-center dark:border-zinc-700 huata-cuadro">
                        <flux:icon.clipboard-document-list class="size-10 text-huata-verde-400" />
                        <flux:heading>Aún no se tomó asistencia este día</flux:heading>
                        <flux:text class="max-w-md">Todos empiezan como <strong>presentes</strong>. Solo cambia a quien llegó tarde o faltó y presiona <strong>Guardar</strong>.</flux:text>
                        <flux:button variant="primary" icon="plus" wire:click="nuevo">Tomar asistencia</flux:button>
                    </div>
                @else
                    <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                        <flux:input
                            wire:model.live.debounce.300ms="busqueda"
                            icon="magnifying-glass"
                            placeholder="Buscar por nombre o documento..."
                            class="sm:max-w-sm"
                        />
                        @if ($editando)
                            <div class="flex gap-2">
                                <flux:button size="sm" variant="subtle" icon="check-circle" wire:click="marcarTodos('presente')">Todos presentes</flux:button>
                                <flux:button size="sm" variant="subtle" icon="x-circle" wire:click="marcarTodos('falta')">Todos falta</flux:button>
                            </div>
                        @endif
                    </div>

                    <div class="overflow-x-auto rounded-xl border border-huata-verde-100 px-3 dark:border-zinc-700 huata-cuadro">
                        <flux:table>
                            <flux:table.columns class="bg-white/80 dark:bg-zinc-800/80">
                                <flux:table.column>Nombre y Apellidos</flux:table.column>
                                <flux:table.column>Documento</flux:table.column>
                                <flux:table.column>{{ $editando ? 'Marcar como' : 'Estado' }}</flux:table.column>
                            </flux:table.columns>

                            <flux:table.rows>
                                @forelse ($usuarios as $usuario)
                                    @php $registro = $registrosDelDia->get($usuario->id); @endphp
                                    <flux:table.row wire:key="asistencia-{{ $fecha }}-{{ $usuario->id }}">
                                        <flux:table.cell class="font-medium">
                                            <div class="flex items-center gap-3">
                                                <flux:avatar size="sm" :initials="$usuario->initials()" />
                                                <span class="whitespace-nowrap">{{ $usuario->nombreCompleto() }}</span>
                                            </div>
                                        </flux:table.cell>
                                        <flux:table.cell>{{ $usuario->documento ?? '—' }}</flux:table.cell>
                                        <flux:table.cell>
                                            @if ($editando)
                                                <div class="inline-flex gap-1 rounded-lg bg-zinc-100 p-1 dark:bg-zinc-800">
                                                    @foreach ($opciones as $opcion)
                                                        <label wire:key="opcion-{{ $usuario->id }}-{{ $opcion->value }}" class="cursor-pointer">
                                                            <input
                                                                type="radio"
                                                                class="peer sr-only"
                                                                name="estado-{{ $usuario->id }}"
                                                                value="{{ $opcion->value }}"
                                                                wire:model="estados.{{ $usuario->id }}"
                                                            />
                                                            <span @class([
                                                                'flex items-center gap-1 rounded-md px-3 py-1.5 text-sm font-medium text-zinc-500 transition hover:bg-white peer-checked:shadow peer-focus-visible:ring-2 peer-focus-visible:ring-huata-verde-500 dark:text-zinc-400 dark:hover:bg-zinc-700',
                                                                $estiloOpcion[$opcion->value],
                                                            ])>
                                                                <flux:icon :name="$opcion->icono()" variant="micro" />
                                                                {{ $opcion->label() }}
                                                            </span>
                                                        </label>
                                                    @endforeach
                                                </div>
                                            @elseif ($registro)
                                                <div class="flex flex-wrap items-center gap-2">
                                                    <flux:badge :color="$registro->estado->color()" :icon="$registro->estado->icono()" size="sm">
                                                        {{ $registro->estado->label() }}
                                                    </flux:badge>
                                                    @if ($registro->justificacion)
                                                        <flux:badge :color="$registro->justificacion->estado->color()" size="sm" variant="outline">
                                                            Justificación {{ Str::lower($registro->justificacion->estado->label()) }}
                                                        </flux:badge>
                                                    @endif
                                                </div>
                                            @else
                                                <flux:badge color="zinc" size="sm">Sin registro</flux:badge>
                                            @endif
                                        </flux:table.cell>
                                    </flux:table.row>
                                @empty
                                    <flux:table.row>
                                        <flux:table.cell colspan="3" class="text-center text-zinc-500">
                                            {{ $busqueda ? 'No se encontraron trabajadores.' : 'No hay trabajadores activos.' }}
                                        </flux:table.cell>
                                    </flux:table.row>
                                @endforelse
                            </flux:table.rows>
                        </flux:table>
                    </div>

                    @if ($editando)
                        {{-- Barra fija para no olvidar guardar --}}
                        <div class="sticky bottom-4 z-10 flex flex-col gap-3 rounded-xl border border-huata-verde-200 bg-white/95 p-3 shadow-lg backdrop-blur sm:flex-row sm:items-center sm:justify-between dark:border-zinc-700 dark:bg-zinc-900/95">
                            <flux:text class="text-sm">
                                <span class="font-semibold text-huata-verde-600" x-text="contar('presente')"></span> presentes ·
                                <span class="font-semibold text-huata-amarillo-600" x-text="contar('tardanza')"></span> tardanzas ·
                                <span class="font-semibold text-huata-rojo-500" x-text="contar('falta')"></span> faltas
                            </flux:text>
                            <div class="flex justify-end gap-2">
                                <flux:button variant="ghost" wire:click="cancelar">Cancelar</flux:button>
                                <flux:button variant="primary" icon="check" wire:click="guardar">
                                    <span wire:loading.remove wire:target="guardar">Guardar asistencia</span>
                                    <span wire:loading wire:target="guardar">Guardando...</span>
                                </flux:button>
                            </div>
                        </div>
                    @endif
                @endif
            </section>
        </div>
    @elseif ($pestana === 'justificaciones')
        {{-- Justificaciones --}}
        <section class="flex flex-col gap-4 rounded-xl border border-huata-verde-100 p-4 dark:border-zinc-700 huata-cuadro animate-huata-in">
            <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <div>
                    <flux:heading size="lg">Justificaciones</flux:heading>
                    <flux:text class="text-xs">Presiona "Ver" para leer el motivo y el archivo. Si la aceptas, la falta o tardanza queda justificada.</flux:text>
                </div>
                <div class="flex flex-wrap gap-1 rounded-lg bg-zinc-100 p-1 dark:bg-zinc-800">
                    @foreach (['pendiente' => 'Pendientes', 'aceptada' => 'Aceptadas', 'rechazada' => 'Rechazadas', 'todas' => 'Todas'] as $valor => $etiqueta)
                        <button
                            type="button"
                            wire:key="filtro-{{ $valor }}"
                            wire:click="$set('filtroJustificacion', '{{ $valor }}')"
                            @class([
                                'flex items-center gap-1.5 rounded-md px-3 py-1.5 text-sm font-medium transition',
                                'bg-white text-huata-verde-700 shadow dark:bg-zinc-700 dark:text-huata-verde-300' => $filtroJustificacion === $valor,
                                'text-zinc-500 hover:text-zinc-800 dark:text-zinc-400 dark:hover:text-zinc-200' => $filtroJustificacion !== $valor,
                            ])
                        >
                            {{ $etiqueta }}
                            @if ($valor === 'pendiente' && $pendientes > 0)
                                <span class="rounded-full bg-huata-amarillo-400 px-1.5 text-xs font-bold text-zinc-900">{{ $pendientes }}</span>
                            @endif
                        </button>
                    @endforeach
                </div>
            </div>

            <div class="overflow-x-auto rounded-lg border border-huata-verde-100 px-3 dark:border-zinc-700">
                <flux:table>
                    <flux:table.columns class="bg-white/80 dark:bg-zinc-800/80">
                        <flux:table.column>Trabajador</flux:table.column>
                        <flux:table.column>Fecha</flux:table.column>
                        <flux:table.column>Motivo</flux:table.column>
                        <flux:table.column>Archivo</flux:table.column>
                        <flux:table.column>Estado</flux:table.column>
                        <flux:table.column class="w-px whitespace-nowrap">Acciones</flux:table.column>
                    </flux:table.columns>

                    <flux:table.rows>
                        @forelse ($justificaciones as $justificacion)
                            @php $asistencia = $justificacion->asistencia; @endphp
                            <flux:table.row wire:key="justificacion-{{ $justificacion->id }}">
                                <flux:table.cell>
                                    <div class="flex items-center gap-3">
                                        <flux:avatar size="sm" :initials="$asistencia->user->initials()" />
                                        <div>
                                            <p class="whitespace-nowrap font-medium">{{ $asistencia->user->nombreCompleto() }}</p>
                                            <p class="text-xs text-zinc-500">Doc. {{ $asistencia->user->documento ?? '—' }}</p>
                                        </div>
                                    </div>
                                </flux:table.cell>
                                <flux:table.cell>
                                    <div class="flex flex-col gap-1">
                                        <span class="whitespace-nowrap">
                                            {{ $asistencia->fecha->format('d/m/Y') }}
                                            @if ($asistencia->horario)
                                                <span class="text-xs text-zinc-500">· {{ $asistencia->horario->nombre }}</span>
                                            @endif
                                        </span>
                                        <flux:badge :color="$asistencia->estado->color()" size="sm" class="w-fit">{{ $asistencia->estado->label() }}</flux:badge>
                                    </div>
                                </flux:table.cell>
                                <flux:table.cell class="max-w-xs">
                                    <button type="button" class="line-clamp-2 text-left hover:underline" wire:click="verJustificacion({{ $justificacion->id }})">
                                        {{ $justificacion->motivo }}
                                    </button>
                                </flux:table.cell>
                                <flux:table.cell>
                                    @if ($justificacion->archivo)
                                        @if ($justificacion->archivoEsPdf())
                                            <flux:button size="sm" variant="subtle" icon="document-text" :href="route('asistencia.justificacion.archivo', $justificacion)" target="_blank">PDF</flux:button>
                                        @else
                                            <a href="{{ route('asistencia.justificacion.archivo', $justificacion) }}" target="_blank">
                                                <img src="{{ route('asistencia.justificacion.archivo', $justificacion) }}" alt="Evidencia" class="size-12 rounded-lg object-cover ring-1 ring-huata-verde-100 transition hover:scale-105" />
                                            </a>
                                        @endif
                                    @else
                                        <span class="text-xs text-zinc-400">Sin archivo</span>
                                    @endif
                                </flux:table.cell>
                                <flux:table.cell>
                                    <flux:badge :color="$justificacion->estado->color()" size="sm">{{ $justificacion->estado->label() }}</flux:badge>
                                </flux:table.cell>
                                <flux:table.cell>
                                    <div class="flex gap-1">
                                        <flux:button size="sm" variant="ghost" icon="eye" wire:click="verJustificacion({{ $justificacion->id }})">Ver</flux:button>
                                        @if ($justificacion->estado === \App\Enums\EstadoJustificacion::Pendiente)
                                            <flux:button size="sm" variant="primary" icon="check" wire:click="aceptar({{ $justificacion->id }})">Aceptar</flux:button>
                                            <flux:button size="sm" variant="danger" icon="x-mark" wire:click="rechazar({{ $justificacion->id }})" wire:confirm="¿Rechazar esta justificación?">Rechazar</flux:button>
                                        @endif
                                    </div>
                                </flux:table.cell>
                            </flux:table.row>
                        @empty
                            <flux:table.row>
                                <flux:table.cell colspan="6">
                                    <div class="flex flex-col items-center gap-2 py-8 text-center">
                                        <flux:icon.inbox class="size-8 text-huata-verde-400" />
                                        <flux:text>No hay justificaciones {{ $filtroJustificacion === 'todas' ? '' : $filtroJustificacion.'s' }}.</flux:text>
                                    </div>
                                </flux:table.cell>
                            </flux:table.row>
                        @endforelse
                    </flux:table.rows>
                </flux:table>
            </div>
        </section>
    @else
        {{-- Acopiadores: marcan su asistencia por horario en los días elegidos --}}
        @php
            $diasElegidos = collect($semanasAcopiadores)->flatten(1)->where('delMes', true)->where('seleccionado', true)->count();
            $mesTerminado = \Carbon\CarbonImmutable::parse($mesAcopiadores.'-01')->endOfMonth()->lt(\App\Models\Asistencia::hoy());
        @endphp

        <div class="flex flex-col gap-6">
            <div class="grid items-start gap-6 lg:grid-cols-[22rem_1fr]">
                {{-- Calendario de días de asistencia --}}
                <section class="flex flex-col gap-4 rounded-xl border border-huata-verde-100 p-4 dark:border-zinc-700 huata-cuadro animate-huata-in">
                    <div class="flex items-start justify-between gap-2">
                        <div>
                            <flux:heading size="lg" class="flex items-center gap-2"><span class="flex size-5 items-center justify-center rounded-full bg-huata-verde-600 text-[11px] font-bold text-white">1</span> Días de asistencia</flux:heading>
                            <flux:text class="text-xs">Toca un día para activarlo o quitarlo. Solo los días en verde cuentan.</flux:text>
                        </div>
                        <flux:badge color="green" size="sm">{{ $diasElegidos }} {{ $diasElegidos === 1 ? 'día' : 'días' }}</flux:badge>
                    </div>

                    <div class="flex items-center justify-between">
                        <flux:button size="sm" variant="ghost" icon="chevron-left" wire:click="cambiarMesAcopiadores(-1)" aria-label="Mes anterior" />
                        <flux:heading class="capitalize">
                            {{ \Carbon\CarbonImmutable::parse($mesAcopiadores.'-01')->translatedFormat('F Y') }}
                        </flux:heading>
                        <flux:button size="sm" variant="ghost" icon="chevron-right" wire:click="cambiarMesAcopiadores(1)" aria-label="Mes siguiente" />
                    </div>

                    <div class="flex flex-col gap-1">
                        <div class="grid grid-cols-7 gap-1 text-center text-xs font-semibold text-zinc-500">
                            @foreach (['Lu', 'Ma', 'Mi', 'Ju', 'Vi', 'Sá', 'Do'] as $diaSemana)
                                <div>{{ $diaSemana }}</div>
                            @endforeach
                        </div>

                        <div class="grid grid-cols-7 gap-1 transition" wire:loading.class="opacity-60" wire:target="alternarDiaAcopiador, seleccionarDiasDelMes, cambiarMesAcopiadores">
                            @foreach ($semanasAcopiadores as $semana)
                                @foreach ($semana as $dia)
                                    <button
                                        type="button"
                                        wire:key="dia-acopiador-{{ $dia['fecha'] }}"
                                        wire:click="alternarDiaAcopiador('{{ $dia['fecha'] }}')"
                                        @disabled($dia['esPasado'])
                                        title="{{ $dia['seleccionado'] ? 'Se toma asistencia' : 'No se toma asistencia' }}"
                                        @class([
                                            'relative flex aspect-square items-center justify-center rounded-lg text-sm transition',
                                            'bg-huata-verde-600 font-bold text-white shadow-sm hover:bg-huata-verde-700' => $dia['seleccionado'] && ! $dia['esPasado'],
                                            'bg-huata-verde-100 font-semibold text-huata-verde-700 dark:bg-huata-verde-500/20 dark:text-huata-verde-300' => $dia['seleccionado'] && $dia['esPasado'],
                                            'text-zinc-700 hover:bg-huata-verde-50 dark:text-zinc-200 dark:hover:bg-zinc-700' => ! $dia['seleccionado'] && ! $dia['esPasado'],
                                            'text-zinc-300 dark:text-zinc-600' => ! $dia['seleccionado'] && $dia['esPasado'],
                                            'ring-2 ring-huata-amarillo-400' => $dia['esHoy'],
                                            'opacity-40' => ! $dia['delMes'],
                                            'cursor-not-allowed' => $dia['esPasado'],
                                        ])
                                    >
                                        {{ $dia['dia'] }}
                                        @if ($dia['fecha'] === $fecha)
                                            <span @class([
                                                'absolute bottom-1 size-1.5 rounded-full',
                                                'bg-white' => $dia['seleccionado'] && ! $dia['esPasado'],
                                                'bg-huata-verde-600' => ! $dia['seleccionado'] || $dia['esPasado'],
                                            ])></span>
                                        @endif
                                    </button>
                                @endforeach
                            @endforeach
                        </div>
                    </div>

                    <div class="flex flex-wrap gap-x-4 gap-y-1 text-xs text-zinc-500">
                        <span class="flex items-center gap-1.5"><span class="size-2.5 rounded bg-huata-verde-600"></span> Se toma</span>
                        <span class="flex items-center gap-1.5"><span class="size-2.5 rounded ring-2 ring-huata-amarillo-400"></span> Hoy</span>
                        <span class="flex items-center gap-1.5"><span class="size-1.5 rounded-full bg-huata-verde-600"></span> Día que estás viendo</span>
                    </div>

                    <flux:separator variant="subtle" />

                    <div class="flex flex-col gap-2">
                        <flux:text class="text-xs">Elegir rápido los días que quedan del mes:</flux:text>
                        <div class="grid grid-cols-3 gap-2">
                            <flux:button size="sm" variant="subtle" wire:click="seleccionarDiasDelMes('laborables')" :disabled="$mesTerminado">Lun – Sáb</flux:button>
                            <flux:button size="sm" variant="subtle" wire:click="seleccionarDiasDelMes('todos')" :disabled="$mesTerminado">Todos</flux:button>
                            <flux:button size="sm" variant="subtle" wire:click="seleccionarDiasDelMes('ninguno')" wire:confirm="¿Quitar los días que faltan de este mes?" :disabled="$mesTerminado">Ninguno</flux:button>
                        </div>
                    </div>
                </section>

                <div class="flex min-w-0 flex-col gap-6">
                    {{-- Horarios --}}
                    <section class="flex flex-col gap-4 rounded-xl border border-huata-verde-100 p-4 dark:border-zinc-700 huata-cuadro animate-huata-in-delay-1">
                        <div class="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                            <div>
                                <flux:heading size="lg" class="flex items-center gap-2"><span class="flex size-5 items-center justify-center rounded-full bg-huata-verde-600 text-[11px] font-bold text-white">2</span> Horarios</flux:heading>
                                <flux:text class="text-xs">Horas en que el acopiador puede marcar. Si no marca a tiempo, queda con falta.</flux:text>
                            </div>
                            <flux:button size="sm" variant="primary" icon="plus" wire:click="nuevoHorario" class="shrink-0">Nuevo horario</flux:button>
                        </div>

                        <div class="grid gap-3 md:grid-cols-2">
                            @forelse ($horarios as $horario)
                                <div wire:key="horario-{{ $horario->id }}" @class([
                                    'flex flex-col gap-3 rounded-lg border border-huata-verde-100 bg-white/60 p-3 dark:border-zinc-700 dark:bg-zinc-800/60',
                                    'opacity-60' => ! $horario->activo,
                                ])>
                                    <div class="flex items-center justify-between gap-2">
                                        <div class="flex min-w-0 items-center gap-2">
                                            <flux:icon :name="$horario->inicio() < '12:00' ? 'sun' : 'moon'" class="size-5 shrink-0 text-huata-amarillo-500" />
                                            <flux:heading class="truncate">{{ $horario->nombre }}</flux:heading>
                                            @unless ($horario->activo)
                                                <flux:badge size="sm" color="zinc">Inactivo</flux:badge>
                                            @endunless
                                        </div>
                                        <div class="flex shrink-0 gap-1">
                                            <flux:tooltip content="Editar">
                                                <flux:button size="sm" variant="ghost" icon="pencil-square" wire:click="editarHorario({{ $horario->id }})" aria-label="Editar horario" />
                                            </flux:tooltip>
                                            <flux:tooltip content="Eliminar">
                                                <flux:button size="sm" variant="ghost" icon="trash" class="text-huata-rojo-500!" wire:click="confirmarEliminarHorario({{ $horario->id }})" aria-label="Eliminar horario" />
                                            </flux:tooltip>
                                        </div>
                                    </div>
                                    <div class="grid grid-cols-3 gap-2 text-center text-xs">
                                        <div class="rounded-lg bg-huata-verde-50 px-2 py-1.5 dark:bg-huata-verde-500/10">
                                            <p class="flex items-center justify-center gap-1 font-medium text-huata-verde-700 dark:text-huata-verde-400"><flux:icon.check-circle variant="micro" /> Presente</p>
                                            <p class="mt-0.5 font-semibold">{{ $horario->inicio() }} – {{ $horario->limitePresente() }}</p>
                                        </div>
                                        <div class="rounded-lg bg-huata-amarillo-400/15 px-2 py-1.5">
                                            <p class="flex items-center justify-center gap-1 font-medium text-huata-amarillo-600"><flux:icon.clock variant="micro" /> Tardanza</p>
                                            <p class="mt-0.5 font-semibold">{{ $horario->inicioTardanza() }} – {{ $horario->limiteTardanza() }}</p>
                                        </div>
                                        <div class="rounded-lg bg-huata-rojo-500/10 px-2 py-1.5">
                                            <p class="flex items-center justify-center gap-1 font-medium text-huata-rojo-600"><flux:icon.x-circle variant="micro" /> Falta</p>
                                            <p class="mt-0.5 font-semibold">Si no marca</p>
                                        </div>
                                    </div>
                                </div>
                            @empty
                                <div class="col-span-full flex flex-col items-center gap-2 rounded-lg border border-dashed border-huata-verde-200 p-8 text-center dark:border-zinc-700">
                                    <flux:icon.clock class="size-8 text-huata-verde-400" />
                                    <flux:text>Todavía no hay horarios. Crea uno, por ejemplo "Mañana" de 05:00 a 05:20.</flux:text>
                                </div>
                            @endforelse
                        </div>
                    </section>

                    {{-- Asistencia del día --}}
                    <section class="flex flex-col gap-4 rounded-xl border border-huata-verde-100 p-4 dark:border-zinc-700 huata-cuadro animate-huata-in-delay-1">
                        <flux:heading size="lg" class="flex items-center gap-2"><span class="flex size-5 items-center justify-center rounded-full bg-huata-verde-600 text-[11px] font-bold text-white">3</span> Asistencia del día</flux:heading>

                        <div class="flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
                            <div class="flex flex-col gap-2">
                                <div class="flex items-center gap-1">
                                    <flux:button size="sm" variant="ghost" icon="chevron-left" wire:click="cambiarDia(-1)" aria-label="Día anterior" />
                                    <span class="px-1 text-center text-base font-semibold capitalize text-huata-verde-700 dark:text-huata-verde-400">
                                        {{ $fechaSeleccionada->translatedFormat('l, j \d\e F') }}
                                    </span>
                                    <flux:button size="sm" variant="ghost" icon="chevron-right" wire:click="cambiarDia(1)" :disabled="$fechaSeleccionada->gte(\App\Models\Asistencia::hoy())" aria-label="Día siguiente" />
                                    <flux:button size="sm" variant="subtle" wire:click="irAHoy" class="ms-1">Hoy</flux:button>
                                </div>
                                <div>
                                    @if ($esDiaAcopiador)
                                        <flux:badge color="green" size="sm" icon="check-badge">Día de asistencia</flux:badge>
                                    @else
                                        <flux:badge color="zinc" size="sm" icon="calendar">No se toma asistencia este día</flux:badge>
                                    @endif
                                </div>
                            </div>
                            <flux:input
                                wire:model.live.debounce.300ms="busqueda"
                                icon="magnifying-glass"
                                placeholder="Buscar acopiador..."
                                class="xl:max-w-xs"
                            />
                        </div>

                        <div class="overflow-x-auto rounded-lg border border-huata-verde-100 px-3 dark:border-zinc-700">
                            <flux:table>
                                <flux:table.columns class="bg-white/80 dark:bg-zinc-800/80">
                                    <flux:table.column>Nombre y Apellidos</flux:table.column>
                                    <flux:table.column>Documento</flux:table.column>
                                    @foreach ($horariosActivos as $horario)
                                        <flux:table.column wire:key="col-horario-{{ $horario->id }}">{{ $horario->nombre }}</flux:table.column>
                                    @endforeach
                                    <flux:table.column class="w-px whitespace-nowrap">Acciones</flux:table.column>
                                </flux:table.columns>

                                <flux:table.rows>
                                    @forelse ($acopiadores as $acopiador)
                                        @php $marcas = $marcasAcopiadores->get($acopiador->id, collect()); @endphp
                                        <flux:table.row wire:key="acopiador-{{ $fecha }}-{{ $acopiador->id }}">
                                            <flux:table.cell class="font-medium">
                                                <div class="flex items-center gap-3">
                                                    <flux:avatar size="sm" :initials="$acopiador->initials()" />
                                                    <span class="whitespace-nowrap">{{ $acopiador->nombreCompleto() }}</span>
                                                </div>
                                            </flux:table.cell>
                                            <flux:table.cell>{{ $acopiador->documento ?? '—' }}</flux:table.cell>
                                            @foreach ($horariosActivos as $horario)
                                                @php $marca = $marcas->get($horario->id); @endphp
                                                <flux:table.cell wire:key="marca-{{ $acopiador->id }}-{{ $horario->id }}">
                                                    @if ($marca)
                                                        <div class="flex items-center gap-2">
                                                            <flux:badge :color="$marca->estado->color()" :icon="$marca->estado->icono()" size="sm">{{ $marca->estado->label() }}</flux:badge>
                                                            @if ($marca->horaMarcada())
                                                                <span class="text-xs text-zinc-500">{{ $marca->horaMarcada() }}</span>
                                                            @endif
                                                        </div>
                                                    @elseif (! $esDiaAcopiador)
                                                        <span class="text-xs text-zinc-400">No se toma</span>
                                                    @elseif ($horario->yaCerro($fecha, \App\Models\Asistencia::ahora()))
                                                        <flux:badge color="zinc" size="sm">Sin registro</flux:badge>
                                                    @else
                                                        <flux:badge color="blue" size="sm" icon="clock">Pendiente</flux:badge>
                                                    @endif
                                                </flux:table.cell>
                                            @endforeach
                                            <flux:table.cell>
                                                <flux:button size="sm" variant="subtle" icon="pencil-square" wire:click="editarAcopiador({{ $acopiador->id }})" :disabled="$horariosActivos->isEmpty()">
                                                    Editar
                                                </flux:button>
                                            </flux:table.cell>
                                        </flux:table.row>
                                    @empty
                                        <flux:table.row>
                                            <flux:table.cell colspan="{{ 3 + $horariosActivos->count() }}" class="text-center text-zinc-500">
                                                {{ $busqueda ? 'No se encontraron acopiadores.' : 'No hay acopiadores activos.' }}
                                            </flux:table.cell>
                                        </flux:table.row>
                                    @endforelse
                                </flux:table.rows>
                            </flux:table>
                        </div>
                    </section>
                </div>
            </div>
        </div>
    @endif

    <x-confirm-modal
        name="confirmar-eliminar-horario"
        accion="eliminarHorario"
        titulo="¿Eliminar este horario?"
        mensaje="También se borrarán las asistencias y justificaciones de los acopiadores en este horario. Si solo quieres dejar de usarlo, edítalo y desactívalo."
    />

    <flux:modal name="horario-form" class="md:w-[28rem]">
        <form wire:submit="guardarHorario" class="flex flex-col gap-5">
            <div>
                <flux:heading size="lg">{{ $horarioId ? 'Editar horario' : 'Nuevo horario' }}</flux:heading>
                <flux:text class="text-xs">Quien marca antes de "Presente hasta" queda presente; antes de "Tardanza hasta", con tardanza. Si no marca, falta.</flux:text>
            </div>

            <flux:input label="Nombre" wire:model="horarioNombre" placeholder="Mañana" />
            <flux:input label="Hora de inicio" type="time" wire:model="horaInicio" />
            <div class="grid grid-cols-2 gap-3">
                <flux:input label="Presente hasta" type="time" wire:model="horaLimitePresente" />
                <flux:input label="Tardanza hasta" type="time" wire:model="horaLimiteTardanza" />
            </div>
            <flux:switch label="Horario activo" wire:model="horarioActivo" />

            <div class="flex justify-end gap-2">
                <flux:modal.close>
                    <flux:button variant="ghost">Cancelar</flux:button>
                </flux:modal.close>
                <flux:button type="submit" variant="primary">Guardar</flux:button>
            </div>
        </form>
    </flux:modal>

    <flux:modal name="acopiador-form" class="md:w-[30rem]">
        <form wire:submit="guardarAcopiador" class="flex flex-col gap-5">
            <div>
                <flux:heading size="lg">{{ $acopiadorNombre }}</flux:heading>
                <flux:subheading class="capitalize">{{ \Carbon\CarbonImmutable::parse($fecha)->translatedFormat('l, j \d\e F \d\e Y') }}</flux:subheading>
            </div>

            @foreach ($horariosActivos as $horario)
                <flux:select wire:key="estado-acopiador-{{ $horario->id }}" :label="$horario->nombre.' ('.$horario->inicio().' – '.$horario->limiteTardanza().')'" wire:model="estadosAcopiador.{{ $horario->id }}">
                    <flux:select.option value="">Sin registro</flux:select.option>
                    @foreach ($opciones as $opcion)
                        <flux:select.option value="{{ $opcion->value }}">{{ $opcion->label() }}</flux:select.option>
                    @endforeach
                </flux:select>
            @endforeach

            <div class="flex justify-end gap-2">
                <flux:modal.close>
                    <flux:button variant="ghost">Cancelar</flux:button>
                </flux:modal.close>
                <flux:button type="submit" variant="primary">Guardar</flux:button>
            </div>
        </form>
    </flux:modal>

    <flux:modal name="justificacion-detalle" class="md:w-[36rem]">
        @if ($justificacionDetalle)
            @php $asistencia = $justificacionDetalle->asistencia; @endphp
            <div class="flex flex-col gap-4">
                <div>
                    <flux:heading size="lg">{{ $asistencia->user->nombreCompleto() }}</flux:heading>
                    <flux:subheading>
                        {{ $asistencia->estado->label() }} del {{ $asistencia->fecha->translatedFormat('l j \d\e F') }} · Doc. {{ $asistencia->user->documento ?? '—' }}
                    </flux:subheading>
                </div>

                <div class="rounded-lg bg-zinc-50 p-3 text-sm whitespace-pre-line dark:bg-zinc-800">{{ $justificacionDetalle->motivo }}</div>

                @if ($justificacionDetalle->archivo)
                    @if ($justificacionDetalle->archivoEsPdf())
                        <iframe src="{{ route('asistencia.justificacion.archivo', $justificacionDetalle) }}" class="h-96 w-full rounded-lg border border-huata-verde-100"></iframe>
                    @else
                        <img src="{{ route('asistencia.justificacion.archivo', $justificacionDetalle) }}" alt="Evidencia" class="max-h-96 w-full rounded-lg object-contain" />
                    @endif
                @endif

                <div class="flex items-center justify-between gap-2">
                    <flux:badge :color="$justificacionDetalle->estado->color()">{{ $justificacionDetalle->estado->label() }}</flux:badge>
                    @if ($justificacionDetalle->estado === \App\Enums\EstadoJustificacion::Pendiente)
                        <div class="flex gap-2">
                            <flux:button variant="danger" icon="x-mark" wire:click="rechazar({{ $justificacionDetalle->id }})">Rechazar</flux:button>
                            <flux:button variant="primary" icon="check" wire:click="aceptar({{ $justificacionDetalle->id }})">Aceptar</flux:button>
                        </div>
                    @else
                        <flux:modal.close>
                            <flux:button variant="ghost">Cerrar</flux:button>
                        </flux:modal.close>
                    @endif
                </div>
            </div>
        @endif
    </flux:modal>
</div>

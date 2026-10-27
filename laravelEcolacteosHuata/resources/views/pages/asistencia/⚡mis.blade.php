<?php

use App\Enums\EstadoAsistencia;
use App\Enums\EstadoJustificacion;
use App\Models\Asistencia;
use App\Models\DiaAsistenciaAcopiador;
use App\Models\HorarioAsistencia;
use App\Models\Justificacion;
use Carbon\CarbonImmutable;
use Flux\Flux;
use Illuminate\Support\Facades\Storage;
use Livewire\Component;
use Livewire\WithFileUploads;

new class extends Component
{
    use WithFileUploads;

    /** Mes que se está viendo (Y-m). */
    public string $mes = '';

    public ?int $asistenciaId = null;

    public string $motivo = '';

    /** @var \Livewire\Features\SupportFileUploads\TemporaryUploadedFile|null */
    public $archivo = null;

    public function mount(): void
    {
        abort_if(auth()->user()->rol === null, 403);

        if (auth()->user()->esAcopiador()) {
            HorarioAsistencia::registrarFaltasVencidas();
        }

        $this->mes = Asistencia::hoy()->format('Y-m');
    }

    public function with(): array
    {
        $inicioMes = CarbonImmutable::parse("{$this->mes}-01");

        $asistencias = Asistencia::query()
            ->with('justificacion', 'horario')
            ->where('user_id', auth()->id())
            ->whereDate('fecha', '>=', $inicioMes)
            ->whereDate('fecha', '<=', $inicioMes->endOfMonth())
            ->orderByDesc('fecha')
            ->orderByDesc('hora_marcada')
            ->get();

        $esAcopiador = auth()->user()->esAcopiador();
        $ahora = Asistencia::ahora();

        return [
            'inicioMes' => $inicioMes,
            'asistencias' => $asistencias,
            'presentes' => $asistencias->where('estado', EstadoAsistencia::Presente)->count(),
            'tardanzas' => $asistencias->where('estado', EstadoAsistencia::Tardanza)->count(),
            'faltas' => $asistencias->where('estado', EstadoAsistencia::Falta)->count(),
            'porJustificar' => Asistencia::query()
                ->with('justificacion')
                ->where('user_id', auth()->id())
                ->whereIn('estado', [EstadoAsistencia::Falta, EstadoAsistencia::Tardanza])
                ->whereDate('fecha', '>=', Asistencia::hoy()->subDays(Asistencia::DIAS_PARA_JUSTIFICAR))
                ->get()
                ->filter(fn (Asistencia $a) => $a->puedeJustificarse())
                ->count(),
            'asistenciaAJustificar' => $this->asistenciaId ? Asistencia::find($this->asistenciaId) : null,
            'esAcopiador' => $esAcopiador,
            'ahora' => $ahora,
            'horariosHoy' => $esAcopiador
                ? HorarioAsistencia::query()->where('activo', true)->orderBy('hora_inicio')->get()
                : collect(),
            'marcasHoy' => $esAcopiador
                ? Asistencia::query()
                    ->where('user_id', auth()->id())
                    ->whereNotNull('horario_asistencia_id')
                    ->whereDate('fecha', $ahora->toDateString())
                    ->get()
                    ->keyBy('horario_asistencia_id')
                : collect(),
            'horarioAbierto' => $esAcopiador ? HorarioAsistencia::abiertoA($ahora) : null,
            'hoyEsDiaDeAsistencia' => $esAcopiador && DiaAsistenciaAcopiador::esDiaDeAsistencia($ahora->toDateString()),
        ];
    }

    /**
     * El acopiador marca su asistencia en el horario que está abierto en este momento: presente
     * o tardanza según la hora.
     */
    public function marcarAsistencia(): void
    {
        abort_unless(auth()->user()->esAcopiador(), 403);

        $ahora = Asistencia::ahora();
        $horario = HorarioAsistencia::abiertoA($ahora);

        if (! $horario) {
            Flux::toast('No hay un horario de asistencia abierto en este momento.', variant: 'warning');

            return;
        }

        $yaMarco = Asistencia::query()
            ->where('user_id', auth()->id())
            ->where('horario_asistencia_id', $horario->id)
            ->whereDate('fecha', $ahora->toDateString())
            ->exists();

        if ($yaMarco) {
            Flux::toast("Ya marcaste tu asistencia de {$horario->nombre}.", variant: 'warning');

            return;
        }

        $asistencia = Asistencia::create([
            'user_id' => auth()->id(),
            'fecha' => $ahora->toDateString(),
            'horario_asistencia_id' => $horario->id,
            'estado' => $horario->estadoParaHora($ahora->format('H:i')),
            'hora_marcada' => $ahora->format('H:i:s'),
        ]);

        Flux::toast(
            "¡Asistencia marcada! {$asistencia->estado->label()} a las {$ahora->format('H:i')} ({$horario->nombre}).",
            variant: $asistencia->estado === EstadoAsistencia::Presente ? 'success' : 'warning',
        );
    }

    public function cambiarMes(int $desplazamiento): void
    {
        $this->mes = CarbonImmutable::parse("{$this->mes}-01")->addMonths($desplazamiento)->format('Y-m');
    }

    public function abrirJustificar(int $id): void
    {
        $asistencia = $this->asistenciaPropia($id);

        if (! $asistencia->puedeJustificarse()) {
            Flux::toast('Esta asistencia ya no se puede justificar.', variant: 'warning');

            return;
        }

        $this->asistenciaId = $asistencia->id;
        $this->reset(['motivo', 'archivo']);
        $this->resetValidation();
        Flux::modal('justificar-form')->show();
    }

    public function justificar(): void
    {
        $data = $this->validate([
            'motivo' => 'required|string|min:10|max:1000',
            'archivo' => 'nullable|file|mimes:pdf,jpg,jpeg,png,webp|max:5120',
        ], [], [
            'motivo' => 'contexto',
            'archivo' => 'archivo',
        ]);

        $asistencia = $this->asistenciaPropia((int) $this->asistenciaId);

        if (! $asistencia->puedeJustificarse()) {
            $this->addError('motivo', 'El plazo de una semana para justificar ya venció.');

            return;
        }

        $anterior = $asistencia->justificacion;
        $ruta = $this->archivo?->store('justificaciones', 'local');

        if ($anterior?->archivo) {
            Storage::disk('local')->delete($anterior->archivo);
        }

        Justificacion::updateOrCreate(['asistencia_id' => $asistencia->id], [
            'motivo' => $data['motivo'],
            'archivo' => $ruta,
            'estado' => EstadoJustificacion::Pendiente,
            'revisado_por' => null,
            'revisado_at' => null,
        ]);

        $this->reset(['asistenciaId', 'motivo', 'archivo']);
        Flux::modal('justificar-form')->close();
        Flux::toast('Justificación enviada. Te avisaremos cuando sea revisada.', variant: 'success');
    }

    private function asistenciaPropia(int $id): Asistencia
    {
        return Asistencia::query()->where('user_id', auth()->id())->findOrFail($id);
    }
};
?>

<div class="flex flex-col gap-6">
    <div class="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <x-ui.titulo titulo="Asistencia">Revisa tus asistencias y justifica tus faltas o tardanzas dentro de una semana.</x-ui.titulo>

        <div class="flex items-center gap-2 rounded-xl bg-white/70 p-1 dark:bg-zinc-800/70">
            <flux:button size="sm" variant="ghost" icon="chevron-left" wire:click="cambiarMes(-1)" aria-label="Mes anterior" />
            <span class="min-w-36 text-center font-semibold capitalize">{{ $inicioMes->translatedFormat('F Y') }}</span>
            <flux:button size="sm" variant="ghost" icon="chevron-right" wire:click="cambiarMes(1)" aria-label="Mes siguiente" />
        </div>
    </div>

    @if ($esAcopiador)
        @php $yaMarcoAbierto = $horarioAbierto && $marcasHoy->has($horarioAbierto->id); @endphp
        <div wire:poll.30s class="flex flex-col gap-4 rounded-xl border border-huata-verde-100 p-5 dark:border-zinc-700 huata-cuadro animate-huata-in md:flex-row md:items-center md:justify-between">
            <div class="flex flex-col gap-3">
                <div>
                    <flux:text class="text-xs uppercase tracking-wide">Hora actual</flux:text>
                    <p class="text-3xl font-bold text-huata-verde-700 dark:text-huata-verde-400">{{ $ahora->format('H:i') }}</p>
                </div>
                <div class="flex flex-wrap gap-2">
                    @forelse ($horariosHoy as $horario)
                        @php $marca = $marcasHoy->get($horario->id); @endphp
                        <div wire:key="hoy-{{ $horario->id }}" class="flex items-center gap-2 rounded-lg bg-white/70 px-3 py-2 text-sm dark:bg-zinc-800/70">
                            <span class="font-medium">{{ $horario->nombre }}</span>
                            <span class="text-xs text-zinc-500">{{ $horario->inicio() }} – {{ $horario->limiteTardanza() }}</span>
                            @if ($marca)
                                <flux:badge :color="$marca->estado->color()" size="sm">
                                    {{ $marca->estado->label() }}{{ $marca->horaMarcada() ? ' · '.$marca->horaMarcada() : '' }}
                                </flux:badge>
                            @elseif (! $hoyEsDiaDeAsistencia)
                                <flux:badge color="zinc" size="sm">Hoy no se marca</flux:badge>
                            @elseif ($horarioAbierto?->id === $horario->id)
                                <flux:badge color="blue" size="sm" icon="bell-alert">Abierto ahora</flux:badge>
                            @elseif ($horario->yaCerro($ahora->toDateString(), $ahora))
                                <flux:badge color="red" size="sm">Falta</flux:badge>
                            @else
                                <flux:badge color="zinc" size="sm">Pendiente</flux:badge>
                            @endif
                        </div>
                    @empty
                        <flux:text>El administrador todavía no definió horarios de asistencia.</flux:text>
                    @endforelse
                </div>
            </div>

            <div class="flex flex-col items-center gap-2">
                <button
                    type="button"
                    wire:click="marcarAsistencia"
                    wire:loading.attr="disabled"
                    @disabled(! $horarioAbierto || $yaMarcoAbierto)
                    @class([
                        'flex size-36 flex-col items-center justify-center gap-1 rounded-full text-lg font-bold shadow-lg transition',
                        'bg-huata-verde-600 text-white hover:scale-105 hover:bg-huata-verde-700 animate-pulse' => $horarioAbierto && ! $yaMarcoAbierto,
                        'cursor-not-allowed bg-zinc-200 text-zinc-500 dark:bg-zinc-700 dark:text-zinc-400' => ! $horarioAbierto || $yaMarcoAbierto,
                    ])
                >
                    <flux:icon :name="$yaMarcoAbierto ? 'check-badge' : 'finger-print'" class="size-10" />
                    {{ $yaMarcoAbierto ? 'Marcado' : 'Asistí' }}
                </button>
                <flux:text class="max-w-56 text-center text-xs">
                    @if ($yaMarcoAbierto)
                        Ya marcaste tu asistencia de {{ $horarioAbierto->nombre }}.
                    @elseif ($horarioAbierto)
                        @if ($horarioAbierto->estadoParaHora($ahora->format('H:i')) === \App\Enums\EstadoAsistencia::Presente)
                            Marca antes de las {{ $horarioAbierto->limitePresente() }} para quedar como presente.
                        @else
                            Ya es tardanza. Tienes hasta las {{ $horarioAbierto->limiteTardanza() }}.
                        @endif
                    @elseif (! $hoyEsDiaDeAsistencia)
                        Hoy no es día de asistencia.
                    @else
                        El botón se activa cuando empieza tu horario.
                    @endif
                </flux:text>
            </div>
        </div>
    @endif

    @if ($porJustificar > 0)
        <flux:callout icon="exclamation-triangle" color="amber" class="animate-huata-in">
            <flux:callout.heading>Tienes {{ $porJustificar }} {{ $porJustificar === 1 ? 'falta o tardanza' : 'faltas o tardanzas' }} por justificar</flux:callout.heading>
            <flux:callout.text>Recuerda que solo tienes {{ \App\Models\Asistencia::DIAS_PARA_JUSTIFICAR }} días desde la fecha para enviar tu justificación.</flux:callout.text>
        </flux:callout>
    @endif

    <div class="grid grid-cols-3 gap-4">
        <div class="animate-huata-in rounded-xl border border-huata-verde-100 p-5 dark:border-zinc-700 huata-cuadro">
            <flux:text class="flex items-center gap-1"><flux:icon.check-circle variant="mini" class="text-huata-verde-600" /> Presentes</flux:text>
            <p class="mt-1 text-3xl font-bold text-huata-verde-700">{{ $presentes }}</p>
        </div>
        <div class="animate-huata-in-delay-1 rounded-xl border border-huata-verde-100 p-5 dark:border-zinc-700 huata-cuadro">
            <flux:text class="flex items-center gap-1"><flux:icon.clock variant="mini" class="text-huata-amarillo-500" /> Tardanzas</flux:text>
            <p class="mt-1 text-3xl font-bold text-huata-amarillo-600">{{ $tardanzas }}</p>
        </div>
        <div class="animate-huata-in-delay-2 rounded-xl border border-huata-verde-100 p-5 dark:border-zinc-700 huata-cuadro">
            <flux:text class="flex items-center gap-1"><flux:icon.x-circle variant="mini" class="text-huata-rojo-500" /> Faltas</flux:text>
            <p class="mt-1 text-3xl font-bold text-huata-rojo-500">{{ $faltas }}</p>
        </div>
    </div>

    <div class="overflow-x-auto rounded-xl border border-huata-verde-100 px-3 dark:border-zinc-700 huata-cuadro animate-huata-in-delay-2">
        <flux:table>
            <flux:table.columns class="bg-white/80 dark:bg-zinc-800/80">
                <flux:table.column>Fecha</flux:table.column>
                @if ($esAcopiador)
                    <flux:table.column>Horario</flux:table.column>
                @endif
                <flux:table.column>Estado</flux:table.column>
                <flux:table.column>Acciones</flux:table.column>
            </flux:table.columns>

            <flux:table.rows>
                @forelse ($asistencias as $asistencia)
                    <flux:table.row wire:key="mi-asistencia-{{ $asistencia->id }}">
                        <flux:table.cell class="font-medium capitalize">
                            {{ $asistencia->fecha->translatedFormat('l, j \d\e F') }}
                        </flux:table.cell>
                        @if ($esAcopiador)
                            <flux:table.cell>
                                {{ $asistencia->horario?->nombre ?? '—' }}
                                @if ($asistencia->horaMarcada())
                                    <span class="text-xs text-zinc-500">· {{ $asistencia->horaMarcada() }}</span>
                                @endif
                            </flux:table.cell>
                        @endif
                        <flux:table.cell>
                            <flux:badge :color="$asistencia->estado->color()" :icon="$asistencia->estado->icono()" size="sm">
                                {{ $asistencia->estado->label() }}
                            </flux:badge>
                        </flux:table.cell>
                        <flux:table.cell>
                            @if (! $asistencia->estado->esJustificable())
                                <span class="text-xs text-zinc-400">—</span>
                            @elseif ($asistencia->puedeJustificarse())
                                <div class="flex flex-wrap items-center gap-2">
                                    <flux:button size="sm" variant="primary" icon="document-plus" wire:click="abrirJustificar({{ $asistencia->id }})">
                                        {{ $asistencia->justificacion ? 'Volver a justificar' : 'Justificar' }}
                                    </flux:button>
                                    @if ($asistencia->justificacion)
                                        <flux:badge color="red" size="sm">Rechazada</flux:badge>
                                    @endif
                                    <span class="text-xs text-zinc-500">
                                        @php $restantes = $asistencia->diasRestantesParaJustificar(); @endphp
                                        {{ $restantes === 0 ? 'Último día para justificar' : "Quedan {$restantes} ".($restantes === 1 ? 'día' : 'días') }}
                                    </span>
                                </div>
                            @elseif ($asistencia->justificacion)
                                <flux:badge :color="$asistencia->justificacion->estado->color()" size="sm">
                                    Justificación {{ Str::lower($asistencia->justificacion->estado->label()) }}
                                </flux:badge>
                            @else
                                <flux:badge color="zinc" size="sm" icon="lock-closed">Plazo vencido</flux:badge>
                            @endif
                        </flux:table.cell>
                    </flux:table.row>
                @empty
                    <flux:table.row>
                        <flux:table.cell colspan="{{ $esAcopiador ? 4 : 3 }}" class="text-center text-zinc-500">
                            No tienes asistencias registradas en este mes.
                        </flux:table.cell>
                    </flux:table.row>
                @endforelse
            </flux:table.rows>
        </flux:table>
    </div>

    <flux:modal name="justificar-form" class="md:w-[30rem]">
        <form wire:submit="justificar" class="flex flex-col gap-5">
            <div>
                <flux:heading size="lg">Justificar {{ Str::lower($asistenciaAJustificar?->estado->label() ?? 'falta') }}</flux:heading>
                @if ($asistenciaAJustificar)
                    <flux:subheading class="capitalize">{{ $asistenciaAJustificar->fecha->translatedFormat('l, j \d\e F \d\e Y') }}</flux:subheading>
                @endif
            </div>

            <flux:textarea
                label="Contexto"
                wire:model="motivo"
                rows="4"
                placeholder="Explica el motivo de tu falta o tardanza..."
            />

            <div class="flex flex-col gap-2">
                <flux:label>PDF o imagen (opcional)</flux:label>
                <label class="flex cursor-pointer flex-col items-center gap-2 rounded-lg border-2 border-dashed border-huata-verde-200 p-5 text-center transition hover:bg-huata-verde-50 dark:border-zinc-600 dark:hover:bg-zinc-800">
                    <flux:icon.arrow-up-tray class="size-6 text-huata-verde-500" />
                    <span class="text-sm text-zinc-600 dark:text-zinc-300">
                        @if ($archivo)
                            {{ $archivo->getClientOriginalName() }}
                        @else
                            Haz clic para subir una constancia, receta médica, foto...
                        @endif
                    </span>
                    <span class="text-xs text-zinc-400">PDF, JPG, PNG o WEBP · máximo 5 MB</span>
                    <input type="file" wire:model="archivo" accept=".pdf,image/png,image/jpeg,image/webp" class="sr-only" />
                </label>
                <div wire:loading wire:target="archivo" class="text-xs text-zinc-500">Subiendo archivo...</div>
                <flux:error name="archivo" />
            </div>

            <div class="flex justify-end gap-2">
                <flux:modal.close>
                    <flux:button variant="ghost">Cancelar</flux:button>
                </flux:modal.close>
                <flux:button type="submit" variant="primary" icon="paper-airplane" wire:loading.attr="disabled" wire:target="archivo,justificar">
                    Enviar justificación
                </flux:button>
            </div>
        </form>
    </flux:modal>
</div>

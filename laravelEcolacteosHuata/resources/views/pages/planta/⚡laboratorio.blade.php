<?php

use App\Models\Proveedor;
use App\Models\Recojo;
use App\Models\ResultadoLaboratorio;
use App\Models\Turno;
use Flux\Flux;
use Illuminate\Support\Collection;
use Livewire\Attributes\Computed;
use Livewire\Attributes\Validate;
use Livewire\Component;

new class extends Component
{
    public ?int $resultadoId = null;

    public ?int $enviarProduccionId = null;

    public ?int $detalleId = null;

    #[Validate('required|exists:proveedores,id')]
    public ?int $proveedorId = null;

    #[Validate('required|date')]
    public string $fecha = '';

    public ?int $turnoId = null;

    #[Validate('required|numeric|min:0.01')]
    public string $litros = '';

    #[Validate('nullable|numeric')]
    public string $densidad = '';

    // Datos del ticket del analizador SmartScan AI.
    #[Validate('nullable|string|max:30')]
    public string $sn = '';

    #[Validate('nullable|string|max:20')]
    public string $modo = '';

    #[Validate('nullable|date_format:H:i')]
    public string $horaAnalisis = '';

    #[Validate('nullable|date')]
    public string $fechaAnalisis = '';

    #[Validate('nullable|numeric')]
    public string $temperatura = '';

    #[Validate('nullable|numeric')]
    public string $grasa = '';

    #[Validate('nullable|numeric')]
    public string $sng = '';

    #[Validate('nullable|numeric')]
    public string $proteina = '';

    #[Validate('nullable|numeric')]
    public string $lactosa = '';

    #[Validate('nullable|numeric')]
    public string $sales = '';

    #[Validate('nullable|numeric')]
    public string $totalSolidos = '';

    #[Validate('nullable|numeric')]
    public string $aguaAnadida = '';

    #[Validate('nullable|numeric')]
    public string $puntoCongelacion = '';

    #[Validate('nullable|numeric')]
    public string $ph = '';

    public string $observaciones = '';

    public bool $conforme = true;

    public function mount(): void
    {
        $this->fecha = now()->toDateString();
    }

    public function nuevo(): void
    {
        $this->reset([
            'resultadoId', 'proveedorId', 'turnoId', 'litros', 'densidad', 'sn', 'modo', 'horaAnalisis', 'fechaAnalisis',
            'temperatura', 'grasa', 'sng', 'proteina', 'lactosa', 'sales', 'totalSolidos',
            'aguaAnadida', 'puntoCongelacion', 'ph', 'observaciones',
        ]);
        $this->fecha = now()->toDateString();
        $this->conforme = true;
        $this->resetValidation();
        Flux::modal('laboratorio-form')->show();
    }

    public function with(): array
    {
        return [
            // Solo proveedores cuyo acopiador ya presionó "Entregar a planta": recién ahí la
            // leche llega físicamente a planta y tiene sentido evaluarla en laboratorio.
            'proveedoresEntregados' => Proveedor::with('user')
                ->whereHas('recojos', fn ($q) => $q->where('entregado', true))
                ->get(),
            'turnos' => Turno::where('es_produccion', true)->orderBy('hora_inicio')->get(),
            'resultados' => ResultadoLaboratorio::with('proveedor.user', 'turno')
                ->orderByDesc('fecha')
                ->limit(30)
                ->get(),
            'resultadoDetalle' => $this->detalleId
                ? ResultadoLaboratorio::with('proveedor.user', 'turno')->find($this->detalleId)
                : null,
        ];
    }

    /**
     * Los recojos entregados de este proveedor en la fecha elegida: pueden ser varios si tuvo
     * más de una ronda de recojo ese día (ej. turno mañana y tarde).
     *
     * @return Collection<int, Recojo>
     */
    #[Computed]
    public function recojosDelDia(): Collection
    {
        if (! $this->proveedorId || ! $this->fecha) {
            return collect();
        }

        return Recojo::query()
            ->where('proveedor_id', $this->proveedorId)
            ->whereDate('fecha', $this->fecha)
            ->where('entregado', true)
            ->get();
    }

    #[Computed]
    public function litrosRecogidosProveedor(): float
    {
        return (float) $this->recojosDelDia->sum('litros');
    }

    public function verDetalle(int $id): void
    {
        $this->detalleId = $id;
        Flux::modal('detalle-resultado')->show();
    }

    public function editar(int $id): void
    {
        $r = ResultadoLaboratorio::findOrFail($id);

        $this->resultadoId = $r->id;
        $this->proveedorId = $r->proveedor_id;
        $this->fecha = $r->fecha->toDateString();
        $this->turnoId = $r->turno_id;
        $this->litros = (string) $r->litros;
        $this->densidad = (string) ($r->densidad ?? '');
        $this->sn = (string) ($r->sn ?? '');
        $this->modo = (string) ($r->modo ?? '');
        $this->horaAnalisis = $r->hora_analisis ? substr((string) $r->hora_analisis, 0, 5) : '';
        $this->fechaAnalisis = $r->fecha_analisis?->toDateString() ?? '';
        $this->temperatura = (string) ($r->temperatura ?? '');
        $this->grasa = (string) ($r->grasa ?? '');
        $this->sng = (string) ($r->sng ?? '');
        $this->proteina = (string) ($r->proteina ?? '');
        $this->lactosa = (string) ($r->lactosa ?? '');
        $this->sales = (string) ($r->sales ?? '');
        $this->totalSolidos = (string) ($r->total_solidos ?? '');
        $this->aguaAnadida = (string) ($r->agua_anadida ?? '');
        $this->puntoCongelacion = (string) ($r->punto_congelacion ?? '');
        $this->ph = (string) ($r->ph ?? '');
        $this->observaciones = $r->observaciones ?? '';
        $this->conforme = $r->conforme;

        $this->resetValidation();
        Flux::modal('laboratorio-form')->show();
    }

    public function guardar(): void
    {
        $data = $this->validate();

        $proveedor = Proveedor::findOrFail($this->proveedorId);
        $esNuevo = $this->resultadoId === null;

        $recojosDelDia = Recojo::query()
            ->where('proveedor_id', $proveedor->id)
            ->whereDate('fecha', $data['fecha'])
            ->where('entregado', true)
            ->get();

        if ($recojosDelDia->isEmpty()) {
            $this->addError('proveedorId', 'Este proveedor no tiene un recojo entregado en esta fecha.');

            return;
        }

        ResultadoLaboratorio::updateOrCreate(['id' => $this->resultadoId], [
            'proveedor_id' => $proveedor->id,
            'recojo_id' => $recojosDelDia->sortByDesc('id')->first()->id,
            'fecha' => $data['fecha'],
            'turno_id' => $this->turnoId,
            'litros' => $data['litros'],
            'resultado' => $this->conforme ? 'conforme' : 'no conforme',
            'densidad' => $data['densidad'] ?: null,
            'sn' => $data['sn'] ?: null,
            'modo' => $data['modo'] ?: null,
            'hora_analisis' => $data['horaAnalisis'] ?: null,
            'fecha_analisis' => $data['fechaAnalisis'] ?: null,
            'temperatura' => $data['temperatura'] ?: null,
            'grasa' => $data['grasa'] ?: null,
            'sng' => $data['sng'] ?: null,
            'proteina' => $data['proteina'] ?: null,
            'lactosa' => $data['lactosa'] ?: null,
            'sales' => $data['sales'] ?: null,
            'total_solidos' => $data['totalSolidos'] ?: null,
            'agua_anadida' => $data['aguaAnadida'] ?: null,
            'punto_congelacion' => $data['puntoCongelacion'] ?: null,
            'ph' => $data['ph'] ?: null,
            'observaciones' => $data['observaciones'] ?? '',
            'conforme' => $this->conforme,
        ]);

        if ($esNuevo && ! $this->conforme) {
            $proveedor->registrarAlteracion();
        }

        // El litraje que registra el laboratorio es el definitivo: ajusta el/los recojo(s) de
        // ese día (y por lo tanto el pago pendiente al proveedor) para que coincidan, salvo los
        // que ya se hayan pagado. Si hubo más de un recojo ese día, se reparte proporcionalmente
        // según el peso original de cada uno.
        $recojosAjustables = $recojosDelDia->where('pagado', false);
        $litrajeAjustado = $recojosAjustables->isNotEmpty();

        if ($recojosAjustables->count() === 1) {
            $recojo = $recojosAjustables->first();
            $recojo->update([
                'litros' => $data['litros'],
                'total' => round((float) $data['litros'] * (float) $recojo->precio_litro, 2),
            ]);
        } elseif ($recojosAjustables->count() > 1) {
            $totalOriginal = (float) $recojosAjustables->sum('litros');

            foreach ($recojosAjustables as $recojo) {
                $proporcion = $totalOriginal > 0 ? (float) $recojo->litros / $totalOriginal : 1 / $recojosAjustables->count();
                $litrosRecojo = round((float) $data['litros'] * $proporcion, 2);

                $recojo->update([
                    'litros' => $litrosRecojo,
                    'total' => round($litrosRecojo * (float) $recojo->precio_litro, 2),
                ]);
            }
        }

        Flux::modal('laboratorio-form')->close();

        $mensaje = $esNuevo ? 'Resultado de laboratorio registrado.' : 'Resultado de laboratorio actualizado.';
        $mensaje .= $litrajeAjustado
            ? ' Se actualizó el litraje y el pago del recojo.'
            : ' El recojo ya estaba pagado, no se modificó su litraje.';

        Flux::toast($mensaje, variant: 'success');
    }

    public function pedirEnviarProduccion(int $id): void
    {
        $this->enviarProduccionId = $id;
        Flux::modal('confirmar-enviar-produccion')->show();
    }

    public function enviarProduccion(): void
    {
        $resultado = ResultadoLaboratorio::findOrFail($this->enviarProduccionId);
        $resultado->enviarAProduccion();

        Flux::modal('confirmar-enviar-produccion')->close();
        Flux::toast('Leche enviada a producción: se sumó al stock de insumos.', variant: 'success');
    }

    public function desbloquear(int $id): void
    {
        Proveedor::findOrFail($id)->desbloquear();

        Flux::toast('Proveedor desbloqueado.', variant: 'success');
    }
};
?>

<div class="flex flex-col gap-8">
    <x-ui.titulo titulo="Laboratorio">Registra resultados de calidad de leche por proveedor.</x-ui.titulo>

    <div>
        <div class="mb-3 flex flex-wrap items-center justify-between gap-3">
            <flux:heading size="lg">Resultados de laboratorio</flux:heading>
            <flux:modal.trigger name="laboratorio-form">
                <flux:button size="sm" variant="primary" icon="plus" wire:click="nuevo">
                    Nuevo resultado
                </flux:button>
            </flux:modal.trigger>
        </div>
        <div class="max-h-[36rem] overflow-x-auto overflow-y-auto rounded-xl border border-huata-verde-100 px-3 dark:border-zinc-700 huata-cuadro">
            <flux:table>
                <flux:table.columns sticky class="bg-white/80 dark:bg-zinc-800/80">
                    <flux:table.column>Proveedor</flux:table.column>
                    <flux:table.column>Fecha</flux:table.column>
                    <flux:table.column>Litros</flux:table.column>
                    <flux:table.column>Resultado</flux:table.column>
                    <flux:table.column class="w-px whitespace-nowrap">Acciones</flux:table.column>
                </flux:table.columns>
                <flux:table.rows>
                    @forelse ($resultados as $resultado)
                        @php $proveedor = $resultado->proveedor; @endphp
                        <flux:table.row wire:key="lab-{{ $resultado->id }}">
                            <flux:table.cell>
                                <p class="font-medium">{{ $proveedor->user->nombreCompleto() }}</p>
                                @if ($proveedor->bloqueado)
                                    <div class="mt-1 flex items-center gap-1.5">
                                        <flux:badge color="red" size="sm">Bloqueado ({{ $proveedor->alteraciones }}/{{ \App\Models\Proveedor::UMBRAL_BLOQUEO }})</flux:badge>
                                        <flux:button size="sm" variant="ghost" wire:click="desbloquear({{ $proveedor->id }})">Desbloquear</flux:button>
                                    </div>
                                @elseif ($proveedor->alteraciones > 0)
                                    <flux:badge color="amber" size="sm" class="mt-1">
                                        En observación ({{ $proveedor->alteraciones }}/{{ \App\Models\Proveedor::UMBRAL_BLOQUEO }})
                                    </flux:badge>
                                @endif
                            </flux:table.cell>
                            <flux:table.cell>{{ $resultado->fecha->format('d/m/Y') }}</flux:table.cell>
                            <flux:table.cell>{{ number_format((float) $resultado->litros, 2) }}</flux:table.cell>
                            <flux:table.cell>
                                <div class="flex flex-wrap gap-1">
                                    <flux:badge :color="$resultado->conforme ? 'green' : 'red'" size="sm">
                                        {{ $resultado->conforme ? 'Conforme' : 'No conforme' }}
                                    </flux:badge>
                                    @if ($resultado->enviado_produccion)
                                        <flux:badge color="blue" size="sm" icon="check-circle">Enviado a producción</flux:badge>
                                    @endif
                                </div>
                            </flux:table.cell>
                            <flux:table.cell class="whitespace-nowrap">
                                <div class="flex flex-nowrap items-center gap-2">
                                    <flux:modal.trigger name="detalle-resultado">
                                        <flux:button size="sm" variant="ghost" icon="table-cells" wire:click="verDetalle({{ $resultado->id }})" />
                                    </flux:modal.trigger>
                                    <flux:modal.trigger name="laboratorio-form">
                                        <flux:button size="sm" variant="ghost" icon="pencil" wire:click="editar({{ $resultado->id }})" />
                                    </flux:modal.trigger>
                                    <flux:button
                                        as="a"
                                        target="_blank"
                                        :href="route('planta.laboratorio.pdf', $resultado->id)"
                                        size="sm"
                                        variant="ghost"
                                        icon="document-arrow-down"
                                    />
                                    @if ($resultado->conforme && ! $resultado->enviado_produccion)
                                        <flux:modal.trigger name="confirmar-enviar-produccion">
                                            <flux:button size="sm" variant="primary" icon="arrow-up-tray" wire:click="pedirEnviarProduccion({{ $resultado->id }})">
                                                Enviar a producción
                                            </flux:button>
                                        </flux:modal.trigger>
                                    @endif
                                </div>
                            </flux:table.cell>
                        </flux:table.row>
                    @empty
                        <flux:table.row>
                            <flux:table.cell colspan="5" class="text-center text-zinc-500">Sin resultados registrados.</flux:table.cell>
                        </flux:table.row>
                    @endforelse
                </flux:table.rows>
            </flux:table>
        </div>
    </div>

    <flux:modal name="detalle-resultado" class="md:w-[36rem]">
        <div class="flex flex-col gap-5">
            <div>
                <flux:heading size="lg">{{ $resultadoDetalle?->proveedor?->user?->nombreCompleto() }}</flux:heading>
                <flux:subheading>
                    {{ $resultadoDetalle?->fecha?->format('d/m/Y') }}
                    @if ($resultadoDetalle?->turno)
                        &middot; {{ $resultadoDetalle->turno->nombre }}
                    @endif
                    &middot; {{ number_format((float) ($resultadoDetalle?->litros ?? 0), 2) }} L
                </flux:subheading>
            </div>

            <div class="grid grid-cols-2 gap-x-4 gap-y-3 sm:grid-cols-3">
                <div>
                    <flux:text class="text-xs text-zinc-500">SN</flux:text>
                    <p class="font-medium">{{ $resultadoDetalle?->sn ?? '—' }}</p>
                </div>
                <div>
                    <flux:text class="text-xs text-zinc-500">Mode</flux:text>
                    <p class="font-medium">{{ $resultadoDetalle?->modo ?? '—' }}</p>
                </div>
                <div>
                    <flux:text class="text-xs text-zinc-500">Temp. (°C)</flux:text>
                    <p class="font-medium">{{ $resultadoDetalle?->temperatura !== null ? number_format((float) $resultadoDetalle->temperatura, 2) : '—' }}</p>
                </div>
                <div>
                    <flux:text class="text-xs text-zinc-500">Grasa (%)</flux:text>
                    <p class="font-medium">{{ $resultadoDetalle?->grasa !== null ? number_format((float) $resultadoDetalle->grasa, 2) : '—' }}</p>
                </div>
                <div>
                    <flux:text class="text-xs text-zinc-500">SNG (%)</flux:text>
                    <p class="font-medium">{{ $resultadoDetalle?->sng !== null ? number_format((float) $resultadoDetalle->sng, 2) : '—' }}</p>
                </div>
                <div>
                    <flux:text class="text-xs text-zinc-500">Densidad</flux:text>
                    <p class="font-medium">{{ $resultadoDetalle?->densidad !== null ? number_format((float) $resultadoDetalle->densidad, 3) : '—' }}</p>
                </div>
                <div>
                    <flux:text class="text-xs text-zinc-500">Proteína (%)</flux:text>
                    <p class="font-medium">{{ $resultadoDetalle?->proteina !== null ? number_format((float) $resultadoDetalle->proteina, 2) : '—' }}</p>
                </div>
                <div>
                    <flux:text class="text-xs text-zinc-500">Lactosa (%)</flux:text>
                    <p class="font-medium">{{ $resultadoDetalle?->lactosa !== null ? number_format((float) $resultadoDetalle->lactosa, 2) : '—' }}</p>
                </div>
                <div>
                    <flux:text class="text-xs text-zinc-500">Sales (%)</flux:text>
                    <p class="font-medium">{{ $resultadoDetalle?->sales !== null ? number_format((float) $resultadoDetalle->sales, 2) : '—' }}</p>
                </div>
                <div>
                    <flux:text class="text-xs text-zinc-500">Total sólidos (%)</flux:text>
                    <p class="font-medium">{{ $resultadoDetalle?->total_solidos !== null ? number_format((float) $resultadoDetalle->total_solidos, 2) : '—' }}</p>
                </div>
                <div>
                    <flux:text class="text-xs text-zinc-500">Agua añadida (%)</flux:text>
                    <p class="font-medium">{{ $resultadoDetalle?->agua_anadida !== null ? number_format((float) $resultadoDetalle->agua_anadida, 2) : '—' }}</p>
                </div>
                <div>
                    <flux:text class="text-xs text-zinc-500">Punto cong. (°C)</flux:text>
                    <p class="font-medium">{{ $resultadoDetalle?->punto_congelacion !== null ? number_format((float) $resultadoDetalle->punto_congelacion, 3) : '—' }}</p>
                </div>
                <div>
                    <flux:text class="text-xs text-zinc-500">pH</flux:text>
                    <p class="font-medium">{{ $resultadoDetalle?->ph !== null ? number_format((float) $resultadoDetalle->ph, 2) : '—' }}</p>
                </div>
                <div>
                    <flux:text class="text-xs text-zinc-500">Hora</flux:text>
                    <p class="font-medium">{{ $resultadoDetalle?->hora_analisis ? substr((string) $resultadoDetalle->hora_analisis, 0, 5) : '—' }}</p>
                </div>
                <div>
                    <flux:text class="text-xs text-zinc-500">Fecha</flux:text>
                    <p class="font-medium">{{ $resultadoDetalle?->fecha_analisis?->format('d/m/Y') ?? '—' }}</p>
                </div>
            </div>

            @if ($resultadoDetalle?->observaciones)
                <div>
                    <flux:text class="text-xs text-zinc-500">Observaciones</flux:text>
                    <p>{{ $resultadoDetalle->observaciones }}</p>
                </div>
            @endif

            <div class="flex justify-end">
                <flux:modal.close>
                    <flux:button variant="ghost">Cerrar</flux:button>
                </flux:modal.close>
            </div>
        </div>
    </flux:modal>

    <flux:modal name="laboratorio-form" class="md:w-[36rem]">
        <form wire:submit="guardar" class="flex flex-col gap-6">
            <flux:heading size="lg">{{ $resultadoId ? 'Editar resultado de laboratorio' : 'Resultado de laboratorio' }}</flux:heading>

            <div class="grid grid-cols-2 gap-3">
                <flux:input type="date" label="Fecha" wire:model.live="fecha" />
                <flux:select label="Turno (opcional)" wire:model="turnoId">
                    <flux:select.option value="">Sin turno</flux:select.option>
                    @foreach ($turnos as $turno)
                        <flux:select.option value="{{ $turno->id }}">{{ $turno->nombre }}</flux:select.option>
                    @endforeach
                </flux:select>
            </div>

            <flux:select label="Proveedor" wire:model.live="proveedorId" placeholder="Selecciona un proveedor...">
                @forelse ($proveedoresEntregados as $proveedor)
                    <flux:select.option value="{{ $proveedor->id }}" wire:key="lab-prov-opt-{{ $proveedor->id }}">
                        {{ $proveedor->user->nombreCompleto() }}
                    </flux:select.option>
                @empty
                    <flux:select.option value="" disabled>Ningún proveedor tiene una entrega a planta todavía</flux:select.option>
                @endforelse
            </flux:select>

            <div class="grid grid-cols-2 gap-3">
                <div class="rounded-lg bg-huata-verde-50 p-3 dark:bg-huata-verde-900/30">
                    <flux:text class="text-xs text-zinc-500">Litraje que recogieron los acopiadores</flux:text>
                    <p class="text-lg font-semibold text-huata-verde-700">
                        @if ($this->recojosDelDia->isNotEmpty())
                            {{ number_format($this->litrosRecogidosProveedor, 2) }} L
                        @else
                            Sin recojo en esta fecha
                        @endif
                    </p>
                </div>
                <flux:input type="number" step="0.01" min="0" label="Litraje que llegó a planta" wire:model="litros" />
            </div>

            <flux:heading size="sm">Análisis (SmartScan AI)</flux:heading>
            {{-- Mismo orden que el ticket del analizador: SN y Mode arriba, hora y fecha al lado de pH. --}}
            <div class="grid grid-cols-3 gap-3">
                <flux:input label="SN" wire:model="sn" placeholder="48359" />
                <flux:input label="Mode" wire:model="modo" placeholder="1" />
                <flux:input type="number" step="0.01" label="Temp. (°C)" wire:model="temperatura" />
                <flux:input type="number" step="0.01" label="Grasa (%)" wire:model="grasa" />
                <flux:input type="number" step="0.01" label="SNG (%)" wire:model="sng" />
                <flux:input type="number" step="0.001" label="Densidad" wire:model="densidad" />
                <flux:input type="number" step="0.01" label="Proteína (%)" wire:model="proteina" />
                <flux:input type="number" step="0.01" label="Lactosa (%)" wire:model="lactosa" />
                <flux:input type="number" step="0.01" label="Sales (%)" wire:model="sales" />
                <flux:input type="number" step="0.01" label="Total sólidos (%)" wire:model="totalSolidos" />
                <flux:input type="number" step="0.01" label="Agua añadida (%)" wire:model="aguaAnadida" />
                <flux:input type="number" step="0.001" label="Punto cong. (°C)" wire:model="puntoCongelacion" />
                <flux:input type="number" step="0.01" label="pH" wire:model="ph" />
                <flux:input type="time" label="Hora" wire:model="horaAnalisis" />
                <flux:input type="date" label="Fecha" wire:model="fechaAnalisis" />
            </div>

            <flux:textarea label="Observaciones" wire:model="observaciones" rows="2" />

            <flux:switch label="Resultado conforme" wire:model="conforme" />
            @if (! $conforme)
                <flux:text class="text-huata-rojo-600">
                    Esto sumará una alteración de calidad al proveedor. Al llegar a 3, se bloquea automáticamente.
                </flux:text>
            @endif

            <div class="flex justify-end gap-2">
                <flux:modal.close>
                    <flux:button variant="ghost">Cancelar</flux:button>
                </flux:modal.close>
                <flux:button type="submit" variant="primary">Guardar</flux:button>
            </div>
        </form>
    </flux:modal>

    <x-confirm-modal
        name="confirmar-enviar-produccion"
        accion="enviarProduccion"
        titulo="¿Enviar esta leche a producción?"
        mensaje="Se sumará al stock de insumos disponible para producir."
        textoConfirmar="Enviar"
        variante="primary"
        icono="arrow-up-tray"
    />
</div>

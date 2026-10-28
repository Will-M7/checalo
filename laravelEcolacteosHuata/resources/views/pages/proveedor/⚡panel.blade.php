<?php

use App\Enums\Rol;
use App\Models\Acopiador;
use App\Models\Comunicado;
use App\Models\PagoProveedor;
use App\Models\Proveedor;
use App\Models\Recojo;
use App\Models\ResultadoLaboratorio;
use Flux\Flux;
use Livewire\Component;

new class extends Component
{
    public string $finca_ubicacion = '';

    public string $finca_referencia = '';

    public ?float $latitud = null;

    public ?float $longitud = null;

    public ?int $acopiador_id = null;

    public function with(): array
    {
        $proveedor = auth()->user()->proveedor;

        return [
            'proveedor' => $proveedor,
            'entregadoHoy' => Recojo::query()->where('proveedor_id', $proveedor?->id)->whereDate('fecha', now()->toDateString())->sum('litros'),
            'entregadoSemana' => Recojo::query()->where('proveedor_id', $proveedor?->id)->whereBetween('fecha', [now()->startOfWeek()->toDateString(), now()->endOfWeek()->toDateString()])->sum('litros'),
            'acopiadores' => Acopiador::with('user')->get()->sortBy(fn (Acopiador $acopiador) => $acopiador->user->nombreCompleto()),
            'entregadoMes' => Recojo::query()->where('proveedor_id', $proveedor?->id)->whereBetween('fecha', [now()->startOfMonth()->toDateString(), now()->endOfMonth()->toDateString()])->sum('litros'),
            'pagosMes' => (float) PagoProveedor::query()->where('proveedor_id', $proveedor?->id)->whereBetween('fecha', [now()->startOfMonth()->toDateString(), now()->endOfMonth()->toDateString()])->sum('monto'),
            'ultimasEntregas' => Recojo::query()->where('proveedor_id', $proveedor?->id)->latest('created_at')->latest('id')->limit(5)->get(),
            'ultimosResultados' => ResultadoLaboratorio::query()->where('proveedor_id', $proveedor?->id)->latest('created_at')->latest('id')->limit(4)->get(),
            'comunicados' => Comunicado::query()->paraRol(Rol::Proveedor)->latest('created_at')->latest('id')->limit(3)->get(),
        ];
    }

    public function editarUbicacion(): void
    {
        $proveedor = auth()->user()->proveedor;

        $this->finca_ubicacion = $proveedor?->finca_ubicacion ?? '';
        $this->finca_referencia = $proveedor?->finca_referencia ?? '';
        $this->latitud = $proveedor?->latitud ? (float) $proveedor->latitud : null;
        $this->longitud = $proveedor?->longitud ? (float) $proveedor->longitud : null;
        $this->acopiador_id = $proveedor?->acopiador_id;

        $this->resetValidation();
        Flux::modal('ubicacion-form')->show();
        $this->mostrarRutasDelAcopiador();
    }

    public function updatedAcopiadorId(): void
    {
        $this->mostrarRutasDelAcopiador();
    }

    /**
     * Envía al mapa del formulario el camino de las rutas activas del acopiador elegido, para que
     * el proveedor ubique su finca sobre (o cerca de) la ruta por donde recoge.
     */
    private function mostrarRutasDelAcopiador(): void
    {
        $this->dispatch('mapa-ubicacion-rutas',
            lat: $this->latitud,
            lng: $this->longitud,
            rutas: Acopiador::find($this->acopiador_id)?->rutasParaMapa() ?? [],
        );
    }

    public function guardarUbicacion(): void
    {
        $data = $this->validate([
            'finca_ubicacion' => 'required|string|max:255',
            'finca_referencia' => 'nullable|string|max:255',
            'acopiador_id' => 'required|exists:acopiadores,id',
        ]);

        abort_if(! auth()->user()->proveedor, 404);

        $proveedor = Proveedor::guardarElegidoPorProveedor(auth()->id(), [
            'finca_ubicacion' => $data['finca_ubicacion'],
            'finca_referencia' => $data['finca_referencia'] ?? '',
            'latitud' => $this->latitud,
            'longitud' => $this->longitud,
            'acopiador_id' => $data['acopiador_id'],
        ]);

        Flux::modal('ubicacion-form')->close();
        Flux::toast($proveedor->rutas()->exists() ? 'Ubicación guardada. Ya estás en la ruta de tu acopiador.' : 'Ubicación guardada. Tu acopiador aún no tiene ruta: se avisó a planta.', variant: 'success');
    }
};
?>

<div class="flex flex-col gap-8">
    <x-ui.page-header
        titulo="Panel del proveedor"
        :subtitulo="'Bienvenido, '.auth()->user()->nombreCompleto().'.'"
        :etiqueta="Rol::Proveedor->etiquetaPanel()"
    />

    @if ($proveedor?->bloqueado)
        <div class="hh-panel hh-entra flex gap-3 p-4" role="alert" style="border-color: var(--hh-peligro)">
            <flux:icon icon="exclamation-triangle" class="mt-0.5 size-5 shrink-0 text-[var(--hh-peligro)]" />
            <div>
                <p class="text-sm font-semibold text-[var(--hh-texto)]">Tu leche no está siendo recogida</p>
                <p class="mt-0.5 text-sm text-[var(--hh-texto-2)]">Se detectaron {{ $proveedor->alteraciones }} alteraciones de calidad en tu leche. Comunícate con planta para resolverlo.</p>
            </div>
        </div>
    @endif

    <section aria-label="Tus entregas" class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <x-ui.kpi etiqueta="Entregado hoy" :valor="number_format((float) $entregadoHoy, 2).' L'" icono="beaker" :indice="0" />
        <x-ui.kpi etiqueta="Entregado esta semana" :valor="number_format((float) $entregadoSemana, 2).' L'" icono="calendar-days" :indice="1" />
        <x-ui.kpi etiqueta="Entregado este mes" :valor="number_format((float) $entregadoMes, 2).' L'" icono="chart-bar" :indice="2" />
        <x-ui.kpi etiqueta="Pagos recibidos este mes" :valor="'S/ '.number_format($pagosMes, 2)" icono="banknotes" color="#23865b" :indice="3" />
    </section>

    <div class="grid gap-6 lg:grid-cols-2">
        <x-ui.panel titulo="Tu acopiador" :indice="4">
            <div class="flex items-start gap-3">
                <span class="grid size-11 shrink-0 place-items-center rounded-xl border border-[var(--hh-borde)] text-[var(--hh-marca)]"><flux:icon icon="truck" class="size-5" /></span>
                <div class="min-w-0">
                    <p class="text-base font-semibold text-[var(--hh-texto)]">{{ $proveedor?->acopiador?->user?->nombreCompleto() ?? 'Sin asignar' }}</p>
                    @if ($proveedor?->acopiador)
                        <p class="text-sm text-[var(--hh-texto-2)]">{{ $proveedor->acopiador->ruta_nombre }}</p>
                    @else
                        <p class="text-sm text-[var(--hh-texto-2)]">Elige tu acopiador al editar la ubicación de tu finca.</p>
                    @endif
                </div>
            </div>
        </x-ui.panel>

        <x-ui.panel titulo="Ubicación de tu finca" :indice="5">
            <div class="flex flex-wrap items-start justify-between gap-3">
                <div class="flex min-w-0 items-start gap-3">
                    <span class="grid size-11 shrink-0 place-items-center rounded-xl border border-[var(--hh-borde)] text-[var(--hh-marca)]"><flux:icon icon="map-pin" class="size-5" /></span>
                    <div class="min-w-0">
                        <p class="text-base font-semibold text-[var(--hh-texto)]">{{ $proveedor?->finca_ubicacion ?: 'Sin registrar' }}</p>
                        @if ($proveedor?->finca_referencia)
                            <p class="text-sm text-[var(--hh-texto-2)]">{{ $proveedor->finca_referencia }}</p>
                        @endif
                    </div>
                </div>
                <flux:modal.trigger name="ubicacion-form">
                    <flux:button size="sm" variant="primary" icon="map-pin" wire:click="editarUbicacion">Editar ubicación</flux:button>
                </flux:modal.trigger>
            </div>
        </x-ui.panel>
    </div>

    <div class="grid gap-6 xl:grid-cols-2">
        <x-ui.panel titulo="Mis últimas entregas" descripcion="Leche que recogieron de tu finca." :indice="6">
            @if ($ultimasEntregas->isEmpty())
                <x-ui.vacio titulo="Aún no hay entregas" descripcion="Cuando recojan tu leche, el detalle aparecerá aquí." icono="truck" />
            @else
                <ul class="flex flex-col divide-y divide-[var(--hh-borde)]">
                    @foreach ($ultimasEntregas as $entrega)
                        <li class="flex items-center gap-3 py-3 first:pt-0 last:pb-0">
                            <div class="min-w-0 flex-1">
                                <p class="text-sm font-semibold text-[var(--hh-texto)]">{{ $entrega->fecha->format('d/m/Y') }}</p>
                                <p class="text-xs text-[var(--hh-texto-2)]">{{ $entrega->entregado ? 'Llegó a planta' : 'En camino a planta' }}</p>
                            </div>
                            <span class="text-sm font-semibold tabular-nums text-[var(--hh-texto)]">{{ number_format((float) $entrega->litros, 2) }} L</span>
                        </li>
                    @endforeach
                </ul>
            @endif
        </x-ui.panel>

        <x-ui.panel titulo="Resultados de laboratorio" descripcion="La calidad de tu leche." :indice="7">
            @if ($ultimosResultados->isEmpty())
                <x-ui.vacio titulo="Sin resultados todavía" descripcion="Los análisis de tu leche aparecerán aquí." icono="beaker" />
            @else
                <ul class="flex flex-col divide-y divide-[var(--hh-borde)]">
                    @foreach ($ultimosResultados as $resultado)
                        <li class="flex items-center gap-3 py-3 first:pt-0 last:pb-0">
                            <div class="min-w-0 flex-1">
                                <p class="text-sm font-semibold text-[var(--hh-texto)]">{{ $resultado->fecha->format('d/m/Y') }}</p>
                            </div>
                            <flux:badge :color="$resultado->conforme ? 'green' : 'red'" size="sm">{{ $resultado->conforme ? 'Conforme' : 'No conforme' }}</flux:badge>
                        </li>
                    @endforeach
                </ul>
            @endif
        </x-ui.panel>
    </div>

    @if ($comunicados->isNotEmpty())
        <x-ui.panel titulo="Comunicados" :indice="8">
            <ul class="flex flex-col divide-y divide-[var(--hh-borde)]">
                @foreach ($comunicados as $comunicado)
                    <li class="py-3 first:pt-0 last:pb-0">
                        <p class="text-sm font-semibold text-[var(--hh-texto)]">{{ $comunicado->titulo }}</p>
                        <p class="text-xs text-[var(--hh-texto-2)]">{{ $comunicado->fecha->format('d/m/Y') }}</p>
                    </li>
                @endforeach
            </ul>
        </x-ui.panel>
    @endif

    <section aria-labelledby="accesos-titulo">
        <h2 id="accesos-titulo" class="hh-entra mb-4 text-lg font-semibold tracking-tight text-[var(--hh-texto)]" style="--i: 9">Accesos rápidos</h2>
        @php
            $accesos = [
                ['proveedor.entregas', 'Mis entregas', 'Historial de la leche que entregaste.', 'truck'],
                ['proveedor.laboratorio', 'Laboratorio', 'Resultados de calidad de tu leche.', 'beaker'],
                ['proveedor.comunicados', 'Comunicados', 'Avisos y asesoramiento de la planta.', 'megaphone'],
            ];
        @endphp
        <div class="grid gap-3 sm:grid-cols-3">
            @foreach ($accesos as [$ruta, $titulo, $descripcion, $icono])
                @if(auth('web')->user()->puedePantalla(\App\Authorization\Pantallas::deRuta($ruta) ?? $ruta))
                <x-ui.accion :href="route($ruta)" :titulo="$titulo" :descripcion="$descripcion" :icono="$icono" :indice="10 + $loop->index" />
                @endif
            @endforeach
        </div>
    </section>

    <flux:modal name="ubicacion-form" class="md:w-[28rem]">
        <form wire:submit="guardarUbicacion" class="flex flex-col gap-4">
            <div>
                <flux:heading size="lg">Editar ubicación</flux:heading>
                <flux:subheading>Al guardar, quedarás en la ruta del acopiador que elijas.</flux:subheading>
            </div>
            <flux:input label="Ubicación / dirección" wire:model="finca_ubicacion" placeholder="Comunidad de Huata" />
            <flux:input label="Referencia" wire:model="finca_referencia" placeholder="A 200m de la escuela..." />
            <flux:select label="Acopiador que recoge tu leche" wire:model.live="acopiador_id">
                <flux:select.option value="">Selecciona...</flux:select.option>
                @foreach ($acopiadores as $acopiador)
                    <flux:select.option value="{{ $acopiador->id }}" wire:key="acopiador-{{ $acopiador->id }}">{{ $acopiador->user->nombreCompleto() }}</flux:select.option>
                @endforeach
            </flux:select>
            @if ($acopiador_id)
                <p class="-mt-2 text-xs text-zinc-500">
                    <span class="mr-1 inline-block h-1 w-5 rounded bg-blue-600 align-middle"></span>
                    La línea azul es la ruta por donde recoge este acopiador. Marca tu finca y guarda: quedarás en su ruta automáticamente.
                </p>
            @endif
            <x-mapa-ubicacion :lat="$latitud" :lng="$longitud" :editable="true" lat-model="latitud" lng-model="longitud" height="200px" />
            <div class="flex justify-end gap-2">
                <flux:modal.close><flux:button variant="ghost">Cancelar</flux:button></flux:modal.close>
                <flux:button type="submit" variant="primary">Guardar</flux:button>
            </div>
        </form>
    </flux:modal>
</div>

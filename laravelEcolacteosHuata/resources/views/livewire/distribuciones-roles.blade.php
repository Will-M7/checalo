<div>
    <flux:modal name="distribuciones" class="w-full md:min-w-[48rem] md:max-w-5xl">
        @if($abierto)
        <div class="flex flex-col gap-5">
            <flux:heading size="lg">Distribuciones de roles</flux:heading>
            <flux:text>El rol protegido solo lo modifica el administrador principal. Seleccionar o guardar no modifica accesos. Las operaciones requieren conexión.</flux:text>
            <div class="flex flex-wrap gap-2">
                <flux:button wire:loading.attr="disabled" wire:click="nueva">Nueva distribución</flux:button>
                <flux:button wire:loading.attr="disabled" wire:click="copiar(true)">Guardar asignaciones actuales como copia</flux:button>
            </div>
            <div class="flex flex-wrap items-center gap-3"><flux:input wire:model.live.debounce.300ms="busqueda" placeholder="Buscar distribución..." class="max-w-sm" /><x-ui.filtros :activos="$filtros"><flux:select label="Tipo" wire:model.live="filtros.tipo"><flux:select.option value="">Todos</flux:select.option><flux:select.option value="base">Base protegida</flux:select.option><flux:select.option value="personalizada">Personalizada</flux:select.option></flux:select><flux:select label="Contiene rol" wire:model.live="filtros.rol"><flux:select.option value="">Todos</flux:select.option>@foreach($datos['roles'] as $r)<flux:select.option :value="$r['codigo']">{{ $r['nombre'] }}</flux:select.option>@endforeach</flux:select></x-ui.filtros></div>
            <div class="grid gap-3 sm:grid-cols-2">
                @forelse($distribucionesFiltradas as $d)
                <div class="rounded-xl border border-huata-verde-100 p-3 dark:border-zinc-700" wire:key="distribucion-{{ $d['id'] }}">
                    <flux:heading>{{ $d['nombre'] }}</flux:heading>
                    <flux:text>v{{ $d['version'] }} · {{ $d['tipo'] === 'base' ? 'Base protegida' : 'Personalizada' }}{{ $d['predeterminada'] ? ' · Predeterminada para instalaciones nuevas' : '' }}</flux:text>
                    <flux:text>{{ $d['coincide'] ? 'Sus roles coinciden con las asignaciones actuales' : 'Contiene diferencias respecto de las asignaciones actuales' }}</flux:text>
                    <flux:button wire:loading.attr="disabled" size="sm" wire:click="ver({{ $d['id'] }})">Consultar / editar</flux:button>
                </div>
                @empty<flux:text>No se encontraron distribuciones.</flux:text>@endforelse
            </div>
            <flux:separator />
            <flux:input label="Nombre de la distribución" wire:model="nombre" :readonly="$base" />
            @if($distribucionId)
                <flux:text>La vista previa utiliza la versión guardada. Guarda primero los cambios del formulario.</flux:text>
                <div class="flex flex-wrap gap-2">
                    <flux:button wire:loading.attr="disabled" wire:click="copiar">Duplicar como copia editable</flux:button>
                    <flux:button wire:loading.attr="disabled" variant="primary" wire:click="previsualizar('aplicar')">Comparar distribución guardada</flux:button>
                </div>
                @php($origen = collect($datos['distribuciones'])->firstWhere('id', $distribucionId)['origen'] ?? [])
                @if($base && $origen)
                    <flux:text>{{ $origen['descripcion'] ?? $origen['origen'] ?? '' }}</flux:text>
                    @if(isset($origen['revision']))<flux:text>Revisión histórica: {{ $origen['revision'] }}</flux:text>@endif
                    @foreach($origen['adaptaciones'] ?? [] as $nota)<flux:text>{{ $nota }}</flux:text>@endforeach
                @endif
            @endif
            @foreach(array_diff($rolesSeleccionados, array_column($datos['roles'], 'codigo')) as $ausente)
                <flux:text>Rol ausente: {{ $ausente }}. La aplicación queda bloqueada.</flux:text>
                @if(!$base)<flux:button wire:loading.attr="disabled" size="sm" wire:click="quitarReferencia('{{ $ausente }}')">Retirar referencia ausente de esta copia</flux:button>@endif
            @endforeach
            <div class="flex flex-col gap-3">
                @foreach($datos['roles'] as $rol)
                <details class="rounded-xl border border-huata-verde-100 p-3 dark:border-zinc-700" wire:key="plantilla-rol-{{ $rol['registro_id'] }}">
                    <summary>{{ $rol['nombre'] }} · {{ count($funciones[$rol['codigo']] ?? []) }} pantallas</summary>
                    <div class="mt-3 flex flex-col gap-3">
                        <flux:checkbox wire:model.live="rolesSeleccionados" :value="$rol['codigo']" :label="'Incluir '.$rol['nombre']" :disabled="$base" />
                        @if(in_array($rol['codigo'], $rolesSeleccionados, true))
                        <div class="grid gap-3 sm:grid-cols-2">
                            @foreach($datos['pantallas'] as $p)
                                <flux:checkbox wire:model="funciones.{{ $rol['codigo'] }}" :value="$p['id']" :label="$p['area'].' · '.$p['nombre']" :description="$p['android'] ? 'Web y Android' : 'Solo web'" :disabled="$base" />
                            @endforeach
                        </div>
                        @endif
                    </div>
                </details>
                @endforeach
            </div>
            @if(!$base)<flux:button wire:loading.attr="disabled" wire:click="guardar" variant="primary">Guardar plantilla sin aplicar</flux:button>@endif
            <flux:error name="distribucion" />
            @if($datos['rol_prueba_presente'])
                <flux:separator /><flux:heading>Retirar el rol de prueba ventas (001)</flux:heading>
                <flux:text>La cuenta se conserva. Selecciona explícitamente un rol de destino y revisa los accesos antes de confirmar.</flux:text>
                @foreach($datos['usuarios_prueba'] as $u)
                    <flux:select wire:model="reemplazos.{{ $u['id'] }}" :label="$u['nombre'].' · ID '.$u['id']">
                        <flux:select.option value="">Seleccionar rol autorizado</flux:select.option>
                        @foreach($datos['roles'] as $r)@if($r['activo'] && !in_array($r['codigo'], ['001','tomar_asistencia'], true))<flux:select.option :value="$r['codigo']">{{ $r['nombre'] }}</flux:select.option>@endif @endforeach
                    </flux:select>
                @endforeach
                <flux:button wire:loading.attr="disabled" wire:click="previsualizar('retirar-001')">Revisar reasignación y eliminación de 001</flux:button>
            @endif
            @if($datos['auxiliar_pendiente'])<flux:text>El auxiliar tomar_asistencia tiene dependencias pendientes. No se eliminará hasta resolverlas; la pantalla asistencia.tomar se conserva.</flux:text>@endif
            @if($vista)
                <flux:separator /><flux:heading size="lg">Vista previa: {{ $vista['nombre'] }}</flux:heading>
                @foreach($vista['advertencias'] as $a)<flux:text>{{ $a }}</flux:text>@endforeach
                @foreach($vista['errores'] as $e)<flux:text class="text-red-600">{{ $e }}</flux:text>@endforeach
                @foreach($vista['roles'] as $r)
                    <div class="rounded-xl border border-huata-verde-100 p-3 dark:border-zinc-700">
                        <flux:heading>{{ $r['nombre'] }}{{ !$r['activo'] ? ' · Inactivo (se conserva)' : '' }}</flux:heading>
                        <flux:text>Actuales: {{ implode(', ', $r['actuales']) ?: 'Ninguna' }}</flux:text>
                        <flux:text>Destino: {{ implode(', ', $r['destino']) ?: 'Ninguna' }}</flux:text>
                        <flux:text>Añade: {{ implode(', ', $r['agrega']) ?: 'Ninguna' }}</flux:text>
                        <flux:text>Retira: {{ implode(', ', $r['retira']) ?: 'Ninguna' }}</flux:text>
                        <flux:text>Usuarios afectados: {{ collect($r['usuarios'])->map(fn($u) => $u['nombre'].' (ID '.$u['id'].')')->implode(', ') ?: 'Ninguno' }}</flux:text>
                    </div>
                @endforeach
                <flux:text>Roles no afectados: {{ implode(', ', $vista['no_afectados']) ?: 'Ninguno' }}</flux:text>
                @if($vista['token'])
                    <flux:checkbox wire:model="confirmado" label="Confirmo exclusivamente las diferencias de esta vista previa" />
                    <flux:button wire:loading.attr="disabled" wire:click="aplicar" variant="primary">Confirmar operación</flux:button>
                @endif
            @endif
            <flux:separator /><flux:heading>Aplicaciones y recuperación</flux:heading>
            <flux:text>Una recuperación se bloquea si hubo cambios posteriores de autorización o usuarios.</flux:text>
            @forelse($datos['historial'] as $h)
                <div class="flex flex-wrap items-center justify-between gap-2">
                    <flux:text>{{ $h['fecha'] }} · {{ $h['nombre'] }} · {{ $h['tipo'] }} · Actor {{ $h['actor_id'] }}</flux:text>
                    @if($h['tipo'] === 'aplicacion')<flux:button wire:loading.attr="disabled" size="sm" wire:click="previsualizar('recuperar', '{{ $h['id'] }}')">{{ $h['recuperable'] ? 'Preparar recuperación' : 'Consultar conflicto de recuperación' }}</flux:button>@endif
                </div>
            @empty<flux:text>Aún no se ha aplicado ninguna distribución.</flux:text>@endforelse
        </div>
        @endif
    </flux:modal>
</div>

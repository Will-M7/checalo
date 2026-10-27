<?php
use App\Models\{ReceptorPago, User};
use App\Services\Payments\{ReceptoresService, TelefonoPeru};
use Illuminate\Support\Facades\DB;
use Livewire\Attributes\Locked;
use Livewire\{Component, WithFileUploads};
use Flux\Flux;

new class extends Component {
    use WithFileUploads;
    #[Locked] public int $vendedorId;
    public string $nombre = '';
    public string $celular = '';
    public string $whatsapp = '';
    public string $referencia = '';
    public $qr = null;
    public bool $confirmacionFisica = false;
    public bool $declaracionTitularidad = false;
    public string $modalidad = 'personal';
    public string $receptorCompartido = '';
    public string $receptorCentral = '';
    public bool $permisoPersonal = false;
    public bool $autorizacionResponsable = false;
    public string $modoBridge = 'personal';
    #[Locked] public string $enlaceBridge = '';
    #[Locked] public string $operatividad = 'Sin consultar';
    private function autorizar(): User {
        $actor = auth('web')->user();
        abort_unless($actor && $actor->activo && $actor->puedePantalla('vendedor.configuracion-pagos'), 403);
        abort_unless($actor->id === $this->vendedorId || $actor->puedeAdministracionCritica(), 403);
        return $actor;
    }
    public function mount(): void {
        $this->vendedorId = request()->integer('vendedor') ?: auth('web')->id();
        $this->autorizar();
        if (! config('payments.enabled') || strlen((string) \App\Services\Payments\PaymentCredentials::key()) < 32) {
            $this->operatividad = 'Vinculación comercial con Payment Core pendiente. El QR guardado es solo una previsualización; no está disponible para cobrar.';
        }
        $r = ReceptorPago::where('user_id', $this->vendedorId)->first();
        if ($r) { $this->nombre = $r->nombre_receptor; $this->celular = $r->celular; $this->whatsapp = $r->whatsapp; $this->referencia = $r->referencia_externa ?? ''; }
        if (app(\App\Services\Payments\AsignacionesReceptor::class)->configurada()) {
            $a = DB::table('payment_seller_assignments')->where('user_id', $this->vendedorId)->first();
            $this->modalidad = $a?->mode ?? 'personal';
            $this->receptorCompartido = (string) ($a?->receiver_id ?? '');
            $this->permisoPersonal = (bool) ($a?->personal_allowed ?? false);
            $this->receptorCentral = (string) (DB::table('payment_receiver_policy')->where('id', 1)->value('central_receiver_id') ?? '');
        }
    }
    public function guardar(): void {
        $actor = $this->autorizar();
        abort_unless($actor->puedeAdministracionCritica() || app(\App\Services\Payments\AsignacionesReceptor::class)->puedePersonal($actor), 403);
        $data = $this->validate(['nombre' => 'required|string|max:100', 'celular' => 'required|string|max:20', 'whatsapp' => 'required|string|max:20',
            'referencia' => 'nullable|string|max:100|regex:/^[a-zA-Z0-9_-]*$/', 'qr' => 'nullable|image|mimes:jpg,jpeg,png,webp|max:2048|dimensions:min_width=100,min_height=100,max_width=2400,max_height=2400']);
        $phone = TelefonoPeru::normalizar($data['celular']); $wa = TelefonoPeru::normalizar($data['whatsapp']);
        $path = $this->qr?->store('pagos/qr', 'public');
        $hash = $path ? hash_file('sha256', $this->qr->getRealPath()) : null;
        DB::transaction(function () use ($actor, $phone, $wa, $path, $hash) {
            DB::table('integracion_secuencias')->where('clave', 'reparto')->increment('valor', 0);
            $r = ReceptorPago::firstOrNew(['user_id' => $this->vendedorId]);
            $reference = $actor->puedeAdministracionCritica() ? ($this->referencia ?: null) : ($r->referencia_externa ?? 'personal_'.$actor->id);
            $changed = ! $r->exists || $r->nombre_receptor !== $this->nombre || $r->celular !== $phone || $r->referencia_externa !== $reference || ($hash && $r->qr_sha256 !== $hash);
            if ($changed && ! $actor->puedeAdministracionCritica() && ! $this->declaracionTitularidad) {
                throw \Illuminate\Validation\ValidationException::withMessages(['receptor' => 'Confirma la declaración de titularidad de la cuenta personal.']);
            }
            $r->fill(['nombre_receptor' => $this->nombre, 'celular' => $phone, 'whatsapp' => $wa, 'referencia_externa' => $reference]);
            if ($path) { $r->qr_path = $path; $r->qr_sha256 = $hash; }
            if ($changed) { $r->enabled = false; $r->verified_at = null; $r->verified_by = null; $r->version = ($r->version ?? 0) + 1; }
            $r->save();
            if ($changed && app(\App\Services\Payments\AsignacionesReceptor::class)->configurada()) {
                app(\App\Services\Payments\AsignacionesReceptor::class)->auditar($actor, $this->vendedorId, $r->id, 'receiver_identity_saved', null, ['identity_version' => $r->version, 'qr_sha256' => $r->qr_sha256, 'ownership_declared' => $this->declaracionTitularidad]);
            }
        }, 5);
        $this->qr = null;
        $this->confirmacionFisica = false;
        Flux::toast('Configuración guardada. Los cambios de receptor o QR requieren nueva habilitación técnica.');
    }
    public function prepararBridge(): void {
        $actor = $this->autorizar();
        $r = ReceptorPago::where('user_id', $this->vendedorId)->firstOrFail();
        $mode = $actor->puedeAdministracionCritica() ? $this->modoBridge : 'personal';
        $ticket = app(\App\Services\Payments\BridgeProvisioning::class)->ticket($actor, $r, $mode, $this->autorizacionResponsable);
        $base = request()->getSchemeAndHttpHost();
        if (! str_starts_with($base, 'https://') && ! app()->environment('local', 'testing')) abort(422, 'La vinculación pública requiere HTTPS.');
        $this->enlaceBridge = 'huatabridge://enroll?server='.rawurlencode($base).'&ticket='.$ticket;
        $this->autorizacionResponsable = false;
        Flux::toast('Enlace de vinculación preparado. Vence en diez minutos; la APK no contiene credenciales.');
    }
    public function revocarBridge(string $id): void {
        $actor = $this->autorizar();
        app(\App\Services\Payments\BridgeProvisioning::class)->revoke($actor, $id);
        Flux::toast('Dispositivo revocado. La evidencia y la cola se conservan.');
    }
    public function guardarAsignacion(): void {
        $actor = $this->autorizar();
        app(\App\Services\Payments\AsignacionesReceptor::class)->asignar($actor, User::findOrFail($this->vendedorId), $this->modalidad, $this->receptorCompartido ? (int) $this->receptorCompartido : null, $this->permisoPersonal, $this->autorizacionResponsable);
        $this->autorizacionResponsable = false;
        Flux::toast('Asignación guardada. Los pedidos anteriores conservan su receptor.');
    }
    public function guardarCentral(): void {
        $actor = $this->autorizar();
        app(\App\Services\Payments\AsignacionesReceptor::class)->central($actor, (int) $this->receptorCentral, $this->autorizacionResponsable);
        $this->autorizacionResponsable = false;
        Flux::toast('Cuenta central configurada con autorización de su responsable.');
    }
    public function consultarConexion(): void {
        $this->autorizar();
        $r = ReceptorPago::where('user_id', $this->vendedorId)->first();
        $this->operatividad = $r ? app(ReceptoresService::class)->diagnostico($r) : 'Guarda primero el receptor y su QR.';
    }
    public function verificar(): void {
        $this->cambiarReceptor('verify');
    }
    public function alternar(): void {
        $actor = $this->autorizar(); abort_unless($actor->puedeAdministracionCritica(), 403);
        $r = ReceptorPago::where('user_id', $this->vendedorId)->firstOrFail();
        $this->cambiarReceptor($r->enabled ? 'disable' : 'enable');
    }
    public function desactivar(): void { $this->cambiarReceptor('disable'); }
    private function cambiarReceptor(string $accion): void {
        $actor = $this->autorizar(); abort_unless($actor->puedeAdministracionCritica(), 403);
        $r = ReceptorPago::where('user_id', $this->vendedorId)->firstOrFail();
        try {
            app(ReceptoresService::class)->cambiar($actor, $r, $accion, $this->confirmacionFisica);
            $this->confirmacionFisica = false;
            $this->consultarConexion();
            Flux::toast(match ($accion) { 'verify' => 'Verificación física y técnica registrada. La recepción sigue desactivada.', 'enable' => 'Recepción habilitada en Core y Laravel.', default => 'Recepción deshabilitada en Core y Laravel.' });
        } catch (\Illuminate\Validation\ValidationException $e) {
            $this->operatividad = $e->validator->errors()->first();
            Flux::toast($this->operatividad, variant: 'danger');
        }
    }
    public function with(): array {
        $actor = $this->autorizar();
        $asignaciones = app(\App\Services\Payments\AsignacionesReceptor::class);
        return ['bridgeListo' => app(\App\Services\Payments\BridgeProvisioning::class)->ready(), 'dispositivosBridge' => app(\App\Services\Payments\BridgeProvisioning::class)->ready() ? DB::table('payment_bridge_devices')->where('owner_id', $this->vendedorId)->get(['id', 'status', 'mode']) : collect(), 'politicasListas' => $asignaciones->configurada(), 'personalPermitido' => $actor->puedeAdministracionCritica() || $asignaciones->puedePersonal($actor), 'receptorAsignado' => $asignaciones->receptor(User::findOrFail($this->vendedorId)), 'receptoresAutorizados' => $actor->puedeAdministracionCritica() ? ReceptorPago::whereNotNull('verified_at')->get() : collect(), 'receptor' => ReceptorPago::where('user_id', $this->vendedorId)->first(), 'vendedores' => auth('web')->user()->puedeAdministracionCritica() ? User::conPantalla('vendedor.configuracion-pagos')->orderBy('nombres')->get() : collect()];
    }
}; ?>
<div class="flex flex-col gap-6" x-data x-on:toast-show.stop="$el.querySelector('ui-toast').showToast($event.detail)">
    {{-- Una sola instancia reemplaza la confirmación anterior; no se apila en el grupo global. --}}
    <flux:toast />
    <x-ui.titulo titulo="Configuración de pagos">QR y contacto de tu receptor. Los pedidos existentes conservan su asignación original.</x-ui.titulo>
    @if($vendedores->isNotEmpty())
        <div class="flex flex-wrap gap-2">@foreach($vendedores as $seller) <flux:button as="a" size="sm" :href="route('vendedor.configuracion-pagos', ['vendedor' => $seller->id])">{{ $seller->nombreCompleto() }}</flux:button> @endforeach</div>
    @endif
    @if($personalPermitido)
    <form wire:submit="guardar" class="huata-cuadro flex max-w-lg flex-col gap-4 rounded-xl border p-5">
        <flux:input label="Nombre del titular/receptor" wire:model="nombre" required />
        <flux:input label="Número Yape receptor" wire:model="celular" required />
        <flux:input label="WhatsApp de atención" wire:model="whatsapp" required />
        @if(auth('web')->user()->puedeAdministracionCritica()) <flux:input label="Referencia del receptor autorizado" wire:model="referencia" /> @endif
        <flux:input type="file" label="QR (PNG, JPEG o WebP, máximo 2 MB)" wire:model="qr" accept="image/png,image/jpeg,image/webp" />
        @if($qr) <img src="{{ $qr->temporaryUrl() }}" alt="Previsualización del QR propuesto" class="w-48 rounded-xl">
        @elseif($receptor?->qr_path) <img src="{{ asset('storage/'.$receptor->qr_path) }}" alt="QR guardado" class="w-48 rounded-xl"> @endif
        <flux:text>Subir una imagen no demuestra titularidad. La habilitación exige la verificación del receptor y su Bridge por el administrador.</flux:text>
        @unless(auth('web')->user()->puedeAdministracionCritica()) <flux:checkbox wire:model="declaracionTitularidad" label="Declaro ser titular o estar autorizado para esta cuenta personal y el QR registrado" /> @endunless
        <flux:button type="submit" variant="primary" wire:loading.attr="disabled" wire:target="guardar,qr">Guardar cambios</flux:button>
    </form>
    @else <flux:text>El receptor asignado es central o compartido y su responsable gestiona Bridge. No necesitas instalar otro Bridge.</flux:text> @endif
    <div class="huata-cuadro flex max-w-lg flex-col gap-3 rounded-xl border p-5">
        <flux:badge :color="$receptor?->enabled ? 'green' : 'amber'">{{ $receptor?->enabled ? 'Recibe nuevos pedidos' : 'Recepción desactivada' }}</flux:badge>
        <flux:text>Vinculación: {{ $receptor?->verified_at ? 'Verificada por administrador' : 'Pendiente de verificación' }}</flux:text>
        <flux:text>{{ $operatividad }}</flux:text>
        <flux:button wire:click="consultarConexion">Consultar disponibilidad</flux:button>
        @if($receptorAsignado) <flux:text>Receptor asignado para nuevos cobros: {{ $receptorAsignado->nombre_receptor }} · {{ $receptorAsignado->celular }}</flux:text> @endif
        @if($politicasListas && auth('web')->user()->puedeAdministracionCritica())
            <flux:select wire:model="receptorCentral" label="Cuenta central autorizada">
                <flux:select.option value="">Seleccionar receptor verificado</flux:select.option>
                @foreach($receptoresAutorizados as $opcion) <flux:select.option :value="$opcion->id">{{ $opcion->nombre_receptor }} · {{ $opcion->referencia_externa }}</flux:select.option> @endforeach
            </flux:select>
            <flux:button wire:click="guardarCentral">Guardar cuenta central</flux:button>
            <flux:select wire:model="modalidad" label="Modalidad del vendedor">
                <flux:select.option value="personal">Cuenta personal autorizada</flux:select.option>
                <flux:select.option value="central">Cuenta central</flux:select.option>
                <flux:select.option value="shared">Otro receptor autorizado</flux:select.option>
            </flux:select>
            <flux:select wire:model="receptorCompartido" label="Otro receptor existente">
                <flux:select.option value="">Seleccionar receptor</flux:select.option>
                @foreach($receptoresAutorizados as $opcion) <flux:select.option :value="$opcion->id">{{ $opcion->nombre_receptor }} · {{ $opcion->referencia_externa }}</flux:select.option> @endforeach
            </flux:select>
            <flux:checkbox wire:model="permisoPersonal" label="Permitir el alta de cuenta personal a este vendedor" />
            <flux:checkbox wire:model="autorizacionResponsable" label="Confirmo la autorización del responsable del receptor central o compartido" />
            <flux:button wire:click="guardarAsignacion">Guardar asignación del vendedor</flux:button>
        @endif
        @if($bridgeListo && $personalPermitido && $receptor)
            <flux:button as="a" :href="route('vendedor.bridge-descarga')">Descargar Bridge firmado</flux:button>
            @if(auth('web')->user()->puedeAdministracionCritica())
                <flux:select wire:model="modoBridge" label="Autorización del dispositivo Bridge">
                    <flux:select.option value="personal">Personal: exige sesión Huata compatible</flux:select.option>
                    <flux:select.option value="shared">Compartido: autorizado para el receptor</flux:select.option>
                    <flux:select.option value="direct">Directo: teléfono sin Huata, autorizado por responsable</flux:select.option>
                </flux:select>
            @endif
            <flux:button wire:click="prepararBridge" wire:loading.attr="disabled">Preparar vinculación o actualizar dispositivo</flux:button>
            @if($enlaceBridge) <flux:button as="a" :href="$enlaceBridge" rel="noreferrer">Vincular Bridge instalado en este teléfono</flux:button> @endif
            <flux:text>Instala la APK conservando sus datos, vuelve aquí y pulsa Vincular. Android solicitará el acceso a notificaciones. No necesitas escribir servidor ni claves.</flux:text>
            @foreach($dispositivosBridge as $device)
                <flux:text>{{ $device->id }} · {{ $device->mode }} · {{ $device->status }}</flux:text>
                @unless($device->status === 'revoked') <flux:button wire:click="revocarBridge('{{ $device->id }}')">Revocar dispositivo</flux:button> @endunless
            @endforeach
        @endif
        @if(auth('web')->user()->puedeAdministracionCritica())
            <flux:checkbox wire:model="confirmacionFisica" label="Confirmo físicamente que titular, número, cuenta Yape y QR corresponden al receptor guardado" />
            <flux:button wire:click="verificar" wire:loading.attr="disabled">Verificar vinculación técnica</flux:button>
            @if($receptor)
                <flux:button wire:click="alternar" wire:loading.attr="disabled">{{ $receptor->enabled ? 'Desactivar nuevos pedidos' : 'Activar nuevos pedidos' }}</flux:button>
                @unless($receptor->enabled) <flux:button wire:click="desactivar" wire:loading.attr="disabled">Confirmar desactivación en Core</flux:button> @endunless
            @endif
        @endif
    </div>
</div>

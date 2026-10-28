<?php

namespace App\Livewire;

use App\Models\{ReceptorPago, User};
use App\Services\Payments\{AsignacionesReceptor, HuataYape, ReceptoresService, SolicitudesRecepcionYape};
use Illuminate\Support\Facades\DB;
use Livewire\Attributes\Locked;
use Livewire\{Component, WithFileUploads, WithPagination};
use Flux\Flux;

class ReceptoresYape extends Component
{
    use WithFileUploads, WithPagination;

    #[Locked] public ?int $editando = null;
    public string $nombre = '';
    public string $numero = '';
    public string $responsable = '';
    public string $buscarUsuario = '';
    public string $buscar = '';
    public string $vendedor = '';
    public string $receptorAsignado = '';
    public $qr = null;
    public array $confirmacionFisica = [];
    public array $confirmacionTransferencia = [];
    #[Locked] public array $diagnosticos = [];

    private function actor(): User
    {
        $actor = auth('web')->user();
        abort_unless($actor?->activo && $actor->puedePantalla('admin.receptores-yape'), 403);
        return $actor;
    }

    public function nuevo(): void
    {
        $this->actor();
        $this->reset('editando', 'nombre', 'numero', 'responsable', 'qr', 'confirmacionFisica', 'confirmacionTransferencia');
        $this->resetValidation();
    }

    public function editar(int $id): void
    {
        $this->nuevo();
        $r = ReceptorPago::findOrFail($id);
        $this->editando = $id;
        $this->nombre = $r->nombre_receptor;
        $this->numero = $r->celular;
        $this->responsable = (string) (app(SolicitudesRecepcionYape::class)->actual($id)?->responsible_id ?? $r->user_id);
    }

    public function guardar(): void
    {
        $actor = $this->actor();
        $this->validate(['nombre' => 'required|string|max:100', 'numero' => 'required|string|max:20', 'responsable' => 'required|integer|exists:users,id',
            'qr' => 'nullable|image|mimes:jpg,jpeg,png,webp|max:2048|dimensions:min_width=100,min_height=100,max_width=2400,max_height=2400']);
        $path = $this->qr?->store('pagos/qr', 'public');
        $hash = $path ? hash_file('sha256', $this->qr->getRealPath()) : null;
        $r = app(SolicitudesRecepcionYape::class)->guardar($actor, $this->editando, trim($this->nombre), $this->numero, (int) $this->responsable, $path, $hash);
        $this->editar($r->id);
        Flux::toast('Receptor guardado. La solicitud llegará al responsable cuando Huata se conecte. Los cobros requieren verificación y habilitación administrativa.');
    }

    public function reenviar(int $id): void
    {
        $this->actor();
        $r = ReceptorPago::findOrFail($id);
        $responsible = app(SolicitudesRecepcionYape::class)->actual($id)?->responsible_id ?? $r->user_id;
        app(SolicitudesRecepcionYape::class)->guardar($this->actor(), $id, $r->nombre_receptor, $r->celular, $responsible, null, null, true);
        Flux::toast('Nueva solicitud pendiente. Se conserva el historial de decisiones.');
    }

    public function asignar(): void
    {
        $this->validate(['vendedor' => 'required|integer', 'receptorAsignado' => 'required|integer']);
        app(SolicitudesRecepcionYape::class)->asignar($this->actor(), User::findOrFail($this->vendedor), ReceptorPago::findOrFail($this->receptorAsignado));
        Flux::toast('Asignación guardada para cobros nuevos. Solo se ofrecerá el QR cuando el receptor esté disponible.');
    }

    public function revocar(int $id): void
    {
        app(SolicitudesRecepcionYape::class)->revocar($this->actor(), ReceptorPago::findOrFail($id));
        Flux::toast('Receptor y recepción revocados. Historial y evidencias conservados.');
    }

    public function transferir(int $id): void
    {
        $actor = $this->actor();
        $q = app(SolicitudesRecepcionYape::class)->actual($id);
        abort_unless($q?->status === 'accepted' && $q->installation_id, 422, 'Primero debe aceptar el responsable.');
        if (! ($this->confirmacionTransferencia[$id] ?? false)) throw \Illuminate\Validation\ValidationException::withMessages(['recepcion' => 'Comprueba Huata físicamente, revisa la cola anterior y detén el listener anterior antes de confirmar.']);
        app(HuataYape::class)->approve($actor, $q->installation_id, $id, 'shared', true);
        $this->confirmacionTransferencia = [];
        Flux::toast('Transferencia autorizada. Huata completará la vinculación al consultar su estado.');
    }

    public function central(int $id): void
    {
        $actor = $this->actor();
        $request = app(SolicitudesRecepcionYape::class)->actual($id);
        if ($request && $request->status !== 'accepted') throw \Illuminate\Validation\ValidationException::withMessages(['recepcion' => 'El responsable debe aceptar antes de configurar la cuenta central.']);
        app(AsignacionesReceptor::class)->central($actor, $id, true);
        Flux::toast('Receptor central configurado. Los cobros comprobarán su disponibilidad.');
    }

    public function consultar(int $id): void
    {
        $this->actor();
        $this->diagnosticos[$id] = app(ReceptoresService::class)->diagnostico(ReceptorPago::findOrFail($id));
    }

    public function verificar(int $id): void { $this->cambiar($id, 'verify'); }
    public function habilitar(int $id): void { $this->cambiar($id, 'enable'); }
    public function deshabilitar(int $id): void { $this->cambiar($id, 'disable'); }

    private function cambiar(int $id, string $accion): void
    {
        app(ReceptoresService::class)->cambiar($this->actor(), ReceptorPago::findOrFail($id), $accion, (bool) ($this->confirmacionFisica[$id] ?? false));
        $this->confirmacionFisica = [];
        $this->consultar($id);
        Flux::toast('Estado confirmado por Core y Laravel. Aceptar un teléfono no demuestra titularidad ni confirma pagos.');
    }

    public function render()
    {
        $this->actor();
        $service = app(SolicitudesRecepcionYape::class);
        $receivers = ReceptorPago::where(fn ($q) => $q->where('nombre_receptor', 'like', '%'.$this->buscar.'%')->orWhere('celular', 'like', '%'.$this->buscar.'%'))->orderByDesc('id')->paginate(10);
        $states = [];
        foreach ($receivers as $r) {
            $q = $service->actual($r->id);
            $d = $q?->installation_id ? DB::table('huata_yape_installations')->find($q->installation_id) : DB::table('huata_yape_installations')->where('receiver_id', $r->id)->orderByDesc('updated_at')->first();
            $states[$r->id] = ['request' => $q, 'device' => $d, 'responsible' => User::find($q?->responsible_id ?? $r->user_id),
                'label' => match (true) {
                    $q?->status === 'revoked', $d?->status === 'revoked' && ! $q => 'Revocado',
                    $q?->status === 'rejected' => 'Rechazado',
                    $q?->status === 'pending' => 'Pendiente de aceptación',
                    $q?->status === 'accepted' && ! $d?->notification_permission => 'Aceptado, pendiente de permiso Android',
                    $d?->status === 'authorized' => 'Pendiente de vinculación técnica',
                    $d?->status === 'active' && (! $d->last_communication_at || now()->diffInMinutes($d->last_communication_at, true) > 2) => 'Sin comunicación',
                    $d?->status === 'active' => 'Activo',
                    default => 'Instalación anterior; solicitar aceptación',
                },
                'sellers' => User::whereIn('id', DB::table('payment_seller_assignments')->where('receiver_id', $r->id)->select('user_id'))->get()];
        }
        $usuarios = User::where('activo', true)->where(fn ($q) => $q->where('nombres', 'like', '%'.$this->buscarUsuario.'%')->orWhere('apellidos', 'like', '%'.$this->buscarUsuario.'%')->orWhere('usuario', 'like', '%'.$this->buscarUsuario.'%')->orWhere('name', 'like', '%'.$this->buscarUsuario.'%'))->orderBy('nombres')->limit(100)->get();
        if ($this->responsable && ! $usuarios->contains('id', (int) $this->responsable) && ($selected = User::where('activo', true)->find($this->responsable))) $usuarios->push($selected);
        return view('livewire.receptores-yape', ['receptores' => $receivers, 'estados' => $states,
            'usuarios' => $usuarios,
            'vendedores' => User::where('activo', true)->orderBy('nombres')->get()->filter(fn ($u) => $u->puedeVender('vendedor.ventas') || $u->puedeVender('vendedor.pedidos-web')),
            'opcionesReceptor' => ReceptorPago::orderBy('nombre_receptor')->get(), 'editado' => $this->editando ? ReceptorPago::find($this->editando) : null]);
    }
}

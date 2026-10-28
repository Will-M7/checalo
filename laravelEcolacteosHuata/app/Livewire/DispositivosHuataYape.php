<?php
namespace App\Livewire;
use App\Models\ReceptorPago;
use App\Services\Payments\{AsignacionesReceptor, HuataYape};
use Illuminate\Support\Facades\DB;
use Livewire\Attributes\Locked;
use Livewire\Component;
use Flux\Flux;
class DispositivosHuataYape extends Component {
    #[Locked] public ?int $receptorId = null;
    public string $modo = 'personal';
    public bool $transferencia = false;
    private function autorizar(): void { app(AsignacionesReceptor::class)->administrar(auth('web')->user()); }
    public function aprobar(string $id): void {
        $this->autorizar();
        abort_unless($this->receptorId, 422, 'Guarda o selecciona primero el receptor.');
        app(HuataYape::class)->approve(auth('web')->user(), $id, $this->receptorId, $this->modo, $this->transferencia);
        $this->transferencia = false;
        Flux::toast('Dispositivo Huata autorizado. El titular debe aceptar el permiso en su aplicación.', variant: 'success');
    }
    public function revocar(string $id): void {
        $this->autorizar(); app(HuataYape::class)->revoke(auth('web')->user(), $id);
        Flux::toast('Recepción del dispositivo revocada. Se conservan cola e historial.', variant: 'success');
    }
    public function render() {
        $this->autorizar();
        return view('livewire.dispositivos-huata-yape', ['listo' => app(HuataYape::class)->ready(),
            'receptor' => $this->receptorId ? ReceptorPago::findOrFail($this->receptorId) : null,
            'dispositivos' => app(HuataYape::class)->ready() ? DB::table('huata_yape_installations')->join('users', 'huata_yape_installations.user_id', '=', 'users.id')
                ->where(fn ($q) => $q->whereNull('receiver_id')->orWhere('receiver_id', $this->receptorId))
                ->select('huata_yape_installations.*', 'users.name as nombre_usuario')->orderByDesc('created_at')->get() : collect()]);
    }
}

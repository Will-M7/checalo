<?php
namespace App\Livewire;
use Livewire\Component;
use App\Services\VendedoresWeb;
class DisponibilidadWeb extends Component
{
    public function pausar(bool $pausa): void
    {
        app(VendedoresWeb::class)->cambiar(auth('web')->user(), auth('web')->user(), $pausa, false);
    }
    public function render()
    {
        abort_unless(auth('web')->user()?->puedePantalla('vendedor.pedidos-web'), 403);
        return view('livewire.disponibilidad-web', ['disponibilidad' => app(VendedoresWeb::class)->estado(auth('web')->user())]);
    }
}

<?php

namespace App\Notifications;

use App\Models\Proveedor;
use Illuminate\Notifications\Notification;

class ProveedorUbicacionActualizada extends Notification
{
    public function __construct(public Proveedor $proveedor)
    {
        //
    }

    /**
     * @return array<int, string>
     */
    public function via(object $notifiable): array
    {
        return ['database'];
    }

    /**
     * @return array<string, mixed>
     */
    public function toArray(object $notifiable): array
    {
        return [
            'proveedor_id' => $this->proveedor->id,
            'titulo' => 'Proveedor actualizó su ubicación',
            'mensaje' => $this->proveedor->user->nombreCompleto().' cambió la ubicación de su finca. Puede necesitar una nueva ruta asignada.',
        ];
    }
}

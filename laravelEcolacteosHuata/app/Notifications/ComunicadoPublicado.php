<?php

namespace App\Notifications;

use App\Models\Comunicado;
use Illuminate\Notifications\Notification;
use Illuminate\Support\Str;

class ComunicadoPublicado extends Notification
{
    public function __construct(public Comunicado $comunicado)
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
            'comunicado_id' => $this->comunicado->id,
            'titulo' => $this->comunicado->tipo->label().': '.$this->comunicado->titulo,
            'mensaje' => Str::limit($this->comunicado->descripcion, 100),
        ];
    }
}

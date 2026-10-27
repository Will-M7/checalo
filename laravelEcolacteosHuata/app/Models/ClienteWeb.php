<?php

namespace App\Models;

use Illuminate\Auth\Notifications\ResetPassword;
use Illuminate\Foundation\Auth\User as Authenticatable;
use Illuminate\Notifications\Notifiable;

class ClienteWeb extends Authenticatable
{
    use Notifiable;

    protected $table = 'clientes_web';

    protected $fillable = ['nombres', 'apellidos', 'celular', 'email', 'password'];

    protected $hidden = ['password', 'remember_token'];

    protected function casts(): array
    {
        return ['password' => 'hashed'];
    }

    public function sendPasswordResetNotification($token): void
    {
        $notification = new class($token) extends ResetPassword
        {
            protected function resetUrl($notifiable): string
            {
                return route('cliente.password.reset', ['token' => $this->token, 'email' => $notifiable->email]);
            }
        };
        $this->notify($notification);
    }

    public function saldoMinor(): int
    {
        return (int) MovimientoSaldo::where('cliente_web_id', $this->id)->where('estado', 'aplicado')->sum('amount_minor');
    }

    public function nombreCompleto(): string
    {
        return $this->nombres.' '.$this->apellidos;
    }
}

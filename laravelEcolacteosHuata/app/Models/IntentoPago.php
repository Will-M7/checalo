<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class IntentoPago extends Model
{
    protected $table = 'intentos_pago';

    protected $guarded = [];

    protected function casts(): array
    {
        return ['receptor_snapshot' => 'array', 'expires_at' => 'datetime', 'applied_at' => 'datetime', 'amount_minor' => 'integer'];
    }

    public function pedido()
    {
        return $this->belongsTo(PedidoWeb::class, 'pedido_web_id');
    }

    public function cliente()
    {
        return $this->belongsTo(ClienteWeb::class, 'cliente_web_id');
    }
}

<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class ReceptorPago extends Model
{
    protected $table = 'receptores_pago';

    protected $guarded = [];

    protected function casts(): array
    {
        return ['enabled' => 'boolean', 'verified_at' => 'datetime'];
    }

    public function vendedor()
    {
        return $this->belongsTo(User::class, 'user_id');
    }

    public function snapshot(): array
    {
        $seller = $this->relationLoaded('vendedorCobro') ? $this->getRelation('vendedorCobro') : $this->vendedor;
        return ['receptor_id' => $this->id, 'external_reference' => $this->referencia_externa, 'version' => $this->version,
            'nombre' => $this->nombre_receptor, 'celular' => $this->celular, 'qr_path' => $this->qr_path,
            'qr_sha256' => $this->qr_sha256, 'whatsapp' => $this->whatsapp, 'vendedor_nombre' => $seller->nombreCompleto(), 'vendedor_id' => $seller->id];
    }
}

<?php

namespace App\Models;

use Database\Factories\EmpresaFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;

#[Fillable(['razon_social', 'ruc', 'telefono', 'direccion'])]
class Empresa extends Model
{
    /** @use HasFactory<EmpresaFactory> */
    use HasFactory;

    protected $table = 'empresa';

    /**
     * Teléfono de la empresa listo para wa.me (solo dígitos; un celular de 9 dígitos lleva el
     * prefijo de Perú), o null si no hay teléfono.
     */
    public function whatsapp(): ?string
    {
        $digitos = preg_replace('/\D+/', '', (string) $this->telefono);

        if ($digitos === '') {
            return null;
        }

        return strlen($digitos) === 9 ? '51'.$digitos : $digitos;
    }

    /**
     * La configuración de la empresa es una fila única; la crea con valores por defecto
     * si todavía no existe.
     */
    public static function actual(): self
    {
        return static::query()->firstOrCreate([], [
            'razon_social' => 'Ecolácteos Huata',
            'ruc' => '',
            'telefono' => '941431262',
            'direccion' => 'Huata, Puno, Perú',
        ]);
    }
}

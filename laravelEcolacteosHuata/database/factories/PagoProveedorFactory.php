<?php

namespace Database\Factories;

use App\Enums\MetodoPago;
use App\Enums\Periodo;
use App\Models\PagoProveedor;
use App\Models\Proveedor;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<PagoProveedor>
 */
class PagoProveedorFactory extends Factory
{
    /**
     * Define the model's default state.
     *
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        return [
            'proveedor_id' => Proveedor::factory(),
            'recojo_id' => null,
            'fecha' => now()->toDateString(),
            'monto' => fake()->randomFloat(2, 20, 300),
            'metodo_pago' => MetodoPago::Efectivo,
            'periodo' => Periodo::Semana,
        ];
    }
}

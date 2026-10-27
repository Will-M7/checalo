<?php

namespace Database\Factories;

use App\Models\Compra;
use App\Models\CompraDetalle;
use App\Models\Insumo;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<CompraDetalle>
 */
class CompraDetalleFactory extends Factory
{
    /**
     * Define the model's default state.
     *
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        $cantidad = fake()->randomFloat(3, 5, 100);
        $costoUnitario = fake()->randomFloat(4, 0.1, 5);

        return [
            'compra_id' => Compra::factory(),
            'insumo_id' => Insumo::factory(),
            'cantidad' => $cantidad,
            'costo_unitario' => $costoUnitario,
            'subtotal' => round($cantidad * $costoUnitario, 2),
        ];
    }
}

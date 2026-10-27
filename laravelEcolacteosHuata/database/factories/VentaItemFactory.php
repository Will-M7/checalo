<?php

namespace Database\Factories;

use App\Models\Produccion;
use App\Models\Venta;
use App\Models\VentaItem;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<VentaItem>
 */
class VentaItemFactory extends Factory
{
    /**
     * Define the model's default state.
     *
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        $precio = fake()->randomFloat(2, 5, 40);
        $cantidad = fake()->numberBetween(1, 5);

        return [
            'venta_id' => Venta::factory(),
            'produccion_id' => Produccion::factory(),
            'nombre' => fake()->word(),
            'precio_unitario' => $precio,
            'cantidad' => $cantidad,
            'subtotal' => round($precio * $cantidad, 2),
        ];
    }
}

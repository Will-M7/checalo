<?php

namespace Database\Factories;

use App\Models\PedidoWeb;
use App\Models\PedidoWebItem;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<PedidoWebItem>
 */
class PedidoWebItemFactory extends Factory
{
    /**
     * Define the model's default state.
     *
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        $cantidad = fake()->numberBetween(1, 5);
        $precio = fake()->randomFloat(2, 3, 30);

        return [
            'pedido_web_id' => PedidoWeb::factory(),
            'produccion_id' => null,
            'producto' => fake()->words(2, true),
            'cantidad' => $cantidad,
            'precio_unitario' => $precio,
            'subtotal' => round($cantidad * $precio, 2),
        ];
    }
}

<?php

namespace Database\Factories;

use App\Models\Insumo;
use App\Models\Produccion;
use App\Models\ProduccionDetalle;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<ProduccionDetalle>
 */
class ProduccionDetalleFactory extends Factory
{
    /**
     * Define the model's default state.
     *
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        return [
            'produccion_id' => Produccion::factory(),
            'insumo_id' => Insumo::factory(),
            'cantidad_consumida' => fake()->randomFloat(3, 0.1, 10),
        ];
    }
}

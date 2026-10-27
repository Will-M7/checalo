<?php

namespace Database\Factories;

use App\Models\UnidadMedida;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<UnidadMedida>
 */
class UnidadMedidaFactory extends Factory
{
    /**
     * Define the model's default state.
     *
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        return [
            'nombre' => fake()->randomElement(['Kilogramo', 'Litro', 'Gramo', 'Mililitro', 'Unidad']),
            'simbolo' => fake()->randomElement(['kg', 'L', 'g', 'ml', 'un']),
        ];
    }
}

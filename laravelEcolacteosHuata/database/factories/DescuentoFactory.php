<?php

namespace Database\Factories;

use App\Models\Descuento;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<Descuento>
 */
class DescuentoFactory extends Factory
{
    /**
     * Define the model's default state.
     *
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        return [
            'nombre' => fake()->randomElement(['Descuento por volumen', 'Descuento de temporada', 'Cliente frecuente']),
            'porcentaje' => fake()->randomFloat(2, 5, 20),
            'activo' => true,
        ];
    }
}

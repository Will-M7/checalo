<?php

namespace Database\Factories;

use App\Models\PrecioLeche;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<PrecioLeche>
 */
class PrecioLecheFactory extends Factory
{
    /**
     * Define the model's default state.
     *
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        return [
            'precio_litro' => fake()->randomFloat(2, 1.2, 2.5),
            'vigente_desde' => now()->toDateString(),
        ];
    }
}

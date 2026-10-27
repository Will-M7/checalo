<?php

namespace Database\Factories;

use App\Models\DiaAsistenciaAcopiador;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<DiaAsistenciaAcopiador>
 */
class DiaAsistenciaAcopiadorFactory extends Factory
{
    /**
     * Define the model's default state.
     *
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        return [
            'fecha' => fake()->unique()->dateTimeBetween('-1 month', '+1 month')->format('Y-m-d'),
        ];
    }
}

<?php

namespace Database\Factories;

use App\Models\Acopiador;
use App\Models\Ruta;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<Ruta>
 */
class RutaFactory extends Factory
{
    /**
     * Define the model's default state.
     *
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        return [
            'acopiador_id' => Acopiador::factory(),
            'nombre' => 'Ruta '.fake()->streetName(),
            'activa' => true,
        ];
    }
}

<?php

namespace Database\Factories;

use App\Models\Empresa;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<Empresa>
 */
class EmpresaFactory extends Factory
{
    /**
     * Define the model's default state.
     *
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        return [
            'razon_social' => 'Ecolácteos Huata',
            'ruc' => fake()->numerify('20###########'),
            'telefono' => fake()->numerify('9########'),
            'direccion' => 'Huata, Puno, Perú',
        ];
    }
}

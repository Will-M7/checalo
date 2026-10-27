<?php

namespace Database\Factories;

use App\Models\Insumo;
use App\Models\UnidadMedida;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<Insumo>
 */
class InsumoFactory extends Factory
{
    /**
     * Define the model's default state.
     *
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        return [
            'nombre' => fake()->randomElement(['Leche fresca', 'Cuajo', 'Sal', 'Cultivo láctico', 'Envase 500ml']),
            'unidad_medida_id' => UnidadMedida::factory(),
            'costo_unitario' => fake()->randomFloat(4, 0.1, 5),
            'stock_actual' => fake()->randomFloat(3, 10, 200),
        ];
    }
}

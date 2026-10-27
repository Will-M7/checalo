<?php

namespace Database\Factories;

use App\Models\Proveedor;
use App\Models\ResultadoLaboratorio;
use App\Models\Turno;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<ResultadoLaboratorio>
 */
class ResultadoLaboratorioFactory extends Factory
{
    /**
     * Define the model's default state.
     *
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        return [
            'proveedor_id' => Proveedor::factory(),
            'fecha' => now()->toDateString(),
            'turno_id' => Turno::factory(),
            'litros' => fake()->randomFloat(2, 5, 30),
            'resultado' => 'conforme',
            'densidad' => fake()->randomFloat(3, 1.028, 1.034),
            'acidez' => fake()->randomFloat(3, 0.13, 0.17),
            'temperatura' => fake()->randomFloat(2, 2, 8),
            'grasa' => fake()->randomFloat(2, 3, 5),
            'sng' => fake()->randomFloat(2, 8, 9),
            'proteina' => fake()->randomFloat(2, 3, 4),
            'lactosa' => fake()->randomFloat(2, 4, 5),
            'sales' => fake()->randomFloat(2, 0.6, 0.8),
            'total_solidos' => fake()->randomFloat(2, 12, 14),
            'agua_anadida' => 0,
            'punto_congelacion' => fake()->randomFloat(3, -0.56, -0.50),
            'ph' => fake()->randomFloat(2, 6.6, 6.8),
            'observaciones' => '',
            'conforme' => true,
        ];
    }

    /**
     * Resultado no conforme (alteración de calidad).
     */
    public function noConforme(string $resultado = 'aguado'): static
    {
        return $this->state(fn (array $attributes) => [
            'resultado' => $resultado,
            'conforme' => false,
        ]);
    }
}

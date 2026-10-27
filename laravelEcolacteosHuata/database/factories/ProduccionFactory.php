<?php

namespace Database\Factories;

use App\Enums\EstadoProduccion;
use App\Models\Produccion;
use App\Models\Receta;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<Produccion>
 */
class ProduccionFactory extends Factory
{
    /**
     * Define the model's default state.
     *
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        return [
            'fecha' => now()->toDateString(),
            'receta_id' => Receta::factory(),
            'cantidad_producida' => fake()->randomFloat(3, 1, 20),
            'lote' => 'L-'.fake()->unique()->numerify('######'),
            'turno_id' => null,
            'responsable_id' => null,
            'estado' => EstadoProduccion::Planificada,
        ];
    }
}

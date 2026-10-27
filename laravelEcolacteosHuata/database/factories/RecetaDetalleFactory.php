<?php

namespace Database\Factories;

use App\Models\Insumo;
use App\Models\Receta;
use App\Models\RecetaDetalle;
use App\Models\UnidadMedida;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<RecetaDetalle>
 */
class RecetaDetalleFactory extends Factory
{
    /**
     * Define the model's default state.
     *
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        return [
            'receta_id' => Receta::factory(),
            'insumo_id' => Insumo::factory(),
            'cantidad' => fake()->randomFloat(3, 0.1, 10),
            'unidad_medida_id' => UnidadMedida::factory(),
        ];
    }
}

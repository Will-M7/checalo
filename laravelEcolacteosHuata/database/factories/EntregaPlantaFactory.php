<?php

namespace Database\Factories;

use App\Models\Acopiador;
use App\Models\EntregaPlanta;
use App\Models\Turno;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<EntregaPlanta>
 */
class EntregaPlantaFactory extends Factory
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
            'turno_id' => Turno::factory(),
            'fecha' => now()->toDateString(),
            'litros_total' => fake()->randomFloat(2, 50, 400),
            'total_pagado' => 0,
            'total_deuda' => 0,
            'proveedores_no_recogidos' => 0,
            'guia' => 'G-'.fake()->unique()->numerify('######'),
        ];
    }
}

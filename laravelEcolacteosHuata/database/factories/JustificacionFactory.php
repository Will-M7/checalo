<?php

namespace Database\Factories;

use App\Enums\EstadoJustificacion;
use App\Models\Asistencia;
use App\Models\Justificacion;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<Justificacion>
 */
class JustificacionFactory extends Factory
{
    /**
     * Define the model's default state.
     *
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        return [
            'asistencia_id' => Asistencia::factory()->falta(),
            'motivo' => fake()->sentence(),
            'archivo' => null,
            'estado' => EstadoJustificacion::Pendiente,
        ];
    }
}

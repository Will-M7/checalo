<?php

namespace Database\Factories;

use App\Models\HorarioAsistencia;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<HorarioAsistencia>
 */
class HorarioAsistenciaFactory extends Factory
{
    /**
     * Define the model's default state.
     *
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        return [
            'nombre' => 'Mañana',
            'hora_inicio' => '05:00',
            'hora_limite_presente' => '05:10',
            'hora_limite_tardanza' => '05:20',
            'activo' => true,
        ];
    }

    public function tarde(): static
    {
        return $this->state(fn (array $attributes) => [
            'nombre' => 'Tarde',
            'hora_inicio' => '17:00',
            'hora_limite_presente' => '17:10',
            'hora_limite_tardanza' => '17:20',
        ]);
    }
}

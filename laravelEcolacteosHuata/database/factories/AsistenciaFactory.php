<?php

namespace Database\Factories;

use App\Enums\EstadoAsistencia;
use App\Models\Asistencia;
use App\Models\User;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<Asistencia>
 */
class AsistenciaFactory extends Factory
{
    /**
     * Define the model's default state.
     *
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        return [
            'user_id' => User::factory(),
            'fecha' => Asistencia::hoy(),
            'estado' => EstadoAsistencia::Presente,
        ];
    }

    public function falta(): static
    {
        return $this->state(fn (array $attributes) => [
            'estado' => EstadoAsistencia::Falta,
        ]);
    }
}

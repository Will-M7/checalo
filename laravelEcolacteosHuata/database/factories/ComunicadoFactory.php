<?php

namespace Database\Factories;

use App\Enums\Rol;
use App\Enums\TipoComunicado;
use App\Models\Comunicado;
use App\Models\User;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<Comunicado>
 */
class ComunicadoFactory extends Factory
{
    /**
     * Define the model's default state.
     *
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        return [
            'tipo' => TipoComunicado::Comunicado,
            'titulo' => fake()->sentence(4),
            'descripcion' => fake()->paragraph(),
            'fecha' => now()->toDateString(),
            'autor_id' => User::factory()->rol(Rol::PersonalPlanta),
        ];
    }
}

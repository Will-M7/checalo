<?php

namespace Database\Factories;

use App\Enums\Rol;
use App\Models\Acopiador;
use App\Models\Proveedor;
use App\Models\User;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<Proveedor>
 */
class ProveedorFactory extends Factory
{
    /**
     * Define the model's default state.
     *
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        return [
            'user_id' => User::factory()->rol(Rol::Proveedor),
            'finca_ubicacion' => fake()->address(),
            'finca_referencia' => fake()->sentence(4),
            'latitud' => fake()->latitude(-15.9, -15.7),
            'longitud' => fake()->longitude(-70.1, -69.9),
            'acopiador_id' => Acopiador::factory(),
            'bloqueado' => false,
            'alteraciones' => 0,
        ];
    }

    /**
     * Proveedor bloqueado por alcanzar el umbral de alteraciones de calidad.
     */
    public function bloqueado(): static
    {
        return $this->state(fn (array $attributes) => [
            'bloqueado' => true,
            'alteraciones' => Proveedor::UMBRAL_BLOQUEO,
        ]);
    }
}

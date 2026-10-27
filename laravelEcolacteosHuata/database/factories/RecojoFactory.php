<?php

namespace Database\Factories;

use App\Enums\EstadoLeche;
use App\Models\Acopiador;
use App\Models\Proveedor;
use App\Models\Recojo;
use App\Models\Turno;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<Recojo>
 */
class RecojoFactory extends Factory
{
    /**
     * Define the model's default state.
     *
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        $litros = fake()->randomFloat(2, 5, 60);
        $precioLitro = 1.8;

        return [
            'proveedor_id' => Proveedor::factory(),
            'acopiador_id' => Acopiador::factory(),
            'turno_id' => Turno::factory(),
            'fecha' => now()->toDateString(),
            'litros' => $litros,
            'litros_entregados' => $litros,
            'estado_leche' => EstadoLeche::Normal,
            'estado_otro' => '',
            'precio_litro' => $precioLitro,
            'total' => round($litros * $precioLitro, 2),
            'pagado' => false,
            'metodo_pago' => null,
            'entregado' => false,
        ];
    }
}

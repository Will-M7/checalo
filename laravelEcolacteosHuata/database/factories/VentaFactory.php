<?php

namespace Database\Factories;

use App\Enums\MetodoPago;
use App\Models\Venta;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<Venta>
 */
class VentaFactory extends Factory
{
    /**
     * Define the model's default state.
     *
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        $subtotal = fake()->randomFloat(2, 10, 200);

        return [
            'numero_boleta' => 'B001-'.fake()->unique()->numerify('######'),
            'cliente_id' => null,
            'cliente_nombre_libre' => fake()->name(),
            'cliente_doc_libre' => fake()->numerify('########'),
            'vendedor_id' => null,
            'fecha' => now()->toDateString(),
            'subtotal' => $subtotal,
            'descuento' => 0,
            'descuento_tipo' => '',
            'total' => $subtotal,
            'metodo_pago' => MetodoPago::Efectivo,
            'efectivo_recibido' => null,
            'vuelto' => null,
        ];
    }
}

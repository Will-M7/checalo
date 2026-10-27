<?php

namespace Database\Factories;

use App\Enums\EstadoPedidoWeb;
use App\Models\PedidoWeb;
use App\Models\PedidoWebItem;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<PedidoWeb>
 */
class PedidoWebFactory extends Factory
{
    /**
     * Define the model's default state.
     *
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        return [
            'total' => 0,
            'nombre' => fake()->firstName(),
            'celular' => '9'.fake()->numerify('########'),
            'estado' => EstadoPedidoWeb::Pendiente,
        ];
    }

    /**
     * Agrega una línea al pedido y deja el total igual a la suma de sus líneas.
     *
     * @param  array<string, mixed>  $item
     */
    public function conItem(array $item = []): static
    {
        return $this->afterCreating(function (PedidoWeb $pedido) use ($item): void {
            $linea = PedidoWebItem::factory()->make($item);
            $linea->pedido_web_id = $pedido->id;
            $linea->subtotal = round($linea->cantidad * (float) $linea->precio_unitario, 2);
            $linea->save();

            $pedido->update(['total' => $pedido->items()->sum('subtotal')]);
        });
    }
}

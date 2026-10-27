<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Un pedido web pasa a tener varios productos (carrito). Los pedidos de un solo producto que ya
     * existían se copian como su primer ítem y las columnas del producto salen del pedido.
     */
    public function up(): void
    {
        Schema::create('pedido_web_items', function (Blueprint $table) {
            $table->id();
            $table->foreignId('pedido_web_id')->constrained('pedidos_web')->cascadeOnDelete();
            $table->foreignId('produccion_id')->nullable()->constrained('producciones')->nullOnDelete();
            $table->string('producto');
            $table->unsignedInteger('cantidad');
            $table->decimal('precio_unitario', 10, 2);
            $table->decimal('subtotal', 10, 2);
            $table->timestamps();
        });

        DB::table('pedidos_web')->orderBy('id')->each(function (object $pedido): void {
            DB::table('pedido_web_items')->insert([
                'pedido_web_id' => $pedido->id,
                'produccion_id' => $pedido->produccion_id,
                'producto' => $pedido->producto,
                'cantidad' => $pedido->cantidad,
                'precio_unitario' => $pedido->precio_unitario,
                'subtotal' => $pedido->total,
                'created_at' => $pedido->created_at,
                'updated_at' => $pedido->updated_at,
            ]);
        });

        Schema::table('pedidos_web', function (Blueprint $table) {
            $table->dropConstrainedForeignId('produccion_id');
            $table->dropColumn(['producto', 'cantidad', 'precio_unitario']);
        });
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::table('pedidos_web', function (Blueprint $table) {
            $table->foreignId('produccion_id')->nullable()->constrained('producciones')->nullOnDelete();
            $table->string('producto')->default('');
            $table->unsignedInteger('cantidad')->default(1);
            $table->decimal('precio_unitario', 10, 2)->default(0);
        });

        Schema::dropIfExists('pedido_web_items');
    }
};

<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Run the migrations.
     */
    public function up(): void
    {
        Schema::create('ventas', function (Blueprint $table) {
            $table->id();
            $table->string('numero_boleta')->unique();
            $table->foreignId('cliente_id')->nullable()->constrained('clientes')->nullOnDelete();
            $table->string('cliente_nombre_libre')->nullable();
            $table->string('cliente_doc_libre', 15)->nullable();
            $table->foreignId('vendedor_id')->nullable()->constrained('users')->nullOnDelete();
            $table->date('fecha');
            $table->decimal('subtotal', 10, 2);
            $table->decimal('descuento', 10, 2)->default(0);
            $table->string('descuento_tipo')->default('');
            $table->decimal('total', 10, 2);
            $table->string('metodo_pago');
            $table->decimal('efectivo_recibido', 10, 2)->nullable();
            $table->decimal('vuelto', 10, 2)->nullable();
            $table->timestamps();

            $table->index('fecha');
        });
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::dropIfExists('ventas');
    }
};

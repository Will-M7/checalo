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
        Schema::create('pedidos_web', function (Blueprint $table) {
            $table->id();
            $table->foreignId('produccion_id')->nullable()->constrained('producciones')->nullOnDelete();
            $table->string('producto');
            $table->unsignedInteger('cantidad');
            $table->decimal('precio_unitario', 10, 2);
            $table->decimal('total', 10, 2);
            $table->string('nombre', 80);
            $table->string('celular', 20)->nullable();
            $table->string('estado')->default('pendiente')->index();
            $table->timestamps();
        });
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::dropIfExists('pedidos_web');
    }
};

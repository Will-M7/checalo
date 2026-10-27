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
        Schema::create('recojos', function (Blueprint $table) {
            $table->id();
            $table->foreignId('proveedor_id')->constrained('proveedores')->cascadeOnDelete();
            $table->foreignId('acopiador_id')->constrained('acopiadores')->cascadeOnDelete();
            $table->foreignId('turno_id')->constrained('turnos')->cascadeOnDelete();
            $table->date('fecha');
            $table->decimal('litros', 8, 2);
            $table->string('estado_leche');
            $table->string('estado_otro')->default('');
            $table->decimal('precio_litro', 8, 2);
            $table->decimal('total', 10, 2);
            $table->boolean('pagado')->default(false);
            $table->string('metodo_pago')->nullable();
            $table->boolean('entregado')->default(false);
            $table->timestamps();

            $table->index('fecha');
        });
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::dropIfExists('recojos');
    }
};

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
        Schema::create('resultados_laboratorio', function (Blueprint $table) {
            $table->id();
            $table->foreignId('proveedor_id')->constrained('proveedores')->cascadeOnDelete();
            $table->date('fecha');
            $table->foreignId('turno_id')->nullable()->constrained('turnos')->nullOnDelete();
            $table->string('resultado');
            $table->decimal('densidad', 6, 3)->nullable();
            $table->decimal('acidez', 6, 3)->nullable();
            $table->decimal('temperatura', 5, 2)->nullable();
            $table->string('observaciones')->default('');
            $table->boolean('conforme');
            $table->timestamps();
        });
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::dropIfExists('resultados_laboratorio');
    }
};

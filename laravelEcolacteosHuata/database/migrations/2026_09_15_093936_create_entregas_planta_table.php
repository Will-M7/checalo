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
        Schema::create('entregas_planta', function (Blueprint $table) {
            $table->id();
            $table->foreignId('acopiador_id')->constrained('acopiadores')->cascadeOnDelete();
            $table->foreignId('turno_id')->constrained('turnos')->cascadeOnDelete();
            $table->date('fecha');
            $table->decimal('litros_total', 10, 2);
            $table->decimal('total_pagado', 10, 2)->default(0);
            $table->decimal('total_deuda', 10, 2)->default(0);
            $table->unsignedInteger('proveedores_no_recogidos')->default(0);
            $table->string('guia');
            $table->timestamps();
        });
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::dropIfExists('entregas_planta');
    }
};

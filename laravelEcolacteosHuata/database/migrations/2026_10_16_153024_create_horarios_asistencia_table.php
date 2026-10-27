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
        Schema::create('horarios_asistencia', function (Blueprint $table) {
            $table->id();
            $table->string('nombre');
            $table->time('hora_inicio');
            $table->time('hora_limite_presente');
            $table->time('hora_limite_tardanza');
            $table->boolean('activo')->default(true);
            $table->timestamps();
        });
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::dropIfExists('horarios_asistencia');
    }
};

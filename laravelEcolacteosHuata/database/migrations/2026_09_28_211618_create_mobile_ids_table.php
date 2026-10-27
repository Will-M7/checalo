<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Equivalencia entre el id que un registro recibió en la app móvil (UUID) y el que le tocó
     * en MySQL al subirse. Permite resolver las referencias entre registros creados sin señal
     * y que reenviar un cambio no lo duplique.
     */
    public function up(): void
    {
        Schema::create('mobile_ids', function (Blueprint $table) {
            $table->id();
            $table->string('tabla', 40);
            $table->string('uuid', 64);
            $table->unsignedBigInteger('registro_id');
            $table->timestamps();

            $table->unique(['tabla', 'uuid']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('mobile_ids');
    }
};

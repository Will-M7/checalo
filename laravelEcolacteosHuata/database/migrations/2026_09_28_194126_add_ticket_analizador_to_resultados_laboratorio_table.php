<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Datos que imprime el ticket del analizador SmartScan AI: número de serie (SN), modo
     * (Mode) y la hora y fecha en que se hizo el análisis.
     */
    public function up(): void
    {
        Schema::table('resultados_laboratorio', function (Blueprint $table) {
            $table->string('sn', 30)->nullable()->after('litros');
            $table->string('modo', 20)->nullable()->after('sn');
            $table->time('hora_analisis')->nullable()->after('ph');
            $table->date('fecha_analisis')->nullable()->after('hora_analisis');
        });
    }

    public function down(): void
    {
        Schema::table('resultados_laboratorio', function (Blueprint $table) {
            $table->dropColumn(['sn', 'modo', 'hora_analisis', 'fecha_analisis']);
        });
    }
};

<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Los acopiadores marcan asistencia por horario (mañana, tarde...), así que pueden tener
     * varios registros el mismo día: uno por horario.
     */
    public function up(): void
    {
        Schema::table('asistencias', function (Blueprint $table) {
            $table->foreignId('horario_asistencia_id')->nullable()->after('fecha')->constrained('horarios_asistencia')->nullOnDelete();
            $table->time('hora_marcada')->nullable()->after('estado');

            // Primero el índice nuevo: el FK de user_id necesita un índice que empiece por user_id.
            $table->unique(['user_id', 'fecha', 'horario_asistencia_id']);
        });

        Schema::table('asistencias', function (Blueprint $table) {
            $table->dropUnique(['user_id', 'fecha']);
        });
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::table('asistencias', function (Blueprint $table) {
            $table->unique(['user_id', 'fecha']);
        });

        Schema::table('asistencias', function (Blueprint $table) {
            $table->dropUnique(['user_id', 'fecha', 'horario_asistencia_id']);
            $table->dropConstrainedForeignId('horario_asistencia_id');
            $table->dropColumn('hora_marcada');
        });
    }
};

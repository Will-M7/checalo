<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Los turnos de ruta dejan de pertenecer a un acopiador específico: pasan a ser una lista
     * compartida que cualquier acopiador puede usar para sus recojos y entregas, y que se puede
     * asignar a cualquier ruta sin importar el acopiador dueño de esa ruta. El campo booleano
     * reemplaza la antigua distinción "acopiador_id nulo = turno de producción".
     */
    public function up(): void
    {
        Schema::table('turnos', function (Blueprint $table) {
            $table->boolean('es_produccion')->default(false)->after('acopiador_id');
        });

        DB::table('turnos')->whereNull('acopiador_id')->update(['es_produccion' => true]);

        Schema::table('turnos', function (Blueprint $table) {
            $table->dropForeign(['acopiador_id']);
            $table->dropColumn('acopiador_id');
        });
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::table('turnos', function (Blueprint $table) {
            $table->foreignId('acopiador_id')->nullable()->after('id')->constrained('acopiadores')->cascadeOnDelete();
        });

        DB::table('turnos')->where('es_produccion', true)->update(['acopiador_id' => null]);

        Schema::table('turnos', function (Blueprint $table) {
            $table->dropColumn('es_produccion');
        });
    }
};

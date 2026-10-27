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
        Schema::table('resultados_laboratorio', function (Blueprint $table) {
            $table->decimal('litros', 10, 2)->nullable()->after('proveedor_id');
            $table->decimal('grasa', 5, 2)->nullable()->after('temperatura');
            $table->decimal('sng', 5, 2)->nullable()->after('grasa');
            $table->decimal('proteina', 5, 2)->nullable()->after('sng');
            $table->decimal('lactosa', 5, 2)->nullable()->after('proteina');
            $table->decimal('sales', 5, 2)->nullable()->after('lactosa');
            $table->decimal('total_solidos', 5, 2)->nullable()->after('sales');
            $table->decimal('agua_anadida', 5, 2)->nullable()->after('total_solidos');
            $table->decimal('punto_congelacion', 6, 3)->nullable()->after('agua_anadida');
            $table->decimal('ph', 4, 2)->nullable()->after('punto_congelacion');
        });
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::table('resultados_laboratorio', function (Blueprint $table) {
            $table->dropColumn([
                'litros', 'grasa', 'sng', 'proteina', 'lactosa',
                'sales', 'total_solidos', 'agua_anadida', 'punto_congelacion', 'ph',
            ]);
        });
    }
};

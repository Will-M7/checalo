<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Run the migrations.
     */
    public function up(): void
    {
        Schema::table('recojos', function (Blueprint $table) {
            $table->decimal('litros_entregados', 8, 2)->nullable()->after('litros');
        });

        // El litraje que ya tenían los recojos existentes es lo que el acopiador registró al
        // recoger; a partir de ahora "litros" pasa a representar el litraje confirmado a pagar.
        DB::table('recojos')->update(['litros_entregados' => DB::raw('litros')]);
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::table('recojos', function (Blueprint $table) {
            $table->dropColumn('litros_entregados');
        });
    }
};

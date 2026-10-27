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
        Schema::table('rutas', function (Blueprint $table) {
            $table->json('trazo')->nullable()->after('activa');
        });

        Schema::table('ruta_proveedor', function (Blueprint $table) {
            $table->unsignedSmallInteger('orden')->default(0)->after('proveedor_id');
        });
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::table('ruta_proveedor', function (Blueprint $table) {
            $table->dropColumn('orden');
        });

        Schema::table('rutas', function (Blueprint $table) {
            $table->dropColumn('trazo');
        });
    }
};

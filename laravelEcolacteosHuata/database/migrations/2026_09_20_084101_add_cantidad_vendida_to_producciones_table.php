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
        Schema::table('producciones', function (Blueprint $table) {
            $table->decimal('cantidad_vendida', 10, 3)->default(0)->after('cantidad_producida');
        });
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::table('producciones', function (Blueprint $table) {
            $table->dropColumn('cantidad_vendida');
        });
    }
};

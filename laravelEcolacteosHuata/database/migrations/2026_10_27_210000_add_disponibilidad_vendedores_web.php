<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration {
    public function up(): void
    {
        Schema::table('users', function (Blueprint $table) {
            $table->boolean('pedidos_web_autorizado')->default(true);
            $table->boolean('pedidos_web_pausado')->default(false);
        });
    }

    public function down(): void
    {
        // Se conserva la política administrativa y las pausas en un rollback.
    }
};

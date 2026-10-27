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
        Schema::table('users', function (Blueprint $table) {
            $table->string('nombres')->after('name')->default('');
            $table->string('apellidos')->after('nombres')->default('');
            $table->string('documento', 15)->nullable()->unique()->after('apellidos');
            $table->string('celular', 20)->nullable()->after('documento');
            $table->string('rol')->nullable()->after('celular');
            $table->string('usuario')->nullable()->unique()->after('rol');
            $table->boolean('activo')->default(true)->after('usuario');

            $table->string('email')->nullable()->change();
        });
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::table('users', function (Blueprint $table) {
            $table->dropColumn(['nombres', 'apellidos', 'documento', 'celular', 'rol', 'usuario', 'activo']);
        });
    }
};

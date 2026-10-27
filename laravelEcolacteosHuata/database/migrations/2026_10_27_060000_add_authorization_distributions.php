<?php

use App\Authorization\Distribuciones;
use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('authorization_distributions', function (Blueprint $t) {
            $t->id();
            $t->string('codigo')->unique();
            $t->string('nombre', 160);
            $t->string('tipo', 20);
            $t->unsignedInteger('version')->default(1);
            $t->boolean('predeterminada')->default(false);
            $t->json('asignaciones');
            $t->json('origen')->nullable();
            $t->timestamps();
        });
        Schema::create('authorization_distribution_runs', function (Blueprint $t) {
            $t->uuid('id')->primary();
            $t->unsignedBigInteger('distribution_id');
            $t->string('nombre', 160);
            $t->unsignedInteger('version');
            $t->string('tipo', 20);
            $t->unsignedBigInteger('actor_id');
            $t->json('antes');
            $t->json('despues');
            $t->string('huella_posterior', 64);
            $t->uuid('recuperacion_de')->nullable()->unique();
            $t->timestamp('created_at');
        });
        Distribuciones::registrarBases();
    }

    public function down(): void
    {
        throw new RuntimeException('Las distribuciones y sus recuperaciones requieren reversión controlada, no borrado de historial.');
    }
};

<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;
use Illuminate\Support\Str;

return new class extends Migration
{
    /**
     * Un pedido web guarda cómo piensa pagar el cliente, el número de operación de su Yape o Plin
     * y un token secreto con el que el cliente (sin iniciar sesión) abre su comprobante o boleta.
     */
    public function up(): void
    {
        Schema::table('pedidos_web', function (Blueprint $table) {
            $table->string('metodo_pago', 20)->default('efectivo')->after('celular');
            $table->string('codigo_operacion', 30)->nullable()->after('metodo_pago');
            $table->string('token', 40)->nullable()->unique()->after('codigo_operacion');
        });

        DB::table('pedidos_web')->whereNull('token')->orderBy('id')->each(function (object $pedido): void {
            DB::table('pedidos_web')->where('id', $pedido->id)->update(['token' => Str::random(40)]);
        });
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::table('pedidos_web', function (Blueprint $table) {
            $table->dropUnique(['token']);
            $table->dropColumn(['metodo_pago', 'codigo_operacion', 'token']);
        });
    }
};

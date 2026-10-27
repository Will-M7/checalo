<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('clientes_web', function (Blueprint $t) {
            $t->id();
            $t->string('nombres', 80);
            $t->string('apellidos', 80);
            $t->string('celular', 12)->unique();
            $t->string('email')->unique();
            $t->string('password');
            $t->rememberToken();
            $t->unsignedBigInteger('wallet_version')->default(0);
            $t->timestamps();
        });
        Schema::create('clientes_web_password_reset_tokens', function (Blueprint $t) {
            $t->string('email')->primary();
            $t->string('token');
            $t->timestamp('created_at')->nullable();
        });
        Schema::create('receptores_pago', function (Blueprint $t) {
            $t->id();
            $t->foreignId('user_id')->unique()->constrained('users')->restrictOnDelete();
            $t->string('nombre_receptor', 100);
            $t->string('celular', 12);
            $t->string('whatsapp', 12);
            $t->string('referencia_externa', 100)->nullable()->unique();
            $t->string('qr_path')->nullable();
            $t->string('qr_sha256', 64)->nullable();
            $t->unsignedInteger('version')->default(1);
            $t->boolean('enabled')->default(false);
            $t->timestamp('verified_at')->nullable();
            $t->foreignId('verified_by')->nullable()->constrained('users')->restrictOnDelete();
            $t->timestamps();
        });
        Schema::create('integracion_secuencias', function (Blueprint $t) {
            $t->string('clave')->primary();
            $t->unsignedBigInteger('valor')->default(0);
        });
        $max = DB::table('ventas')->pluck('numero_boleta')->map(fn ($n) => preg_match('/^B001-(\d+)$/', $n, $m) ? (int) $m[1] : 0)->max() ?? 0;
        DB::table('integracion_secuencias')->insert([['clave' => 'boleta', 'valor' => $max], ['clave' => 'reparto', 'valor' => 0]]);
        Schema::table('ventas', function (Blueprint $t) {
            $t->uuid('referencia_canal')->nullable()->unique();
            $t->string('request_hash', 64)->nullable();
        });
        Schema::table('pedidos_web', function (Blueprint $t) {
            $t->foreignId('cliente_web_id')->nullable()->constrained('clientes_web')->restrictOnDelete();
            $t->foreignId('vendedor_asignado_id')->nullable()->constrained('users')->restrictOnDelete();
            $t->foreignId('receptor_pago_id')->nullable()->constrained('receptores_pago')->restrictOnDelete();
            $t->json('receptor_snapshot')->nullable();
            $t->json('detalle_cobro')->nullable();
            $t->string('canal', 20)->default('legacy');
            $t->string('pago_estado', 30)->default('pendiente');
            $t->string('idempotency_key', 64)->nullable()->unique();
            $t->timestamp('expires_at')->nullable();
        });
        Schema::create('intentos_pago', function (Blueprint $t) {
            $t->id();
            $t->uuid('referencia')->unique();
            $t->foreignId('cliente_web_id')->nullable()->constrained('clientes_web')->restrictOnDelete();
            $t->foreignId('user_id')->constrained('users')->restrictOnDelete();
            $t->foreignId('receptor_pago_id')->constrained('receptores_pago')->restrictOnDelete();
            $t->foreignId('pedido_web_id')->nullable()->unique()->constrained('pedidos_web')->restrictOnDelete();
            $t->string('proposito', 20);
            $t->unsignedBigInteger('amount_minor');
            $t->string('currency', 3)->default('604');
            $t->json('receptor_snapshot');
            $t->string('external_id', 100)->nullable()->unique();
            $t->string('estado', 20)->default('PENDING');
            $t->string('ultimo_resultado', 40)->nullable();
            $t->string('evidence_id', 128)->nullable()->unique();
            $t->timestamp('expires_at')->nullable();
            $t->timestamp('applied_at')->nullable();
            $t->foreignId('venta_id')->nullable()->constrained('ventas')->restrictOnDelete();
            $t->timestamps();
            $t->index(['estado', 'applied_at']);
        });
        Schema::create('movimientos_saldo', function (Blueprint $t) {
            $t->id();
            $t->foreignId('cliente_web_id')->constrained('clientes_web')->restrictOnDelete();
            $t->string('tipo', 30);
            $t->bigInteger('amount_minor');
            $t->string('currency', 3)->default('604');
            $t->string('idempotencia', 150)->unique();
            $t->string('origen', 150);
            $t->string('estado', 20)->default('aplicado');
            $t->foreignId('intento_pago_id')->nullable()->constrained('intentos_pago')->restrictOnDelete();
            $t->foreignId('pedido_web_id')->nullable()->constrained('pedidos_web')->restrictOnDelete();
            $t->foreignId('autorizado_por')->nullable()->constrained('users')->restrictOnDelete();
            $t->foreignId('reverso_de_id')->nullable()->unique()->constrained('movimientos_saldo')->restrictOnDelete();
            $t->timestamps();
            $t->index(['cliente_web_id', 'estado']);
        });
    }

    public function down(): void
    {
        // Never destroy financial history on rollback; recover through a forward migration.
        if (DB::table('intentos_pago')->exists() || DB::table('movimientos_saldo')->exists()) {
            throw new RuntimeException('La migración contiene historial financiero; requiere una migración de corrección, no borrado.');
        }
        Schema::dropIfExists('movimientos_saldo');
        Schema::dropIfExists('intentos_pago');
        Schema::table('ventas', function (Blueprint $t) {
            $t->dropUnique(['referencia_canal']);
            $t->dropColumn(['referencia_canal', 'request_hash']);
        });
        Schema::table('pedidos_web', function (Blueprint $t) {
            foreach (['cliente_web_id', 'vendedor_asignado_id', 'receptor_pago_id'] as $column) {
                $t->dropConstrainedForeignId($column);
            }
            $t->dropUnique(['idempotency_key']);
            $t->dropColumn(['receptor_snapshot', 'detalle_cobro', 'canal', 'pago_estado', 'idempotency_key', 'expires_at']);
        });
        Schema::dropIfExists('integracion_secuencias');
        Schema::dropIfExists('receptores_pago');
        Schema::dropIfExists('clientes_web_password_reset_tokens');
        Schema::dropIfExists('clientes_web');
    }
};

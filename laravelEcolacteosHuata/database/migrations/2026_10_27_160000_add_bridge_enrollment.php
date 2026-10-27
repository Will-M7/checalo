<?php
use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;
return new class extends Migration {
    public function up(): void {
        Schema::create('payment_bridge_devices', function (Blueprint $t) {
            $t->string('id', 100)->primary();
            $t->foreignId('receiver_id')->constrained('receptores_pago')->restrictOnDelete();
            $t->foreignId('owner_id')->constrained('users')->restrictOnDelete();
            $t->string('mode', 16);
            $t->string('status', 16)->default('pending');
            $t->string('secret_hash', 64)->nullable()->unique();
            $t->text('secret_encrypted')->nullable();
            $t->string('installation_hash', 64)->nullable();
            $t->timestamps();
        });
        Schema::create('payment_bridge_tickets', function (Blueprint $t) {
            $t->string('token_hash', 64)->primary();
            $t->string('device_id', 100); $t->foreign('device_id')->references('id')->on('payment_bridge_devices')->restrictOnDelete();
            $t->string('claim_hash', 64)->nullable();
            $t->timestamp('expires_at'); $t->timestamp('used_at')->nullable();
            $t->foreignId('created_by')->constrained('users')->restrictOnDelete();
        });
        Schema::create('payment_bridge_assertions', function (Blueprint $t) {
            $t->string('token_hash', 64)->primary();
            $t->string('device_id', 100); $t->foreign('device_id')->references('id')->on('payment_bridge_devices')->restrictOnDelete();
            $t->foreignId('user_id')->constrained('users')->restrictOnDelete();
            $t->string('nonce_hash', 64); $t->string('event_id', 128);
            $t->timestamp('expires_at'); $t->timestamp('used_at')->nullable();
        });
    }
    public function down(): void {
        if (\Illuminate\Support\Facades\DB::table('payment_bridge_devices')->exists()) throw new RuntimeException('No borrar dispositivos ni inscripciones existentes. Usar migración correctiva.');
        Schema::dropIfExists('payment_bridge_assertions'); Schema::dropIfExists('payment_bridge_tickets'); Schema::dropIfExists('payment_bridge_devices');
    }
};

<?php
use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;
return new class extends Migration {
    public function up(): void {
        Schema::create('huata_yape_installations', function (Blueprint $t) {
            $t->uuid('id')->primary();
            $t->string('installation_hash', 64)->unique();
            $t->foreignId('user_id')->constrained('users')->restrictOnDelete();
            $t->foreignId('session_user_id')->nullable()->constrained('users')->restrictOnDelete();
            $t->foreignId('receiver_id')->nullable()->constrained('receptores_pago')->restrictOnDelete();
            $t->foreignId('approved_by')->nullable()->constrained('users')->restrictOnDelete();
            $t->string('bridge_device_id', 100)->nullable();
            $t->foreign('bridge_device_id')->references('id')->on('payment_bridge_devices')->restrictOnDelete();
            $t->string('label', 100);
            $t->string('mode', 16)->default('personal');
            $t->string('status', 16)->default('pending');
            $t->boolean('consent')->default(false);
            $t->boolean('listener_ready')->default(false);
            $t->boolean('replacement_confirmed')->default(false);
            $t->string('session_token_hash', 64)->nullable();
            $t->text('ticket_encrypted')->nullable();
            $t->timestamp('last_seen_at')->nullable();
            $t->timestamp('last_communication_at')->nullable();
            $t->timestamps();
        });
    }
    public function down(): void { throw new RuntimeException('Se conservan las autorizaciones e historial de dispositivos Huata.'); }
};

<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('payment_receiver_policy', function (Blueprint $t) {
            $t->id();
            $t->foreignId('central_receiver_id')->nullable()->constrained('receptores_pago')->restrictOnDelete();
            $t->foreignId('updated_by')->nullable()->constrained('users')->restrictOnDelete();
            $t->timestamps();
        });
        Schema::create('payment_seller_assignments', function (Blueprint $t) {
            $t->foreignId('user_id')->primary()->constrained('users')->restrictOnDelete();
            $t->string('mode', 16);
            $t->foreignId('receiver_id')->nullable()->constrained('receptores_pago')->restrictOnDelete();
            $t->boolean('personal_allowed')->default(false);
            $t->foreignId('assigned_by')->constrained('users')->restrictOnDelete();
            $t->timestamp('owner_authorized_at')->nullable();
            $t->unsignedInteger('version')->default(1);
            $t->timestamps();
        });
        Schema::create('payment_authorization_events', function (Blueprint $t) {
            $t->uuid('id')->primary();
            $t->foreignId('actor_id')->constrained('users')->restrictOnDelete();
            $t->foreignId('subject_id')->nullable()->constrained('users')->restrictOnDelete();
            $t->foreignId('receiver_id')->nullable()->constrained('receptores_pago')->restrictOnDelete();
            $t->string('action', 64);
            $t->text('before_state')->nullable();
            $t->text('after_state');
            $t->timestamp('created_at');
        });
    }

    public function down(): void
    {
        if (\Illuminate\Support\Facades\DB::table('payment_authorization_events')->exists() || \Illuminate\Support\Facades\DB::table('payment_seller_assignments')->exists()) {
            throw new RuntimeException('Conservar historial y asignaciones de pagos: recuperación mediante migración hacia adelante.');
        }
        Schema::dropIfExists('payment_authorization_events');
        Schema::dropIfExists('payment_seller_assignments');
        Schema::dropIfExists('payment_receiver_policy');
    }
};

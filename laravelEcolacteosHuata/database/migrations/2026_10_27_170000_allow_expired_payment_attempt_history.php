<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration {
    public function up(): void {
        if (! in_array(DB::getDriverName(), ['sqlite', 'pgsql'], true)) throw new RuntimeException('Esta migración requiere índice parcial SQLite/PostgreSQL; no modificar índices de otro motor sin adaptación probada.');
        // Preserve every historical attempt; only one PENDING/PAID is allowed per order.
        Schema::table('intentos_pago', fn (Blueprint $t) => $t->dropUnique('intentos_pago_pedido_web_id_unique'));
        DB::statement("CREATE UNIQUE INDEX intentos_pago_order_active_unique ON intentos_pago(pedido_web_id) WHERE estado IN ('PENDING','PAID')");
    }
    public function down(): void {
        if (DB::table('intentos_pago')->whereNotNull('pedido_web_id')->select('pedido_web_id')->groupBy('pedido_web_id')->havingRaw('COUNT(*) > 1')->exists()) {
            throw new RuntimeException('No eliminar historial de intentos para revertir. Usar migración correctiva.');
        }
        DB::statement('DROP INDEX intentos_pago_order_active_unique');
        Schema::table('intentos_pago', fn (Blueprint $t) => $t->unique('pedido_web_id'));
    }
};

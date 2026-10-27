<?php

use App\Authorization\Autorizacion;
use App\Models\RolDinamico;
use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('movimientos_financieros', function (Blueprint $t) {
            $t->id();
            $t->string('clave', 180)->unique();
            $t->string('tipo', 40)->index();
            $t->unsignedBigInteger('amount_minor');
            $t->string('currency', 3)->default('604');
            $t->string('origen', 180);
            $t->string('metodo', 30)->nullable();
            $t->string('estado', 30)->default('confirmado')->index();
            $t->foreignId('vendedor_id')->nullable()->constrained('users')->restrictOnDelete();
            $t->foreignId('custodio_id')->nullable()->constrained('users')->restrictOnDelete();
            $t->foreignId('receptor_pago_id')->nullable()->constrained('receptores_pago')->restrictOnDelete();
            $t->foreignId('cliente_web_id')->nullable()->constrained('clientes_web')->restrictOnDelete();
            $t->foreignId('intento_pago_id')->nullable()->constrained('intentos_pago')->restrictOnDelete();
            $t->foreignId('venta_id')->nullable()->constrained('ventas')->restrictOnDelete();
            $t->foreignId('pedido_web_id')->nullable()->constrained('pedidos_web')->restrictOnDelete();
            $t->foreignId('pago_proveedor_id')->nullable()->constrained('pagos_proveedor')->restrictOnDelete();
            $t->foreignId('movimiento_saldo_id')->nullable()->constrained('movimientos_saldo')->restrictOnDelete();
            $t->foreignId('actor_id')->nullable()->constrained('users')->restrictOnDelete();
            $t->foreignId('originado_por_id')->nullable()->constrained('movimientos_financieros')->restrictOnDelete();
            foreach (['recaudado', 'ventas', 'recargas', 'pasivo', 'custodia', 'empresa', 'rendido', 'egresos'] as $col) {
                $t->bigInteger($col.'_minor')->default(0);
            }
            $t->json('detalle')->nullable();
            $t->timestamp('created_at')->index();
        });
        DB::table('integracion_secuencias')->insert(['clave' => 'finanzas', 'valor' => 0]);
        // Solo el módulo nuevo: no sustituye asignaciones ni distribuciones existentes.
        DB::transaction(function () {
            $admin = RolDinamico::where('codigo', 'administrador')->first();
            if ($admin && ! in_array('admin.finanzas', $admin->pantallas, true)) {
                $antes = $admin->pantallas;
                $admin->update(['pantallas' => [...$antes, 'admin.finanzas']]);
                Autorizacion::registrar(null, 'catalogo.finanzas', 'rol:'.$admin->id, $antes, $admin->pantallas);
            }
        });
    }

    public function down(): void
    {
        // No se eliminan automáticamente registros financieros ni sus permisos.
        throw new RuntimeException('Migración financiera conservadora: requiere recuperación explícita, no elimina movimientos.');
    }
};

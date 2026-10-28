<?php

use App\Authorization\Autorizacion;
use App\Models\RolDinamico;
use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration {
    public function up(): void
    {
        Schema::create('yape_reception_requests', function (Blueprint $t) {
            $t->bigIncrements('sequence');
            $t->uuid('id')->unique();
            $t->foreignId('receiver_id')->constrained('receptores_pago')->restrictOnDelete();
            $t->foreignId('responsible_id')->constrained('users')->restrictOnDelete();
            $t->foreignId('assigned_by')->constrained('users')->restrictOnDelete();
            $t->uuid('installation_id')->nullable();
            $t->foreign('installation_id')->references('id')->on('huata_yape_installations')->restrictOnDelete();
            $t->string('identity_hash', 64);
            $t->json('receiver_snapshot');
            $t->string('status', 16)->default('pending');
            $t->timestamp('decided_at')->nullable();
            $t->timestamps();
            $t->index(['responsible_id', 'status']);
            $t->index(['receiver_id', 'created_at']);
        });
        Schema::table('huata_yape_installations', fn (Blueprint $t) => $t->boolean('notification_permission')->default(false));
        // Un responsable puede escuchar otra cuenta; user_id conserva la custodia histórica.
        Schema::table('receptores_pago', fn (Blueprint $t) => $t->dropUnique(['user_id']));
        DB::transaction(function () {
            $admin = RolDinamico::where('codigo', 'administrador')->first();
            if ($admin && ! in_array('admin.receptores-yape', $admin->pantallas, true)) {
                $antes = $admin->pantallas;
                $admin->update(['pantallas' => [...$antes, 'admin.receptores-yape']]);
                Autorizacion::registrar(null, 'catalogo.receptores_yape', 'rol:'.$admin->id, $antes, $admin->pantallas);
            }
            // Una pausa histórica queda como bloqueo administrable. No reactiva cuentas ni borra la evidencia de pausa.
            foreach (DB::table('users')->where('pedidos_web_pausado', true)->get() as $u) {
                DB::table('users')->where('id', $u->id)->update(['pedidos_web_autorizado' => false]);
                Autorizacion::registrar(null, 'recepcion_web_control_administrativo', 'usuario:'.$u->id,
                    ['autorizado' => (bool) $u->pedidos_web_autorizado, 'pausado' => true], ['autorizado' => false, 'motivo' => 'Pausa histórica; requiere autorización administrativa']);
            }
        });
    }

    public function down(): void
    {
        throw new RuntimeException('Se conservan solicitudes, decisiones y evidencias. Usar una migración correctiva.');
    }
};

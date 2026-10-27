<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Un proveedor pertenece a una sola ruta. Si ya estaba en varias, se queda en la del acopiador
     * al que pertenece (o, si ninguna es suya, en la de menor id) y sale de las demás.
     */
    public function up(): void
    {
        $duplicados = DB::table('ruta_proveedor')
            ->select('proveedor_id')
            ->groupBy('proveedor_id')
            ->havingRaw('count(*) > 1')
            ->pluck('proveedor_id');

        foreach ($duplicados as $proveedorId) {
            $acopiadorId = DB::table('proveedores')->where('id', $proveedorId)->value('acopiador_id');

            $rutaQueQueda = DB::table('ruta_proveedor')
                ->join('rutas', 'rutas.id', '=', 'ruta_proveedor.ruta_id')
                ->where('ruta_proveedor.proveedor_id', $proveedorId)
                ->orderByRaw('rutas.acopiador_id = ? desc', [$acopiadorId])
                ->orderBy('rutas.id')
                ->value('rutas.id');

            $rutasQueSalen = DB::table('ruta_proveedor')
                ->where('proveedor_id', $proveedorId)
                ->where('ruta_id', '!=', $rutaQueQueda)
                ->pluck('ruta_id');

            DB::table('ruta_proveedor')
                ->where('proveedor_id', $proveedorId)
                ->whereIn('ruta_id', $rutasQueSalen)
                ->delete();

            // Para que la sincronización incremental de la app móvil se entere del cambio.
            DB::table('rutas')->whereIn('id', $rutasQueSalen)->update(['updated_at' => now()]);
        }

        Schema::table('ruta_proveedor', function (Blueprint $table) {
            $table->unique('proveedor_id');
        });
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::table('ruta_proveedor', function (Blueprint $table) {
            $table->dropUnique(['proveedor_id']);
        });
    }
};

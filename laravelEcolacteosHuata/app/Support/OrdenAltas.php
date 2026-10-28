<?php
namespace App\Support;

use Illuminate\Support\Carbon;
use Illuminate\Support\Facades\DB;

/** Metadatos de lectura: fechas reales, solo dentro del ámbito autorizado. */
final class OrdenAltas
{
    public const TABLAS = [
        'usuario' => 'users', 'acopiador' => 'acopiadores', 'proveedor' => 'proveedores',
        'descuento' => 'descuentos', 'cliente' => 'clientes', 'unidad_medida' => 'unidades_medida',
        'insumo' => 'insumos', 'receta' => 'recetas', 'comunicado' => 'comunicados',
        'recojo' => 'recojos', 'resultado_laboratorio' => 'resultados_laboratorio',
        'entrega_planta' => 'entregas_planta', 'compra' => 'compras', 'compra_detalle' => 'compra_detalles',
        'produccion' => 'producciones', 'venta' => 'ventas',
    ];

    public static function paraSync(array $datos): array
    {
        $altas = [];
        foreach (self::TABLAS as $local => $tabla) {
            $ids = $datos['ids'][$local] ?? [];
            if ($local === 'usuario') $ids = array_intersect($ids, array_column($datos['usuarios'], 'id'));
            // También en incrementales: actualiza el orden de una APK anterior sin borrar datos.
            foreach (array_chunk($ids, 500) as $tanda) {
                foreach (DB::table($tabla)->whereIn('id', $tanda)->get(['id', 'created_at']) as $fila) {
                    $altas[] = ['tabla' => $local, 'registro_id' => (string) $fila->id,
                        'creado_en' => $fila->created_at === null ? null : Carbon::parse($fila->created_at, config('app.timezone'))->utc()->format('Y-m-d\TH:i:s.v\Z')];
                }
            }
        }
        return $altas;
    }
}

<?php

namespace App\Authorization;

use App\Models\Acopiador;
use App\Models\Asistencia;
use App\Models\Comunicado;
use App\Models\EntregaPlanta;
use App\Models\Justificacion;
use App\Models\PagoProveedor;
use App\Models\Proveedor;
use App\Models\Recojo;
use App\Models\ResultadoLaboratorio;
use App\Models\Ruta;
use App\Models\Turno;
use App\Models\User;

final class DatosMoviles
{
    /** Dependencias de consulta; no otorgan permisos de mutación sobre esas tablas. */
    public static function filtrar(array $datos, User $user): array
    {
        $dependencias = [
            'admin.panel' => ['empresa', 'usuarios', 'acopiadores', 'proveedores', 'recojos', 'producciones', 'ventas'],
            'admin.usuarios' => ['usuarios', 'acopiadores', 'proveedores', 'turnos', 'rutas'],
            'admin.asignar-roles' => ['usuarios', 'acopiadores', 'proveedores'],
            'admin.descuentos' => ['descuentos'], 'admin.precios' => ['precio_leche'],
            'admin.turnos' => ['turnos', 'turnos_produccion', 'acopiadores', 'usuarios', 'rutas'], 'admin.empresa' => ['empresa'],
            'vendedor.panel' => ['producciones', 'ventas', 'clientes', 'descuentos'],
            'vendedor.ventas' => ['producciones', 'recetas', 'clientes', 'descuentos', 'ventas', 'unidades_medida'],
            'vendedor.produccion' => ['producciones', 'recetas'], 'vendedor.inventario' => ['producciones', 'recetas'],
            'vendedor.historial' => ['ventas', 'clientes', 'producciones'], 'vendedor.clientes' => ['clientes'],
            'produccion.panel' => ['producciones', 'insumos', 'recetas', 'compras', 'unidades_medida'],
            'produccion.registros' => ['producciones', 'insumos', 'recetas', 'receta_detalles', 'turnos_produccion', 'unidades_medida', 'usuarios'],
            'produccion.recetas' => ['recetas', 'receta_detalles', 'insumos', 'unidades_medida'],
            'produccion.insumos' => ['insumos', 'unidades_medida'], 'produccion.unidades' => ['unidades_medida'],
            'produccion.productos' => ['producciones', 'recetas'], 'produccion.compras' => ['compras', 'compra_detalles', 'insumos', 'unidades_medida'],
            'produccion.inventario' => ['insumos', 'unidades_medida', 'producciones', 'recetas'],
            'planta.panel' => ['recojos', 'entregas_planta', 'resultados_laboratorio', 'proveedores', 'acopiadores', 'usuarios'],
            'planta.entregas' => ['entregas_planta', 'recojos', 'acopiadores', 'usuarios', 'turnos'],
            'planta.laboratorio' => ['resultados_laboratorio', 'proveedores', 'usuarios', 'turnos', 'acopiadores', 'insumos', 'unidades_medida'],
            'planta.pagos' => ['pagos_proveedor', 'recojos', 'proveedores', 'usuarios', 'acopiadores'],
            'planta.rutas' => ['rutas', 'proveedores', 'acopiadores', 'usuarios', 'turnos', 'recojos'], 'planta.comunicados' => ['comunicados', 'usuarios'],
            'acopiador.panel' => ['acopiadores', 'proveedores', 'rutas', 'recojos', 'entregas_planta', 'turnos', 'precio_leche', 'usuarios'],
            'acopiador.recojo' => ['acopiadores', 'proveedores', 'rutas', 'recojos', 'turnos', 'precio_leche', 'usuarios', 'pagos_proveedor'],
            'acopiador.mapa-recojo' => ['acopiadores', 'proveedores', 'rutas', 'recojos', 'turnos', 'usuarios'],
            'acopiador.entrega' => ['acopiadores', 'entregas_planta', 'recojos', 'turnos', 'usuarios'],
            'acopiador.inventario' => ['acopiadores', 'recojos', 'proveedores', 'turnos', 'usuarios'],
            'proveedor.panel' => ['proveedores', 'acopiadores', 'usuarios', 'recojos', 'pagos_proveedor'],
            'proveedor.entregas' => ['proveedores', 'recojos', 'pagos_proveedor', 'turnos', 'usuarios'],
            'proveedor.laboratorio' => ['proveedores', 'resultados_laboratorio', 'turnos'], 'proveedor.comunicados' => ['comunicados'],
            'asistencia.tomar' => ['usuarios', 'acopiadores', 'horarios_asistencia', 'dias_asistencia_acopiador', 'asistencias', 'justificaciones'],
            'asistencia.mis' => ['horarios_asistencia', 'dias_asistencia_acopiador', 'asistencias', 'justificaciones'],
        ];
        $habilitadas = Autorizacion::pantallas($user);
        $permitidas = collect($habilitadas)->flatMap(fn ($p) => $dependencias[$p] ?? [])->unique()->all();
        $globales = collect($habilitadas)->reject(fn ($p) => str_starts_with($p, 'acopiador.') || str_starts_with($p, 'proveedor.') || $p === 'asistencia.mis')
            ->flatMap(fn ($p) => $dependencias[$p] ?? [])->unique()->all();
        $nombres = ['descuento' => 'descuentos', 'usuario' => 'usuarios', 'acopiador' => 'acopiadores', 'proveedor' => 'proveedores', 'turno' => 'turnos', 'turno_produccion' => 'turnos_produccion', 'ruta' => 'rutas', 'cliente' => 'clientes', 'unidad_medida' => 'unidades_medida', 'insumo' => 'insumos', 'receta' => 'recetas', 'receta_detalle' => 'receta_detalles', 'comunicado' => 'comunicados', 'recojo' => 'recojos', 'resultado_laboratorio' => 'resultados_laboratorio', 'entrega_planta' => 'entregas_planta', 'pago_proveedor' => 'pagos_proveedor', 'compra' => 'compras', 'compra_detalle' => 'compra_detalles', 'produccion' => 'producciones', 'venta' => 'ventas', 'horario_asistencia' => 'horarios_asistencia', 'dia_asistencia_acopiador' => 'dias_asistencia_acopiador', 'asistencia' => 'asistencias', 'justificacion' => 'justificaciones'];
        $propios = self::ambitosPersonales($user);
        foreach ($datos as $clave => $filas) {
            if (in_array($clave, ['ids', 'autorizacion', 'servidor_hora', 'notificaciones'], true)) {
                continue;
            }
            if (! in_array($clave, $permitidas, true)) {
                $datos[$clave] = [];

                continue;
            }
            $filas = collect($filas);
            if (isset($propios[$clave]) && ! in_array($clave, $globales, true)) {
                $filas = $filas->filter(fn ($r) => in_array($r['id'], $propios[$clave], true));
            }
            $datos[$clave] = $filas->values()->all();
        }
        if (! collect($datos['usuarios'])->contains(fn ($r) => $r['id'] === (string) $user->id)) {
            $datos['usuarios'][] = self::usuarioPropio($user);
        }
        // Los IDs se filtran contra consultas completas, nunca contra filas incrementales de since.
        foreach ($datos['ids'] as $tabla => $ids) {
            $clave = $nombres[$tabla] ?? $tabla;
            if (! in_array($clave, $permitidas, true) && $tabla !== 'usuario') {
                $ids = [];
            }
            if (isset($propios[$clave]) && ! in_array($clave, $globales, true)) {
                $ids = array_values(array_intersect($ids, $propios[$clave]));
            }
            $datos['ids'][$tabla] = $ids;
        }

        return $datos;
    }

    private static function ambitosPersonales(User $user): array
    {
        $ids = fn ($query) => $query->pluck('id')->map(fn ($id) => (string) $id)->all();
        $aco = $user->acopiador?->id;
        $proveedores = Proveedor::query()->where(function ($q) use ($user, $aco) {
            $q->where('user_id', $user->id);
            if ($aco) {
                $q->orWhere('acopiador_id', $aco);
            }
        });
        $proveedorIds = $ids(clone $proveedores);
        $usuarios = (clone $proveedores)->pluck('user_id')->map(fn ($id) => (string) $id)->all();
        $acoIds = array_values(array_filter([(string) $aco, (string) $user->proveedor?->acopiador_id]));
        $usuarios = [...$usuarios, ...Acopiador::whereIn('id', $acoIds)->pluck('user_id')->map(fn ($id) => (string) $id)->all(), (string) $user->id];
        $turnos = Turno::where(function ($q) use ($aco, $proveedorIds) {
            $q->whereHas('recojos', fn ($r) => $r->whereIn('proveedor_id', $proveedorIds));
            if ($aco) {
                $q->orWhereHas('rutas', fn ($r) => $r->where('acopiador_id', $aco));
            }
        });
        $asistencias = Asistencia::where('user_id', $user->id)->pluck('id');
        $audiencias = [...$user->rolesAsignados()->map(fn ($r) => $r->value)->all(), ...Autorizacion::roles($user)->pluck('codigo')->all()];
        $comunicados = Comunicado::where(function ($q) use ($audiencias) {
            $q->whereNull('roles');
            foreach ($audiencias as $rol) {
                $q->orWhereJsonContains('roles', $rol);
            }
        });

        return [
            'usuarios' => array_values(array_unique($usuarios)), 'proveedores' => $proveedorIds, 'acopiadores' => $acoIds,
            'turnos' => $ids($turnos),
            'rutas' => $aco ? $ids(Ruta::where('acopiador_id', $aco)) : [],
            'entregas_planta' => $aco ? $ids(EntregaPlanta::where('acopiador_id', $aco)) : [],
            'recojos' => $ids(Recojo::whereIn('proveedor_id', $proveedorIds)),
            'resultados_laboratorio' => $ids(ResultadoLaboratorio::whereIn('proveedor_id', $proveedorIds)),
            'pagos_proveedor' => $ids(PagoProveedor::whereIn('proveedor_id', $proveedorIds)),
            'asistencias' => $asistencias->map(fn ($id) => (string) $id)->all(),
            'justificaciones' => $ids(Justificacion::whereIn('asistencia_id', $asistencias)),
            'comunicados' => $ids($comunicados),
        ];
    }

    private static function usuarioPropio(User $u): array
    {
        return ['id' => (string) $u->id, 'nombres' => $u->nombres, 'apellidos' => $u->apellidos, 'documento' => $u->documento ?? '', 'celular' => $u->celular ?? '', 'rol' => $u->rol?->value, 'usuario' => $u->usuario, 'activo' => (bool) $u->activo, 'roles' => $u->rolesAsignados()->map(fn ($r) => $r->value)->all(), 'toma_asistencia' => (bool) $u->toma_asistencia, 'password_hash' => $u->password];
    }
}

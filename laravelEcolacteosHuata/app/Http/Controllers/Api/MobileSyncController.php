<?php

namespace App\Http\Controllers\Api;

use App\Authorization\Autorizacion;
use App\Authorization\DatosMoviles;
use App\Enums\Rol;
use App\Http\Controllers\Controller;
use App\Models\Acopiador;
use App\Models\Asistencia;
use App\Models\Cliente;
use App\Models\Compra;
use App\Models\CompraDetalle;
use App\Models\Comunicado;
use App\Models\Descuento;
use App\Models\DiaAsistenciaAcopiador;
use App\Models\Empresa;
use App\Models\EntregaPlanta;
use App\Models\HorarioAsistencia;
use App\Models\Insumo;
use App\Models\Justificacion;
use App\Models\PagoProveedor;
use App\Models\PrecioLeche;
use App\Models\Produccion;
use App\Models\Proveedor;
use App\Models\Receta;
use App\Models\RecetaDetalle;
use App\Models\Recojo;
use App\Models\ResultadoLaboratorio;
use App\Models\Ruta;
use App\Models\Turno;
use App\Models\UnidadMedida;
use App\Models\User;
use App\Models\Venta;
use App\Models\VentaItem;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Carbon;

class MobileSyncController extends Controller
{
    /**
     * Devuelve, en una sola respuesta, todas las tablas que la app móvil (Kotlin
     * Multiplatform / SQLite) necesita reflejar localmente. Sin `since` manda un volcado
     * completo (primera instalación); con `since` filtra cada tabla por `updated_at` para
     * mandar solo lo que cambió, más la lista de ids vigentes para borrar lo eliminado.
     * Solo responde a teléfonos con token (ver AuthenticateMobileToken).
     *
     * Las producciones son lo que se vende (no hay catálogo de productos): cada venta baja con
     * sus ítems y la producción de la que salió cada uno.
     */
    public function sync(Request $request): JsonResponse
    {
        // Igual que al abrir "Tomar asistencia" en la web: los acopiadores que no marcaron en un
        // horario que ya cerró quedan con falta, así el teléfono también las ve.
        HorarioAsistencia::registrarFaltasVencidas();

        // Se toma antes de consultar: lo que cambie mientras se arma la respuesta vuelve a
        // bajar en la siguiente sincronización en vez de perderse.
        $servidorHora = now();

        $since = $request->query('since');
        // Un "+" de la zona horaria (ej. "+00:00") puede llegar decodificado como espacio si el
        // cliente no lo manda percent-encoded en la query string; nunca es un espacio válido acá.
        $desde = is_string($since) && $since !== ''
            ? Carbon::parse(str_replace(' ', '+', $since))->setTimezone(config('app.timezone'))
            : null;

        // `>=`: `updated_at` tiene precisión de segundos, así que una fila guardada en el mismo
        // segundo que la última sincronización también debe volver a bajar (el upsert es idempotente).
        $filtrar = fn (Builder $query) => $desde ? $query->where('updated_at', '>=', $desde) : $query;

        // Turnos y rutas se mandan siempre completos: son pocos y sus cambios de proveedores /
        // turnos asignados (tablas pivote) no tocan su `updated_at`.
        $turnos = Turno::with('rutas')->get();

        $datos = DatosMoviles::filtrar([
            'servidor_hora' => $servidorHora->toIso8601String(),
            'autorizacion' => Autorizacion::snapshot($request->user()),
            'empresa' => Empresa::query()->tap($filtrar)->get()->map($this->mapEmpresa(...)),
            'precio_leche' => PrecioLeche::query()->tap($filtrar)->get()->map($this->mapPrecioLeche(...)),
            'descuentos' => Descuento::query()->tap($filtrar)->get()->map(fn (Descuento $d) => [
                'id' => (string) $d->id,
                'nombre' => (string) $d->nombre,
                'porcentaje' => (float) $d->porcentaje,
                'activo' => (bool) $d->activo,
            ]),
            'usuarios' => User::query()->tap($filtrar)->get()->map($this->mapUsuario(...)),
            'acopiadores' => Acopiador::query()->tap($filtrar)->get()->map($this->mapAcopiador(...)),
            'proveedores' => Proveedor::query()->tap($filtrar)->get()->map($this->mapProveedor(...)),
            'turnos' => $turnos->where('es_produccion', false)->map($this->mapTurno(...))->values(),
            'turnos_produccion' => $turnos->where('es_produccion', true)->map($this->mapTurnoProduccion(...))->values(),
            'rutas' => Ruta::with('proveedores')->get()->map($this->mapRuta(...)),
            'clientes' => Cliente::query()->tap($filtrar)->get()->map($this->mapCliente(...)),
            'unidades_medida' => UnidadMedida::query()->tap($filtrar)->get()->map($this->mapUnidadMedida(...)),
            'insumos' => Insumo::query()->tap($filtrar)->get()->map($this->mapInsumo(...)),
            'recetas' => Receta::query()->tap($filtrar)->get()->map($this->mapReceta(...)),
            'receta_detalles' => RecetaDetalle::query()->tap($filtrar)->get()->map($this->mapRecetaDetalle(...)),
            'comunicados' => Comunicado::query()->tap($filtrar)->get()->map($this->mapComunicado(...)),
            'recojos' => Recojo::query()->tap($filtrar)->get()->map($this->mapRecojo(...)),
            'resultados_laboratorio' => ResultadoLaboratorio::query()->tap($filtrar)->get()->map($this->mapResultadoLaboratorio(...)),
            'entregas_planta' => EntregaPlanta::query()->tap($filtrar)->get()->map($this->mapEntregaPlanta(...)),
            'pagos_proveedor' => PagoProveedor::query()->tap($filtrar)->get()->map($this->mapPagoProveedor(...)),
            'compras' => Compra::with('detalles')->tap($filtrar)->get()->map($this->mapCompra(...)),
            'compra_detalles' => Compra::with('detalles')->tap($filtrar)->get()
                ->flatMap(fn (Compra $c) => $c->detalles)
                ->map($this->mapCompraDetalle(...))
                ->values(),
            'producciones' => Produccion::query()->tap($filtrar)->get()->map($this->mapProduccion(...)),
            'ventas' => Venta::with('items')->tap($filtrar)->get()->map($this->mapVenta(...)),
            'horarios_asistencia' => HorarioAsistencia::query()->tap($filtrar)->get()->map($this->mapHorarioAsistencia(...)),
            'dias_asistencia_acopiador' => DiaAsistenciaAcopiador::query()->tap($filtrar)->get()->map(fn (DiaAsistenciaAcopiador $d) => [
                'id' => (string) $d->id,
                'fecha' => $d->fecha->toDateString(),
            ]),
            'asistencias' => Asistencia::query()->tap($filtrar)->get()->map($this->mapAsistencia(...)),
            'justificaciones' => Justificacion::query()->tap($filtrar)->get()->map($this->mapJustificacion(...)),
            // Las notificaciones de la campana (database) del usuario del token, como en la web.
            // Siempre las más recientes completas: el teléfono reemplaza su lista con estas.
            'notificaciones' => $request->user()->notifications()->latest()->limit(30)->get()->map(fn ($n) => [
                'id' => (string) $n->id,
                'tipo' => class_basename($n->type),
                'titulo' => (string) ($n->data['titulo'] ?? 'Notificación'),
                'mensaje' => (string) ($n->data['mensaje'] ?? ''),
                'leida' => $n->read_at !== null,
                'creada_en' => $n->created_at?->toIso8601String(),
            ]),
            'ids' => $this->idsVigentes(),
        ], $request->user());
        $datos['altas'] = \App\Support\OrdenAltas::paraSync($datos);
        return response()->json($datos);
    }

    /**
     * Todos los ids que siguen existiendo en el servidor, por tabla del móvil. Con esto la app
     * borra de su SQLite lo que se eliminó en Laravel (un `since` solo trae lo creado o
     * modificado, nunca lo borrado).
     *
     * @return array<string, array<int, string>>
     */
    private function idsVigentes(): array
    {
        $ids = fn (Builder $query) => $query->pluck('id')->map(fn ($id) => (string) $id)->values()->all();

        return [
            'empresa' => $ids(Empresa::query()),
            'precio_leche' => $ids(PrecioLeche::query()),
            'descuento' => $ids(Descuento::query()),
            'usuario' => $ids(User::query()),
            'acopiador' => $ids(Acopiador::query()),
            'proveedor' => $ids(Proveedor::query()),
            'turno' => $ids(Turno::query()->where('es_produccion', false)),
            'turno_produccion' => $ids(Turno::query()->where('es_produccion', true)),
            'ruta' => $ids(Ruta::query()),
            'cliente' => $ids(Cliente::query()),
            'unidad_medida' => $ids(UnidadMedida::query()),
            'insumo' => $ids(Insumo::query()),
            'receta' => $ids(Receta::query()),
            'receta_detalle' => $ids(RecetaDetalle::query()),
            'comunicado' => $ids(Comunicado::query()),
            'recojo' => $ids(Recojo::query()),
            'resultado_laboratorio' => $ids(ResultadoLaboratorio::query()),
            'entrega_planta' => $ids(EntregaPlanta::query()),
            'pago_proveedor' => $ids(PagoProveedor::query()),
            'compra' => $ids(Compra::query()),
            'compra_detalle' => $ids(CompraDetalle::query()),
            'produccion' => $ids(Produccion::query()),
            'venta' => $ids(Venta::query()),
            'horario_asistencia' => $ids(HorarioAsistencia::query()),
            'dia_asistencia_acopiador' => $ids(DiaAsistenciaAcopiador::query()),
            'asistencia' => $ids(Asistencia::query()),
            'justificacion' => $ids(Justificacion::query()),
        ];
    }

    /**
     * @return array<string, mixed>
     */
    private function mapEmpresa(Empresa $e): array
    {
        return [
            'id' => (string) $e->id,
            'razon_social' => (string) $e->razon_social,
            'ruc' => (string) $e->ruc,
            'telefono' => (string) $e->telefono,
            'direccion' => (string) $e->direccion,
        ];
    }

    /**
     * @return array<string, mixed>
     */
    private function mapPrecioLeche(PrecioLeche $p): array
    {
        return [
            'id' => (string) $p->id,
            'precio_litro' => (float) $p->precio_litro,
            'vigente_desde' => $p->vigente_desde->toDateString(),
        ];
    }

    /**
     * @return array<string, mixed>
     */
    private function mapUsuario(User $u): array
    {
        return [
            'autorizacion' => (request()->user()?->puedePantalla('admin.asignar-roles') || request()->user()?->puedePantalla('admin.usuarios')) ? Autorizacion::snapshot($u) : null,
            'id' => (string) $u->id,
            'nombres' => (string) $u->nombres,
            'apellidos' => (string) $u->apellidos,
            'documento' => (string) ($u->documento ?? ''),
            'celular' => (string) ($u->celular ?? ''),
            'rol' => $u->rol?->value,
            'usuario' => (string) $u->usuario,
            'activo' => (bool) $u->activo,
            'roles' => $u->rolesAsignados()->map(fn (Rol $r) => $r->value)->values()->all(),
            // Si el administrador le asignó "Tomar asistencia" (el administrador siempre la toma).
            'toma_asistencia' => (bool) $u->toma_asistencia,
            // Hash bcrypt (nunca la contraseña): permite iniciar sesión en el teléfono sin señal.
            'password_hash' => $u->id === request()->user()?->id ? (string) $u->password : '',
        ];
    }

    /**
     * @return array<string, mixed>
     */
    private function mapAcopiador(Acopiador $a): array
    {
        return [
            'id' => (string) $a->id,
            'usuario_id' => (string) $a->user_id,
            'ruta_nombre' => (string) $a->ruta_nombre,
        ];
    }

    /**
     * @return array<string, mixed>
     */
    private function mapProveedor(Proveedor $p): array
    {
        return [
            'id' => (string) $p->id,
            'usuario_id' => (string) $p->user_id,
            'finca_ubicacion' => (string) $p->finca_ubicacion,
            'finca_referencia' => (string) $p->finca_referencia,
            'latitud' => $p->latitud !== null ? (float) $p->latitud : null,
            'longitud' => $p->longitud !== null ? (float) $p->longitud : null,
            'acopiador_id' => $p->acopiador_id !== null ? (string) $p->acopiador_id : null,
            'bloqueado' => (bool) $p->bloqueado,
            'alteraciones' => (int) $p->alteraciones,
        ];
    }

    /**
     * Los turnos de ruta (es_produccion = false) son una lista general que ven todos los
     * acopiadores. `acopiador_id` se manda solo como referencia (el de su primera ruta) y
     * va vacío si el turno todavía no está en ninguna ruta.
     *
     * @return array<string, mixed>
     */
    private function mapTurno(Turno $t): array
    {
        return [
            'id' => (string) $t->id,
            'acopiador_id' => (string) ($t->rutas->firstWhere('acopiador_id', request()->user()?->acopiador?->id)?->acopiador_id ?? $t->rutas->first()?->acopiador_id ?? ''),
            'nombre' => (string) $t->nombre,
            'hora_inicio' => substr((string) $t->hora_inicio, 0, 5),
            'hora_fin' => substr((string) $t->hora_fin, 0, 5),
        ];
    }

    /**
     * @return array<string, mixed>
     */
    private function mapTurnoProduccion(Turno $t): array
    {
        return [
            'id' => (string) $t->id,
            'nombre' => (string) $t->nombre,
            'hora_inicio' => substr((string) $t->hora_inicio, 0, 5),
            'hora_fin' => substr((string) $t->hora_fin, 0, 5),
        ];
    }

    /**
     * @return array<string, mixed>
     */
    private function mapRuta(Ruta $r): array
    {
        return [
            'id' => (string) $r->id,
            'acopiador_id' => (string) $r->acopiador_id,
            'nombre' => (string) $r->nombre,
            'activa' => (bool) $r->activa,
            // La relación ya viene ordenada por el pivot `orden`: la app usa este orden de recojo.
            'proveedor_ids' => $r->proveedores->pluck('id')->map(fn ($id) => (string) $id)->values(),
            'trazo' => collect($r->trazo ?? [])
                ->map(fn (array $punto) => [(float) $punto[0], (float) $punto[1]])
                ->values(),
        ];
    }

    /**
     * Solo manda los campos que Laravel administra (nombres/apellidos/documento/compras);
     * favorito/mayorista/descuentos son exclusivos del dispositivo (Laravel no los tiene,
     * usa un catálogo de descuentos aparte que no existe en el modelo móvil).
     *
     * @return array<string, mixed>
     */
    private function mapCliente(Cliente $c): array
    {
        return [
            'id' => (string) $c->id,
            'nombres' => (string) $c->nombres,
            'apellidos' => (string) $c->apellidos,
            'documento' => (string) $c->documento,
            'compras_count' => (int) $c->compras_count,
        ];
    }

    /**
     * @return array<string, mixed>
     */
    private function mapUnidadMedida(UnidadMedida $u): array
    {
        return [
            'id' => (string) $u->id,
            'nombre' => (string) $u->nombre,
            'simbolo' => (string) $u->simbolo,
        ];
    }

    /**
     * @return array<string, mixed>
     */
    private function mapInsumo(Insumo $i): array
    {
        return [
            'id' => (string) $i->id,
            'nombre' => (string) $i->nombre,
            'unidad_medida_id' => (string) $i->unidad_medida_id,
            'costo_unitario' => (float) $i->costo_unitario,
            'stock_actual' => (float) $i->stock_actual,
        ];
    }

    /**
     * @return array<string, mixed>
     */
    private function mapReceta(Receta $r): array
    {
        return [
            'id' => (string) $r->id,
            'nombre' => (string) $r->nombre,
            'descripcion' => '',
            'producto_id' => null,
        ];
    }

    /**
     * @return array<string, mixed>
     */
    private function mapRecetaDetalle(RecetaDetalle $d): array
    {
        return [
            'id' => (string) $d->id,
            'receta_id' => (string) $d->receta_id,
            'insumo_id' => (string) $d->insumo_id,
            'cantidad' => (float) $d->cantidad,
        ];
    }

    /**
     * @return array<string, mixed>
     */
    private function mapComunicado(Comunicado $c): array
    {
        return [
            'id' => (string) $c->id,
            'tipo' => $c->tipo?->value,
            'titulo' => (string) $c->titulo,
            'descripcion' => (string) $c->descripcion,
            'fecha' => $c->fecha->toDateString(),
            'autor_id' => (string) $c->autor_id,
            'roles' => $c->roles,
        ];
    }

    /**
     * @return array<string, mixed>
     */
    private function mapRecojo(Recojo $r): array
    {
        return [
            'id' => (string) $r->id,
            'proveedor_id' => (string) $r->proveedor_id,
            'acopiador_id' => (string) $r->acopiador_id,
            'turno_id' => (string) $r->turno_id,
            'fecha' => $r->fecha->toDateString(),
            'litros' => (float) $r->litros,
            // Lo que entregó el proveedor (el acopiador puede corregir `litros`, el litraje que llegó y se paga).
            'litros_entregados' => $r->litros_entregados !== null ? (float) $r->litros_entregados : null,
            'estado_leche' => $r->estado_leche,
            'estado_otro' => (string) $r->estado_otro,
            'precio_litro' => (float) $r->precio_litro,
            'total' => (float) $r->total,
            'pagado' => (bool) $r->pagado,
            'metodo_pago' => $r->metodo_pago,
            'entregado' => (bool) $r->entregado,
        ];
    }

    /**
     * @return array<string, mixed>
     */
    private function mapResultadoLaboratorio(ResultadoLaboratorio $r): array
    {
        return [
            'id' => (string) $r->id,
            'proveedor_id' => (string) $r->proveedor_id,
            'fecha' => $r->fecha->toDateString(),
            'turno_id' => $r->turno_id !== null ? (string) $r->turno_id : null,
            'resultado' => $r->resultado,
            'densidad' => $r->densidad !== null ? (float) $r->densidad : null,
            'acidez' => $r->acidez !== null ? (float) $r->acidez : null,
            'temperatura' => $r->temperatura !== null ? (float) $r->temperatura : null,
            'observaciones' => (string) $r->observaciones,
            'conforme' => (bool) $r->conforme,
            'enviado_produccion' => (bool) $r->enviado_produccion,
            'litros' => $this->decimalONulo($r->litros),
            'grasa' => $this->decimalONulo($r->grasa),
            'sng' => $this->decimalONulo($r->sng),
            'proteina' => $this->decimalONulo($r->proteina),
            'lactosa' => $this->decimalONulo($r->lactosa),
            'sales' => $this->decimalONulo($r->sales),
            'total_solidos' => $this->decimalONulo($r->total_solidos),
            'agua_anadida' => $this->decimalONulo($r->agua_anadida),
            'punto_congelacion' => $this->decimalONulo($r->punto_congelacion),
            'ph' => $this->decimalONulo($r->ph),
            // Ticket del analizador, en el mismo formato que lo imprime ("09:42", "06/06/26").
            'sn' => $r->sn,
            'modo' => $r->modo,
            'hora_analisis' => $r->hora_analisis ? substr((string) $r->hora_analisis, 0, 5) : null,
            'fecha_analisis' => $r->fecha_analisis?->format('d/m/y'),
        ];
    }

    private function decimalONulo(mixed $valor): ?float
    {
        return $valor !== null ? (float) $valor : null;
    }

    /**
     * @return array<string, mixed>
     */
    private function mapEntregaPlanta(EntregaPlanta $e): array
    {
        return [
            'id' => (string) $e->id,
            'acopiador_id' => (string) $e->acopiador_id,
            'turno_id' => (string) $e->turno_id,
            'fecha' => $e->fecha->toDateString(),
            'litros_total' => (float) $e->litros_total,
            'total_pagado' => (float) $e->total_pagado,
            'total_deuda' => (float) $e->total_deuda,
            'proveedores_no_recogidos' => (int) $e->proveedores_no_recogidos,
            'guia' => (string) $e->guia,
        ];
    }

    /**
     * @return array<string, mixed>
     */
    private function mapPagoProveedor(PagoProveedor $p): array
    {
        return [
            'id' => (string) $p->id,
            'proveedor_id' => (string) $p->proveedor_id,
            'recojo_id' => $p->recojo_id !== null ? (string) $p->recojo_id : null,
            'fecha' => $p->fecha->toDateString(),
            'monto' => (float) $p->monto,
            'metodo_pago' => $p->metodo_pago,
            'periodo' => $p->periodo,
        ];
    }

    /**
     * Laravel no guarda el total de la compra en la cabecera; se calcula desde el detalle
     * (mobile sí lo espera como columna NOT NULL).
     *
     * @return array<string, mixed>
     */
    private function mapCompra(Compra $c): array
    {
        return [
            'id' => (string) $c->id,
            'fecha' => $c->fecha->toDateString(),
            'proveedor_insumo' => '',
            'descripcion' => (string) $c->descripcion,
            'total' => (float) $c->detalles->sum('subtotal'),
        ];
    }

    /**
     * @return array<string, mixed>
     */
    private function mapCompraDetalle(CompraDetalle $d): array
    {
        return [
            'id' => (string) $d->id,
            'compra_id' => (string) $d->compra_id,
            'insumo_id' => (string) $d->insumo_id,
            'cantidad' => (float) $d->cantidad,
            'costo_unitario' => (float) $d->costo_unitario,
            'subtotal' => (float) $d->subtotal,
        ];
    }

    /**
     * @return array<string, mixed>
     */
    private function mapProduccion(Produccion $p): array
    {
        return [
            'id' => (string) $p->id,
            'fecha' => $p->fecha->toDateString(),
            'nombre' => (string) $p->nombre,
            'receta_id' => (string) $p->receta_id,
            'cantidad_producida' => (float) $p->cantidad_producida,
            'cantidad_vendida' => (float) $p->cantidad_vendida,
            'lote' => (string) $p->lote,
            'turno_id' => $p->turno_id !== null ? (string) $p->turno_id : null,
            'responsable_id' => $p->responsable_id !== null ? (string) $p->responsable_id : null,
            'estado' => $p->estado->value,
        ];
    }

    /**
     * La app guarda la fecha con hora: se usa la de registro (`fecha` es solo el día).
     *
     * @return array<string, mixed>
     */
    private function mapVenta(Venta $v): array
    {
        return [
            'id' => (string) $v->id,
            'numero_boleta' => (string) $v->numero_boleta,
            'cliente_id' => $v->cliente_id !== null ? (string) $v->cliente_id : null,
            'cliente_nombre_libre' => $v->pedidoWeb?->cliente_web_id ? null : $v->cliente_nombre_libre,
            'cliente_doc_libre' => $v->pedidoWeb?->cliente_web_id ? null : $v->cliente_doc_libre,
            'vendedor_id' => $v->vendedor_id !== null ? (string) $v->vendedor_id : null,
            'fecha' => $v->fecha->toDateString().'T'.($v->created_at?->format('H:i:s') ?? '00:00:00'),
            'subtotal' => (float) $v->subtotal,
            'descuento' => (float) $v->descuento,
            'descuento_tipo' => (string) $v->descuento_tipo,
            'total' => (float) $v->total,
            'metodo_pago' => (string) $v->metodo_pago,
            'efectivo_recibido' => $v->efectivo_recibido !== null ? (float) $v->efectivo_recibido : null,
            'vuelto' => $v->vuelto !== null ? (float) $v->vuelto : null,
            'items' => $v->items->map(fn (VentaItem $i) => [
                'id' => (string) $i->id,
                'produccion_id' => $i->produccion_id !== null ? (string) $i->produccion_id : null,
                'nombre' => (string) $i->nombre,
                'precio_unitario' => (float) $i->precio_unitario,
                'cantidad' => (int) $i->cantidad,
                'subtotal' => (float) $i->subtotal,
            ])->values(),
        ];
    }

    /**
     * @return array<string, mixed>
     */
    private function mapHorarioAsistencia(HorarioAsistencia $h): array
    {
        return [
            'id' => (string) $h->id,
            'nombre' => (string) $h->nombre,
            'hora_inicio' => $h->inicio(),
            'hora_limite_presente' => $h->limitePresente(),
            'hora_limite_tardanza' => $h->limiteTardanza(),
            'activo' => (bool) $h->activo,
        ];
    }

    /**
     * Sin horario es la asistencia general (la tabla); con horario, la marca de un acopiador.
     *
     * @return array<string, mixed>
     */
    private function mapAsistencia(Asistencia $a): array
    {
        return [
            'id' => (string) $a->id,
            'usuario_id' => (string) $a->user_id,
            'fecha' => $a->fecha->toDateString(),
            'horario_asistencia_id' => $a->horario_asistencia_id !== null ? (string) $a->horario_asistencia_id : null,
            'estado' => $a->estado->value,
            'hora_marcada' => $a->horaMarcada(),
            'registrado_por' => $a->registrado_por !== null ? (string) $a->registrado_por : null,
        ];
    }

    /**
     * El archivo adjunto no baja al teléfono (se ve en la web): solo se avisa que existe.
     *
     * @return array<string, mixed>
     */
    private function mapJustificacion(Justificacion $j): array
    {
        return [
            'id' => (string) $j->id,
            'asistencia_id' => (string) $j->asistencia_id,
            'motivo' => (string) $j->motivo,
            'tiene_archivo' => $j->archivo !== null,
            'estado' => $j->estado->value,
            'enviada_en' => $j->created_at?->toDateString(),
        ];
    }
}

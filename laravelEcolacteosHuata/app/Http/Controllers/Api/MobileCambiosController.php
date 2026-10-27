<?php

namespace App\Http\Controllers\Api;

use App\Authorization\Autorizacion;
use App\Authorization\Pantallas;
use App\Enums\EstadoAsistencia;
use App\Enums\EstadoJustificacion;
use App\Enums\EstadoProduccion;
use App\Enums\Rol;
use App\Enums\TeamRole;
use App\Exceptions\CambioMovilRechazado;
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
use App\Models\Team;
use App\Models\Turno;
use App\Models\UnidadMedida;
use App\Models\User;
use App\Models\Venta;
use App\Notifications\ComunicadoPublicado;
use App\Services\Payments\Dinero;
use App\Services\Payments\StockInsuficiente;
use App\Services\Payments\VentasService;
use Carbon\CarbonImmutable;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\QueryException;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Carbon;
use Illuminate\Support\Collection;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Notification;
use Illuminate\Support\Facades\Storage;
use Illuminate\Support\Facades\Validator;
use Illuminate\Support\Str;
use Illuminate\Validation\Rule;
use Illuminate\Validation\ValidationException;
use Throwable;

class MobileCambiosController extends Controller
{
    /**
     * Tabla local de la app móvil => modelo de Laravel que la refleja (las mismas que baja el sync).
     *
     * @var array<string, class-string<Model>>
     */
    private const MODELOS = [
        'empresa' => Empresa::class,
        'precio_leche' => PrecioLeche::class,
        'descuento' => Descuento::class,
        'usuario' => User::class,
        'acopiador' => Acopiador::class,
        'proveedor' => Proveedor::class,
        'turno' => Turno::class,
        'turno_produccion' => Turno::class,
        'ruta' => Ruta::class,
        'cliente' => Cliente::class,
        'unidad_medida' => UnidadMedida::class,
        'insumo' => Insumo::class,
        'receta' => Receta::class,
        'receta_detalle' => RecetaDetalle::class,
        'comunicado' => Comunicado::class,
        'recojo' => Recojo::class,
        'resultado_laboratorio' => ResultadoLaboratorio::class,
        'entrega_planta' => EntregaPlanta::class,
        'pago_proveedor' => PagoProveedor::class,
        'compra' => Compra::class,
        'compra_detalle' => CompraDetalle::class,
        'produccion' => Produccion::class,
        'venta' => Venta::class,
        'horario_asistencia' => HorarioAsistencia::class,
        'dia_asistencia_acopiador' => DiaAsistenciaAcopiador::class,
        'asistencia' => Asistencia::class,
        'justificacion' => Justificacion::class,
    ];

    /**
     * Tablas de "Tomar asistencia": solo las cambia quien toma asistencia (como en la web). La
     * marca propia del acopiador y las justificaciones del trabajador se revisan en su método.
     *
     * @var array<int, string>
     */
    private const TABLAS_ASISTENCIA = ['horario_asistencia', 'dia_asistencia_acopiador', 'asistencia', 'justificacion'];

    /**
     * @var array<int, string>
     */
    private const TABLAS_SOLO_TOMA_ASISTENCIA = ['horario_asistencia', 'dia_asistencia_acopiador'];

    /**
     * Aplica en MySQL lo que se creó, editó o eliminó en la app móvil, en el mismo orden en que
     * pasó en el teléfono. Cada cambio trae la fila local completa (columnas del SQLite de la
     * app). A los registros nuevos (id UUID) les asigna un id de Laravel y lo devuelve para que
     * el teléfono reemplace el suyo; un cambio rechazado no detiene a los demás.
     */
    public function store(Request $request): JsonResponse
    {
        $request->validate([
            'cambios' => ['required', 'array'],
            'cambios.*.tabla' => ['required', 'string'],
            'cambios.*.id' => ['required', 'string'],
            'cambios.*.operacion' => ['required', Rule::in(['guardar', 'eliminar'])],
            'cambios.*.datos' => ['nullable', 'array'],
        ]);

        /** @var User $user */
        $user = $request->user();
        $ids = [];
        $errores = [];

        // APK anteriores descartan la cola al recibir HTTP 200, incluso con errores.
        // Solo el protocolo nuevo conserva rechazos parciales; el antiguo es atómico.
        $heredado = $request->header('X-Huata-Authorization-Protocol') !== '1';
        $nivel = DB::transactionLevel();
        if ($heredado) {
            DB::beginTransaction();
        }
        try {
            foreach ($request->input('cambios') as $cambio) {
                $tabla = (string) $cambio['tabla'];
                $id = (string) $cambio['id'];

                // "Marcar como leída" de la campana del teléfono: solo las notificaciones propias.
                if ($tabla === 'notificacion') {
                    if ($this->booleano($cambio['datos'] ?? [], 'leida')) {
                        $user->notifications()->whereKey($id)->first()?->markAsRead();
                    }

                    continue;
                }

                try {
                    $permitidas = Pantallas::escritura($tabla);
                    // El perfil propio conserva sus reglas; los campos de autorización se protegen abajo.
                    $perfilPropio = $tabla === 'usuario' && $this->idServidor($tabla, $id) === $user->id && $cambio['operacion'] === 'guardar';
                    if (! $perfilPropio && ! collect($permitidas)->contains(fn ($p) => $user->puedePantalla($p))) {
                        $errores[] = ['tabla' => $tabla, 'id' => $id, 'code' => 'AUTHORIZATION_REVOKED', 'message' => 'La función ya no está asignada. Conserva el trabajo local para revisión.'];

                        continue;
                    }
                    if ($tabla === 'venta') {
                        $p = $cambio['operacion'] === 'eliminar' ? 'vendedor.historial' : 'vendedor.ventas';
                        if (! $user->puedePantalla($p)) {
                            throw new CambioMovilRechazado('La operación de venta no está asignada.');
                        }
                    }
                    $this->autorizarContexto($tabla, $id, $cambio['datos'] ?? [], $user);
                    $idServidor = DB::transaction(fn () => $cambio['operacion'] === 'eliminar'
                        ? $this->eliminar($tabla, $id, $user)
                        : $this->guardar($tabla, $id, $cambio['datos'] ?? [], $user));

                    if ($idServidor !== null && ! $this->esIdDelServidor($id)) {
                        $ids[] = ['tabla' => $tabla, 'id_local' => $id, 'id' => (string) $idServidor];
                    }
                } catch (CambioMovilRechazado $e) {
                    $errores[] = ['tabla' => $tabla, 'id' => $id, 'message' => $e->getMessage()];
                } catch (QueryException $e) {
                    report($e);
                    $errores[] = ['tabla' => $tabla, 'id' => $id, 'message' => $cambio['operacion'] === 'eliminar'
                        ? 'No se puede eliminar porque tiene registros relacionados en el servidor.'
                        : 'El servidor no aceptó los datos (revisa que no estén repetidos o incompletos).'];
                } catch (Throwable $e) {
                    report($e);
                    $errores[] = ['tabla' => $tabla, 'id' => $id, 'message' => 'Error del servidor al guardar el cambio.'];
                }
            }

            if ($heredado && $errores) {
                DB::rollBack();

                return response()->json(['ids' => [], 'errores' => $errores, 'message' => 'No se aplicó el lote. Conserva la cola y revisa las autorizaciones en Laravel.'], 409);
            }
            if ($heredado) {
                DB::commit();
            }

            return response()->json(['ids' => $ids, 'errores' => $errores]);
        } finally {
            if ($heredado && DB::transactionLevel() > $nivel) {
                DB::rollBack($nivel);
            }
        }
    }

    /**
     * @param  array<string, mixed>  $datos
     */
    private function guardar(string $tabla, string $id, array $datos, User $user): int
    {
        $modelo = self::MODELOS[$tabla] ?? throw new CambioMovilRechazado("Los datos de «{$tabla}» no se guardan en Laravel.");

        if (in_array($tabla, self::TABLAS_SOLO_TOMA_ASISTENCIA, true)) {
            $this->exigirTomaAsistencia($user);
        }

        $registro = $this->registroExistente($tabla, $id);
        $nuevo = $registro === null;

        $registro = match ($tabla) {
            'usuario' => $this->guardarUsuario($registro, $datos, $user),
            'acopiador' => $this->guardarAcopiador($registro, $datos),
            'proveedor' => $this->guardarProveedor($datos),
            'produccion' => $this->guardarProduccion($registro, $datos, $user),
            'venta' => $this->guardarVenta($registro, $datos, $user),
            'horario_asistencia' => $this->guardarHorarioAsistencia($registro, $datos),
            'dia_asistencia_acopiador' => $this->guardarDiaAsistenciaAcopiador($registro, $datos),
            'asistencia' => $this->guardarAsistencia($registro, $datos, $user),
            'justificacion' => $this->guardarJustificacion($registro, $datos, $user),
            default => tap($registro ?? new $modelo, fn (Model $m) => $m->fill($this->atributos($tabla, $datos, $nuevo, $user))->save()),
        };

        if ($nuevo) {
            $this->despuesDeCrear($tabla, $registro, $user);
        }

        // "Enviar a producción" en el teléfono: como en la web, suma la leche al stock del insumo.
        if ($registro instanceof ResultadoLaboratorio && $this->booleano($datos, 'enviado_produccion')) {
            $registro->enviarAProduccion();
        }

        if ($tabla === 'ruta') {
            $this->sincronizarProveedoresDeRuta($registro, $datos);
        }

        if (! $this->esIdDelServidor($id)) {
            DB::table('mobile_ids')->updateOrInsert(
                ['tabla' => $tabla, 'uuid' => $id],
                ['registro_id' => $registro->getKey(), 'updated_at' => now(), 'created_at' => now()],
            );
        }

        return (int) $registro->getKey();
    }

    private function eliminar(string $tabla, string $id, User $user): null
    {
        $modelo = self::MODELOS[$tabla] ?? throw new CambioMovilRechazado("Los datos de «{$tabla}» no se guardan en Laravel.");
        $idServidor = $this->idServidor($tabla, $id);

        if ($idServidor === null) {
            return null;
        }

        if ($tabla === 'usuario') {
            $this->exigirAdministrador($user);

            if ($idServidor === $user->id) {
                throw new CambioMovilRechazado('No puedes eliminar tu propio usuario.');
            }
        }

        // Como "Limpiar historial" del vendedor en la web: borra la boleta (y sus ítems) sin reponer stock.
        if ($tabla === 'venta' && ! $user->puedePantalla('vendedor.historial')) {
            throw new CambioMovilRechazado('Solo el vendedor puede limpiar el historial de ventas.');
        }

        if (in_array($tabla, self::TABLAS_ASISTENCIA, true)) {
            $this->exigirTomaAsistencia($user);
        }

        if ($tabla === 'justificacion') {
            throw new CambioMovilRechazado('Las justificaciones no se eliminan: acéptala o recházala.');
        }

        $registro = $modelo::query()->whereKey($idServidor)->first();

        if ($registro instanceof DiaAsistenciaAcopiador && $registro->fecha->lt(Asistencia::hoy())) {
            throw new CambioMovilRechazado('No se pueden cambiar días de asistencia pasados.');
        }

        if ($registro instanceof HorarioAsistencia) {
            $registro->eliminarConMarcas();
            DB::table('mobile_ids')->where('tabla', $tabla)->where('registro_id', $idServidor)->delete();

            return null;
        }

        if ($registro instanceof Produccion) {
            if ((float) $registro->cantidad_vendida > 0) {
                throw new CambioMovilRechazado('No se puede eliminar la producción: ya tiene ventas registradas.');
            }

            $registro->revertirConsumo();
        }

        if ($registro instanceof Venta && $registro->protegida()) {
            throw new CambioMovilRechazado('La venta conserva pagos externos o saldo y no puede eliminarse.');
        }
        if ($registro instanceof User) {
            Autorizacion::autorizarGestionUsuario($user, $registro);
        }
        $registro?->delete();
        DB::table('mobile_ids')->where('tabla', $tabla)->where('registro_id', $idServidor)->delete();

        return null;
    }

    /**
     * Columnas de Laravel a partir de la fila del teléfono (todas llegan como texto).
     *
     * @param  array<string, mixed>  $d
     * @return array<string, mixed>
     */
    private function atributos(string $tabla, array $d, bool $nuevo, User $user): array
    {
        return match ($tabla) {
            'empresa' => $this->solo($d, ['razon_social', 'ruc', 'telefono', 'direccion']),
            'precio_leche' => ['precio_litro' => $this->numero($d, 'precio_litro'), 'vigente_desde' => $this->texto($d, 'vigente_desde')],
            'descuento' => [
                'nombre' => $this->texto($d, 'nombre'),
                'porcentaje' => $this->numero($d, 'porcentaje'),
                'activo' => $this->booleano($d, 'activo'),
            ],
            'turno', 'turno_produccion' => [
                ...$this->solo($d, ['nombre', 'hora_inicio', 'hora_fin']),
                'es_produccion' => $tabla === 'turno_produccion',
            ],
            'ruta' => [
                'acopiador_id' => $this->referencia('acopiador', $d['acopiador_id'] ?? null),
                'nombre' => $this->texto($d, 'nombre'),
                'activa' => $this->booleano($d, 'activa'),
                'trazo' => $this->trazo($d['trazo'] ?? null),
            ],
            'cliente' => [
                ...$this->solo($d, ['nombres', 'apellidos', 'documento']),
                'compras_count' => (int) ($d['compras_count'] ?? 0),
            ],
            'unidad_medida' => $this->solo($d, ['nombre', 'simbolo']),
            'insumo' => [
                'nombre' => $this->texto($d, 'nombre'),
                'unidad_medida_id' => $this->referencia('unidad_medida', $d['unidad_medida_id'] ?? null),
                'costo_unitario' => $this->numero($d, 'costo_unitario'),
                // El stock lo mueven las compras y la producción: solo se toma el inicial (el teléfono
                // ya le sumó/restó a `stock_actual` sus propias compras y producciones), o el que se
                // corrigió a mano en el teléfono (`stock_editado`), como al editar el insumo en la web.
                ...($nuevo ? ['stock_actual' => $this->numero($d, array_key_exists('stock_inicial', $d) ? 'stock_inicial' : 'stock_actual')] : []),
                ...(! $nuevo && $this->booleano($d, 'stock_editado') ? ['stock_actual' => $this->numero($d, 'stock_actual')] : []),
            ],
            'receta' => ['nombre' => $this->texto($d, 'nombre')],
            'receta_detalle' => [
                'receta_id' => $this->referencia('receta', $d['receta_id'] ?? null),
                'insumo_id' => $insumoId = $this->referencia('insumo', $d['insumo_id'] ?? null),
                'cantidad' => $this->numero($d, 'cantidad'),
                'unidad_medida_id' => Insumo::query()->whereKey($insumoId)->value('unidad_medida_id'),
            ],
            'comunicado' => [
                'tipo' => strtolower($this->texto($d, 'tipo')),
                'titulo' => $this->texto($d, 'titulo'),
                'descripcion' => $this->texto($d, 'descripcion'),
                'fecha' => $this->texto($d, 'fecha'),
                'autor_id' => $this->referenciaOpcional('usuario', $d['autor_id'] ?? null) ?? $user->id,
                'roles' => filled($d['roles'] ?? null) ? $this->roles((string) $d['roles'])->all() : null,
            ],
            'recojo' => [
                'proveedor_id' => $this->referencia('proveedor', $d['proveedor_id'] ?? null),
                'acopiador_id' => $this->referencia('acopiador', $d['acopiador_id'] ?? null),
                'turno_id' => $this->referencia('turno', $d['turno_id'] ?? null),
                'fecha' => $this->texto($d, 'fecha'),
                'litros' => $this->numero($d, 'litros'),
                'estado_leche' => strtolower($this->texto($d, 'estado_leche')),
                'estado_otro' => $this->texto($d, 'estado_otro'),
                'precio_litro' => $this->numero($d, 'precio_litro'),
                'total' => $this->numero($d, 'total'),
                'pagado' => $this->booleano($d, 'pagado'),
                'metodo_pago' => filled($d['metodo_pago'] ?? null) ? strtolower((string) $d['metodo_pago']) : null,
                'entregado' => $this->booleano($d, 'entregado'),
                ...($nuevo ? ['litros_entregados' => $this->numero($d, 'litros')] : []),
            ],
            'resultado_laboratorio' => [
                'proveedor_id' => $this->referencia('proveedor', $d['proveedor_id'] ?? null),
                'fecha' => $this->texto($d, 'fecha'),
                'turno_id' => $this->referenciaOpcional('turno', $d['turno_id'] ?? null),
                // Local: CONFORME / NO_CONFORME / SOLEADO…; Laravel: "conforme" / "no conforme" / "soleado"…
                'resultado' => str_replace('_', ' ', strtolower($this->texto($d, 'resultado'))),
                ...collect(['densidad', 'acidez', 'temperatura', 'litros', 'grasa', 'sng', 'proteina', 'lactosa', 'sales',
                    'total_solidos', 'agua_anadida', 'punto_congelacion', 'ph'])
                    ->mapWithKeys(fn (string $campo) => [$campo => $this->numeroONulo($d, $campo)])->all(),
                'observaciones' => $this->texto($d, 'observaciones'),
                'conforme' => $this->booleano($d, 'conforme'),
                'sn' => $d['sn'] ?? null,
                'modo' => $d['modo'] ?? null,
                'hora_analisis' => $d['hora_analisis'] ?? null,
                'fecha_analisis' => $this->fechaTicket($d['fecha_analisis'] ?? null),
            ],
            'entrega_planta' => [
                'acopiador_id' => $this->referencia('acopiador', $d['acopiador_id'] ?? null),
                'turno_id' => $this->referencia('turno', $d['turno_id'] ?? null),
                'fecha' => $this->texto($d, 'fecha'),
                'litros_total' => $this->numero($d, 'litros_total'),
                'total_pagado' => $this->numero($d, 'total_pagado'),
                'total_deuda' => $this->numero($d, 'total_deuda'),
                'proveedores_no_recogidos' => (int) ($d['proveedores_no_recogidos'] ?? 0),
                'guia' => $this->texto($d, 'guia'),
            ],
            'pago_proveedor' => [
                'proveedor_id' => $this->referencia('proveedor', $d['proveedor_id'] ?? null),
                'recojo_id' => $this->referenciaOpcional('recojo', $d['recojo_id'] ?? null),
                'fecha' => $this->texto($d, 'fecha'),
                'monto' => $this->numero($d, 'monto'),
                'metodo_pago' => strtolower($this->texto($d, 'metodo_pago')),
                'periodo' => strtolower($this->texto($d, 'periodo')),
            ],
            'compra' => [
                'fecha' => $this->texto($d, 'fecha'),
                'descripcion' => $this->texto($d, 'descripcion'),
                ...($nuevo ? ['registrado_por' => $user->id] : []),
            ],
            'compra_detalle' => [
                'compra_id' => $this->referencia('compra', $d['compra_id'] ?? null),
                'insumo_id' => $this->referencia('insumo', $d['insumo_id'] ?? null),
                'cantidad' => $this->numero($d, 'cantidad'),
                'costo_unitario' => $this->numero($d, 'costo_unitario'),
                'subtotal' => $this->numero($d, 'subtotal'),
            ],
        };
    }

    /**
     * Lo mismo que hace la pantalla web al crear: avisar a los destinatarios de un comunicado y
     * sumar la compra al stock del insumo.
     */
    private function despuesDeCrear(string $tabla, Model $registro, User $user): void
    {
        if ($registro instanceof Comunicado) {
            $destinatarios = User::query()
                ->where('activo', true)
                ->where('id', '!=', $registro->autor_id)
                ->when($registro->roles, fn ($q) => $q->conAlgunRol(collect($registro->roles)->map(fn (string $v) => Rol::from($v))->all()))
                ->get();

            Notification::send($destinatarios, new ComunicadoPublicado($registro));
        }

        if ($registro instanceof CompraDetalle) {
            $registro->insumo?->registrarCompra((float) $registro->cantidad, (float) $registro->costo_unitario);
        }
    }

    /**
     * Crear, editar o cambiar roles de usuarios es solo del administrador; cada uno sí puede
     * actualizar su propia cuenta. La contraseña llega como hash bcrypt, nunca en claro.
     *
     * @param  array<string, mixed>  $d
     */
    private function guardarUsuario(?User $user, array $d, User $autor): User
    {
        if ($user?->id !== $autor->id) {
            $this->exigirAdministrador($autor);
            if ($user) {
                Autorizacion::autorizarGestionUsuario($autor, $user);
            }
        }

        if ($user !== null) {
            // Los campos heredados que manda una APK vieja no cambian asignaciones centrales.
            $propuestos = $this->roles((string) ($d['roles'] ?? ''))->push($this->roles((string) ($d['rol'] ?? ''))->first())->filter()->unique()->sort()->values()->all();
            $actuales = $user->rolesAsignados()->map(fn ($r) => $r->value)->sort()->values()->all();
            if ($user->id === $autor->id && ((isset($d['rol']) && $propuestos !== $actuales) || (isset($d['activo']) && $this->booleano($d, 'activo') !== (bool) $user->activo) || (isset($d['toma_asistencia']) && $this->booleano($d, 'toma_asistencia') !== (bool) $user->toma_asistencia))) {
                throw new CambioMovilRechazado('No puedes cambiar tu propia autorización mediante sincronización.');
            }
        }
        if (array_key_exists('toma_asistencia', $d) && $this->booleano($d, 'toma_asistencia') !== (bool) ($user?->toma_asistencia ?? false)) {
            throw new CambioMovilRechazado('Asigna asistencia.tomar a un rol desde Roles y funciones. La sincronización no concede privilegios.');
        }
        $usuario = $this->texto($d, 'usuario');
        $documento = $this->texto($d, 'documento');

        // Reenvío de un usuario que ya se creó pero cuyo id el teléfono no alcanzó a recibir.
        if ($user === null) {
            $user = User::query()->where('usuario', $usuario)->where('documento', $documento)->first();
        }

        if (User::query()->where('usuario', $usuario)->whereKeyNot($user?->id)->exists()) {
            throw new CambioMovilRechazado("El usuario «{$usuario}» ya existe en el servidor.");
        }

        if (User::query()->where('documento', $documento)->whereKeyNot($user?->id)->exists()) {
            throw new CambioMovilRechazado("Ya existe un usuario con el DNI/RUC {$documento} en el servidor.");
        }

        $hash = (string) ($d['password_hash'] ?? '');
        $esBcrypt = preg_match('/^\$2[aby]\$\d{2}\$[.\/A-Za-z0-9]{53}$/', $hash) === 1;

        if ($user === null && ! $esBcrypt) {
            throw new CambioMovilRechazado("La contraseña de «{$usuario}» no se puede enviar: vuelve a asignársela en la app.");
        }

        $roles = $this->roles((string) ($d['roles'] ?? ''));
        $rol = $this->roles((string) ($d['rol'] ?? ''))->first() ?? throw new CambioMovilRechazado("«{$usuario}» no tiene un rol válido.");

        $atributos = [
            'name' => trim($this->texto($d, 'nombres').' '.$this->texto($d, 'apellidos')),
            'nombres' => $this->texto($d, 'nombres'),
            'apellidos' => $this->texto($d, 'apellidos'),
            'documento' => $documento,
            'celular' => $this->texto($d, 'celular'),
            'rol' => $user?->rol ?? $rol,
            'roles' => $user?->roles ?? ($roles->reject(fn (string $r) => $r === $rol)->values()->all() ?: null),
            'usuario' => $usuario,
            'activo' => $user?->id === $autor->id ? $user->activo : $this->booleano($d, 'activo'),
        ];

        if ($user === null) {
            foreach (Pantallas::heredadas(Rol::from($rol)) as $pantalla) {
                if (! Autorizacion::puedeDelegar($autor, $pantalla)) {
                    throw new CambioMovilRechazado('No puedes delegar funciones que no posees.');
                }
            }
        }
        if ($user === null) {
            $user = User::create([
                ...$atributos,
                'email' => $usuario.'@ecolacteoshuata.pe',
                'email_verified_at' => now(),
                'password' => Str::random(40),
            ]);

            $team = Team::principal();
            $team->members()->attach($user, ['role' => TeamRole::Owner->value]);
            $user->switchTeam($team);
        } else {
            $user->update($atributos);
        }

        // El cast `hashed` volvería a cifrar (o rechazaría) un hash de otro costo: se guarda tal cual.
        if ($esBcrypt) {
            DB::table('users')->where('id', $user->id)->update([
                'password' => preg_replace('/^\$2[ab]\$/', '\$2y\$', $hash),
            ]);
        }

        return $user;
    }

    /**
     * Un usuario tiene un solo perfil de acopiador: si Laravel ya se lo creó, se reutiliza.
     *
     * @param  array<string, mixed>  $d
     */
    private function guardarAcopiador(?Acopiador $acopiador, array $d): Acopiador
    {
        $userId = $this->referencia('usuario', $d['usuario_id'] ?? null);
        $acopiador ??= Acopiador::query()->firstOrNew(['user_id' => $userId]);
        $acopiador->fill(['user_id' => $userId, 'ruta_nombre' => $this->texto($d, 'ruta_nombre')])->save();

        return $acopiador;
    }

    /**
     * Igual que `admin/usuarios`: si cambió la ubicación de la finca se avisa a planta.
     *
     * @param  array<string, mixed>  $d
     */
    private function guardarProveedor(array $d): Proveedor
    {
        $proveedor = Proveedor::guardarConUbicacion($this->referencia('usuario', $d['usuario_id'] ?? null), [
            'finca_ubicacion' => $this->texto($d, 'finca_ubicacion'),
            'finca_referencia' => $this->texto($d, 'finca_referencia'),
            'latitud' => $this->numeroONulo($d, 'latitud'),
            'longitud' => $this->numeroONulo($d, 'longitud'),
            'acopiador_id' => $this->referenciaOpcional('acopiador', $d['acopiador_id'] ?? null),
        ]);

        $proveedor->update([
            'bloqueado' => $this->booleano($d, 'bloqueado'),
            'alteraciones' => (int) ($d['alteraciones'] ?? 0),
        ]);

        return $proveedor;
    }

    /**
     * Igual que `produccion/registros`: al crear o cambiar la cantidad descuenta los insumos de la
     * receta, y al pasar a "completada" la publica para la venta. `cantidad_vendida` la mueven
     * solo las ventas, nunca el teléfono.
     *
     * @param  array<string, mixed>  $d
     */
    private function guardarProduccion(?Produccion $produccion, array $d, User $user): Produccion
    {
        $recetaId = $this->referencia('receta', $d['receta_id'] ?? null);
        $cantidad = $this->numero($d, 'cantidad_producida');

        if ($cantidad <= 0) {
            throw new CambioMovilRechazado('La cantidad producida debe ser mayor a 0.');
        }

        $completada = strtolower($this->texto($d, 'estado')) === EstadoProduccion::Completada->value;
        $atributos = [
            'nombre' => $this->texto($d, 'nombre') ?: Receta::query()->whereKey($recetaId)->value('nombre'),
            'receta_id' => $recetaId,
            'cantidad_producida' => $cantidad,
            'turno_id' => $this->referenciaOpcional('turno_produccion', $d['turno_id'] ?? null),
        ];

        if ($produccion === null) {
            $produccion = Produccion::create([
                ...$atributos,
                'fecha' => $this->texto($d, 'fecha') ?: now()->toDateString(),
                'lote' => $this->loteLibre($this->texto($d, 'lote')),
                'responsable_id' => $this->referenciaOpcional('usuario', $d['responsable_id'] ?? null) ?? $user->id,
                'estado' => EstadoProduccion::Planificada,
            ]);
        } else {
            // Una producción ya publicada que el teléfono reenvía tal cual (p. ej. después de
            // venderla) sigue publicada; editarla en el teléfono la vuelve a planificar.
            $sigueCompletada = $completada && $produccion->estado === EstadoProduccion::Completada;
            $produccion->update([...$atributos, 'estado' => $sigueCompletada ? EstadoProduccion::Completada : EstadoProduccion::Planificada]);
        }

        $produccion->consumirInsumos();

        if ($completada && $produccion->estado !== EstadoProduccion::Completada) {
            $produccion->completar();
        }

        return $produccion;
    }

    /**
     * El lote que generó el teléfono, o uno nuevo si viene vacío o ya lo usa otra producción.
     */
    private function loteLibre(string $lote): string
    {
        while ($lote === '' || Produccion::query()->where('lote', $lote)->exists()) {
            $lote = 'L-'.now()->format('YmdHis').'-'.random_int(100, 999);
        }

        return $lote;
    }

    /**
     * Una venta hecha en el teléfono (con o sin señal) llega con sus productos en `items` (JSON).
     * Las ventas no se editan: si ya se había recibido, se devuelve la misma. La boleta toma el
     * siguiente correlativo del servidor y cada ítem descuenta lo vendido de su producción.
     *
     * @param  array<string, mixed>  $d
     */
    private function guardarVenta(?Venta $venta, array $d, User $user): Venta
    {
        if (! $user->activo || ! $user->puedePantalla('vendedor.ventas')) {
            throw new CambioMovilRechazado('Solo vendedores autorizados pueden importar ventas.');
        }
        if ($venta !== null) {
            return $venta;
        }
        $method = strtolower($this->texto($d, 'metodo_pago'));
        if (in_array($method, ['yape', 'saldo'], true)) {
            throw new CambioMovilRechazado('Yape y saldo requieren confirmación en el punto de venta web. La operación móvil no se confirma.');
        }
        $items = json_decode((string) ($d['items'] ?? '[]'), true);
        if (! is_array($items) || ! $items) {
            throw new CambioMovilRechazado('La venta no tiene productos.');
        }
        $lines = array_map(fn ($item) => ['produccion_id' => $this->referencia('produccion', $item['produccion_id'] ?? null), 'cantidad' => $item['cantidad'] ?? null], $items);
        $names = array_filter(array_map('trim', explode(',', $this->texto($d, 'descuento_tipo'))));
        $discounts = Descuento::where('activo', true)->whereIn('nombre', $names)->pluck('id')->all();
        $service = app(VentasService::class);
        try {
            $quote = $service->cotizar($lines, $discounts);
            $date = substr($this->texto($d, 'fecha'), 0, 10) ?: now()->toDateString();
            Validator::make(['fecha' => $date], ['fecha' => 'required|date_format:Y-m-d'])->validate();
            $quote['fecha'] = $date;
            $cash = $method === 'efectivo' ? Dinero::minor($this->texto($d, 'efectivo_recibido')) : null;

            return $service->registrar($quote, $user, $method, ['cliente_id' => $this->referenciaOpcional('cliente', $d['cliente_id'] ?? null), 'cliente_nombre_libre' => mb_substr($this->texto($d, 'cliente_nombre_libre'), 0, 80), 'cliente_doc_libre' => mb_substr($this->texto($d, 'cliente_doc_libre'), 0, 15)], $cash);
        } catch (ValidationException|\InvalidArgumentException|StockInsuficiente $e) {
            throw new CambioMovilRechazado('No se pudo importar la venta: revisa productos, cantidades, importe y stock.');
        }
    }

    /**
     * Igual que el formulario de horarios de "Tomar asistencia": presente hasta va después del
     * inicio y tardanza hasta, después de presente hasta.
     *
     * @param  array<string, mixed>  $d
     */
    private function guardarHorarioAsistencia(?HorarioAsistencia $horario, array $d): HorarioAsistencia
    {
        $horas = collect(['hora_inicio', 'hora_limite_presente', 'hora_limite_tardanza'])
            ->mapWithKeys(fn (string $campo) => [$campo => substr($this->texto($d, $campo), 0, 5)]);

        if ($this->texto($d, 'nombre') === '' || $horas->contains(fn (string $hora) => preg_match('/^\d{2}:\d{2}$/', $hora) !== 1)) {
            throw new CambioMovilRechazado('El horario necesita nombre y horas válidas (HH:MM).');
        }

        if (! ($horas['hora_inicio'] < $horas['hora_limite_presente'] && $horas['hora_limite_presente'] < $horas['hora_limite_tardanza'])) {
            throw new CambioMovilRechazado('Las horas del horario deben ir en orden: inicio, presente hasta y tardanza hasta.');
        }

        return tap($horario ?? new HorarioAsistencia, fn (HorarioAsistencia $h) => $h->fill([
            'nombre' => $this->texto($d, 'nombre'),
            ...$horas->all(),
            'activo' => $this->booleano($d, 'activo'),
        ])->save());
    }

    /**
     * Un día por fecha; los días pasados no se cambian para no generar faltas de forma retroactiva.
     *
     * @param  array<string, mixed>  $d
     */
    private function guardarDiaAsistenciaAcopiador(?DiaAsistenciaAcopiador $dia, array $d): DiaAsistenciaAcopiador
    {
        $fecha = CarbonImmutable::parse($this->texto($d, 'fecha'));

        if ($fecha->lt(Asistencia::hoy())) {
            throw new CambioMovilRechazado('No se pueden cambiar días de asistencia pasados.');
        }

        return $dia ?? DiaAsistenciaAcopiador::query()->firstOrCreate(['fecha' => $fecha->toDateString()]);
    }

    /**
     * Asistencia general (sin horario) o marca de un acopiador en un horario. Hay una sola por
     * trabajador, día y horario: si otro dispositivo o la web ya la registró, se actualiza esa.
     *
     * @param  array<string, mixed>  $d
     */
    private function guardarAsistencia(?Asistencia $asistencia, array $d, User $user): Asistencia
    {
        $userId = $this->referencia('usuario', $d['usuario_id'] ?? null);
        $horarioId = filled($d['horario_asistencia_id'] ?? null)
            ? $this->referencia('horario_asistencia', $d['horario_asistencia_id'])
            : null;

        // El acopiador marcó "Asistí" en su teléfono (con la hora a la que lo hizo).
        if ($userId === $user->id && $horarioId !== null && filled($d['hora_marcada'] ?? null)) {
            return $this->marcarAsistenciaPropia($asistencia, $horarioId, $d, $user);
        }

        $this->exigirTomaAsistencia($user);

        $fecha = CarbonImmutable::parse($this->texto($d, 'fecha'));
        $estado = EstadoAsistencia::tryFrom(strtolower($this->texto($d, 'estado')))
            ?? throw new CambioMovilRechazado('El estado de asistencia no es válido.');

        if ($fecha->isAfter(Asistencia::hoy())) {
            throw new CambioMovilRechazado('No se puede tomar asistencia de un día futuro.');
        }

        $asistencia ??= Asistencia::query()
            ->where('user_id', $userId)
            ->whereDate('fecha', $fecha)
            ->when(
                $horarioId,
                fn ($q) => $q->where('horario_asistencia_id', $horarioId),
                fn ($q) => $q->whereNull('horario_asistencia_id'),
            )
            ->first()
            ?? new Asistencia(['user_id' => $userId, 'fecha' => $fecha->toDateString(), 'horario_asistencia_id' => $horarioId]);

        $asistencia->fill(['estado' => $estado, 'registrado_por' => $user->id])->save();

        return $asistencia;
    }

    /**
     * Igual que `marcarAsistencia` de "Asistencia" en la web, pero con la hora en que se marcó en
     * el teléfono (puede llegar después, al recuperar la señal): presente o tardanza según el
     * horario, solo en días de asistencia y mientras el horario estaba abierto.
     *
     * @param  array<string, mixed>  $d
     */
    private function marcarAsistenciaPropia(?Asistencia $asistencia, int $horarioId, array $d, User $user): Asistencia
    {
        if (! $user->esAcopiador()) {
            throw new CambioMovilRechazado('Solo los acopiadores marcan su asistencia por horario.');
        }

        $horario = HorarioAsistencia::query()->find($horarioId)
            ?? throw new CambioMovilRechazado('El horario de asistencia ya no existe.');
        $fecha = CarbonImmutable::parse($this->texto($d, 'fecha'));
        $horaMarcada = substr($this->texto($d, 'hora_marcada'), 0, 8);
        $hora = substr($horaMarcada, 0, 5);

        if ($fecha->isAfter(Asistencia::hoy())) {
            throw new CambioMovilRechazado('No se puede marcar asistencia de un día futuro.');
        }

        if (! DiaAsistenciaAcopiador::esDiaDeAsistencia($fecha->toDateString())) {
            throw new CambioMovilRechazado('Ese día no es día de asistencia de los acopiadores.');
        }

        $estado = $horario->estadoParaHora($hora)
            ?? throw new CambioMovilRechazado("El horario «{$horario->nombre}» no estaba abierto a las {$hora}.");

        $asistencia ??= Asistencia::query()
            ->where('user_id', $user->id)
            ->where('horario_asistencia_id', $horario->id)
            ->whereDate('fecha', $fecha)
            ->first();

        if ($asistencia === null) {
            return Asistencia::create([
                'user_id' => $user->id,
                'fecha' => $fecha->toDateString(),
                'horario_asistencia_id' => $horario->id,
                'estado' => $estado,
                'hora_marcada' => $horaMarcada,
            ]);
        }

        // Solo se reemplaza la falta que se registró sola mientras el teléfono no tenía señal; una
        // marca ya hecha o corregida por quien toma asistencia se queda como está.
        if ($asistencia->hora_marcada === null && $asistencia->registrado_por === null) {
            $asistencia->update(['estado' => $estado, 'hora_marcada' => $horaMarcada]);
        }

        return $asistencia;
    }

    /**
     * El trabajador envía (o vuelve a enviar, si se la rechazaron) la justificación de su falta o
     * tardanza, como en "Asistencia" de la web; quien toma asistencia la acepta o rechaza.
     *
     * @param  array<string, mixed>  $d
     */
    private function guardarJustificacion(?Justificacion $justificacion, array $d, User $user): Justificacion
    {
        $asistencia = Asistencia::query()->with('justificacion')->find($this->referencia('asistencia', $d['asistencia_id'] ?? null))
            ?? throw new CambioMovilRechazado('La asistencia de esta justificación ya no existe.');
        $justificacion ??= $asistencia->justificacion;
        $estado = EstadoJustificacion::tryFrom(strtolower($this->texto($d, 'estado')));

        if ($asistencia->user_id === $user->id && $estado === EstadoJustificacion::Pendiente) {
            // Reenvío de la misma justificación que el teléfono no alcanzó a confirmar.
            if ($justificacion?->estado === EstadoJustificacion::Pendiente && $justificacion->motivo === $this->texto($d, 'motivo')) {
                return $justificacion;
            }

            return $this->enviarJustificacion($asistencia, $justificacion, $d);
        }

        $this->exigirTomaAsistencia($user);

        if ($justificacion === null) {
            throw new CambioMovilRechazado('Las justificaciones las envía el trabajador desde «Asistencia».');
        }

        if ($estado !== null && $estado !== EstadoJustificacion::Pendiente && $estado !== $justificacion->estado) {
            $justificacion->revisar($estado, $user);
        }

        return $justificacion;
    }

    /**
     * El archivo (PDF o imagen) solo se adjunta desde la web: desde el teléfono va el motivo.
     *
     * @param  array<string, mixed>  $d
     */
    private function enviarJustificacion(Asistencia $asistencia, ?Justificacion $anterior, array $d): Justificacion
    {
        $motivo = $this->texto($d, 'motivo');

        if (mb_strlen($motivo) < 10 || mb_strlen($motivo) > 1000) {
            throw new CambioMovilRechazado('El contexto de la justificación debe tener entre 10 y 1000 caracteres.');
        }

        if (! $asistencia->puedeJustificarse()) {
            throw new CambioMovilRechazado('Esta asistencia ya no se puede justificar (el plazo es de una semana).');
        }

        if ($anterior?->archivo) {
            Storage::disk('local')->delete($anterior->archivo);
        }

        return Justificacion::updateOrCreate(['asistencia_id' => $asistencia->id], [
            'motivo' => $motivo,
            'archivo' => null,
            'estado' => EstadoJustificacion::Pendiente,
            'revisado_por' => null,
            'revisado_at' => null,
        ]);
    }

    private function siguienteBoletaLibre(): string
    {
        $numero = Venta::siguienteNumeroBoleta();

        while (Venta::query()->where('numero_boleta', $numero)->exists()) {
            $numero = sprintf('B001-%06d', (int) substr($numero, 5) + 1);
        }

        return $numero;
    }

    /**
     * La app manda los proveedores de la ruta en su orden de recojo (`proveedor_ids`, separados por coma).
     * Un proveedor pertenece a una sola ruta: los que estaban en otra salen de ella.
     *
     * @param  array<string, mixed>  $d
     */
    private function sincronizarProveedoresDeRuta(Ruta $ruta, array $d): void
    {
        if (! array_key_exists('proveedor_ids', $d)) {
            return;
        }

        $orden = collect(explode(',', (string) $d['proveedor_ids']))
            ->filter()
            ->map(fn (string $id) => $this->referenciaOpcional('proveedor', $id))
            ->filter()
            ->unique()
            ->values()
            ->mapWithKeys(fn (int $id, int $indice) => [$id => ['orden' => $indice + 1]]);

        Ruta::quitarProveedorDeOtrasRutas($orden->keys()->all(), $ruta->id);

        $ruta->proveedores()->sync($orden->all());
    }

    private function registroExistente(string $tabla, string $id): ?Model
    {
        $idServidor = $this->idServidor($tabla, $id);

        if ($idServidor === null) {
            return null;
        }

        $registro = self::MODELOS[$tabla]::query()->find($idServidor);

        if ($registro === null && $this->esIdDelServidor($id)) {
            throw new CambioMovilRechazado('El registro ya se eliminó en el servidor.');
        }

        return $registro;
    }

    /**
     * Id de Laravel de un registro de la app: el mismo si ya es numérico, o el que se le asignó al subirlo.
     */
    private function idServidor(string $tabla, ?string $id): ?int
    {
        if ($id === null || $id === '') {
            return null;
        }

        if ($this->esIdDelServidor($id)) {
            return (int) $id;
        }

        $registroId = DB::table('mobile_ids')->where('tabla', $tabla)->where('uuid', $id)->value('registro_id');

        return $registroId !== null ? (int) $registroId : null;
    }

    private function referencia(string $tabla, mixed $id): int
    {
        return $this->referenciaOpcional($tabla, $id)
            ?? throw new CambioMovilRechazado("Falta un dato relacionado ({$tabla}) que todavía no llega al servidor.");
    }

    private function referenciaOpcional(string $tabla, mixed $id): ?int
    {
        return $this->idServidor($tabla, $id !== null ? (string) $id : null);
    }

    private function esIdDelServidor(string $id): bool
    {
        return $id !== '' && ctype_digit($id);
    }

    private function autorizarContexto(string $tabla, string $id, array $datos, User $user): void
    {
        if ($user->puedePantalla('admin.usuarios')) {
            return;
        }
        if (in_array($tabla, ['recojo', 'entrega_planta'], true)) {
            $registro = $this->registroExistente($tabla, $id);
            $idAcopiador = $registro?->acopiador_id ?? $this->referenciaOpcional('acopiador', $datos['acopiador_id'] ?? null);
            if ((int) $idAcopiador !== (int) $user->acopiador?->id || ! $user->acopiador) {
                throw new CambioMovilRechazado('La operación requiere el perfil de acopiador propietario.');
            }
            if (isset($datos['acopiador_id']) && $this->referenciaOpcional('acopiador', $datos['acopiador_id']) !== $user->acopiador->id) {
                throw new CambioMovilRechazado('No puedes trasladar operaciones a otro acopiador.');
            }
        }
        if ($tabla === 'pago_proveedor' && ! $user->puedePantalla('planta.pagos')) {
            $proveedorId = $this->registroExistente($tabla, $id)?->proveedor_id ?? $this->referenciaOpcional('proveedor', $datos['proveedor_id'] ?? null);
            $proveedor = Proveedor::find($proveedorId);
            if (! $user->acopiador || (int) $proveedor?->acopiador_id !== $user->acopiador->id) {
                throw new CambioMovilRechazado('El pago requiere un proveedor del acopiador autenticado.');
            }
        }
        if ($tabla === 'comunicado' && isset($datos['autor_id']) && $this->referenciaOpcional('usuario', $datos['autor_id']) !== $user->id) {
            throw new CambioMovilRechazado('El autor debe ser el usuario autenticado.');
        }
    }

    private function exigirAdministrador(User $user): void
    {
        if (! $user->puedePantalla('admin.usuarios')) {
            throw new CambioMovilRechazado('Solo un administrador puede crear, editar o eliminar usuarios.');
        }
    }

    private function exigirTomaAsistencia(User $user): void
    {
        if (! $user->puedeTomarAsistencia()) {
            throw new CambioMovilRechazado('Solo quien toma asistencia puede registrarla o revisar justificaciones.');
        }
    }

    /**
     * Roles guardados en la app como nombres del enum local ("ADMIN,ACOPIADOR") => valores de Laravel.
     *
     * @return Collection<int, string>
     */
    private function roles(string $roles): Collection
    {
        return collect(explode(',', $roles))
            ->map(fn (string $r) => strtolower(trim($r)))
            ->filter()
            ->map(fn (string $r) => $r === 'admin' ? Rol::Administrador->value : $r)
            ->filter(fn (string $r) => Rol::tryFrom($r) !== null)
            ->unique()
            ->values();
    }

    /**
     * El trazo local es "lat,lng;lat,lng"; Laravel lo guarda como lista de [lat, lng].
     *
     * @return array<int, array{0: float, 1: float}>|null
     */
    private function trazo(mixed $texto): ?array
    {
        $puntos = collect(explode(';', (string) $texto))
            ->map(fn (string $par) => explode(',', $par))
            ->filter(fn (array $p) => count($p) >= 2 && is_numeric(trim($p[0])) && is_numeric(trim($p[1])))
            ->map(fn (array $p) => [(float) trim($p[0]), (float) trim($p[1])])
            ->values()
            ->all();

        return $puntos ?: null;
    }

    /**
     * El ticket del analizador trae la fecha como "06/06/26".
     */
    private function fechaTicket(mixed $fecha): ?string
    {
        if (blank($fecha)) {
            return null;
        }

        $formato = str_contains((string) $fecha, '/') ? 'd/m/y' : 'Y-m-d';

        return Carbon::createFromFormat($formato, (string) $fecha)->toDateString();
    }

    /**
     * @param  array<string, mixed>  $d
     * @param  array<int, string>  $campos
     * @return array<string, string>
     */
    private function solo(array $d, array $campos): array
    {
        return collect($campos)->mapWithKeys(fn (string $campo) => [$campo => $this->texto($d, $campo)])->all();
    }

    /**
     * @param  array<string, mixed>  $d
     */
    private function texto(array $d, string $campo): string
    {
        return trim((string) ($d[$campo] ?? ''));
    }

    /**
     * @param  array<string, mixed>  $d
     */
    private function numero(array $d, string $campo): float
    {
        return (float) ($d[$campo] ?? 0);
    }

    /**
     * @param  array<string, mixed>  $d
     */
    private function numeroONulo(array $d, string $campo): ?float
    {
        return is_numeric($d[$campo] ?? null) ? (float) $d[$campo] : null;
    }

    /**
     * SQLite guarda los booleanos como 0/1.
     *
     * @param  array<string, mixed>  $d
     */
    private function booleano(array $d, string $campo): bool
    {
        return in_array(strtolower((string) ($d[$campo] ?? '')), ['1', 'true'], true);
    }
}

<?php

use App\Enums\EstadoAsistencia;
use App\Enums\EstadoJustificacion;
use App\Enums\EstadoProduccion;
use App\Enums\Rol;
use App\Models\Acopiador;
use App\Models\Asistencia;
use App\Models\Cliente;
use App\Models\Comunicado;
use App\Models\Descuento;
use App\Models\DiaAsistenciaAcopiador;
use App\Models\EntregaPlanta;
use App\Models\HorarioAsistencia;
use App\Models\Insumo;
use App\Models\Justificacion;
use App\Models\Produccion;
use App\Models\Proveedor;
use App\Models\Receta;
use App\Models\RecetaDetalle;
use App\Models\Recojo;
use App\Models\ResultadoLaboratorio;
use App\Models\Ruta;
use App\Models\Turno;
use App\Models\User;
use App\Models\Venta;
use App\Notifications\ComunicadoPublicado;
use Illuminate\Support\Facades\Hash;
use Illuminate\Testing\TestResponse;
use Livewire\Livewire;

/**
 * Inicia sesión desde la app móvil con un usuario del rol indicado y sube los cambios.
 *
 * @param  array<int, array<string, mixed>>  $cambios
 */
beforeEach(fn () => $this->withHeader('X-Huata-Authorization-Protocol', '1'));

function subirCambios(array $cambios, Rol $rol = Rol::Administrador, ?User $user = null): TestResponse
{
    $user ??= User::factory()->rol($rol)->create([
        'usuario' => 'movil'.fake()->unique()->numberBetween(1, 99999),
        'password' => Hash::make('secreto'),
    ]);
    $user->update(['usuario' => $user->usuario ?: 'movil'.$user->id, 'password' => Hash::make('secreto')]);
    $token = test()->postJson('/api/mobile/login', ['usuario' => $user->usuario, 'password' => 'secreto'])->json('token');

    return test()->withToken($token)->postJson('/api/mobile/cambios', ['cambios' => $cambios]);
}

test('sincronización rechaza nuevas ventas Yape no verificadas y vendedores no autorizados', function () {
    $produccion = Produccion::factory()->create(['estado' => EstadoProduccion::Completada, 'cantidad_producida' => 10, 'cantidad_vendida' => 0]);
    $data = ['metodo_pago' => 'yape', 'fecha' => now()->toDateString(), 'total' => '0.01', 'items' => json_encode([['produccion_id' => $produccion->id, 'cantidad' => 1, 'precio_unitario' => '0.01']])];
    subirCambios([guardarFila('venta', '900001', $data)], Rol::Vendedor)->assertOk()->assertJsonCount(1, 'errores');
    subirCambios([guardarFila('venta', '900002', [...$data, 'metodo_pago' => 'plin'])], Rol::Produccion)->assertOk()->assertJsonCount(1, 'errores');
    expect(Venta::count())->toBe(0)->and($produccion->fresh()->cantidadDisponible())->toBe(10.0);
});

/**
 * Cambio tal como lo arma el teléfono: la fila de SQLite con todos los valores como texto.
 *
 * @param  array<string, mixed>  $datos
 * @return array<string, mixed>
 */
function guardarFila(string $tabla, string $id, array $datos): array
{
    return ['tabla' => $tabla, 'id' => $id, 'operacion' => 'guardar', 'datos' => ['id' => $id, ...$datos]];
}

/**
 * @return array<string, mixed>
 */
function filaUsuario(string $usuario, string $rol): array
{
    return [
        'nombres' => 'Rufino',
        'apellidos' => 'Mamani',
        'documento' => fake()->unique()->numerify('########'),
        'celular' => '987654321',
        'rol' => $rol,
        'roles' => '',
        'usuario' => $usuario,
        // Costo 10 y prefijo `$2b$`, como puede llegar del teléfono.
        'password_hash' => str_replace('$2y$', '$2b$', password_hash('clave-movil', PASSWORD_BCRYPT, ['cost' => 10])),
        'activo' => '1',
        'creado_en' => '2026-09-28T10:00:00',
    ];
}

/**
 * Id de Laravel que le tocó a un registro creado en el teléfono.
 */
function idAsignado(TestResponse $response, string $tabla, string $idLocal): int
{
    return (int) collect($response->json('ids'))->first(fn (array $i) => $i['tabla'] === $tabla && $i['id_local'] === $idLocal)['id'];
}

test('lo creado sin señal en el teléfono llega a Laravel con sus relaciones', function () {
    $turno = Turno::factory()->create();

    $response = subirCambios([
        guardarFila('usuario', 'u-acopiador', filaUsuario('rufino', 'ACOPIADOR')),
        guardarFila('acopiador', 'a-1', ['usuario_id' => 'u-acopiador', 'ruta_nombre' => '']),
        guardarFila('usuario', 'u-proveedor', filaUsuario('juana', 'PROVEEDOR')),
        guardarFila('proveedor', 'p-1', [
            'usuario_id' => 'u-proveedor', 'finca_ubicacion' => 'Huata', 'finca_referencia' => '', 'latitud' => '-15.6',
            'longitud' => '-69.9', 'acopiador_id' => 'a-1', 'bloqueado' => '0', 'alteraciones' => '0',
        ]),
        guardarFila('ruta', 'r-1', [
            'acopiador_id' => 'a-1', 'nombre' => 'Ruta norte', 'activa' => '1', 'trazo' => '-15.6,-69.9;-15.61,-69.91', 'proveedor_ids' => 'p-1',
        ]),
        guardarFila('recojo', 'rec-1', [
            'proveedor_id' => 'p-1', 'acopiador_id' => 'a-1', 'turno_id' => (string) $turno->id, 'fecha' => '2026-09-28',
            'litros' => '20.5', 'estado_leche' => 'NORMAL', 'estado_otro' => '', 'precio_litro' => '1.9', 'total' => '38.95',
            'pagado' => '0', 'metodo_pago' => null, 'entregado' => '0', 'sincronizado' => '0',
        ]),
    ])->assertOk()->assertJsonPath('errores', []);

    $acopiador = Acopiador::query()->findOrFail(idAsignado($response, 'acopiador', 'a-1'));
    $proveedor = Proveedor::query()->findOrFail(idAsignado($response, 'proveedor', 'p-1'));
    $ruta = Ruta::query()->findOrFail(idAsignado($response, 'ruta', 'r-1'));
    $recojo = Recojo::query()->findOrFail(idAsignado($response, 'recojo', 'rec-1'));

    expect($acopiador->user_id)->toBe(idAsignado($response, 'usuario', 'u-acopiador'))
        ->and($proveedor->acopiador_id)->toBe($acopiador->id)
        ->and($ruta->proveedores->pluck('id')->all())->toBe([$proveedor->id])
        ->and($ruta->trazo)->toBe([[-15.6, -69.9], [-15.61, -69.91]])
        ->and($recojo->proveedor_id)->toBe($proveedor->id)
        ->and($recojo->estado_leche)->toBe('normal')
        ->and((float) $recojo->litros)->toBe(20.5);

    $this->postJson('/api/mobile/login', ['usuario' => 'rufino', 'password' => 'clave-movil'])->assertOk();
});

test('los cambios y eliminaciones hechos en el teléfono se aplican en Laravel', function () {
    $editado = Descuento::factory()->create(['nombre' => 'Antes']);
    $eliminado = Descuento::factory()->create();

    subirCambios([
        guardarFila('descuento', (string) $editado->id, ['nombre' => 'Mayorista', 'porcentaje' => '12.5', 'activo' => '0']),
        ['tabla' => 'descuento', 'id' => (string) $eliminado->id, 'operacion' => 'eliminar'],
    ])->assertOk()->assertJsonPath('errores', []);

    expect($editado->fresh())->nombre->toBe('Mayorista')->activo->toBeFalse()
        ->and(Descuento::query()->find($eliminado->id))->toBeNull();
});

test('reenviar un registro nuevo lo actualiza en vez de duplicarlo', function () {
    $fila = guardarFila('descuento', 'd-1', ['nombre' => 'Feria', 'porcentaje' => '5', 'activo' => '1']);

    $primero = idAsignado(subirCambios([$fila]), 'descuento', 'd-1');
    $fila['datos']['nombre'] = 'Feria de domingo';
    $segundo = idAsignado(subirCambios([$fila]), 'descuento', 'd-1');

    expect($segundo)->toBe($primero)
        ->and(Descuento::query()->where('nombre', 'like', 'Feria%')->count())->toBe(1)
        ->and(Descuento::query()->find($primero)->nombre)->toBe('Feria de domingo');
});

test('una compra registrada en el teléfono suma al stock del insumo', function () {
    $insumo = Insumo::factory()->create(['stock_actual' => 10, 'costo_unitario' => 2]);

    subirCambios([
        guardarFila('compra', 'c-1', ['fecha' => '2026-09-28', 'proveedor_insumo' => '', 'descripcion' => 'Compra de sal', 'total' => '30']),
        guardarFila('compra_detalle', 'cd-1', [
            'compra_id' => 'c-1', 'insumo_id' => (string) $insumo->id, 'cantidad' => '10', 'costo_unitario' => '3', 'subtotal' => '30',
        ]),
    ], Rol::Produccion)->assertOk()->assertJsonPath('errores', []);

    expect((float) $insumo->fresh()->stock_actual)->toBe(20.0)
        ->and((float) $insumo->fresh()->costo_unitario)->toBe(2.5);
});

test('un cambio rechazado se informa sin frenar a los demás', function () {
    $response = subirCambios([
        guardarFila('usuario', 'u-1', filaUsuario('sin-permiso', 'VENDEDOR')),
        guardarFila('cliente', 'c-1', ['nombres' => 'Feria', 'apellidos' => 'Prueba', 'documento' => '12345678']),
    ], Rol::Vendedor)->assertOk();

    expect($response->json('errores'))->toHaveCount(1)
        ->and($response->json('errores.0.tabla'))->toBe('usuario')
        ->and(User::query()->where('usuario', 'sin-permiso')->exists())->toBeFalse()
        ->and(Cliente::query()->where('nombres', 'Feria')->exists())->toBeTrue();
});

test('subir cambios exige el token de la app', function () {
    $this->postJson('/api/mobile/cambios', ['cambios' => []])->assertUnauthorized();
});

/**
 * Receta de 2 de sal por unidad, con 100 de sal en stock.
 *
 * @return array{0: Receta, 1: Insumo}
 */
function recetaConSal(): array
{
    $sal = Insumo::factory()->create(['stock_actual' => 100, 'costo_unitario' => 1.5]);
    $receta = Receta::factory()->create(['nombre' => 'Queso fresco']);
    RecetaDetalle::factory()->create(['receta_id' => $receta->id, 'insumo_id' => $sal->id, 'cantidad' => 2]);

    return [$receta, $sal];
}

test('una producción hecha en el teléfono descuenta los insumos y se publica en Laravel', function () {
    [$receta, $sal] = recetaConSal();
    $fila = guardarFila('produccion', 'pr-1', [
        'fecha' => '2026-10-20', 'receta_id' => (string) $receta->id, 'producto_id' => '', 'cantidad_lotes' => '10',
        'cantidad_producida' => '10', 'observaciones' => '', 'turno_id' => '', 'estado' => 'PLANIFICADA',
        'nombre' => 'Queso fresco', 'lote' => 'L-202610200800-01', 'cantidad_vendida' => '0', 'responsable_id' => '',
    ]);

    $id = idAsignado(subirCambios([$fila], Rol::Produccion)->assertJsonPath('errores', []), 'produccion', 'pr-1');

    expect(Produccion::query()->find($id))
        ->estado->toBe(EstadoProduccion::Planificada)
        ->lote->toBe('L-202610200800-01')
        ->and((float) $sal->fresh()->stock_actual)->toBe(80.0);

    $fila['datos']['estado'] = 'COMPLETADA';
    subirCambios([$fila], Rol::Produccion)->assertJsonPath('errores', []);

    expect(Produccion::query()->find($id)->estado)->toBe(EstadoProduccion::Completada)
        ->and((float) $sal->fresh()->stock_actual)->toBe(80.0);
});

test('una venta hecha sin señal llega con sus ítems y descuenta lo vendido de la producción', function () {
    [$receta] = recetaConSal();
    $produccion = Produccion::factory()->create([
        'receta_id' => $receta->id, 'cantidad_producida' => 10, 'estado' => EstadoProduccion::Completada,
    ]);
    $fila = guardarFila('venta', 'v-1', [
        'numero_boleta' => 'B001-000001', 'cliente_id' => '', 'cliente_nombre_libre' => 'Rosa', 'cliente_doc_libre' => '',
        'fecha' => '2026-10-20T09:30:00', 'subtotal' => '9', 'descuento' => '0', 'descuento_tipo' => '', 'total' => '9',
        'metodo_pago' => 'EFECTIVO', 'efectivo_recibido' => '10', 'vuelto' => '1', 'vendedor_id' => '',
        'items' => json_encode([[
            'produccion_id' => (string) $produccion->id, 'nombre' => 'Queso fresco', 'precio_unitario' => 3, 'cantidad' => 3, 'subtotal' => 9,
        ]]),
    ]);

    $id = idAsignado(subirCambios([$fila], Rol::Vendedor)->assertJsonPath('errores', []), 'venta', 'v-1');
    subirCambios([$fila], Rol::Vendedor)->assertJsonPath('errores', []);

    $venta = Venta::with('items')->find($id);

    expect(Venta::query()->count())->toBe(1)
        ->and($venta->metodo_pago)->toBe('efectivo')
        ->and($venta->fecha->toDateString())->toBe('2026-10-20')
        ->and($venta->items)->toHaveCount(1)
        ->and($venta->items->first()->produccion_id)->toBe($produccion->id)
        ->and((float) $produccion->fresh()->cantidad_vendida)->toBe(3.0);
});

test('una producción con ventas no se elimina desde el teléfono', function () {
    $produccion = Produccion::factory()->create(['cantidad_producida' => 5, 'cantidad_vendida' => 2]);

    $response = subirCambios([['tabla' => 'produccion', 'id' => (string) $produccion->id, 'operacion' => 'eliminar']], Rol::Produccion);

    expect($response->json('errores'))->toHaveCount(1)
        ->and(Produccion::query()->find($produccion->id))->not->toBeNull();
});

test('el stock de un insumo solo se pisa cuando se corrigió a mano en el teléfono', function () {
    $insumo = Insumo::factory()->create(['stock_actual' => 50]);
    $fila = fn (string $editado) => guardarFila('insumo', (string) $insumo->id, [
        'nombre' => $insumo->nombre, 'unidad_medida_id' => (string) $insumo->unidad_medida_id, 'stock_minimo' => '0',
        'costo_unitario' => '1', 'stock_actual' => '12', 'stock_inicial' => '0', 'stock_editado' => $editado,
    ]);

    subirCambios([$fila('0')], Rol::Produccion)->assertJsonPath('errores', []);
    expect((float) $insumo->fresh()->stock_actual)->toBe(50.0);

    subirCambios([$fila('1')], Rol::Produccion)->assertJsonPath('errores', []);
    expect((float) $insumo->fresh()->stock_actual)->toBe(12.0);
});

test('una entrega a planta hecha en el teléfono llega a Laravel y planta la ve', function () {
    $acopiador = Acopiador::factory()->create();
    $turno = Turno::factory()->create();
    $proveedor = Proveedor::factory()->create(['acopiador_id' => $acopiador->id]);
    $recojo = Recojo::factory()->create([
        'proveedor_id' => $proveedor->id, 'acopiador_id' => $acopiador->id, 'turno_id' => $turno->id,
        'fecha' => now()->toDateString(), 'litros' => 37.5, 'precio_litro' => 1.8, 'total' => 67.5, 'entregado' => false,
    ]);

    subirCambios([
        guardarFila('entrega_planta', 'e-1', [
            'acopiador_id' => (string) $acopiador->id, 'turno_id' => (string) $turno->id, 'fecha' => now()->toDateString(),
            'litros_total' => '37.5', 'total_pagado' => '0', 'total_deuda' => '67.5', 'proveedores_no_recogidos' => '0',
            'guia' => 'G-20261020093000',
        ]),
        guardarFila('recojo', (string) $recojo->id, [
            'proveedor_id' => (string) $proveedor->id, 'acopiador_id' => (string) $acopiador->id, 'turno_id' => (string) $turno->id,
            'fecha' => now()->toDateString(), 'litros' => '37.5', 'estado_leche' => 'NORMAL', 'estado_otro' => '',
            'precio_litro' => '1.8', 'total' => '67.5', 'pagado' => '0', 'metodo_pago' => '', 'entregado' => '1',
        ]),
    ], Rol::Acopiador, $acopiador->user)->assertOk()->assertJsonPath('errores', []);

    expect(EntregaPlanta::query()->where('guia', 'G-20261020093000')->first())
        ->acopiador_id->toBe($acopiador->id)
        ->and((float) EntregaPlanta::query()->where('guia', 'G-20261020093000')->value('litros_total'))->toBe(37.5)
        ->and($recojo->fresh()->entregado)->toBeTrue();

    $this->actingAs(User::factory()->rol(Rol::PersonalPlanta)->create());
    Livewire::test('pages::planta.entregas')->assertSee('37.50 L');
});

test('la asistencia tomada en el teléfono llega a Laravel sin duplicar la que ya estaba registrada', function () {
    $trabajador = User::factory()->rol(Rol::Vendedor)->create();
    $otro = User::factory()->rol(Rol::Produccion)->create();
    $existente = Asistencia::factory()->create(['user_id' => $otro->id]);
    $hoy = Asistencia::hoy()->toDateString();

    subirCambios([
        guardarFila('asistencia', 'a-1', ['usuario_id' => (string) $trabajador->id, 'fecha' => $hoy, 'horario_asistencia_id' => null, 'estado' => 'tardanza']),
        guardarFila('asistencia', 'a-2', ['usuario_id' => (string) $otro->id, 'fecha' => $hoy, 'horario_asistencia_id' => null, 'estado' => 'falta']),
    ])->assertOk()->assertJsonPath('errores', []);

    expect(Asistencia::query()->where('user_id', $trabajador->id)->value('estado'))->toBe(EstadoAsistencia::Tardanza)
        ->and(Asistencia::query()->where('user_id', $otro->id)->count())->toBe(1)
        ->and($existente->fresh()->estado)->toBe(EstadoAsistencia::Falta);
});

test('solo quien toma asistencia la registra desde el teléfono', function () {
    $trabajador = User::factory()->rol(Rol::Vendedor)->create();

    subirCambios([
        guardarFila('asistencia', 'a-1', ['usuario_id' => (string) $trabajador->id, 'fecha' => Asistencia::hoy()->toDateString(), 'estado' => 'presente']),
    ], Rol::Vendedor)->assertOk()->assertJsonCount(1, 'errores');

    expect(Asistencia::query()->count())->toBe(0);
});

test('una justificación se acepta desde el teléfono', function () {
    $justificacion = Justificacion::factory()->create();

    subirCambios([
        guardarFila('justificacion', (string) $justificacion->id, ['asistencia_id' => (string) $justificacion->asistencia_id, 'estado' => 'ACEPTADA']),
    ])->assertOk()->assertJsonPath('errores', []);

    expect($justificacion->fresh())
        ->estado->toBe(EstadoJustificacion::Aceptada)
        ->revisado_por->not->toBeNull();
});

test('eliminar un horario desde el teléfono borra sus marcas y los días pasados no se cambian', function () {
    $horario = HorarioAsistencia::factory()->create();
    Asistencia::factory()->create(['horario_asistencia_id' => $horario->id]);
    $ayer = DiaAsistenciaAcopiador::factory()->create(['fecha' => Asistencia::hoy()->subDay()->toDateString()]);

    subirCambios([
        ['tabla' => 'horario_asistencia', 'id' => (string) $horario->id, 'operacion' => 'eliminar'],
        ['tabla' => 'dia_asistencia_acopiador', 'id' => (string) $ayer->id, 'operacion' => 'eliminar'],
        guardarFila('dia_asistencia_acopiador', 'd-1', ['fecha' => Asistencia::hoy()->addDay()->toDateString()]),
    ])->assertOk()->assertJsonCount(1, 'errores');

    expect(HorarioAsistencia::query()->count())->toBe(0)
        ->and(Asistencia::query()->count())->toBe(0)
        ->and($ayer->fresh())->not->toBeNull()
        ->and(DiaAsistenciaAcopiador::query()->count())->toBe(2);
});

test('la cola móvil no recrea el rol auxiliar ni concede asistencia con un flag legado', function () {
    $trabajador = User::factory()->rol(Rol::Vendedor)->create();

    subirCambios([
        guardarFila('usuario', (string) $trabajador->id, [
            ...filaUsuario($trabajador->usuario, 'VENDEDOR'),
            'documento' => (string) $trabajador->documento,
            'password_hash' => '',
            'toma_asistencia' => '1',
        ]),
    ])->assertOk()->assertJsonCount(1, 'errores');

    expect($trabajador->fresh()->toma_asistencia)->toBeFalse()->and($trabajador->puedePantalla('asistencia.tomar'))->toBeFalse();
});

/**
 * Sube cambios con el token de un usuario ya creado (para cuando los datos llevan su id).
 *
 * @param  array<int, array<string, mixed>>  $cambios
 */
function subirCambiosComo(User $user, array $cambios): TestResponse
{
    $user->update(['password' => Hash::make('secreto')]);
    $token = test()->postJson('/api/mobile/login', ['usuario' => $user->usuario, 'password' => 'secreto'])->json('token');

    return test()->withToken($token)->postJson('/api/mobile/cambios', ['cambios' => $cambios]);
}

test('el acopiador marca su asistencia desde el teléfono y el estado sale de la hora del horario', function () {
    $acopiador = User::factory()->rol(Rol::Acopiador)->create();
    $horario = HorarioAsistencia::factory()->create();
    $hoy = Asistencia::hoy()->toDateString();
    DiaAsistenciaAcopiador::factory()->create(['fecha' => $hoy]);

    subirCambiosComo($acopiador, [
        guardarFila('asistencia', 'm-1', [
            'usuario_id' => (string) $acopiador->id, 'fecha' => $hoy, 'horario_asistencia_id' => (string) $horario->id,
            'estado' => 'PRESENTE', 'hora_marcada' => '05:15:30',
        ]),
        guardarFila('asistencia', 'm-2', [
            'usuario_id' => (string) $acopiador->id, 'fecha' => $hoy, 'horario_asistencia_id' => (string) $horario->id,
            'estado' => 'PRESENTE', 'hora_marcada' => '05:08:00',
        ]),
    ])->assertOk()->assertJsonPath('errores', []);

    expect(Asistencia::query()->where('user_id', $acopiador->id)->count())->toBe(1)
        ->and(Asistencia::query()->where('user_id', $acopiador->id)->first())
        ->estado->toBe(EstadoAsistencia::Tardanza)
        ->hora_marcada->toBe('05:15:30');
});

test('la marca del acopiador fuera del horario o en un día sin asistencia se rechaza', function () {
    $acopiador = User::factory()->rol(Rol::Acopiador)->create();
    $horario = HorarioAsistencia::factory()->create();
    $hoy = Asistencia::hoy()->toDateString();

    subirCambiosComo($acopiador, [
        guardarFila('asistencia', 'm-1', [
            'usuario_id' => (string) $acopiador->id, 'fecha' => $hoy, 'horario_asistencia_id' => (string) $horario->id,
            'estado' => 'PRESENTE', 'hora_marcada' => '05:05:00',
        ]),
    ])->assertOk()->assertJsonCount(1, 'errores');

    DiaAsistenciaAcopiador::factory()->create(['fecha' => $hoy]);

    subirCambiosComo($acopiador, [
        guardarFila('asistencia', 'm-2', [
            'usuario_id' => (string) $acopiador->id, 'fecha' => $hoy, 'horario_asistencia_id' => (string) $horario->id,
            'estado' => 'PRESENTE', 'hora_marcada' => '09:00:00',
        ]),
    ])->assertOk()->assertJsonCount(1, 'errores');

    expect(Asistencia::query()->count())->toBe(0);
});

test('la marca hecha sin señal reemplaza la falta que se registró sola', function () {
    $acopiador = User::factory()->rol(Rol::Acopiador)->create();
    $horario = HorarioAsistencia::factory()->create();
    $hoy = Asistencia::hoy()->toDateString();
    DiaAsistenciaAcopiador::factory()->create(['fecha' => $hoy]);
    $falta = Asistencia::factory()->falta()->create(['user_id' => $acopiador->id, 'horario_asistencia_id' => $horario->id]);

    subirCambiosComo($acopiador, [
        guardarFila('asistencia', 'm-1', [
            'usuario_id' => (string) $acopiador->id, 'fecha' => $hoy, 'horario_asistencia_id' => (string) $horario->id,
            'estado' => 'PRESENTE', 'hora_marcada' => '05:03:00',
        ]),
    ])->assertOk()->assertJsonPath('errores', []);

    expect($falta->fresh()->estado)->toBe(EstadoAsistencia::Presente);
});

test('el trabajador justifica su falta desde el teléfono y puede volver a hacerlo si se la rechazan', function () {
    $acopiador = User::factory()->rol(Rol::Acopiador)->create();
    $falta = Asistencia::factory()->falta()->create(['user_id' => $acopiador->id]);

    subirCambiosComo($acopiador, [
        guardarFila('justificacion', 'j-1', [
            'asistencia_id' => (string) $falta->id, 'motivo' => 'Estuve enfermo con fiebre.', 'tiene_archivo' => '0', 'estado' => 'PENDIENTE',
        ]),
    ])->assertOk()->assertJsonPath('errores', []);

    $justificacion = Justificacion::query()->where('asistencia_id', $falta->id)->sole();
    $justificacion->update(['estado' => EstadoJustificacion::Rechazada]);

    subirCambiosComo($acopiador, [
        guardarFila('justificacion', (string) $justificacion->id, [
            'asistencia_id' => (string) $falta->id, 'motivo' => 'Adjunto la constancia en la web.', 'tiene_archivo' => '0', 'estado' => 'PENDIENTE',
        ]),
        guardarFila('justificacion', 'j-2', [
            'asistencia_id' => (string) Asistencia::factory()->falta()->create()->id, 'motivo' => 'No es mi asistencia.', 'estado' => 'PENDIENTE',
        ]),
    ])->assertOk()->assertJsonCount(1, 'errores');

    expect($justificacion->fresh())
        ->estado->toBe(EstadoJustificacion::Pendiente)
        ->motivo->toBe('Adjunto la constancia en la web.');
});

test('enviar a producción desde el teléfono suma la leche al stock una sola vez', function () {
    $resultado = ResultadoLaboratorio::factory()->create(['litros' => 40, 'conforme' => true]);
    $fila = guardarFila('resultado_laboratorio', (string) $resultado->id, [
        'proveedor_id' => (string) $resultado->proveedor_id, 'fecha' => $resultado->fecha->toDateString(), 'turno_id' => null,
        'resultado' => 'CONFORME', 'observaciones' => '', 'conforme' => '1', 'litros' => '40', 'enviado_produccion' => '1',
    ]);

    subirCambios([$fila], Rol::PersonalPlanta)->assertOk()->assertJsonPath('errores', []);
    subirCambios([$fila], Rol::PersonalPlanta)->assertOk()->assertJsonPath('errores', []);

    expect($resultado->fresh()->enviado_produccion)->toBeTrue()
        ->and((float) Insumo::deLeche()->stock_actual)->toBe(40.0);
});

test('limpiar el historial desde el teléfono elimina la boleta como en la web y solo lo hace el vendedor', function () {
    $venta = Venta::factory()->create();
    $otra = Venta::factory()->create();

    subirCambios([['tabla' => 'venta', 'id' => (string) $otra->id, 'operacion' => 'eliminar']], Rol::Produccion)
        ->assertOk()->assertJsonCount(1, 'errores');
    subirCambios([['tabla' => 'venta', 'id' => (string) $venta->id, 'operacion' => 'eliminar']], Rol::Vendedor)
        ->assertOk()->assertJsonPath('errores', []);

    expect(Venta::query()->whereKey($venta->id)->exists())->toBeFalse()
        ->and(Venta::query()->whereKey($otra->id)->exists())->toBeTrue();
});

test('el teléfono baja las notificaciones de su usuario y las marca como leídas', function () {
    $user = User::factory()->rol(Rol::Proveedor)->create(['usuario' => 'prov-notif']);
    $otro = User::factory()->rol(Rol::Proveedor)->create();
    $comunicado = Comunicado::factory()->create();
    $user->notify(new ComunicadoPublicado($comunicado));
    $otro->notify(new ComunicadoPublicado($comunicado));

    $user->update(['password' => Hash::make('secreto')]);
    $token = $this->postJson('/api/mobile/login', ['usuario' => 'prov-notif', 'password' => 'secreto'])->json('token');
    $sync = $this->withToken($token)->getJson('/api/mobile/sync')->assertOk();

    expect($sync->json('notificaciones'))->toHaveCount(1)
        ->and($sync->json('notificaciones.0.tipo'))->toBe('ComunicadoPublicado')
        ->and($sync->json('notificaciones.0.leida'))->toBeFalse();

    $id = $sync->json('notificaciones.0.id');
    $ajena = $otro->notifications()->first()->id;

    $this->withToken($token)->postJson('/api/mobile/cambios', ['cambios' => [
        ['tabla' => 'notificacion', 'id' => $id, 'operacion' => 'guardar', 'datos' => ['id' => $id, 'leida' => '1']],
        ['tabla' => 'notificacion', 'id' => $ajena, 'operacion' => 'guardar', 'datos' => ['id' => $ajena, 'leida' => '1']],
    ]])->assertOk()->assertJsonPath('ids', [])->assertJsonPath('errores', []);

    expect($user->unreadNotifications()->count())->toBe(0)
        ->and($otro->unreadNotifications()->count())->toBe(1);
});

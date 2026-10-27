<?php

use App\Authorization\Distribuciones;
use App\Enums\EstadoProduccion;
use App\Enums\Rol;
use App\Models\Acopiador;
use App\Models\Asistencia;
use App\Models\Descuento;
use App\Models\DiaAsistenciaAcopiador;
use App\Models\DistribucionRoles;
use App\Models\HorarioAsistencia;
use App\Models\Insumo;
use App\Models\Justificacion;
use App\Models\Produccion;
use App\Models\Proveedor;
use App\Models\Recojo;
use App\Models\Ruta;
use App\Models\Turno;
use App\Models\User;
use App\Models\Venta;
use App\Models\VentaItem;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Illuminate\Testing\TestResponse;

/**
 * Inicia sesión como lo hace la app móvil y devuelve el token con el que pide el sync.
 */
function tokenMovil(): string
{
    $user = User::factory()->rol(Rol::Administrador)->create([
        'usuario' => 'movil'.fake()->unique()->numberBetween(1, 99999),
        'password' => Hash::make('secreto'),
    ]);

    // Fixture explícita con los accesos históricos de producción para probar el volcado completo.
    $principal = User::findOrFail(DB::table('authorization_state')->where('id', 1)->value('administrador_protegido'));
    Distribuciones::confirmar($principal, Distribuciones::previsualizar($principal, 'aplicar', DistribucionRoles::where('codigo', 'en-desarrollo')->value('id'))['token'], true);

    return test()->postJson('/api/mobile/login', ['usuario' => $user->usuario, 'password' => 'secreto'])
        ->assertOk()
        ->json('token');
}

function syncMovil(string $query = ''): TestResponse
{
    return test()->withToken(tokenMovil())->getJson('/api/mobile/sync'.$query);
}

test('el endpoint de sync sin `since` devuelve un volcado completo de las tablas que la app móvil necesita', function () {
    $acopiador = Acopiador::factory()->create();
    $proveedor = Proveedor::factory()->create(['acopiador_id' => $acopiador->id]);
    $turno = Turno::factory()->create();
    Recojo::factory()->create([
        'proveedor_id' => $proveedor->id,
        'acopiador_id' => $acopiador->id,
        'turno_id' => $turno->id,
    ]);

    $response = syncMovil()->assertOk();

    $response->assertJsonStructure([
        'servidor_hora',
        'empresa', 'precio_leche', 'usuarios', 'acopiadores', 'proveedores',
        'turnos', 'turnos_produccion', 'rutas', 'clientes', 'unidades_medida',
        'insumos', 'recetas', 'receta_detalles', 'comunicados', 'recojos',
        'resultados_laboratorio', 'entregas_planta', 'pagos_proveedor', 'compras', 'compra_detalles',
        'ids',
    ]);

    expect($response->json('acopiadores'))->toHaveCount(1)
        ->and($response->json('proveedores'))->toHaveCount(1)
        ->and($response->json('recojos'))->toHaveCount(1)
        ->and($response->json('recojos.0.id'))->toBeString()
        ->and($response->json('recojos.0.acopiador_id'))->toBe((string) $acopiador->id);
});

test('el endpoint de sync rechaza teléfonos sin token o con un token inválido', function () {
    $this->getJson('/api/mobile/sync')->assertUnauthorized();
    $this->withToken('no-existe')->getJson('/api/mobile/sync')->assertUnauthorized();
});

test('el token deja de servir si el usuario se desactiva', function () {
    $user = User::factory()->rol(Rol::Acopiador)->create(['usuario' => 'rufino', 'password' => Hash::make('secreto')]);
    $token = $this->postJson('/api/mobile/login', ['usuario' => 'rufino', 'password' => 'secreto'])->json('token');

    $user->update(['activo' => false]);

    $this->withToken($token)->getJson('/api/mobile/sync')->assertUnauthorized();
});

test('el login móvil entrega un token y guarda solo su hash', function () {
    User::factory()->rol(Rol::Vendedor)->create(['usuario' => 'rosa', 'password' => Hash::make('secreto')]);

    $token = $this->postJson('/api/mobile/login', ['usuario' => 'rosa', 'password' => 'secreto'])
        ->assertOk()
        ->json('token');

    expect($token)->toBeString()->not->toBeEmpty();
    $this->assertDatabaseHas('mobile_tokens', ['token' => hash('sha256', $token)]);
    $this->assertDatabaseMissing('mobile_tokens', ['token' => $token]);
});

test('el endpoint de sync entrega solo el hash del usuario autenticado para su login sin señal', function () {
    $own = User::factory()->rol(Rol::Vendedor)->create(['usuario' => 'propio', 'password' => Hash::make('clave-propia')]);
    $other = User::factory()->rol(Rol::Vendedor)->create(['password' => Hash::make('clave-ajena')]);
    $token = $this->postJson('/api/mobile/login', ['usuario' => 'propio', 'password' => 'clave-propia'])->assertOk()->json('token');
    $users = collect($this->withToken($token)->getJson('/api/mobile/sync')->assertOk()->json('usuarios'));
    expect(Hash::check('clave-propia', $users->firstWhere('id', (string) $own->id)['password_hash']))->toBeTrue()
        ->and($users->firstWhere('id', (string) $other->id))->toBeNull();
});

test('el endpoint de sync con `since` en el futuro no devuelve filas que ya existían', function () {
    Acopiador::factory()->create();

    $response = syncMovil('?since='.urlencode(now()->addDay()->toIso8601String()))->assertOk();

    expect($response->json('acopiadores'))->toBeEmpty();
});

test('el endpoint de sync con `since` en el pasado sí devuelve filas existentes', function () {
    Acopiador::factory()->create();

    $response = syncMovil('?since='.urlencode(now()->subDay()->toIso8601String()))->assertOk();

    expect($response->json('acopiadores'))->not->toBeEmpty();
});

test('una fila guardada en el mismo segundo que la última sincronización vuelve a bajar', function () {
    $this->freezeSecond();
    $servidorHora = syncMovil()->json('servidor_hora');

    Acopiador::factory()->create();

    $response = syncMovil('?since='.urlencode($servidorHora))->assertOk();

    expect($response->json('acopiadores'))->toHaveCount(1);
});

test('el endpoint de sync lista los ids vigentes para que el teléfono borre lo eliminado en Laravel', function () {
    $queda = Descuento::factory()->create();
    $borrado = Descuento::factory()->create();
    $servidorHora = syncMovil()->json('servidor_hora');

    $borrado->delete();

    $response = syncMovil('?since='.urlencode($servidorHora))->assertOk();

    expect($response->json('ids.descuento'))->toBe([(string) $queda->id]);
});

test('los cambios de proveedores de una ruta llegan aunque la ruta no se haya editado', function () {
    $ruta = Ruta::factory()->create();
    $proveedor = Proveedor::factory()->create();
    $servidorHora = syncMovil()->json('servidor_hora');

    $this->travel(5)->minutes();
    $ruta->agregarProveedor($proveedor->id);

    $response = syncMovil('?since='.urlencode($servidorHora))->assertOk();
    $rutaSync = collect($response->json('rutas'))->firstWhere('id', (string) $ruta->id);

    expect($rutaSync['proveedor_ids'])->toBe([(string) $proveedor->id]);
});

test('el sync baja las producciones, las ventas con sus ítems y el stock real de los insumos', function () {
    $insumo = Insumo::factory()->create(['stock_actual' => 42.5]);
    $produccion = Produccion::factory()->create(['cantidad_producida' => 8, 'estado' => EstadoProduccion::Completada]);
    $venta = Venta::factory()->create();
    VentaItem::factory()->create(['venta_id' => $venta->id, 'produccion_id' => $produccion->id, 'cantidad' => 2]);

    $response = syncMovil()->assertOk();
    $produccionSync = collect($response->json('producciones'))->firstWhere('id', (string) $produccion->id);

    expect(collect($response->json('insumos'))->firstWhere('id', (string) $insumo->id)['stock_actual'])->toEqual(42.5)
        ->and($produccionSync['estado'])->toBe('completada')
        ->and($produccionSync['cantidad_vendida'])->toEqual(2)
        ->and($response->json('ventas.0.items.0.produccion_id'))->toBe((string) $produccion->id)
        ->and($response->json('ids.venta'))->toBe([(string) $venta->id]);
});

test('el sync baja quién toma asistencia, los horarios, los días de acopiadores, las asistencias y las justificaciones', function () {
    $trabajador = User::factory()->rol(Rol::Vendedor)->create(['toma_asistencia' => true]);
    $horario = HorarioAsistencia::factory()->create();
    $dia = DiaAsistenciaAcopiador::factory()->create(['fecha' => Asistencia::hoy()->toDateString()]);
    $asistencia = Asistencia::factory()->falta()->create(['user_id' => $trabajador->id]);
    $justificacion = Justificacion::factory()->create(['asistencia_id' => $asistencia->id]);

    $response = syncMovil()->assertOk();

    expect(collect($response->json('usuarios'))->firstWhere('id', (string) $trabajador->id)['toma_asistencia'])->toBeTrue()
        ->and($response->json('horarios_asistencia.0'))->toMatchArray([
            'id' => (string) $horario->id, 'hora_inicio' => '05:00', 'hora_limite_presente' => '05:10', 'hora_limite_tardanza' => '05:20',
        ])
        ->and($response->json('dias_asistencia_acopiador.0.fecha'))->toBe($dia->fecha->toDateString())
        ->and(collect($response->json('asistencias'))->firstWhere('id', (string) $asistencia->id))->toMatchArray([
            'usuario_id' => (string) $trabajador->id, 'estado' => 'falta', 'horario_asistencia_id' => null,
        ])
        ->and($response->json('justificaciones.0'))->toMatchArray([
            'asistencia_id' => (string) $asistencia->id, 'estado' => 'pendiente', 'tiene_archivo' => false,
        ])
        ->and($response->json('ids.justificacion'))->toBe([(string) $justificacion->id]);
});

test('el sync baja los litros que entregó el proveedor y el litraje que llegó', function () {
    $recojo = Recojo::factory()->create(['litros' => 18.5, 'litros_entregados' => 20]);

    $recojoSync = collect(syncMovil()->assertOk()->json('recojos'))->firstWhere('id', (string) $recojo->id);

    expect($recojoSync['litros'])->toEqual(18.5)
        ->and($recojoSync['litros_entregados'])->toEqual(20);
});

<?php

use App\Authorization\Pantallas;
use App\Enums\Rol;
use App\Models\{Cliente, RolDinamico, User, UnidadMedida, Proveedor};
use Livewire\Livewire;

function adminOrdenAltas(): User
{
    $user = User::factory()->rol(Rol::Administrador)->create();
    RolDinamico::where('codigo', 'administrador')->update(['pantallas' => array_keys(Pantallas::todas())]);
    return $user;
}

test('crear y editar cliente actualiza listado paginado sin perder filtros ni mover alta anterior', function () {
    $this->actingAs(adminOrdenAltas());
    $old = Cliente::factory()->create(['nombres' => 'Orden Antiguo', 'compras_count' => 0, 'created_at' => now()->subDays(2)]);
    Cliente::factory()->count(11)->create(['nombres' => 'Orden Fila', 'compras_count' => 0, 'created_at' => now()->subDay()]);
    Cliente::factory()->create(['nombres' => 'Orden Excluido', 'compras_count' => 2]);
    $c = Livewire::test('pages::vendedor.clientes')->set('busqueda', 'Orden')->set('filtros.compras', 'sin')->call('setPage', 2)
        ->call('nuevo')->set('nombres', 'Orden Nuevo')->set('apellidos', 'Prueba')->set('documento', '43210987')->call('guardar')->assertHasNoErrors()
        ->assertSet('paginators.page', 1)->assertSet('busqueda', 'Orden')->assertSet('filtros.compras', 'sin');
    expect($c->viewData('clientes')->pluck('compras_count')->unique()->all())->toBe([0]);
    $nuevo = Cliente::where('documento', '43210987')->firstOrFail();
    expect($c->viewData('clientes')->first()->id)->toBe($nuevo->id);
    $fecha = $old->created_at->toISOString();
    $c->call('editar', $old->id)->set('nombres', 'Orden Editado')->call('guardar')->assertHasNoErrors();
    expect($c->viewData('clientes')->first()->id)->toBe($nuevo->id)
        ->and($old->fresh()->created_at->toISOString())->toBe($fecha)
        ->and($old->fresh()->nombres)->toBe('Orden Editado')
        ->and(Cliente::count())->toBe(14);
});

test('usuarios tienen orden por alta y desempate estable al recorrer paginas con busqueda', function () {
    $this->actingAs(adminOrdenAltas());
    $rows = User::factory()->count(13)->rol(Rol::Proveedor)->create(['nombres' => 'Orden Mismo', 'created_at' => now()->subDay()]);
    $recent = User::factory()->rol(Rol::Proveedor)->create(['nombres' => 'Orden Reciente', 'created_at' => now()]);
    $ids = [$recent->id, ...$rows->sortByDesc('id')->pluck('id')->all()];
    $c = Livewire::test('pages::admin.usuarios')->set('busqueda', 'Orden');
    $first = $c->viewData('usuarios')->pluck('id')->all();
    $c->call('setPage', 2);
    $second = $c->viewData('usuarios')->pluck('id')->all();
    expect([...$first, ...$second])->toBe($ids)->and(array_unique([...$first, ...$second]))->toHaveCount(14);
});

test('unidades se ordenan por created_at y no por nombre identificador o ultima edicion', function () {
    $this->actingAs(adminOrdenAltas());
    $newer = UnidadMedida::factory()->create(['nombre' => 'Zeta reciente', 'created_at' => now()->subHour()]);
    $older = UnidadMedida::factory()->create(['nombre' => 'Alfa anterior', 'created_at' => now()->subDay()]);
    $c = Livewire::test('pages::produccion.unidades');
    expect($c->viewData('unidades')->pluck('id')->all())->toBe([$newer->id, $older->id]);
    $older->update(['nombre' => 'Anterior editado']);
    $c->call('$refresh');
    expect($c->viewData('unidades')->first()->id)->toBe($newer->id);
});

test('sync entrega fechas reales en incrementales y solo para filas autorizadas', function () {
    $owner = Proveedor::factory()->create();
    $other = Proveedor::factory()->create();
    $user = $owner->user;
    $user->update(['password' => 'solo-prueba']);
    $token = $this->postJson('/api/mobile/login', ['usuario' => $user->usuario, 'password' => 'solo-prueba'])->assertOk()->json('token');
    $response = $this->withToken($token)->getJson('/api/mobile/sync?since='.urlencode(now()->addDay()->toIso8601String()))->assertOk();
    expect($response->json('proveedores'))->toBeEmpty();
    $altas = collect($response->json('altas'))->where('tabla', 'proveedor');
    expect($altas->pluck('registro_id')->all())->toBe([(string) $owner->id])
        ->and($altas->first()['creado_en'])->toBe($owner->created_at->utc()->format('Y-m-d\TH:i:s.v\Z'));
    expect($altas->contains('registro_id', (string) $other->id))->toBeFalse();
});

test('contrato de altas permite a Kotlin ordenar registros web por su fecha real', function () {
    $admin = adminOrdenAltas();
    $admin->update(['usuario' => 'orden_contrato', 'password' => 'solo-prueba']);
    UnidadMedida::factory()->create(['nombre' => 'Zeta creada después', 'created_at' => now()->setDate(2026, 10, 27)]);
    UnidadMedida::factory()->create(['nombre' => 'Alfa creada antes', 'created_at' => now()->setDate(2026, 10, 26)]);
    $token = $this->postJson('/api/mobile/login', ['usuario' => 'orden_contrato', 'password' => 'solo-prueba'])->assertOk()->json('token');
    $data = $this->withToken($token)->getJson('/api/mobile/sync')->assertOk()->json();
    expect(collect($data['altas'])->where('tabla', 'unidad_medida'))->toHaveCount(2);
    if ($path = getenv('HUATA_EXPORT_ORDEN_FIXTURE')) {
        foreach ($data['usuarios'] as &$u) $u['password_hash'] = '';
        unset($u);
        file_put_contents($path, json_encode($data, JSON_PRETTY_PRINT|JSON_UNESCAPED_UNICODE));
    }
});

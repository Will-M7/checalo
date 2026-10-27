<?php

use App\Authorization\Distribuciones;
use App\Enums\EstadoProduccion;
use App\Enums\Rol;
use App\Enums\TeamRole;
use App\Models\DistribucionRoles;
use App\Models\Insumo;
use App\Models\Produccion;
use App\Models\Receta;
use App\Models\Team;
use App\Models\UnidadMedida;
use App\Models\User;
use App\Models\Venta;
use Livewire\Livewire;

function usuarioAdmin(): User
{
    $user = User::factory()->rol(Rol::Administrador)->create();
    // Esta prueba comprueba operaciones de la distribución histórica, no el nuevo valor Inicial.
    Distribuciones::confirmar($user, Distribuciones::previsualizar($user, 'aplicar', DistribucionRoles::where('codigo', 'en-desarrollo')->value('id'))['token'], true);
    $team = Team::principal();

    $team->members()->attach($user, ['role' => TeamRole::Owner->value]);
    $user->switchTeam($team);

    return $user->fresh();
}

test('el sidebar del administrador solo muestra sus propios módulos, sin los de los demás roles', function () {
    $secciones = Rol::Administrador->navegacionAgrupada();

    expect($secciones)->not->toBeNull()
        ->and(array_keys($secciones))->toEqual(['General', 'Gestión', 'Operaciones', 'Configuración']);

    // Agrupar el menú no debe quitar ningún acceso del administrador.
    $enSecciones = collect($secciones)->flatten(1)->pluck('route')->sort()->values()->all();
    $propios = collect(Rol::Administrador->navegacion())->pluck('route')->sort()->values()->all();
    expect($enSecciones)->toEqual($propios);

    foreach ($secciones as $items) {
        foreach ($items as $item) {
            expect($item)->toHaveKeys(['route', 'label', 'icon']);
        }
    }
});

test('los demás roles no tienen navegación agrupada (usan la lista plana de siempre)', function () {
    expect(Rol::Vendedor->navegacionAgrupada())->toBeNull()
        ->and(Rol::Produccion->navegacionAgrupada())->toBeNull();
});

test('el administrador puede registrar una venta desde el módulo de producción y ver la boleta', function () {
    $admin = usuarioAdmin();
    $unidad = UnidadMedida::factory()->create();
    $insumo = Insumo::factory()->create(['unidad_medida_id' => $unidad->id, 'costo_unitario' => 2, 'stock_actual' => 1000]);
    $receta = Receta::factory()->create();
    $receta->detalles()->create(['insumo_id' => $insumo->id, 'cantidad' => 1, 'unidad_medida_id' => $unidad->id]);

    $produccion = Produccion::factory()->create(['receta_id' => $receta->id, 'cantidad_producida' => 10, 'estado' => EstadoProduccion::Completada]);

    $this->actingAs($admin);

    Livewire::test('pages::vendedor.ventas')
        ->call('agregar', $produccion->id)
        ->set('metodoPago', 'efectivo')
        ->set('efectivoRecibido', 10)
        ->call('guardar')
        ->assertHasNoErrors();

    $venta = Venta::latest('id')->first();

    expect($venta->vendedor_id)->toBe($admin->id);

    $this->get(route('vendedor.ventas.boleta', $venta->id))->assertOk();
});

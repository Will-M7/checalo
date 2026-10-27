<?php

use App\Authorization\Autorizacion;
use App\Enums\EstadoAsistencia;
use App\Enums\EstadoJustificacion;
use App\Enums\Rol;
use App\Models\Asistencia;
use App\Models\DiaAsistenciaAcopiador;
use App\Models\HorarioAsistencia;
use App\Models\Justificacion;
use App\Models\RolDinamico;
use App\Models\User;
use Carbon\CarbonImmutable;
use Illuminate\Database\Eloquent\ModelNotFoundException;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\Storage;
use Livewire\Livewire;

test('el administrador toma asistencia: por defecto todos presentes y guarda tardanzas y faltas', function () {
    $admin = User::factory()->rol(Rol::Administrador)->create();
    $tarde = User::factory()->rol(Rol::Vendedor)->create();
    $ausente = User::factory()->rol(Rol::PersonalPlanta)->create();

    $this->actingAs($admin);

    Livewire::test('pages::asistencia.tomar')
        ->call('nuevo')
        ->assertSet("estados.{$admin->id}", EstadoAsistencia::Presente->value)
        ->set("estados.{$tarde->id}", EstadoAsistencia::Tardanza->value)
        ->set("estados.{$ausente->id}", EstadoAsistencia::Falta->value)
        ->call('guardar')
        ->assertHasNoErrors()
        ->assertSet('editando', false);

    expect(Asistencia::whereDate('fecha', Asistencia::hoy())->count())->toBe(3)
        ->and(Asistencia::where('user_id', $tarde->id)->first()->estado)->toBe(EstadoAsistencia::Tardanza)
        ->and(Asistencia::where('user_id', $ausente->id)->first()->estado)->toBe(EstadoAsistencia::Falta);
});

test('editar la asistencia de un día actualiza los registros sin duplicarlos', function () {
    $admin = User::factory()->rol(Rol::Administrador)->create();
    $trabajador = User::factory()->rol(Rol::Vendedor)->create();
    $ayer = Asistencia::hoy()->subDay()->toDateString();

    Asistencia::factory()->falta()->create(['user_id' => $trabajador->id, 'fecha' => $ayer]);

    $this->actingAs($admin);

    Livewire::test('pages::asistencia.tomar')
        ->call('seleccionarFecha', $ayer)
        ->call('nuevo')
        ->assertSet('editando', false)
        ->call('editar')
        ->set("estados.{$trabajador->id}", EstadoAsistencia::Presente->value)
        ->call('guardar');

    expect(Asistencia::where('user_id', $trabajador->id)->count())->toBe(1)
        ->and(Asistencia::where('user_id', $trabajador->id)->first()->estado)->toBe(EstadoAsistencia::Presente);
});

test('no se puede tomar asistencia de un día futuro', function () {
    $this->actingAs(User::factory()->rol(Rol::Administrador)->create());

    Livewire::test('pages::asistencia.tomar')
        ->call('seleccionarFecha', Asistencia::hoy()->addDay()->toDateString())
        ->assertSet('fecha', Asistencia::hoy()->toDateString());
});

test('solo el administrador o quien tenga el módulo asignado puede tomar asistencia', function () {
    $admin = User::factory()->rol(Rol::Administrador)->create();
    $trabajador = User::factory()->rol(Rol::Vendedor)->create();

    $this->actingAs($trabajador)->get(route('asistencia.tomar'))->assertForbidden();
    $this->actingAs($trabajador)->get(route('asistencia.mis'))->assertOk();

    $this->actingAs($admin);
    Autorizacion::guardarRol($admin, null, 'Responsable de turno', 'responsable_turno', true, ['asistencia.tomar']);
    Livewire::test('pages::admin.asignar-roles')
        ->call('abrirAsignar', $trabajador->id)
        ->set('rolesSeleccionados', ['vendedor', 'responsable_turno'])
        ->call('guardar')
        ->assertHasNoErrors();

    $trabajador->refresh();

    expect($trabajador->toma_asistencia)->toBeFalse()
        ->and(collect($trabajador->navegacionDeRol(Rol::Vendedor))->pluck('route'))
        ->toContain('asistencia.tomar')
        ->not->toContain('asistencia.mis');

    $this->actingAs($trabajador)->get(route('asistencia.tomar'))->assertOk();
});

test('el trabajador justifica una falta con un archivo dentro de la semana', function () {
    Storage::fake('local');
    $trabajador = User::factory()->rol(Rol::Vendedor)->create();
    $falta = Asistencia::factory()->falta()->create(['user_id' => $trabajador->id, 'fecha' => Asistencia::hoy()->subDays(3)]);

    $this->actingAs($trabajador);

    Livewire::test('pages::asistencia.mis')
        ->call('abrirJustificar', $falta->id)
        ->set('motivo', 'Tuve una cita médica en el hospital de Puno.')
        ->set('archivo', UploadedFile::fake()->create('constancia.pdf', 200, 'application/pdf'))
        ->call('justificar')
        ->assertHasNoErrors();

    $justificacion = $falta->justificacion()->first();

    expect($justificacion->estado)->toBe(EstadoJustificacion::Pendiente);
    Storage::disk('local')->assertExists($justificacion->archivo);
});

test('pasada una semana la falta ya no se puede justificar', function () {
    $trabajador = User::factory()->rol(Rol::Vendedor)->create();
    $falta = Asistencia::factory()->falta()->create(['user_id' => $trabajador->id, 'fecha' => Asistencia::hoy()->subDays(8)]);

    $this->actingAs($trabajador);

    Livewire::test('pages::asistencia.mis')
        ->set('asistenciaId', $falta->id)
        ->set('motivo', 'Tuve una cita médica en el hospital de Puno.')
        ->call('justificar')
        ->assertHasErrors('motivo');

    expect($falta->justificacion()->exists())->toBeFalse()
        ->and($falta->fresh()->puedeJustificarse())->toBeFalse();
});

test('un trabajador no puede justificar la asistencia de otro', function () {
    $trabajador = User::factory()->rol(Rol::Vendedor)->create();
    $ajena = Asistencia::factory()->falta()->create();

    $this->actingAs($trabajador);

    expect(fn () => Livewire::test('pages::asistencia.mis')->call('abrirJustificar', $ajena->id))
        ->toThrow(ModelNotFoundException::class);
});

test('quien toma asistencia acepta o rechaza justificaciones', function () {
    $admin = User::factory()->rol(Rol::Administrador)->create();
    $aceptar = Justificacion::factory()->create();
    $rechazar = Justificacion::factory()->create();

    $this->actingAs($admin);

    Livewire::test('pages::asistencia.tomar')
        ->set('pestana', 'justificaciones')
        ->assertSee($aceptar->asistencia->user->nombreCompleto())
        ->call('aceptar', $aceptar->id)
        ->call('rechazar', $rechazar->id);

    expect($aceptar->fresh()->estado)->toBe(EstadoJustificacion::Aceptada)
        ->and($aceptar->fresh()->revisado_por)->toBe($admin->id)
        ->and($rechazar->fresh()->estado)->toBe(EstadoJustificacion::Rechazada);
});

test('el archivo de una justificación solo lo ven su dueño y quien toma asistencia', function () {
    Storage::fake('local');
    $ruta = UploadedFile::fake()->image('receta.jpg')->store('justificaciones', 'local');
    $justificacion = Justificacion::factory()->create(['archivo' => $ruta]);
    $duenio = $justificacion->asistencia->user;

    $urlPara = fn (User $user) => route('asistencia.justificacion.archivo', [
        'current_team' => $user->currentTeam->slug,
        'justificacion' => $justificacion,
    ]);
    $otro = User::factory()->rol(Rol::Vendedor)->create();
    $admin = User::factory()->rol(Rol::Administrador)->create();

    $this->actingAs($otro)->get($urlPara($otro))->assertForbidden();
    $this->actingAs($duenio)->get($urlPara($duenio))->assertOk();
    $this->actingAs($admin)->get($urlPara($admin))->assertOk();
});

test('a los acopiadores y a los proveedores no se les toma asistencia en la tabla general', function () {
    $admin = User::factory()->rol(Rol::Administrador)->create();
    $vendedor = User::factory()->rol(Rol::Vendedor)->create();
    $acopiador = User::factory()->rol(Rol::Acopiador)->create();
    $proveedor = User::factory()->rol(Rol::Proveedor)->create();

    $this->actingAs($admin);

    $estados = Livewire::test('pages::asistencia.tomar')->call('nuevo')->get('estados');

    expect(array_keys($estados))->toContain($admin->id, $vendedor->id)
        ->not->toContain($acopiador->id)
        ->not->toContain($proveedor->id)
        ->and(collect(Rol::Proveedor->navegacion())->pluck('route'))->not->toContain('asistencia.mis');
});

test('el administrador crea y edita horarios de los acopiadores', function () {
    $this->actingAs(User::factory()->rol(Rol::Administrador)->create());

    Livewire::test('pages::asistencia.tomar')
        ->call('nuevoHorario')
        ->set('horarioNombre', 'Mañana')
        ->set('horaInicio', '05:00')
        ->set('horaLimitePresente', '04:50')
        ->set('horaLimiteTardanza', '05:20')
        ->call('guardarHorario')
        ->assertHasErrors('horaLimitePresente')
        ->set('horaLimitePresente', '05:10')
        ->call('guardarHorario')
        ->assertHasNoErrors();

    $horario = HorarioAsistencia::sole();

    Livewire::test('pages::asistencia.tomar')
        ->call('editarHorario', $horario->id)
        ->assertSet('horaLimitePresente', '05:10')
        ->set('horaLimiteTardanza', '05:25')
        ->call('guardarHorario');

    expect($horario->fresh()->limiteTardanza())->toBe('05:25');
});

test('el acopiador marca presente o tardanza según la hora del horario', function (string $hora, EstadoAsistencia $esperado) {
    $horario = HorarioAsistencia::factory()->create();
    DiaAsistenciaAcopiador::factory()->create(['fecha' => '2026-10-16']);
    $acopiador = User::factory()->rol(Rol::Acopiador)->create();
    $this->travelTo(CarbonImmutable::parse("2026-10-16 {$hora}", 'America/Lima'));

    $this->actingAs($acopiador);

    Livewire::test('pages::asistencia.mis')->call('marcarAsistencia')->call('marcarAsistencia');

    $marcas = Asistencia::where('user_id', $acopiador->id)->get();

    expect($marcas)->toHaveCount(1)
        ->and($marcas->first()->estado)->toBe($esperado)
        ->and($marcas->first()->horario_asistencia_id)->toBe($horario->id);
})->with([
    'a tiempo' => ['05:10', EstadoAsistencia::Presente],
    'tarde' => ['05:11', EstadoAsistencia::Tardanza],
]);

test('fuera del horario el acopiador no puede marcar', function () {
    HorarioAsistencia::factory()->create();
    $acopiador = User::factory()->rol(Rol::Acopiador)->create();
    $this->travelTo(CarbonImmutable::parse('2026-10-16 04:59', 'America/Lima'));

    $this->actingAs($acopiador);
    Livewire::test('pages::asistencia.mis')->call('marcarAsistencia');

    expect(Asistencia::where('user_id', $acopiador->id)->exists())->toBeFalse();

    $vendedor = User::factory()->rol(Rol::Vendedor)->create();
    $this->actingAs($vendedor);
    Livewire::test('pages::asistencia.mis')->call('marcarAsistencia')->assertForbidden();
});

test('si el acopiador no marca hasta el final del horario queda con falta', function () {
    $this->travelTo(CarbonImmutable::parse('2026-10-16 04:00', 'America/Lima'));
    $horario = HorarioAsistencia::factory()->create();
    DiaAsistenciaAcopiador::factory()->create(['fecha' => '2026-10-16']);
    $puntual = User::factory()->rol(Rol::Acopiador)->create();
    $ausente = User::factory()->rol(Rol::Acopiador)->create();

    $this->travelTo(CarbonImmutable::parse('2026-10-16 05:05', 'America/Lima'));
    $this->actingAs($puntual);
    Livewire::test('pages::asistencia.mis')->call('marcarAsistencia');

    expect(HorarioAsistencia::registrarFaltasVencidas())->toBe(0);

    $this->travelTo(CarbonImmutable::parse('2026-10-16 05:21', 'America/Lima'));
    $this->artisan('asistencia:registrar-faltas-acopiadores')->assertSuccessful();

    expect(Asistencia::where('user_id', $ausente->id)->where('horario_asistencia_id', $horario->id)->sole()->estado)
        ->toBe(EstadoAsistencia::Falta)
        ->and(Asistencia::where('user_id', $puntual->id)->sole()->estado)->toBe(EstadoAsistencia::Presente)
        ->and(HorarioAsistencia::registrarFaltasVencidas())->toBe(0);
});

test('el administrador corrige la asistencia de un acopiador', function () {
    $horario = HorarioAsistencia::factory()->create();
    $acopiador = User::factory()->rol(Rol::Acopiador)->create();
    $this->travelTo(CarbonImmutable::parse('2026-10-16 04:00', 'America/Lima'));

    $this->actingAs(User::factory()->rol(Rol::Administrador)->create());

    Livewire::test('pages::asistencia.tomar')
        ->set('pestana', 'acopiadores')
        ->assertSee($acopiador->nombreCompleto())
        ->call('editarAcopiador', $acopiador->id)
        ->set("estadosAcopiador.{$horario->id}", EstadoAsistencia::Tardanza->value)
        ->call('guardarAcopiador')
        ->assertHasNoErrors();

    expect(Asistencia::where('user_id', $acopiador->id)->sole()->estado)->toBe(EstadoAsistencia::Tardanza);
});

test('el acopiador que toma asistencia conserva su asistencia personal en el menú', function () {
    $admin = User::factory()->rol(Rol::Administrador)->create();
    $acopiador = User::factory()->rol(Rol::Acopiador)->create();
    $rol = RolDinamico::where('codigo', 'acopiador')->firstOrFail();
    Autorizacion::guardarRol($admin, $rol->id, $rol->nombre, $rol->codigo, true, [...$rol->pantallas, 'asistencia.tomar']);

    $rutas = collect($acopiador->navegacionDeRol(Rol::Acopiador))->pluck('route');

    expect($rutas)->toContain('asistencia.tomar', 'asistencia.mis')
        ->and($rutas->search('asistencia.tomar'))->toBeLessThan($rutas->search('asistencia.mis'));
});

test('al eliminar un horario se borran también las marcas de ese horario', function () {
    $horario = HorarioAsistencia::factory()->create();
    $otro = HorarioAsistencia::factory()->tarde()->create();
    $marca = Asistencia::factory()->create(['horario_asistencia_id' => $horario->id]);
    $marcaOtro = Asistencia::factory()->create(['horario_asistencia_id' => $otro->id]);
    Justificacion::factory()->create(['asistencia_id' => $marca->id]);

    $this->actingAs(User::factory()->rol(Rol::Administrador)->create());

    Livewire::test('pages::asistencia.tomar')
        ->call('confirmarEliminarHorario', $horario->id)
        ->call('eliminarHorario');

    expect(HorarioAsistencia::find($horario->id))->toBeNull()
        ->and(Asistencia::find($marca->id))->toBeNull()
        ->and(Justificacion::count())->toBe(0)
        ->and(Asistencia::find($marcaOtro->id))->not->toBeNull();
});

test('el acopiador no marca ni recibe falta en un día que no es de asistencia', function () {
    $this->travelTo(CarbonImmutable::parse('2026-10-15 04:00', 'America/Lima'));
    HorarioAsistencia::factory()->create();
    $acopiador = User::factory()->rol(Rol::Acopiador)->create();
    DiaAsistenciaAcopiador::factory()->create(['fecha' => '2026-10-16']);

    $this->travelTo(CarbonImmutable::parse('2026-10-15 05:05', 'America/Lima'));
    $this->actingAs($acopiador);
    Livewire::test('pages::asistencia.mis')->call('marcarAsistencia');

    expect(Asistencia::where('user_id', $acopiador->id)->exists())->toBeFalse();

    $this->travelTo(CarbonImmutable::parse('2026-10-16 06:00', 'America/Lima'));

    expect(HorarioAsistencia::registrarFaltasVencidas())->toBe(1)
        ->and(Asistencia::where('user_id', $acopiador->id)->sole()->fecha->toDateString())->toBe('2026-10-16');
});

test('el administrador agrega y quita días de asistencia de los acopiadores, salvo días pasados', function () {
    $this->travelTo(CarbonImmutable::parse('2026-10-16 10:00', 'America/Lima'));
    DiaAsistenciaAcopiador::factory()->create(['fecha' => '2026-10-15']);
    $this->actingAs(User::factory()->rol(Rol::Administrador)->create());

    $pagina = Livewire::test('pages::asistencia.tomar')
        ->set('pestana', 'acopiadores')
        ->call('alternarDiaAcopiador', '2026-10-20');

    expect(DiaAsistenciaAcopiador::esDiaDeAsistencia('2026-10-20'))->toBeTrue();

    $pagina->call('alternarDiaAcopiador', '2026-10-20')->call('alternarDiaAcopiador', '2026-10-15');

    expect(DiaAsistenciaAcopiador::esDiaDeAsistencia('2026-10-20'))->toBeFalse()
        ->and(DiaAsistenciaAcopiador::esDiaDeAsistencia('2026-10-15'))->toBeTrue();
});

test('el administrador selecciona de lunes a sábado los días restantes del mes', function () {
    $this->travelTo(CarbonImmutable::parse('2026-10-16 10:00', 'America/Lima'));
    $this->actingAs(User::factory()->rol(Rol::Administrador)->create());

    Livewire::test('pages::asistencia.tomar')->call('seleccionarDiasDelMes', 'laborables');

    $dias = DiaAsistenciaAcopiador::query()->orderBy('fecha')->get()->map(fn ($d) => $d->fecha->toDateString());

    expect($dias->first())->toBe('2026-10-16')
        ->and($dias->last())->toBe('2026-10-31')
        ->and($dias)->toHaveCount(14)
        ->not->toContain('2026-10-18', '2026-10-25');
});

test('solo quien toma asistencia elige los días de los acopiadores', function () {
    $this->actingAs(User::factory()->rol(Rol::Vendedor)->create());

    Livewire::test('pages::asistencia.tomar')->assertForbidden();

    expect(DiaAsistenciaAcopiador::exists())->toBeFalse();
});

test('la pestaña de acopiadores muestra si el día es de asistencia y las marcas del día', function () {
    $this->travelTo(CarbonImmutable::parse('2026-10-16 10:00', 'America/Lima'));
    $horario = HorarioAsistencia::factory()->create();
    $acopiador = User::factory()->rol(Rol::Acopiador)->create();
    DiaAsistenciaAcopiador::factory()->create(['fecha' => '2026-10-16']);
    Asistencia::factory()->create([
        'user_id' => $acopiador->id,
        'fecha' => '2026-10-16',
        'horario_asistencia_id' => $horario->id,
        'estado' => EstadoAsistencia::Tardanza,
    ]);
    $this->actingAs(User::factory()->rol(Rol::Administrador)->create());

    Livewire::test('pages::asistencia.tomar')
        ->set('pestana', 'acopiadores')
        ->assertSee('Día de asistencia')
        ->assertSee($acopiador->nombreCompleto())
        ->assertSee(EstadoAsistencia::Tardanza->label())
        ->call('cambiarDia', -1)
        ->assertSee('No se toma asistencia este día')
        ->call('cambiarMesAcopiadores', -1)
        ->assertSet('mesAcopiadores', '2026-09')
        ->assertHasNoErrors();
});

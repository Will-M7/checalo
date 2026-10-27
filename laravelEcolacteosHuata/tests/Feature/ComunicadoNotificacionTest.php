<?php

use App\Enums\Rol;
use App\Models\User;
use App\Notifications\ComunicadoPublicado;
use Illuminate\Support\Facades\Notification;
use Livewire\Livewire;

test('publicar un comunicado para todos los roles notifica a los usuarios activos, excepto al autor', function () {
    Notification::fake();

    $autor = User::factory()->rol(Rol::PersonalPlanta)->create();
    $proveedor = User::factory()->rol(Rol::Proveedor)->create();
    $inactivo = User::factory()->rol(Rol::Vendedor)->create(['activo' => false]);

    $this->actingAs($autor);

    Livewire::test('pages::planta.comunicados')
        ->set('titulo', 'Aviso general')
        ->set('descripcion', 'Detalle del aviso')
        ->call('publicar')
        ->assertHasNoErrors();

    Notification::assertSentTo($proveedor, ComunicadoPublicado::class);
    Notification::assertNotSentTo($autor, ComunicadoPublicado::class);
    Notification::assertNotSentTo($inactivo, ComunicadoPublicado::class);
});

test('publicar un comunicado dirigido a un rol específico solo notifica a ese rol', function () {
    Notification::fake();

    $autor = User::factory()->rol(Rol::PersonalPlanta)->create();
    $proveedor = User::factory()->rol(Rol::Proveedor)->create();
    $vendedor = User::factory()->rol(Rol::Vendedor)->create();

    $this->actingAs($autor);

    Livewire::test('pages::planta.comunicados')
        ->set('titulo', 'Aviso para proveedores')
        ->set('descripcion', 'Detalle')
        ->set('paraTodos', false)
        ->set('rolesDestino', [Rol::Proveedor->value])
        ->call('publicar')
        ->assertHasNoErrors();

    Notification::assertSentTo($proveedor, ComunicadoPublicado::class);
    Notification::assertNotSentTo($vendedor, ComunicadoPublicado::class);
});

test('un comunicado dirigido a un rol también notifica a usuarios que tienen ese rol como secundario', function () {
    Notification::fake();

    $autor = User::factory()->rol(Rol::PersonalPlanta)->create();

    // Su rol principal es Vendedor, pero también tiene el rol Acopiador asignado.
    $multiRol = User::factory()->rol(Rol::Vendedor)->create([
        'roles' => [Rol::Vendedor->value, Rol::Acopiador->value],
    ]);

    $this->actingAs($autor);

    Livewire::test('pages::planta.comunicados')
        ->set('titulo', 'Aviso para acopiadores')
        ->set('descripcion', 'Detalle')
        ->set('paraTodos', false)
        ->set('rolesDestino', [Rol::Acopiador->value])
        ->call('publicar')
        ->assertHasNoErrors();

    Notification::assertSentTo($multiRol, ComunicadoPublicado::class);
});

test('el destinatario recibe la notificación con título y mensaje listos para la campanita', function () {
    $autor = User::factory()->rol(Rol::PersonalPlanta)->create();
    $proveedor = User::factory()->rol(Rol::Proveedor)->create();

    $this->actingAs($autor);

    Livewire::test('pages::planta.comunicados')
        ->set('tipo', 'asesoramiento')
        ->set('titulo', 'Buenas prácticas')
        ->set('descripcion', 'Lava el equipo de ordeño antes y después de cada uso.')
        ->call('publicar');

    $notificacion = $proveedor->fresh()->unreadNotifications()->first();

    expect($notificacion)->not->toBeNull()
        ->and($notificacion->data['titulo'])->toBe('Asesoramiento: Buenas prácticas')
        ->and($notificacion->data['mensaje'])->toContain('Lava el equipo');
});

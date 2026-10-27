<?php

use App\Enums\Rol;
use App\Models\User;

test('guests are redirected to the login page', function () {
    // No `current_team` is available for a guest, so hit the concrete path directly
    // rather than resolving the named route (which requires that parameter).
    $response = $this->get('/some-team/dashboard');
    $response->assertRedirect(route('login'));
});

test('authenticated users are redirected to their role panel', function () {
    $user = User::factory()->rol(Rol::PersonalPlanta)->create();

    $response = $this->actingAs($user)->get(route('dashboard'));

    $response->assertRedirect(route('planta.panel'));
});

test('a user without a role is forbidden', function () {
    $user = User::factory()->create(['rol' => null]);

    $response = $this->actingAs($user)->get(route('dashboard'));

    $response->assertForbidden();
});

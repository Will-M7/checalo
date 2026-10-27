<?php

use App\Models\User;

test('email verification is disabled: the verification routes do not exist', function () {
    $user = User::factory()->create();

    $this->actingAs($user)->get('/verify-email')->assertNotFound();
});

test('accounts are created with a verified email and never blocked on it', function () {
    $user = User::factory()->create();

    expect($user->fresh()->email_verified_at)->not->toBeNull();
});

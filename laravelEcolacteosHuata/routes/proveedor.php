<?php

use App\Http\Middleware\EnsureTeamMembership;
use Illuminate\Support\Facades\Route;

Route::prefix('{current_team}/proveedor')
    ->middleware(['auth:web', EnsureTeamMembership::class, 'role:proveedor'])
    ->name('proveedor.')
    ->group(function () {
        Route::livewire('panel', 'pages::proveedor.panel')->name('panel');
        Route::livewire('entregas', 'pages::proveedor.entregas')->name('entregas');
        Route::livewire('laboratorio', 'pages::proveedor.laboratorio')->name('laboratorio');
        Route::livewire('comunicados', 'pages::proveedor.comunicados')->name('comunicados');
    });

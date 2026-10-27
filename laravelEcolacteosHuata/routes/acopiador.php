<?php

use App\Http\Controllers\AcopiadorInventarioPdfController;
use App\Http\Middleware\EnsureTeamMembership;
use Illuminate\Support\Facades\Route;

Route::prefix('{current_team}/acopiador')
    ->middleware(['auth:web', EnsureTeamMembership::class, 'role:acopiador'])
    ->name('acopiador.')
    ->group(function () {
        Route::livewire('panel', 'pages::acopiador.panel')->name('panel');
        Route::livewire('recojo', 'pages::acopiador.recojo')->name('recojo');
        Route::livewire('mapa-recojo', 'pages::acopiador.mapa-recojo')->name('mapa-recojo');
        Route::livewire('entrega', 'pages::acopiador.entrega')->name('entrega');
        Route::livewire('inventario', 'pages::acopiador.inventario')->name('inventario');
        Route::get('inventario/pdf', AcopiadorInventarioPdfController::class)->name('inventario.pdf');
    });

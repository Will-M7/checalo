<?php

use App\Http\Controllers\ResultadoLaboratorioPdfController;
use App\Http\Middleware\EnsureTeamMembership;
use Illuminate\Support\Facades\Route;

Route::prefix('{current_team}/planta')
    ->middleware(['auth:web', EnsureTeamMembership::class, 'role:personal_planta,administrador'])
    ->name('planta.')
    ->group(function () {
        Route::livewire('panel', 'pages::planta.panel')->name('panel');
        Route::livewire('entregas', 'pages::planta.entregas')->name('entregas');
        Route::livewire('laboratorio', 'pages::planta.laboratorio')->name('laboratorio');
        Route::get('laboratorio/{resultado}/pdf', ResultadoLaboratorioPdfController::class)->name('laboratorio.pdf');
        Route::livewire('rutas', 'pages::planta.rutas')->name('rutas');
        Route::livewire('pagos', 'pages::planta.pagos')->name('pagos');
        Route::livewire('comunicados', 'pages::planta.comunicados')->name('comunicados');
    });

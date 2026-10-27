<?php

use App\Http\Controllers\JustificacionArchivoController;
use App\Http\Middleware\EnsureTeamMembership;
use Illuminate\Support\Facades\Route;

/*
 * Asistencia es común a todos los roles: "Tomar asistencia" es del administrador o de quien él
 * se lo asigne; "Asistencia" (mis asistencias) es la vista personal de cada trabajador.
 */
Route::prefix('{current_team}/asistencia')
    ->middleware(['auth:web', EnsureTeamMembership::class])
    ->name('asistencia.')
    ->group(function () {
        Route::livewire('tomar', 'pages::asistencia.tomar')->middleware('can:tomar-asistencia')->name('tomar');
        Route::livewire('mis-asistencias', 'pages::asistencia.mis')->middleware('role:')->name('mis');
        Route::get('justificaciones/{justificacion}/archivo', JustificacionArchivoController::class)->name('justificacion.archivo');
    });

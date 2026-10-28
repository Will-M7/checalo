<?php

use App\Http\Middleware\EnsureTeamMembership;
use Illuminate\Support\Facades\Route;

Route::prefix('{current_team}/admin')
    ->middleware(['auth:web', EnsureTeamMembership::class, 'role:administrador'])
    ->name('admin.')
    ->group(function () {
        Route::livewire('receptores-yape', 'pages::admin.receptores-yape')->name('receptores-yape');
        Route::livewire('finanzas', 'pages::admin.finanzas')->name('finanzas');
        Route::livewire('roles', 'pages::admin.roles')->name('roles');
        Route::livewire('panel', 'pages::admin.panel')->name('panel');
        Route::livewire('usuarios', 'pages::admin.usuarios')->name('usuarios');
        Route::livewire('asignar-roles', 'pages::admin.asignar-roles')->name('asignar-roles');
        Route::livewire('empresa', 'pages::admin.empresa')->name('empresa');
        Route::livewire('descuentos', 'pages::admin.descuentos')->name('descuentos');
        Route::livewire('turnos', 'pages::admin.turnos')->name('turnos');
        Route::livewire('precios', 'pages::admin.precios')->name('precios');
        Route::livewire('acopiador-recojo', 'pages::admin.acopiador-recojo')->name('acopiador-recojo');
        Route::livewire('acopiador-entrega', 'pages::admin.acopiador-entrega')->name('acopiador-entrega');
        Route::livewire('proveedor-entregas', 'pages::admin.proveedor-entregas')->name('proveedor-entregas');
        Route::livewire('proveedor-laboratorio', 'pages::admin.proveedor-laboratorio')->name('proveedor-laboratorio');
    });

<?php

use App\Http\Middleware\EnsureTeamMembership;
use Illuminate\Support\Facades\Route;

Route::prefix('{current_team}/produccion')
    ->middleware(['auth:web', EnsureTeamMembership::class, 'role:produccion,administrador'])
    ->name('produccion.')
    ->group(function () {
        Route::livewire('panel', 'pages::produccion.panel')->name('panel');
        Route::livewire('insumos', 'pages::produccion.insumos')->name('insumos');
        Route::livewire('unidades', 'pages::produccion.unidades')->name('unidades');
        Route::livewire('recetas', 'pages::produccion.recetas')->name('recetas');
        Route::livewire('registros', 'pages::produccion.registros')->name('registros');
        Route::livewire('productos', 'pages::produccion.productos')->name('productos');
        Route::livewire('compras', 'pages::produccion.compras')->name('compras');
        Route::livewire('inventario', 'pages::produccion.inventario')->name('inventario');
    });

<?php

use App\Http\Controllers\VentaBoletaPdfController;
use App\Http\Middleware\EnsureTeamMembership;
use Illuminate\Support\Facades\Route;

Route::prefix('{current_team}/vendedor')
    ->middleware(['auth:web', EnsureTeamMembership::class, 'role:vendedor,administrador'])
    ->name('vendedor.')
    ->group(function () {
        Route::livewire('panel', 'pages::vendedor.panel')->name('panel');
        Route::livewire('ventas', 'pages::vendedor.ventas')->name('ventas');
        Route::get('ventas/{venta}/boleta', VentaBoletaPdfController::class)->name('ventas.boleta');
        Route::livewire('produccion', 'pages::vendedor.produccion')->name('produccion');
        Route::livewire('historial', 'pages::vendedor.historial')->name('historial');
        Route::get('configuracion-pagos', fn () => redirect()->route('admin.receptores-yape'))->name('configuracion-pagos');
        Route::livewire('inventario', 'pages::vendedor.inventario')->name('inventario');
        Route::livewire('clientes', 'pages::vendedor.clientes')->name('clientes');
        Route::livewire('pedidos-web', 'pages::vendedor.pedidos-web')->name('pedidos-web');
    });

Route::get('/pagos/bridge.apk', [\App\Http\Controllers\Api\BridgeEnrollmentController::class, 'download'])->middleware('auth:web')->name('vendedor.bridge-descarga');

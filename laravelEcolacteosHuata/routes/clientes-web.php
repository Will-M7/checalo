<?php

use App\Http\Controllers\ClientesWeb\AuthController;
use App\Http\Controllers\ClientesWeb\CuentaController;
use Illuminate\Support\Facades\Route;

Route::prefix('mi-cuenta')->name('cliente.')->group(function () {
    Route::get('ingresar', [AuthController::class, 'loginForm'])->name('login');
    Route::post('ingresar', [AuthController::class, 'login'])->middleware('throttle:10,1')->name('login.store');
    Route::get('registro', [AuthController::class, 'registerForm'])->name('register');
    Route::post('registro', [AuthController::class, 'register'])->middleware('throttle:5,1')->name('register.store');
    Route::get('recuperar', [AuthController::class, 'forgotForm'])->name('password.request');
    Route::post('recuperar', [AuthController::class, 'forgot'])->middleware('throttle:3,1')->name('password.email');
    Route::get('restablecer/{token}', [AuthController::class, 'resetForm'])->name('password.reset');
    Route::post('restablecer', [AuthController::class, 'reset'])->middleware('throttle:5,1')->name('password.update');
    Route::middleware('auth:cliente')->group(function () {
        Route::get('/', [CuentaController::class, 'index'])->name('cuenta');
        Route::post('salir', [AuthController::class, 'logout'])->name('logout');
        Route::post('perfil', [CuentaController::class, 'perfil'])->middleware('throttle:5,1')->name('perfil');
        Route::get('pedidos/{id}', [CuentaController::class, 'pedido'])->name('pedido');
        Route::post('pedidos/{id}/saldo', [CuentaController::class, 'pagarSaldo'])->middleware('throttle:10,1')->name('pedido.saldo');
        Route::post('recargas', [CuentaController::class, 'recarga'])->middleware('throttle:8,1')->name('recarga');
        Route::get('pagos/{id}', [CuentaController::class, 'pago'])->name('pago');
        Route::get('pagos/{id}/estado', [CuentaController::class, 'consultar'])->middleware('throttle:30,1')->name('pago.estado');
        Route::post('pagos/{id}/validar', [CuentaController::class, 'validar'])->middleware('throttle:5,1')->name('pago.validar');
    });
});

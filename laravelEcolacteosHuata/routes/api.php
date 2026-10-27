<?php

use App\Http\Controllers\Api\MobileAuthController;
use App\Http\Controllers\Api\MobileAutorizacionController;
use App\Http\Controllers\Api\MobileCambiosController;
use App\Http\Controllers\Api\MobileDistribucionesController;
use App\Http\Controllers\Api\MobileSyncController;
use App\Http\Controllers\Api\YapeBridgeController;
use App\Http\Middleware\AuthenticateMobileToken;
use Illuminate\Support\Facades\Route;

Route::post('/mobile/login', [MobileAuthController::class, 'login'])->middleware('throttle:30,1');
Route::get('/mobile/sync', [MobileSyncController::class, 'sync'])->middleware(AuthenticateMobileToken::class);
Route::post('/mobile/cambios', [MobileCambiosController::class, 'store'])->middleware(AuthenticateMobileToken::class);

Route::get('/mobile/autorizaciones', [MobileAutorizacionController::class, 'show'])->middleware(AuthenticateMobileToken::class);
Route::post('/mobile/autorizaciones/asignar', [MobileAutorizacionController::class, 'asignar'])->middleware(AuthenticateMobileToken::class);
Route::get('/mobile/autorizaciones/roles', [MobileAutorizacionController::class, 'catalogo'])->middleware(AuthenticateMobileToken::class);
Route::post('/mobile/autorizaciones/roles', [MobileAutorizacionController::class, 'guardarRol'])->middleware(AuthenticateMobileToken::class);
Route::put('/mobile/autorizaciones/roles/{rol}', [MobileAutorizacionController::class, 'guardarRol'])->whereNumber('rol')->middleware(AuthenticateMobileToken::class);
Route::post('/mobile/autorizaciones/usuarios', [MobileAutorizacionController::class, 'guardarUsuario'])->middleware(AuthenticateMobileToken::class);
Route::put('/mobile/autorizaciones/usuarios/{usuario}', [MobileAutorizacionController::class, 'guardarUsuario'])->whereNumber('usuario')->middleware(AuthenticateMobileToken::class);

Route::prefix('/mobile/autorizaciones/distribuciones')->middleware(AuthenticateMobileToken::class)->group(function () {
    Route::get('/', [MobileDistribucionesController::class, 'index']);
    Route::post('/', [MobileDistribucionesController::class, 'guardar']);
    Route::post('/previsualizar', [MobileDistribucionesController::class, 'previsualizar']);
    Route::post('/confirmar', [MobileDistribucionesController::class, 'confirmar']);
});

// La clave del Bridge se autentica como dispositivo en el Core comercial privado.
// No usa sesiones del personal, compradores ni la API genérica de sincronización.
Route::post('/bridge/heartbeat', [YapeBridgeController::class, 'heartbeat'])->middleware('throttle:120,1');
Route::post('/bridge/notifications', [YapeBridgeController::class, 'notification'])->middleware('throttle:120,1');

Route::post('/bridge/enroll', [\App\Http\Controllers\Api\BridgeEnrollmentController::class, 'enroll'])->middleware('throttle:10,1');
Route::post('/mobile/bridge/assertion', [\App\Http\Controllers\Api\BridgeEnrollmentController::class, 'assertion'])->middleware([AuthenticateMobileToken::class, 'throttle:120,1']);

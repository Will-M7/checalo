<?php

use App\Http\Controllers\PedidoWebBoletaController;
use App\Http\Controllers\PedidoWebController;
use App\Http\Middleware\EnsureTeamMembership;
use App\Models\Empresa;
use App\Models\Produccion;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Route;

Route::get('/', function () {
    $empresa = Empresa::actual();

    return view('welcome', [
        'empresa' => $empresa,
        'productos' => Produccion::catalogoPublico(),
        'whatsapp' => $empresa->whatsapp(),
    ]);
})->name('home');

Route::post('/pedidos', [PedidoWebController::class, 'store'])->middleware(['auth:cliente', 'throttle:8,1'])->name('pedidos-web.store');
Route::get('/pedido/{token}/boleta', PedidoWebBoletaController::class)->middleware('throttle:30,1')->name('pedidos-web.boleta');

Route::prefix('{current_team}')
    ->middleware(['auth:web', EnsureTeamMembership::class])
    ->group(function () {
        Route::get('dashboard', function (Request $request) {
            $primera = collect($request->user()->navegacionAgrupada())->flatten(1)->first();
            abort_unless($primera, 403, 'Tu usuario no tiene funciones asignadas. Contacta al administrador.');
            $panel = $request->user()->rol?->routePrefix().'.panel';

            return redirect()->route($request->user()->puedePantalla($panel) ? $panel : $primera['route']);
        })->name('dashboard');
    });

require __DIR__.'/settings.php';
require __DIR__.'/admin.php';
require __DIR__.'/planta.php';
require __DIR__.'/acopiador.php';
require __DIR__.'/proveedor.php';
require __DIR__.'/vendedor.php';
require __DIR__.'/produccion.php';
require __DIR__.'/asistencia.php';

require __DIR__.'/clientes-web.php';

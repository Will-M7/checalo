<?php

namespace App\Http\Controllers;

use App\Enums\Periodo;
use App\Models\Empresa;
use App\Models\Recojo;
use Barryvdh\DomPDF\Facade\Pdf;
use Illuminate\Http\Request;
use Illuminate\Http\Response;
use Illuminate\Support\Carbon;

class AcopiadorInventarioPdfController extends Controller
{
    public function __invoke(Request $request): Response
    {
        $acopiador = $request->user()->acopiador;

        abort_if(! $acopiador, 404);

        $periodo = Periodo::from($request->string('periodo', 'semana')->value());
        $fecha = Carbon::parse($request->string('fecha', now()->toDateString())->value());
        [$desde, $hasta] = $periodo->rangoParaFecha($fecha);

        $recojos = Recojo::query()
            ->with('proveedor.user', 'turno')
            ->where('acopiador_id', $acopiador->id)
            ->whereBetween('fecha', [$desde->toDateString(), $hasta->toDateString()])
            ->orderByDesc('fecha')
            ->get();

        $pdf = Pdf::loadView('acopiador.inventario-pdf', [
            'recojos' => $recojos,
            'desde' => $desde,
            'hasta' => $hasta,
            'acopiador' => $acopiador,
            'empresa' => Empresa::actual(),
        ])->setPaper('a4', 'portrait');

        return $pdf->stream('inventario.pdf');
    }
}

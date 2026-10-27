<?php

namespace App\Http\Controllers;

use App\Models\Empresa;
use App\Models\ResultadoLaboratorio;
use Barryvdh\DomPDF\Facade\Pdf;
use Illuminate\Http\Response;

class ResultadoLaboratorioPdfController extends Controller
{
    public function __invoke(string $current_team, ResultadoLaboratorio $resultado): Response
    {
        $resultado->load('proveedor.user', 'turno');

        $pdf = Pdf::loadView('planta.resultado-laboratorio-pdf', [
            'resultado' => $resultado,
            'empresa' => Empresa::actual(),
        ])->setPaper('a5', 'portrait');

        return $pdf->stream('analisis-laboratorio-'.$resultado->id.'.pdf');
    }
}

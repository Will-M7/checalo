<?php

namespace App\Http\Controllers;

use App\Models\Justificacion;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Storage;
use Symfony\Component\HttpFoundation\StreamedResponse;

class JustificacionArchivoController extends Controller
{
    /**
     * Muestra el PDF o la imagen adjunta a una justificación. Solo la ve su dueño o quien toma
     * asistencia; los archivos se guardan en el disco privado.
     */
    public function __invoke(Request $request, string $current_team, Justificacion $justificacion): StreamedResponse
    {
        $user = $request->user();

        abort_unless(
            $justificacion->asistencia->user_id === $user->id || $user->puedeTomarAsistencia(),
            403,
        );

        abort_if($justificacion->archivo === null || ! Storage::disk('local')->exists($justificacion->archivo), 404);

        return Storage::disk('local')->response($justificacion->archivo);
    }
}

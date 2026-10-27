<?php

namespace App\Http\Middleware;

use App\Authorization\Pantallas;
use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

class EnsureRole
{
    /**
     * Nombre conservado para compatibilidad de rutas. Autoriza su pantalla central, no los nombres históricos.
     *
     * Los argumentos heredados no conceden permisos. Una ruta sin mapeo se deniega.
     *
     * @param  Closure(Request): (Response)  $next
     */
    public function handle(Request $request, Closure $next, string ...$roles): Response
    {
        $pantallas = Pantallas::paraRuta($request->route()?->getName());
        abort_unless(collect($pantallas)->contains(fn ($p) => $request->user('web')?->puedePantalla($p)), 403);

        return $next($request);
    }
}

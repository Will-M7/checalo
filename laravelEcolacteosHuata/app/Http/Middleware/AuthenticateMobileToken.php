<?php

namespace App\Http\Middleware;

use App\Models\User;
use Closure;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Symfony\Component\HttpFoundation\Response;

class AuthenticateMobileToken
{
    /**
     * Exige el token que `POST /api/mobile/login` entregó al teléfono (header
     * `Authorization: Bearer …`). El usuario dueño del token debe seguir activo.
     *
     * @param  Closure(Request): (Response)  $next
     */
    public function handle(Request $request, Closure $next): Response
    {
        $token = (string) $request->bearerToken();

        $registro = $token !== ''
            ? DB::table('mobile_tokens')->where('token', hash('sha256', $token))->first()
            : null;

        $user = $registro ? User::query()->find($registro->user_id) : null;

        if (! $user || ! $user->activo) {
            return response()->json([
                'message' => 'Inicie sesión en la app con conexión al servidor para descargar los datos.',
            ], 401);
        }

        DB::table('mobile_tokens')->where('id', $registro->id)->update(['last_used_at' => now()]);

        $request->setUserResolver(fn () => $user);

        return $next($request);
    }
}

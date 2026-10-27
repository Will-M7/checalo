<?php

namespace App\Services;

use App\Models\User;
use Illuminate\Support\Facades\{DB, Schema};
use Illuminate\Validation\ValidationException;

final class EliminarUsuarioSeguro
{
    public const MENSAJE = 'Este usuario tiene registros relacionados. Conservamos sus pedidos, ventas, pagos e historial. Usa Desactivar usuario para impedir nuevos accesos.';

    public function comprobar(User $user): void
    {
        // La Web y sync ejecutan el borrado en transacción. Bloqueamos al padre antes
        // de comprobar referencias: también toma el bloqueo de escritura de SQLite.
        DB::table('users')->where('id', $user->id)->update(['id' => $user->id]);
        // Se inspeccionan también relaciones con ON DELETE CASCADE / SET NULL: no se destruye historia.
        foreach (Schema::getTables() as $table) {
            $name = $table['name'];
            foreach (Schema::getForeignKeys($name) as $fk) {
                if ($fk['foreign_table'] !== 'users') continue;
                foreach ($fk['columns'] as $column) {
                    if (DB::table($name)->where($column, $user->id)->exists()) $this->rechazar();
                }
            }
        }
        if (DB::table('sessions')->where('user_id', $user->id)->exists()) $this->rechazar();
        if (DB::table('mobile_ids')->where('tabla', 'usuario')->where('registro_id', $user->id)->exists()) $this->rechazar();
        if (DB::table('notifications')->where('notifiable_type', User::class)->where('notifiable_id', $user->id)->exists()) $this->rechazar();
    }

    private function rechazar(): never
    {
        throw ValidationException::withMessages(['usuario' => self::MENSAJE]);
    }
}

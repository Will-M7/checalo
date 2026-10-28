<?php

namespace App\Services;

use App\Authorization\Autorizacion;
use App\Enums\EstadoPedidoWeb;
use App\Models\{PedidoWeb, ReceptorPago, User};
use Illuminate\Support\Facades\DB;
use Illuminate\Validation\ValidationException;

final class VendedoresWeb
{
    public function estado(User $user): array
    {
        $capaz = $user->rolesDinamicos()->where('activo', true)->where('codigo', '!=', 'administrador')->whereJsonContains('pantallas', 'vendedor.pedidos-web')->exists();
        return ['id' => $user->id, 'nombre' => $user->nombreCompleto(), 'activo' => (bool) $user->activo,
            'capacidad' => $capaz, 'autorizado' => (bool) $user->pedidos_web_autorizado, 'pausado' => (bool) $user->pedidos_web_pausado,
            'estado' => ! $user->activo || ! $capaz || ! $user->pedidos_web_autorizado ? 'inhabilitado' : ($user->pedidos_web_pausado ? 'pausado' : 'disponible')];
    }

    public function cambiar(User $actor, User $user, bool $valor, bool $administrativo): array
    {
        abort_unless($administrativo, 403, 'La recepción web se administra desde Usuarios.');
        abort_unless($actor->activo && ($administrativo ? $actor->puedePantalla('admin.usuarios') : $actor->id === $user->id && $actor->puedePantalla('vendedor.pedidos-web')), 403);
        if ($administrativo) Autorizacion::autorizarGestionUsuario($actor, $user);
        return DB::transaction(function () use ($actor, $user, $valor, $administrativo) {
            DB::table('integracion_secuencias')->where('clave', 'reparto')->increment('valor', 0);
            $user = User::whereKey($user->id)->lockForUpdate()->firstOrFail();
            if (! $administrativo && ! $valor && (! $user->pedidos_web_autorizado || ! User::vendedorWeb()->whereKey($user->id)->exists())) {
                throw ValidationException::withMessages(['disponibilidad' => 'La recepción está inhabilitada por administración. No puedes reactivarla.']);
            }
            $antes = $this->estado($user);
            $campo = $administrativo ? 'pedidos_web_autorizado' : 'pedidos_web_pausado';
            $user->$campo = $valor;
            if ($administrativo) $user->pedidos_web_pausado = false;
            $user->save();
            Autorizacion::registrar($actor, 'disponibilidad_web', 'usuario:'.$user->id, $antes, $this->estado($user));
            return $this->estado($user);
        }, 5);
    }

    /** Debe ejecutarse dentro de la transacción que guarda el pedido. La escritura serializa también SQLite. */
    public function seleccionar(): User
    {
        DB::table('integracion_secuencias')->where('clave', 'reparto')->increment('valor', 0);
        $last = (int) DB::table('integracion_secuencias')->where('clave', 'reparto')->value('valor');
        $sellers = User::disponibleWeb()->orderBy('id')->lockForUpdate()->get();
        $seller = $sellers->first(fn ($s) => $s->id > $last) ?? $sellers->first();
        if (! $seller) throw ValidationException::withMessages(['pedido' => 'No hay vendedores disponibles para atender pedidos web. Intenta nuevamente cuando la tienda active la recepción.']);
        DB::table('integracion_secuencias')->where('clave', 'reparto')->update(['valor' => $seller->id]);
        return $seller;
    }

    public function contacto(User $seller): ?string
    {
        $phone = ReceptorPago::where('user_id', $seller->id)->value('whatsapp') ?: $seller->celular;
        $phone = preg_replace('/\D+/', '', $phone ?? '');
        if (strlen($phone) === 9) $phone = '51'.$phone;
        return preg_match('/^51[0-9]{9}$/', $phone) ? $phone : null;
    }

    public function reasignar(User $actor, int $id, int $sellerId, string $motivo): PedidoWeb
    {
        abort_unless($actor->activo && $actor->puedePantalla('admin.usuarios'), 403);
        validator(['motivo' => $motivo], ['motivo' => 'required|string|min:10|max:250'])->validate();
        return DB::transaction(function () use ($actor, $id, $sellerId, $motivo) {
            DB::table('integracion_secuencias')->where('clave', 'reparto')->increment('valor', 0);
            $order = PedidoWeb::whereKey($id)->lockForUpdate()->firstOrFail();
            $seller = User::disponibleWeb()->findOrFail($sellerId);
            if ($order->estado !== EstadoPedidoWeb::Pendiente || $order->venta_id || \App\Models\IntentoPago::where('pedido_web_id', $id)->where('estado', 'PAID')->exists() || in_array($order->pago_estado, ['pagado', 'paid'], true)) {
                throw ValidationException::withMessages(['pedido' => 'Solo se puede reasignar un pedido pendiente sin venta ni pago confirmado.']);
            }
            $antes = ['vendedor_id' => $order->vendedor_asignado_id];
            // Las intenciones, su receptor, QR y atribución original son inmutables.
            $order->update(['vendedor_asignado_id' => $seller->id, 'receptor_snapshot' => [...($order->receptor_snapshot ?? []),
                'vendedor_id' => $seller->id, 'vendedor_nombre' => $seller->nombreCompleto(), 'vendedor_whatsapp' => $this->contacto($seller)]]);
            Autorizacion::registrar($actor, 'reasignar_pedido_web', 'pedido:'.$id, $antes, ['vendedor_id' => $seller->id, 'motivo' => $motivo]);
            return $order;
        }, 5);
    }
}

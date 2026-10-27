<?php

namespace App\Models;

use App\Enums\Rol;
use App\Notifications\ProveedorUbicacionActualizada;
use Database\Factories\ProveedorFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\BelongsToMany;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Support\Facades\Notification;

#[Fillable([
    'user_id', 'finca_ubicacion', 'finca_referencia', 'latitud', 'longitud',
    'acopiador_id', 'bloqueado', 'alteraciones',
])]
class Proveedor extends Model
{
    /** @use HasFactory<ProveedorFactory> */
    use HasFactory;

    protected $table = 'proveedores';

    /**
     * Número de alteraciones de calidad a partir del cual se bloquea automáticamente al proveedor.
     */
    public const UMBRAL_BLOQUEO = 3;

    /**
     * @return BelongsTo<User, $this>
     */
    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }

    /**
     * @return BelongsTo<Acopiador, $this>
     */
    public function acopiador(): BelongsTo
    {
        return $this->belongsTo(Acopiador::class);
    }

    /**
     * @return BelongsToMany<Ruta, $this>
     */
    public function rutas(): BelongsToMany
    {
        return $this->belongsToMany(Ruta::class, 'ruta_proveedor');
    }

    /**
     * @return HasMany<Recojo, $this>
     */
    public function recojos(): HasMany
    {
        return $this->hasMany(Recojo::class);
    }

    /**
     * @return HasMany<ResultadoLaboratorio, $this>
     */
    public function resultadosLaboratorio(): HasMany
    {
        return $this->hasMany(ResultadoLaboratorio::class);
    }

    /**
     * @return HasMany<PagoProveedor, $this>
     */
    public function pagos(): HasMany
    {
        return $this->hasMany(PagoProveedor::class);
    }

    /**
     * Registra una alteración de calidad y bloquea al proveedor al llegar al umbral.
     */
    public function registrarAlteracion(): void
    {
        $this->increment('alteraciones');

        if ($this->alteraciones >= self::UMBRAL_BLOQUEO) {
            $this->update(['bloqueado' => true]);
        }
    }

    /**
     * Desbloquea al proveedor y reinicia su contador de alteraciones.
     */
    public function desbloquear(): void
    {
        $this->update(['bloqueado' => false, 'alteraciones' => 0]);
    }

    /**
     * Crea o actualiza el proveedor de un usuario. Si la ubicación cambió respecto a la que
     * tenía (ya sea que la edite el propio proveedor o el administrador desde Usuarios/Asignar
     * roles), lo quita de sus rutas y notifica a planta para que le asignen una ruta acorde a la
     * nueva ubicación. El acopiador que venga en $datos (elegido explícitamente en el
     * formulario) siempre se respeta y se guarda tal cual.
     *
     * @param  array<string, mixed>  $datos
     */
    public static function guardarConUbicacion(int $userId, array $datos): self
    {
        $existente = static::where('user_id', $userId)->first();

        $ubicacionCambio = $existente && (
            $existente->finca_ubicacion !== ($datos['finca_ubicacion'] ?? $existente->finca_ubicacion)
            || (float) ($existente->latitud ?? 0) !== (float) ($datos['latitud'] ?? $existente->latitud ?? 0)
            || (float) ($existente->longitud ?? 0) !== (float) ($datos['longitud'] ?? $existente->longitud ?? 0)
        );

        $proveedor = static::updateOrCreate(['user_id' => $userId], $datos);

        if ($ubicacionCambio) {
            $proveedor->notificarCambioUbicacion();
        }

        return $proveedor;
    }

    /**
     * Guarda la ubicación y el acopiador que el propio proveedor eligió y, si alguno cambió, lo
     * agrega automáticamente a la ruta de ese acopiador: la que ya tenía con él o, si no, la ruta
     * activa cuyo camino pasa más cerca de su finca. El camino de la ruta se actualiza para pasar
     * por su nueva ubicación. Sale de las rutas de otros acopiadores. Solo si el acopiador no tiene
     * ninguna ruta activa se avisa a planta para que le asigne una.
     *
     * @param  array{finca_ubicacion: string, finca_referencia: string, latitud: float|null, longitud: float|null, acopiador_id: int}  $datos
     */
    public static function guardarElegidoPorProveedor(int $userId, array $datos): self
    {
        $anterior = static::where('user_id', $userId)->first();
        $puntoAnterior = $anterior?->punto();

        $proveedor = static::updateOrCreate(['user_id' => $userId], $datos);
        $puntoNuevo = $proveedor->punto();

        $cambio = ! $anterior
            || $anterior->acopiador_id !== $proveedor->acopiador_id
            || $anterior->finca_ubicacion !== $proveedor->finca_ubicacion
            || $puntoAnterior !== $puntoNuevo;

        if (! $cambio) {
            return $proveedor;
        }

        $ruta = $proveedor->rutas()->where('acopiador_id', $proveedor->acopiador_id)->where('activa', true)->first()
            ?? $proveedor->acopiador?->rutaMasCercana($puntoNuevo);

        $proveedor->rutas()->detach(
            $proveedor->rutas()->where('rutas.id', '!=', $ruta?->id ?? 0)->pluck('rutas.id')
        );

        if (! $ruta) {
            $proveedor->notificarCambioUbicacion();

            return $proveedor;
        }

        $ruta->agregarProveedor($proveedor->id);

        if ($puntoNuevo) {
            $ruta->ubicarEnTrazo($puntoNuevo, $puntoAnterior);
        }

        return $proveedor;
    }

    /**
     * Coordenadas de la finca como [lat, lng], o null si todavía no se marcó en el mapa.
     *
     * @return array{0: float, 1: float}|null
     */
    public function punto(): ?array
    {
        return $this->latitud !== null && $this->longitud !== null
            ? [(float) $this->latitud, (float) $this->longitud]
            : null;
    }

    /**
     * Quita al proveedor de todas sus rutas (la ruta se armó según su ubicación anterior) y
     * notifica a Administrador del sistema y Personal de planta para que le asignen una nueva.
     */
    public function notificarCambioUbicacion(): void
    {
        $this->rutas()->detach();

        $destinatarios = User::query()
            ->where('activo', true)
            ->conAlgunRol([Rol::Administrador, Rol::PersonalPlanta])
            ->get();

        Notification::send($destinatarios, new ProveedorUbicacionActualizada($this));
    }

    /**
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'latitud' => 'decimal:7',
            'longitud' => 'decimal:7',
            'bloqueado' => 'boolean',
            'alteraciones' => 'integer',
        ];
    }
}

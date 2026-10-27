<?php

namespace App\Models;

use Database\Factories\AcopiadorFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;

#[Fillable(['user_id', 'ruta_nombre'])]
class Acopiador extends Model
{
    /** @use HasFactory<AcopiadorFactory> */
    use HasFactory;

    protected $table = 'acopiadores';

    /**
     * @return BelongsTo<User, $this>
     */
    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }

    /**
     * @return HasMany<Ruta, $this>
     */
    public function rutas(): HasMany
    {
        return $this->hasMany(Ruta::class);
    }

    /**
     * Camino de sus rutas activas, para mostrarlo como referencia en el mapa donde el proveedor
     * marca su finca (evento de navegador "mapa-ubicacion-rutas").
     *
     * @return array<int, array{nombre: string, trazo: array<int, array{0: float, 1: float}>}>
     */
    public function rutasParaMapa(): array
    {
        return $this->rutas()
            ->where('activa', true)
            ->orderBy('nombre')
            ->get()
            ->map(fn (Ruta $ruta) => ['nombre' => $ruta->nombre, 'trazo' => $ruta->trazo ?? []])
            ->values()
            ->all();
    }

    /**
     * La ruta activa cuyo camino pasa más cerca del punto dado. Las rutas sin camino trazado
     * quedan al final; sin punto, devuelve la primera ruta activa por nombre.
     *
     * @param  array{0: float, 1: float}|null  $punto
     */
    public function rutaMasCercana(?array $punto): ?Ruta
    {
        $rutas = $this->rutas()->where('activa', true)->orderBy('nombre')->get();

        if (! $punto) {
            return $rutas->first();
        }

        return $rutas->sortBy(fn (Ruta $ruta) => collect($ruta->trazo ?? [])
            ->map(fn (array $vertice) => Ruta::distancia($punto, $vertice))
            ->min() ?? INF)->first();
    }

    /**
     * @return HasMany<Proveedor, $this>
     */
    public function proveedores(): HasMany
    {
        return $this->hasMany(Proveedor::class);
    }

    /**
     * @return HasMany<Recojo, $this>
     */
    public function recojos(): HasMany
    {
        return $this->hasMany(Recojo::class);
    }

    /**
     * @return HasMany<EntregaPlanta, $this>
     */
    public function entregasPlanta(): HasMany
    {
        return $this->hasMany(EntregaPlanta::class);
    }
}

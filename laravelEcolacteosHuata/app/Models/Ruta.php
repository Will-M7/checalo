<?php

namespace App\Models;

use Database\Factories\RutaFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\BelongsToMany;
use Illuminate\Support\Collection;
use Illuminate\Support\Facades\DB;

#[Fillable(['acopiador_id', 'nombre', 'activa', 'trazo', 'rango'])]
class Ruta extends Model
{
    /** @use HasFactory<RutaFactory> */
    use HasFactory;

    /**
     * @return BelongsTo<Acopiador, $this>
     */
    public function acopiador(): BelongsTo
    {
        return $this->belongsTo(Acopiador::class);
    }

    /**
     * @return BelongsToMany<Turno, $this>
     */
    public function turnos(): BelongsToMany
    {
        return $this->belongsToMany(Turno::class, 'ruta_turno');
    }

    /**
     * Proveedores de la ruta en el orden de recojo definido por planta.
     *
     * @return BelongsToMany<Proveedor, $this>
     */
    public function proveedores(): BelongsToMany
    {
        return $this->belongsToMany(Proveedor::class, 'ruta_proveedor')
            ->withPivot('orden')
            ->orderByPivot('orden')
            ->orderBy('proveedores.id');
    }

    /**
     * Agrega un proveedor al final del orden de recojo (si todavía no está en la ruta). Un
     * proveedor pertenece a una sola ruta: si estaba en otra, sale de ella.
     */
    public function agregarProveedor(int $proveedorId): void
    {
        if ($this->proveedores()->whereKey($proveedorId)->exists()) {
            return;
        }

        static::quitarProveedorDeOtrasRutas($proveedorId, $this->id);

        $siguienteOrden = (int) $this->proveedores()->max('ruta_proveedor.orden') + 1;

        $this->proveedores()->attach($proveedorId, ['orden' => $siguienteOrden]);

        $this->numerarParadasPorTrazo();
    }

    /**
     * Numera las paradas automáticamente según dónde cae cada finca a lo largo del camino, del
     * inicio al fin. Los proveedores sin ubicación quedan al final en el orden que ya tenían. Sin
     * un camino de al menos dos puntos se conserva el orden actual.
     */
    public function numerarParadasPorTrazo(): void
    {
        if (count($this->trazo ?? []) < 2) {
            return;
        }

        $proveedores = $this->proveedores()->get()->values();

        $ordenados = $proveedores
            ->map(fn (Proveedor $proveedor, int $indice) => [
                'id' => $proveedor->id,
                'avance' => $proveedor->punto() ? $this->proyectarEnTrazo($proveedor->punto())['avance'] : INF,
                'indice' => $indice,
            ])
            ->sort(fn (array $a, array $b) => [$a['avance'], $a['indice']] <=> [$b['avance'], $b['indice']])
            ->pluck('id')
            ->values();

        if ($ordenados->all() === $proveedores->pluck('id')->all()) {
            return;
        }

        foreach ($ordenados as $indice => $id) {
            $this->proveedores()->updateExistingPivot($id, ['orden' => $indice + 1]);
        }

        // El pivot no toca `rutas.updated_at`; la app móvil sincroniza por esa fecha.
        $this->touch();
    }

    /**
     * Saca a los proveedores de toda ruta distinta de $rutaId y marca esas rutas como modificadas
     * (el pivot no toca `rutas.updated_at` y la app móvil sincroniza por esa fecha).
     *
     * @param  int|array<int, int>  $proveedorIds
     */
    public static function quitarProveedorDeOtrasRutas(int|array $proveedorIds, int $rutaId): void
    {
        $proveedorIds = (array) $proveedorIds;

        $otras = DB::table('ruta_proveedor')
            ->whereIn('proveedor_id', $proveedorIds)
            ->where('ruta_id', '!=', $rutaId)
            ->pluck('ruta_id')
            ->unique();

        if ($otras->isEmpty()) {
            return;
        }

        DB::table('ruta_proveedor')
            ->whereIn('proveedor_id', $proveedorIds)
            ->where('ruta_id', '!=', $rutaId)
            ->delete();

        static::whereKey($otras)->each(fn (Ruta $ruta) => $ruta->touch());
    }

    /**
     * Hace que el camino pase por la finca de un proveedor: quita el vértice de su ubicación
     * anterior (si estaba en el camino) e inserta el nuevo punto donde menos alarga el recorrido
     * (al inicio, al final o entre los dos vértices más convenientes).
     *
     * @param  array{0: float, 1: float}  $punto
     * @param  array{0: float, 1: float}|null  $puntoAnterior
     */
    public function ubicarEnTrazo(array $punto, ?array $puntoAnterior = null): void
    {
        $esMismoPunto = fn (array $a, array $b) => abs($a[0] - $b[0]) < 1e-7 && abs($a[1] - $b[1]) < 1e-7;

        $trazo = collect($this->trazo ?? [])
            ->map(fn (array $vertice) => [(float) $vertice[0], (float) $vertice[1]])
            ->reject(fn (array $vertice) => $puntoAnterior && $esMismoPunto($vertice, $puntoAnterior))
            ->values()
            ->all();

        if (collect($trazo)->contains(fn (array $vertice) => $esMismoPunto($vertice, $punto))) {
            $this->update(['trazo' => $trazo]);

            return;
        }

        $cantidad = count($trazo);

        // [posición donde insertar => cuánto se alarga el camino]
        $opciones = $cantidad === 0 ? [0 => 0.0] : [
            0 => self::distancia($punto, $trazo[0]),
            $cantidad => self::distancia($trazo[$cantidad - 1], $punto),
        ];

        for ($i = 0; $i < $cantidad - 1; $i++) {
            $opciones[$i + 1] = self::distancia($trazo[$i], $punto)
                + self::distancia($punto, $trazo[$i + 1])
                - self::distancia($trazo[$i], $trazo[$i + 1]);
        }

        $posicion = array_search(min($opciones), $opciones, true);

        array_splice($trazo, $posicion, 0, [$punto]);

        $this->update(['trazo' => $trazo]);
        $this->numerarParadasPorTrazo();
    }

    /**
     * Distancia aproximada (en grados, corregida por latitud) entre dos puntos [lat, lng]. Basta
     * para comparar distancias dentro de una misma zona de recojo.
     *
     * @param  array{0: float, 1: float}  $a
     * @param  array{0: float, 1: float}  $b
     */
    public static function distancia(array $a, array $b): float
    {
        $dx = ((float) $a[1] - (float) $b[1]) * cos(deg2rad(((float) $a[0] + (float) $b[0]) / 2));
        $dy = (float) $a[0] - (float) $b[0];

        return sqrt($dx ** 2 + $dy ** 2);
    }

    /**
     * Distancia en metros desde un punto [lat, lng] hasta el tramo más cercano del camino de la
     * ruta (proyección local, suficiente dentro de la zona de recojo). INF si no tiene camino.
     *
     * @param  array{0: float, 1: float}  $punto
     */
    public function distanciaAlTrazoEnMetros(array $punto): float
    {
        return $this->proyectarEnTrazo($punto)['distancia'];
    }

    /**
     * Proyecta un punto [lat, lng] sobre el tramo más cercano del camino: a cuántos metros está
     * del camino (distancia) y cuántos metros de recorrido desde el inicio hay hasta ese punto del
     * camino (avance). Proyección local, suficiente dentro de la zona de recojo.
     *
     * @param  array{0: float, 1: float}  $punto
     * @return array{distancia: float, avance: float}
     */
    private function proyectarEnTrazo(array $punto): array
    {
        $metrosPorGradoLng = 111320 * cos(deg2rad($punto[0]));
        $aMetros = fn (array $p) => [(float) $p[1] * $metrosPorGradoLng, (float) $p[0] * 110540];

        [$px, $py] = $aMetros($punto);
        $vertices = array_map($aMetros, $this->trazo ?? []);

        if (count($vertices) === 1) {
            return ['distancia' => hypot($px - $vertices[0][0], $py - $vertices[0][1]), 'avance' => 0.0];
        }

        $mejor = ['distancia' => INF, 'avance' => INF];
        $recorrido = 0.0;

        for ($i = 0; $i < count($vertices) - 1; $i++) {
            [$ax, $ay] = $vertices[$i];
            [$bx, $by] = $vertices[$i + 1];
            $largo2 = ($bx - $ax) ** 2 + ($by - $ay) ** 2;
            $t = $largo2 == 0 ? 0 : max(0, min(1, (($px - $ax) * ($bx - $ax) + ($py - $ay) * ($by - $ay)) / $largo2));
            $distancia = hypot($px - ($ax + $t * ($bx - $ax)), $py - ($ay + $t * ($by - $ay)));

            if ($distancia < $mejor['distancia']) {
                $mejor = ['distancia' => $distancia, 'avance' => $recorrido + $t * sqrt($largo2)];
            }

            $recorrido += sqrt($largo2);
        }

        return $mejor;
    }

    /**
     * Datos que consume el componente <x-mapa-ruta>: el camino trazado y las paradas en orden.
     *
     * @param  Collection<int, int>|null  $recogidosIds  Proveedores ya recogidos hoy.
     * @return array{rutaId: int, trazo: array<int, array{0: float, 1: float}>, paradas: array<int, array{id: int, numero: int, nombre: string, ubicacion: string, lat: float|null, lng: float|null, recogido: bool}>}
     */
    public function datosMapa(?Collection $recogidosIds = null): array
    {
        $this->loadMissing('proveedores.user');

        return [
            'rutaId' => $this->id,
            'trazo' => $this->trazo ?? [],
            'paradas' => $this->proveedores->values()->map(fn (Proveedor $proveedor, int $indice) => [
                'id' => $proveedor->id,
                'numero' => $indice + 1,
                'nombre' => $proveedor->user->nombreCompleto(),
                'ubicacion' => $proveedor->finca_ubicacion ?: 'Sin ubicación registrada',
                'lat' => $proveedor->latitud !== null ? (float) $proveedor->latitud : null,
                'lng' => $proveedor->longitud !== null ? (float) $proveedor->longitud : null,
                'recogido' => (bool) $recogidosIds?->contains($proveedor->id),
            ])->all(),
        ];
    }

    /**
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'activa' => 'boolean',
            'trazo' => 'array',
            'rango' => 'integer',
        ];
    }
}

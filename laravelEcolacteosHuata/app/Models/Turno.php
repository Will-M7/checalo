<?php

namespace App\Models;

use Database\Factories\TurnoFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsToMany;
use Illuminate\Database\Eloquent\Relations\HasMany;

#[Fillable(['nombre', 'hora_inicio', 'hora_fin', 'es_produccion'])]
class Turno extends Model
{
    /** @use HasFactory<TurnoFactory> */
    use HasFactory;

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

    /**
     * @return BelongsToMany<Ruta, $this>
     */
    public function rutas(): BelongsToMany
    {
        return $this->belongsToMany(Ruta::class, 'ruta_turno');
    }

    /**
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'es_produccion' => 'boolean',
        ];
    }
}

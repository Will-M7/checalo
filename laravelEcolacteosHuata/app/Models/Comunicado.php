<?php

namespace App\Models;

use App\Enums\Rol;
use App\Enums\TipoComunicado;
use Database\Factories\ComunicadoFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Support\Carbon;

/**
 * @property int $id
 * @property TipoComunicado $tipo
 * @property string $titulo
 * @property string $descripcion
 * @property Carbon $fecha
 * @property int $autor_id
 * @property array<int, string>|null $roles
 * @property-read User|null $autor
 */
#[Fillable(['tipo', 'titulo', 'descripcion', 'fecha', 'autor_id', 'roles'])]
class Comunicado extends Model
{
    /** @use HasFactory<ComunicadoFactory> */
    use HasFactory;

    /**
     * @return BelongsTo<User, $this>
     */
    public function autor(): BelongsTo
    {
        return $this->belongsTo(User::class, 'autor_id');
    }

    /**
     * Visible para todos los roles cuando `roles` es null; si no, solo para los roles seleccionados.
     */
    public function visibleParaRol(Rol $rol): bool
    {
        return $this->roles === null || in_array($rol->value, $this->roles, true);
    }

    /**
     * @return array<int, string>
     */
    public function etiquetasRoles(): array
    {
        if ($this->roles === null) {
            return ['Todos los roles'];
        }

        return collect($this->roles)
            ->map(fn (string $valor) => Rol::from($valor)->label())
            ->all();
    }

    /**
     * Filtra los comunicados visibles para un rol dado (los de `roles: null` son para todos).
     *
     * @param  Builder<Comunicado>  $query
     * @return Builder<Comunicado>
     */
    public function scopeParaRol(Builder $query, Rol $rol): Builder
    {
        return $query->where(function (Builder $q) use ($rol) {
            $q->whereNull('roles')->orWhereJsonContains('roles', $rol->value);
        });
    }

    /**
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'fecha' => 'date',
            'tipo' => TipoComunicado::class,
            'roles' => 'array',
        ];
    }
}

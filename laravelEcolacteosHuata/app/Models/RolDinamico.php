<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsToMany;

class RolDinamico extends Model
{
    protected $table = 'authorization_roles';

    protected $guarded = ['id'];

    protected function casts(): array
    {
        return ['activo' => 'boolean', 'protegido' => 'boolean', 'pantallas' => 'array'];
    }

    public function usuarios(): BelongsToMany
    {
        return $this->belongsToMany(User::class, 'authorization_role_user', 'role_id', 'user_id');
    }

    public function label(): string
    {
        return $this->nombre;
    }

    public function getValueAttribute(): string
    {
        return $this->codigo;
    }
}

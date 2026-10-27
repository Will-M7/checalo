<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class DistribucionRoles extends Model
{
    protected $table = 'authorization_distributions';

    protected $guarded = ['id'];

    protected function casts(): array
    {
        return ['asignaciones' => 'array', 'origen' => 'array', 'predeterminada' => 'boolean', 'version' => 'integer'];
    }
}

<?php

namespace App\Models;

// use Illuminate\Contracts\Auth\MustVerifyEmail;
use App\Authorization\Autorizacion;
use App\Authorization\Pantallas;
use App\Concerns\HasTeams;
use App\Enums\Rol;
use Database\Factories\UserFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Attributes\Hidden;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Collection;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Relations\BelongsToMany;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Database\Eloquent\Relations\HasOne;
use Illuminate\Foundation\Auth\User as Authenticatable;
use Illuminate\Notifications\DatabaseNotification;
use Illuminate\Notifications\DatabaseNotificationCollection;
use Illuminate\Notifications\Notifiable;
use Illuminate\Support\Carbon;
use Illuminate\Support\Str;
use Laravel\Fortify\Contracts\PasskeyUser;
use Laravel\Fortify\PasskeyAuthenticatable;
use Laravel\Fortify\TwoFactorAuthenticatable;

/**
 * @property int $id
 * @property string $name
 * @property string $nombres
 * @property string $apellidos
 * @property string|null $documento
 * @property string|null $celular
 * @property Rol|null $rol
 * @property array<int, string>|null $roles
 * @property bool $toma_asistencia
 * @property string|null $usuario
 * @property bool $activo
 * @property string|null $email
 * @property Carbon|null $email_verified_at
 * @property string $password
 * @property string|null $two_factor_secret
 * @property string|null $two_factor_recovery_codes
 * @property Carbon|null $two_factor_confirmed_at
 * @property string|null $remember_token
 * @property int|null $current_team_id
 * @property Carbon|null $created_at
 * @property Carbon|null $updated_at
 * @property-read Team|null $currentTeam
 * @property-read Collection<int, Team> $ownedTeams
 * @property-read Collection<int, Membership> $teamMemberships
 * @property-read Collection<int, Team> $teams
 * @property-read Acopiador|null $acopiador
 * @property-read Proveedor|null $proveedor
 * @property-read Collection<int, Asistencia> $asistencias
 */
#[Fillable([
    'name', 'email', 'password', 'current_team_id',
    'nombres', 'apellidos', 'documento', 'celular', 'rol', 'roles', 'toma_asistencia', 'usuario', 'activo',
])]
#[Hidden(['password', 'two_factor_secret', 'two_factor_recovery_codes', 'remember_token'])]
class User extends Authenticatable implements PasskeyUser
{
    /** @use HasFactory<UserFactory> */
    use HasFactory, HasTeams, Notifiable, PasskeyAuthenticatable, TwoFactorAuthenticatable;

    public function rolesDinamicos(): BelongsToMany
    {
        return $this->belongsToMany(RolDinamico::class, 'authorization_role_user', 'user_id', 'role_id');
    }

    public function puedePantalla(string $pantalla): bool
    {
        return Autorizacion::permite($this, $pantalla);
    }

    public function puedeAdministracionCritica(): bool
    {
        return collect(Pantallas::CRITICAS)->every(fn ($p) => $this->puedePantalla($p));
    }

    protected static function booted(): void
    {
        static::created(fn (User $user) => Autorizacion::importarUsuario($user));
        static::updating(function (User $user) {
            if ($user->isDirty('activo') && ! $user->activo) {
                Autorizacion::protegerUsuario($user);
            }
            if ($user->isDirty(['rol', 'roles', 'toma_asistencia']) && ($actor = auth('web')->user())) {
                abort_unless($actor->puedePantalla('admin.asignar-roles'), 403);
            }
        });
        static::deleting(function (User $user) {
            Autorizacion::protegerUsuario($user);
            app(\App\Services\EliminarUsuarioSeguro::class)->comprobar($user);
        });
    }

    /**
     * Get the attributes that should be cast.
     *
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'email_verified_at' => 'datetime',
            'password' => 'hashed',
            'two_factor_confirmed_at' => 'datetime',
            'rol' => Rol::class,
            'roles' => 'array',
            'toma_asistencia' => 'boolean',
            'activo' => 'boolean',
            'pedidos_web_autorizado' => 'boolean',
            'pedidos_web_pausado' => 'boolean',
        ];
    }

    /**
     * Get the user's initials
     */
    public function initials(): string
    {
        $initials = Str::initials($this->name, true);

        return Str::length($initials) > 1
            ? Str::substr($initials, 0, 1).Str::substr($initials, -1)
            : $initials;
    }

    /**
     * Nombre completo mostrado en la UI (nombres + apellidos si existen, si no el name genérico).
     */
    public function nombreCompleto(): string
    {
        $completo = trim("{$this->nombres} {$this->apellidos}");

        return $completo !== '' ? $completo : $this->name;
    }

    public function esRol(Rol $rol): bool
    {
        return $this->rol === $rol;
    }

    /**
     * Todos los roles activos de este usuario. La mayoría de usuarios tiene uno solo (su `rol`
     * principal); un administrador puede asignarle roles adicionales desde "Asignar rol a
     * usuarios", que se guardan en la columna `roles`. `rol` principal siempre queda incluido.
     *
     * @return \Illuminate\Support\Collection<int, Rol>
     */
    public function rolesAsignados(): \Illuminate\Support\Collection
    {
        $adicionales = collect($this->roles ?? [])->map(fn (string $valor) => Rol::from($valor));

        return $adicionales->push($this->rol)->filter()->unique()->values();
    }

    public function tieneRol(Rol $rol): bool
    {
        return $this->rolesAsignados()->contains($rol);
    }

    /**
     * Usuarios cuyo rol principal, o cualquiera de sus roles adicionales, esté entre los
     * indicados. Útil para notificar a todos los usuarios con alguno de varios roles.
     *
     * @param  Builder<User>  $query
     * @param  array<int, Rol>  $roles
     * @return Builder<User>
     */
    public function scopeConAlgunRol(Builder $query, array $roles): Builder
    {
        $valores = collect($roles)->map(fn (Rol $rol) => $rol->value)->all();

        return $query->where(function (Builder $q) use ($valores) {
            $q->whereIn('rol', $valores);

            foreach ($valores as $valor) {
                $q->orWhereJsonContains('roles', $valor);
            }
        });
    }

    public function puedeVender(string $pantalla): bool
    {
        return $this->activo && $this->rolesDinamicos()->where('activo', true)->where('codigo', '!=', 'administrador')->whereJsonContains('pantallas', $pantalla)->exists();
    }

    public function scopeVendedorWeb(Builder $query): Builder
    {
        return $query->where('activo', true)->whereHas('rolesDinamicos', fn (Builder $roles) => $roles
            ->where('activo', true)->where('codigo', '!=', 'administrador')->whereJsonContains('pantallas', 'vendedor.pedidos-web'));
    }

    public function scopeDisponibleWeb(Builder $query): Builder
    {
        return $query->vendedorWeb()->where('pedidos_web_autorizado', true)->where('pedidos_web_pausado', false);
    }

    public function scopeConPantalla(Builder $query, string $pantalla): Builder
    {
        return $query->where('activo', true)->whereHas('rolesDinamicos', fn (Builder $roles) => $roles->where('activo', true)->whereJsonContains('pantallas', $pantalla));
    }

    /**
     * Navegación del sidebar para este usuario. Si tiene el rol Administrador (aunque no sea su
     * rol principal), usa la navegación agrupada completa del administrador. Si tiene un único
     * rol, devuelve null (el layout usa la lista plana de ese rol, como siempre). Si tiene varios
     * roles sin ser administrador, agrupa el sidebar por rol, cada uno con su propia navegación.
     *
     * @return array<string, array<int, array{route: string, label: string, icon: string}>>|null
     */
    public function navegacionAgrupada(): ?array
    {
        $catalogo = Pantallas::todas();
        $secciones = [];
        $vistos = [];
        $roles = Autorizacion::roles($this)->sortBy(fn ($r) => $r->codigo === 'administrador' ? 0 : 1);
        $adicionales = Autorizacion::adicionales($this);
        $asistenciaAdicional = in_array('asistencia.tomar', $adicionales, true) || $roles->contains('codigo', 'administrador');
        foreach ($roles as $rol) {
            if ($rol->codigo === 'tomar_asistencia' && $roles->count() > 1) {
                continue;
            }
            $pantallas = array_values(array_unique([...$rol->pantallas, ...$adicionales]));
            $historico = Rol::tryFrom($rol->codigo);
            $base = $historico?->navegacionAgrupada() ?? [$rol->nombre => $historico ? $this->navegacionDeRol($historico) : []];
            if ($historico === Rol::Administrador) {
                $base['Configuración'][] = $catalogo['admin.roles'];
            }
            foreach ($base as $grupo => $items) {
                foreach ($items as $item) {
                    $id = $item['route'];
                    if ($id === 'vendedor.configuracion-pagos') continue;
                    if ($id === 'asistencia.mis' && $asistenciaAdicional && ! $this->esAcopiador()) {
                        continue;
                    }
                    if (in_array($id, $pantallas, true) && ! isset($vistos[$id]) && $this->puedePantalla($id)) {
                        $secciones[$grupo][] = collect($item)->only(['route', 'label', 'icon'])->all();
                        $vistos[$id] = true;
                    }
                }
            }
            foreach ($pantallas as $id) {
                if ($id === 'vendedor.configuracion-pagos') continue;
                if ($id === 'asistencia.mis' && $asistenciaAdicional && ! $this->esAcopiador()) {
                    continue;
                }
                if (! isset($catalogo[$id]) || isset($vistos[$id]) || ! $this->puedePantalla($id)) {
                    continue;
                }
                $grupo = $historico === Rol::Administrador ? 'Funciones adicionales' : $rol->nombre;
                $secciones[$grupo][] = collect($catalogo[$id])->only(['route', 'label', 'icon'])->all();
                $vistos[$id] = true;
            }
        }

        return $secciones;
    }

    /**
     * Navegación de un rol ajustada a este usuario: si el administrador le asignó "Tomar
     * asistencia", ese módulo reemplaza a su "Asistencia" personal. El acopiador conserva
     * ambos, porque igual tiene que marcar su propia asistencia por horario.
     *
     * @return array<int, array{route: string, label: string, icon: string}>
     */
    public function navegacionDeRol(Rol $rol): array
    {
        if (! $this->puedeTomarAsistencia()) {
            return $rol->navegacion();
        }

        $tomarAsistencia = ['route' => 'asistencia.tomar', 'label' => 'Tomar asistencia', 'icon' => 'clipboard-document-check'];

        return collect($rol->navegacion())
            ->flatMap(fn (array $item) => match (true) {
                $item['route'] !== 'asistencia.mis' => [$item],
                $rol === Rol::Acopiador => [$tomarAsistencia, $item],
                default => [[...$tomarAsistencia, 'icon' => $item['icon']]],
            })
            ->values()
            ->all();
    }

    /**
     * El administrador siempre toma asistencia; los demás usuarios solo si se lo asignaron
     * desde "Asignar rol a usuarios".
     */
    public function puedeTomarAsistencia(): bool
    {
        return $this->puedePantalla('asistencia.tomar');
    }

    /**
     * Los acopiadores marcan su propia asistencia por horario en vez de aparecer en la tabla.
     */
    public function esAcopiador(): bool
    {
        return $this->tieneRol(Rol::Acopiador);
    }

    /**
     * Si se le toma asistencia desde la tabla general: no a los acopiadores (marcan por horario)
     * ni a quienes solo son proveedores (no se les toma asistencia).
     */
    public function participaEnAsistenciaGeneral(): bool
    {
        $roles = $this->rolesAsignados();

        return ! $roles->contains(Rol::Acopiador)
            && $roles->contains(fn (Rol $rol) => $rol !== Rol::Proveedor);
    }

    /**
     * @return HasMany<Asistencia, $this>
     */
    public function asistencias(): HasMany
    {
        return $this->hasMany(Asistencia::class);
    }

    /**
     * @return HasOne<Acopiador, $this>
     */
    public function acopiador(): HasOne
    {
        return $this->hasOne(Acopiador::class);
    }

    /**
     * @return HasOne<Proveedor, $this>
     */
    public function proveedor(): HasOne
    {
        return $this->hasOne(Proveedor::class);
    }

    /**
     * Notificaciones no leídas para la campanita de la cabecera.
     *
     * @return DatabaseNotificationCollection<int|string, DatabaseNotification>
     */
    public function notificacionesNoLeidas(): DatabaseNotificationCollection
    {
        return $this->unreadNotifications()->latest()->limit(10)->get();
    }
}

<?php

namespace App\Authorization;

use Livewire\ComponentHook;

/** Se ejecuta antes de mount, hydrate y cada acción; no depende del menú ni de la URL del POST. */
class AutorizarComponente extends ComponentHook
{
    public function boot(): void
    {
        $this->autorizar();
    }

    public function hydrate(): void
    {
        $this->autorizar();
    }

    public function call($method, $params, $returnEarly): void
    {
        $this->autorizar();
    }

    private function autorizar(): void
    {
        if ($usuario = auth('web')->user()) {
            abort_unless($usuario->activo, 403);
        }
        $nombre = $this->component->getName();
        if (! str_starts_with($nombre, 'pages::')) {
            return;
        }
        if (str_starts_with($nombre, 'pages::settings.') || str_starts_with($nombre, 'pages::teams.')) {
            abort_unless(auth('web')->user()?->activo, 403);

            return;
        }
        $pantalla = Pantallas::deComponente($nombre);
        abort_unless($pantalla && auth('web')->user()?->puedePantalla($pantalla), 403);
    }
}

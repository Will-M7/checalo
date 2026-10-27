<?php

namespace App\Livewire;

use App\Authorization\Distribuciones;
use App\Models\DistribucionRoles;
use Flux\Flux;
use Livewire\Attributes\Locked;
use Livewire\Attributes\On;
use Livewire\Component;

class DistribucionesRoles extends Component
{
    #[Locked]
    public bool $abierto = false;

    #[Locked]
    public array $datos = [];

    #[Locked]
    public ?array $vista = null;

    #[Locked]
    public ?int $distribucionId = null;

    #[Locked]
    public ?int $version = null;

    #[Locked]
    public bool $base = false;

    public string $nombre = '';

    public array $rolesSeleccionados = [];

    public array $funciones = [];

    public array $reemplazos = [];

    public bool $confirmado = false;

    #[On('abrir-distribuciones')]
    public function abrir(): void
    {
        $this->actualizar();
        $this->abierto = true;
        $this->nueva();
        Flux::modal('distribuciones')->show();
    }

    private function actualizar(): void
    {
        $this->datos = Distribuciones::catalogo(auth('web')->user());
    }

    public function nueva(): void
    {
        $this->actualizar();
        $this->distribucionId = null;
        $this->version = null;
        $this->base = false;
        $this->nombre = '';
        $this->rolesSeleccionados = [];
        $this->funciones = [];
        $this->vista = null;
        $this->confirmado = false;
        $this->resetValidation();
    }

    public function ver(int $id): void
    {
        $this->actualizar();
        $d = DistribucionRoles::findOrFail($id);
        $this->distribucionId = $d->id;
        $this->version = $d->version;
        $this->base = $d->tipo === 'base';
        $this->nombre = $d->nombre;
        $this->rolesSeleccionados = array_column($d->asignaciones, 'codigo');
        $this->funciones = collect($d->asignaciones)->pluck('pantallas', 'codigo')->all();
        $this->vista = null;
        $this->confirmado = false;
        $this->resetValidation();
    }

    public function updated(string $propiedad): void
    {
        if ($propiedad === 'nombre' || str_starts_with($propiedad, 'rolesSeleccionados') || str_starts_with($propiedad, 'funciones')) {
            $this->vista = null;
            $this->confirmado = false;
        }
    }

    public function quitarReferencia(string $codigo): void
    {
        $this->actualizar();
        abort_if($this->base, 403);
        $this->rolesSeleccionados = array_values(array_diff($this->rolesSeleccionados, [$codigo]));
        unset($this->funciones[$codigo]);
    }

    public function guardar(): void
    {
        $asignaciones = array_map(fn ($codigo) => ['codigo' => $codigo, 'pantallas' => $this->funciones[$codigo] ?? []], $this->rolesSeleccionados);
        $d = Distribuciones::guardar(auth('web')->user(), ['id' => $this->distribucionId, 'version' => $this->version, 'nombre' => $this->nombre, 'asignaciones' => $asignaciones]);
        $this->ver($d->id);
        Flux::toast('Distribución guardada. Las asignaciones activas no cambiaron.', variant: 'success');
    }

    public function copiar(bool $actual = false): void
    {
        $this->actualizar();
        if (! $actual && ! $this->distribucionId) {
            abort(422);
        }
        $d = Distribuciones::copiar(auth('web')->user(), $actual ? ($this->nombre ?: 'Copia de asignaciones actuales') : $this->nombre.' (copia)', $actual ? null : $this->distribucionId);
        $this->ver($d->id);
        Flux::toast('Copia editable guardada. No se ha aplicado.', variant: 'success');
    }

    public function previsualizar(string $operacion, ?string $historial = null): void
    {
        $lista = [];
        foreach ($this->reemplazos as $usuario => $codigo) {
            if ($codigo !== '') {
                $lista[] = ['usuario_id' => (string) $usuario, 'codigo' => $codigo];
            }
        }
        $this->vista = Distribuciones::previsualizar(auth('web')->user(), $operacion, $this->distribucionId, $historial, $lista);
        $this->confirmado = false;
        $this->resetValidation();
    }

    public function aplicar(): void
    {
        Distribuciones::confirmar(auth('web')->user(), $this->vista['token'] ?? '', $this->confirmado);
        $this->vista = null;
        $this->confirmado = false;
        $this->actualizar();
        Flux::toast('Operación confirmada en Laravel.', variant: 'success');
    }

    public function render()
    {
        return view('livewire.distribuciones-roles');
    }
}

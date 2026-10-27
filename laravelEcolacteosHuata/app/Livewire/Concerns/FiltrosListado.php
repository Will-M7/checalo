<?php

namespace App\Livewire\Concerns;

/** Estado exclusivo de lectura; nunca se utiliza para guardar formularios. */
trait FiltrosListado
{
    public array $filtros = [];

    public function filtro(string $nombre): string
    {
        $valor = $this->filtros[$nombre] ?? '';
        return is_scalar($valor) ? trim((string) $valor) : '';
    }

    public function updatedFiltros(): void
    {
        if (method_exists($this, 'resetPage')) $this->resetPage();
    }

    public function updatedBusqueda(): void
    {
        if (method_exists($this, 'resetPage')) $this->resetPage();
    }

    public function limpiarFiltros(): void
    {
        $this->filtros = [];
        foreach (['busqueda', 'busquedaProducto', 'filtroEstado', 'filtroTipo'] as $propiedad) {
            if (property_exists($this, $propiedad)) $this->{$propiedad} = '';
        }
        $this->updatedFiltros();
    }
}

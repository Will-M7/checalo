<?php

use App\Enums\Rol;
use App\Enums\TipoComunicado;
use App\Models\Comunicado;
use App\Models\User;
use App\Notifications\ComunicadoPublicado;
use Flux\Flux;
use Illuminate\Support\Facades\Notification;
use Livewire\Attributes\Validate;
use Livewire\Component;

new class extends Component
{
    #[Validate('required|string')]
    public string $tipo = 'comunicado';

    #[Validate('required|string|max:255')]
    public string $titulo = '';

    #[Validate('required|string|max:2000')]
    public string $descripcion = '';

    public bool $paraTodos = true;

    public array $rolesDestino = [];

    public ?int $eliminarId = null;

    public function with(): array
    {
        return [
            'tipos' => TipoComunicado::cases(),
            'roles' => Rol::cases(),
            'comunicados' => Comunicado::with('autor')->orderByDesc('fecha')->orderByDesc('id')->get(),
        ];
    }

    public function publicar(): void
    {
        $data = $this->validate();

        if (! $this->paraTodos && empty($this->rolesDestino)) {
            $this->addError('rolesDestino', 'Selecciona al menos un rol, o marca "Todos los roles".');

            return;
        }

        $comunicado = Comunicado::create([
            'tipo' => $data['tipo'],
            'titulo' => $data['titulo'],
            'descripcion' => $data['descripcion'],
            'fecha' => now()->toDateString(),
            'autor_id' => auth()->id(),
            'roles' => $this->paraTodos ? null : $this->rolesDestino,
        ]);

        $destinatarios = User::query()
            ->where('activo', true)
            ->where('id', '!=', auth()->id())
            ->when(
                ! $this->paraTodos,
                fn ($q) => $q->conAlgunRol(collect($this->rolesDestino)->map(fn (string $v) => Rol::from($v))->all())
            )
            ->get();

        Notification::send($destinatarios, new ComunicadoPublicado($comunicado));

        $this->reset(['titulo', 'descripcion', 'rolesDestino']);
        $this->tipo = 'comunicado';
        $this->paraTodos = true;

        Flux::toast('Publicado correctamente. Se notificó a '.$destinatarios->count().' usuario(s).', variant: 'success');
    }

    public function pedirEliminar(int $id): void
    {
        $this->eliminarId = $id;
        Flux::modal('confirmar-eliminar-comunicado')->show();
    }

    public function eliminar(): void
    {
        Comunicado::whereKey($this->eliminarId)->delete();

        Flux::modal('confirmar-eliminar-comunicado')->close();
        Flux::toast('Comunicado eliminado.', variant: 'success');
    }
};
?>

<div class="flex flex-col gap-6">
    <x-ui.titulo titulo="Comunicados y asesoramiento">Publica avisos y consejos visibles para todos los proveedores.</x-ui.titulo>

    <form wire:submit="publicar" class="flex flex-col gap-4 rounded-xl border border-huata-verde-100 p-5 dark:border-zinc-700 huata-cuadro">
        <flux:select label="Tipo" wire:model="tipo">
            @foreach ($tipos as $t)
                <flux:select.option value="{{ $t->value }}">{{ $t->label() }}</flux:select.option>
            @endforeach
        </flux:select>

        <flux:input label="Título" wire:model="titulo" placeholder="Cambio de horario de recojo" />
        <flux:textarea label="Descripción" wire:model="descripcion" rows="4" />

        <div>
            <flux:switch label="Todos los roles" wire:model.live="paraTodos" />

            @unless ($paraTodos)
                <div class="mt-3 flex flex-col gap-2 rounded-lg border border-huata-verde-100 p-3 dark:border-zinc-700">
                    <flux:text class="text-xs text-zinc-500">Selecciona uno o varios roles destinatarios:</flux:text>
                    <div class="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
                        @foreach ($roles as $rol)
                            <flux:checkbox
                                wire:key="rol-{{ $rol->value }}"
                                value="{{ $rol->value }}"
                                wire:model="rolesDestino"
                                :label="$rol->label()"
                            />
                        @endforeach
                    </div>
                    @error('rolesDestino')
                        <flux:text class="text-huata-rojo-600">{{ $message }}</flux:text>
                    @enderror
                </div>
            @endunless
        </div>

        <div class="flex justify-end">
            <flux:button type="submit" variant="primary" icon="megaphone">Publicar</flux:button>
        </div>
    </form>

    <div class="flex flex-col gap-3">
        @forelse ($comunicados as $comunicado)
            <div class="flex items-start justify-between gap-4 rounded-xl border border-huata-verde-100 p-4 dark:border-zinc-700 huata-cuadro">
                <div>
                    <div class="mb-1 flex flex-wrap items-center gap-2">
                        <flux:badge :color="$comunicado->tipo->value === 'asesoramiento' ? 'blue' : 'green'" size="sm">
                            {{ $comunicado->tipo->label() }}
                        </flux:badge>
                        <flux:badge color="zinc" size="sm">{{ implode(', ', $comunicado->etiquetasRoles()) }}</flux:badge>
                        <flux:text class="text-xs text-zinc-500">{{ $comunicado->fecha->format('d/m/Y') }} &middot; {{ $comunicado->autor?->nombreCompleto() }}</flux:text>
                    </div>
                    <flux:heading size="sm">{{ $comunicado->titulo }}</flux:heading>
                    <flux:text>{{ $comunicado->descripcion }}</flux:text>
                </div>
                <flux:modal.trigger name="confirmar-eliminar-comunicado">
                    <flux:button size="sm" variant="ghost" icon="trash" wire:click="pedirEliminar({{ $comunicado->id }})" />
                </flux:modal.trigger>
            </div>
        @empty
            <div class="rounded-xl border border-huata-verde-100 p-6 text-center text-zinc-500 dark:border-zinc-700 huata-cuadro">
                No hay comunicados publicados todavía.
            </div>
        @endforelse
    </div>

    <x-confirm-modal
        name="confirmar-eliminar-comunicado"
        accion="eliminar"
        titulo="¿Eliminar este comunicado?"
        mensaje="Esta acción no se puede deshacer."
    />
</div>

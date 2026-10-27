@props([
    'name',
    'accion',
    'titulo' => '¿Estás seguro?',
    'mensaje' => 'Esta acción no se puede deshacer.',
    'textoConfirmar' => 'Eliminar',
    'variante' => 'danger',
    'icono' => 'exclamation-triangle',
])

<flux:modal :name="$name" class="md:w-96">
    <div class="flex flex-col gap-5">
        <div class="flex items-start gap-3">
            <div
                @class([
                    'flex size-10 shrink-0 items-center justify-center rounded-full',
                    'bg-huata-rojo-50 dark:bg-huata-rojo-500/10' => $variante === 'danger',
                    'bg-huata-verde-50 dark:bg-huata-verde-500/10' => $variante !== 'danger',
                ])
            >
                <flux:icon
                    :name="$icono"
                    @class([
                        'size-5',
                        'text-huata-rojo-500' => $variante === 'danger',
                        'text-huata-verde-600 dark:text-huata-verde-400' => $variante !== 'danger',
                    ])
                />
            </div>
            <div>
                <flux:heading size="lg">{{ $titulo }}</flux:heading>
                <flux:subheading>{{ $mensaje }}</flux:subheading>
            </div>
        </div>

        <div class="flex justify-end gap-2">
            <flux:modal.close>
                <flux:button variant="ghost">Cancelar</flux:button>
            </flux:modal.close>
            <flux:button variant="{{ $variante }}" wire:click="{{ $accion }}">{{ $textoConfirmar }}</flux:button>
        </div>
    </div>
</flux:modal>

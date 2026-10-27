@props([
    'sidebar' => false,
])

@if($sidebar)
    <flux:sidebar.brand :name="config('app.name', 'Laravel')" {{ $attributes }}>
        <x-slot name="logo" class="flex aspect-square size-8 items-center justify-center rounded-md bg-white ring-1 ring-huata-verde-200 dark:ring-huata-verde-800">
            <x-app-logo-icon class="size-7" />
        </x-slot>
    </flux:sidebar.brand>
@else
    <flux:brand :name="config('app.name', 'Laravel')" {{ $attributes }}>
        <x-slot name="logo" class="flex aspect-square size-8 items-center justify-center rounded-md bg-white ring-1 ring-huata-verde-200 dark:ring-huata-verde-800">
            <x-app-logo-icon class="size-7" />
        </x-slot>
    </flux:brand>
@endif

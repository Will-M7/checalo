@props([
    'title',
    'description',
])

<div class="flex w-full flex-col gap-1.5 text-start">
    <h1 class="text-balance text-2xl font-semibold tracking-tight text-[var(--hh-texto)] sm:text-3xl">{{ $title }}</h1>
    <p class="text-pretty text-sm text-[var(--hh-texto-2)] sm:text-base">{{ $description }}</p>
</div>

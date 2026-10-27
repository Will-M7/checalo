<x-layouts::app.sidebar :title="$title ?? null">
    <flux:main class="animate-huata-in">
        {{ $slot }}

        <footer class="hh-pie mt-12 flex flex-col gap-2 border-t border-[var(--hh-borde)] pt-5 text-xs text-[var(--hh-texto-2)] sm:flex-row sm:items-center sm:justify-between">
            <span class="flex items-center gap-2">
                <img src="{{ asset('images/logo-huata.jpg') }}" alt="" width="22" height="24" class="h-6 w-auto rounded bg-white p-0.5 ring-1 ring-[var(--hh-borde)]">
                &copy; {{ now()->year }} {{ config('app.name') }} &middot; Sistema de gestión
            </span>
            <span>Huata, Puno, Perú</span>
        </footer>
    </flux:main>
</x-layouts::app.sidebar>

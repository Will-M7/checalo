<!DOCTYPE html>
<html lang="{{ str_replace('_', '-', app()->getLocale()) }}">
    <head>
        @include('partials.head')
    </head>
    <body class="hh-cuerpo">
        @php
            $usuarioActual = auth()->user();
            $rol = $usuarioActual?->rol;
            $rolesCentrales = $usuarioActual ? \App\Authorization\Autorizacion::roles($usuarioActual) : collect();
            $rolesActuales = $rolesCentrales->pluck('nombre')->join(', ');
            $rolAbierto = $rolesCentrales->contains(fn ($r) => !\App\Enums\Rol::tryFrom($r->codigo) && $r->codigo !== 'tomar_asistencia');

            // Secciones del menú: el administrador las trae agrupadas; los demás roles, en una sola lista.
            $secciones = $usuarioActual?->navegacionAgrupada()
                ?? ($rol ? [$rol->label() => $usuarioActual->navegacionDeRol($rol)] : []);

            $todosLosModulos = collect($secciones)->flatMap(fn ($items, $grupo) => collect($items)->map(fn ($item) => [...$item, 'grupo' => $grupo]))->values();
            $moduloActual = $todosLosModulos->first(fn ($item) => request()->routeIs($item['route']));
            $paginaActual = $moduloActual['label'] ?? ($title ?? 'Configuración');

            // Buscador global: solo módulos reales a los que este usuario tiene acceso.
            $modulosBusqueda = $todosLosModulos->map(fn ($item) => [
                'label' => $item['label'],
                'grupo' => $item['grupo'],
                'icon' => $item['icon'],
                'url' => route($item['route']),
            ])->all();
        @endphp

        <flux:sidebar sticky collapsible class="hh-lateral">
            <flux:sidebar.header class="gap-1">
                <a href="{{ route('dashboard') }}" wire:navigate class="flex min-w-0 items-center gap-3 rounded-xl focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--hh-marca)]">
                    <span class="grid size-11 shrink-0 place-items-center rounded-xl border border-[var(--hh-lateral-borde)] bg-white p-1 shadow-[0_4px_12px_rgb(23_61_114/0.12)]">
                        <img src="{{ asset('images/logo-huata.jpg') }}" alt="Ecolácteos Huata" width="44" height="48" class="size-full object-contain">
                    </span>
                    <span class="min-w-0 in-data-flux-sidebar-collapsed-desktop:hidden">
                        <span class="block truncate font-display text-[0.84rem] font-bold tracking-tight text-[var(--hh-lateral-texto)]">Ecolácteos Huata</span>
                        <span class="block truncate text-[11px] font-medium text-[var(--hh-lateral-texto-2)]">Sistema de gestión</span>
                    </span>
                </a>
                <flux:sidebar.collapse />
            </flux:sidebar.header>

            @if ($rol)
                <div class="hh-lateral-rol flex items-center gap-3 rounded-xl p-3 in-data-flux-sidebar-collapsed-desktop:hidden">
                    <span class="grid size-9 shrink-0 place-items-center rounded-lg bg-[var(--hh-marca-suave)] text-[var(--hh-marca)]"><flux:icon :icon="$rolAbierto ? 'squares-2x2' : $rol->iconoPanel()" class="size-5" /></span>
                    <span class="min-w-0">
                        <span class="block truncate text-[10px] font-bold uppercase tracking-[0.16em] text-[var(--hh-marca)]">{{ $rolAbierto ? $rolesActuales : $rol->etiquetaPanel() }}</span>
                        <span class="block truncate text-sm font-semibold text-[var(--hh-lateral-texto)]">{{ $rolAbierto ? 'Funciones asignadas' : $rol->subtituloPanel() }}</span>
                    </span>
                </div>
            @endif

            <flux:sidebar.nav class="min-h-0 flex-1 gap-3 overflow-y-auto overscroll-contain">
                @foreach ($secciones as $grupo => $items)
                    <div class="flex flex-col" role="group" aria-label="{{ $grupo }}">
                        <div class="hh-grupo-titulo px-3 pb-1 pt-1 text-[10px] font-bold uppercase tracking-[0.16em] in-data-flux-sidebar-collapsed-desktop:hidden">{{ $grupo }}</div>
                        @foreach ($items as $item)
                            <flux:sidebar.item
                                :icon="$item['icon']"
                                :href="route($item['route'])"
                                :current="request()->routeIs($item['route'])"
                                wire:navigate
                            >
                                {{ $item['label'] }}
                            </flux:sidebar.item>
                        @endforeach
                    </div>
                @endforeach
            </flux:sidebar.nav>

            {{-- Pie: sitio público, perfil, configuración y cierre de sesión --}}
            <div class="hh-lateral-usuario flex flex-col gap-0.5 pt-3">
                <flux:sidebar.item icon="arrow-top-right-on-square" :href="route('home')" target="_blank" class="in-data-flux-sidebar-collapsed-desktop:mx-auto">
                    Ver sitio web
                </flux:sidebar.item>
                <flux:sidebar.item icon="cog-6-tooth" :href="route('profile.edit')" :current="request()->routeIs('profile.edit', 'appearance.edit', 'security.edit')" wire:navigate class="in-data-flux-sidebar-collapsed-desktop:mx-auto">
                    Configuración
                </flux:sidebar.item>
                <form method="POST" action="{{ route('logout') }}" class="w-full">
                    @csrf
                    <flux:sidebar.item as="button" type="submit" icon="arrow-right-start-on-rectangle" class="in-data-flux-sidebar-collapsed-desktop:mx-auto">
                        Cerrar sesión
                    </flux:sidebar.item>
                </form>

                <div class="mt-2 flex items-center gap-3 rounded-xl border border-[var(--hh-lateral-borde)] bg-white p-2.5 in-data-flux-sidebar-collapsed-desktop:hidden dark:bg-white/5">
                    <flux:avatar :name="$usuarioActual?->nombreCompleto()" size="sm" color="auto" :color:seed="$usuarioActual?->id" />
                    <span class="min-w-0">
                        <span class="block truncate text-sm font-semibold text-[var(--hh-lateral-texto)]">{{ $usuarioActual?->nombreCompleto() }}</span>
                        <span class="block truncate text-xs text-[var(--hh-lateral-texto-2)]">{{ $rolesActuales }}</span>
                    </span>
                </div>
            </div>
        </flux:sidebar>

        <flux:header container sticky class="hh-superior gap-2">
            <flux:sidebar.toggle class="mr-1 lg:hidden" icon="bars-2" inset="left" />

            <nav aria-label="Ruta de navegación" class="flex min-w-0 items-center gap-2 text-sm">
                <span class="hidden truncate font-medium text-[var(--hh-texto-2)] sm:inline">{{ $rolAbierto ? $rolesActuales : ($rol?->etiquetaPanel() ?? config('app.name')) }}</span>
                <flux:icon icon="chevron-right" class="hidden size-3.5 shrink-0 text-[var(--hh-texto-2)] sm:block" />
                <span class="truncate font-bold text-[var(--hh-texto)]" aria-current="page">{{ $paginaActual }}</span>
            </nav>

            <flux:spacer />

            {{-- Buscador global de módulos (Ctrl + K) --}}
            <div
                x-data="{
                    abierto: false,
                    q: '',
                    sel: 0,
                    items: @js($modulosBusqueda),
                    get filtrados() {
                        const q = this.q.trim().toLowerCase();
                        return q ? this.items.filter((i) => (i.label + ' ' + i.grupo).toLowerCase().includes(q)) : this.items;
                    },
                    abrir() { this.abierto = true; this.q = ''; this.sel = 0; this.$nextTick(() => this.$refs.q.focus()); },
                    ir(item) { if (!item) return; this.abierto = false; Livewire.navigate(item.url); },
                }"
                x-on:keydown.window.ctrl.k.prevent="abrir()"
                x-on:keydown.window.meta.k.prevent="abrir()"
                x-on:keydown.escape.window="abierto = false"
            >
                <button type="button" x-on:click="abrir()" class="hh-buscador hidden h-10 w-64 items-center gap-2 rounded-xl px-3 text-sm md:flex" aria-label="Buscar módulos">
                    <flux:icon icon="magnifying-glass" class="size-4" />
                    <span class="flex-1 text-start">Buscar módulos…</span>
                    <kbd class="rounded-md border border-[var(--hh-borde-fuerte)] px-1.5 py-0.5 text-[10px] font-semibold">Ctrl K</kbd>
                </button>
                <flux:button variant="ghost" square icon="magnifying-glass" class="md:hidden" aria-label="Buscar módulos" x-on:click="abrir()" />

                <template x-teleport="body">
                    <div x-show="abierto" x-cloak class="fixed inset-0 z-[70] grid place-items-start justify-items-center p-4 pt-[12vh]" role="dialog" aria-modal="true" aria-label="Buscar módulos">
                        <div class="absolute inset-0 bg-[#061b3f]/55 backdrop-blur-sm" x-on:click="abierto = false" x-transition.opacity></div>
                        <div x-show="abierto" x-transition:enter="transition duration-200 ease-out" x-transition:enter-start="opacity-0 translate-y-3 scale-[0.98]" x-transition:enter-end="opacity-100 translate-y-0 scale-100" x-trap.noscroll="abierto" class="hh-panel-solido relative w-full max-w-lg overflow-hidden shadow-[0_24px_60px_rgb(6_27_63/0.35)]">
                            <div class="flex items-center gap-3 border-b border-[var(--hh-borde)] px-4">
                                <flux:icon icon="magnifying-glass" class="size-5 text-[var(--hh-texto-2)]" />
                                <input
                                    x-ref="q" x-model="q" type="text" placeholder="¿A qué módulo quieres ir?" autocomplete="off"
                                    class="h-14 w-full bg-transparent text-base text-[var(--hh-texto)] outline-none placeholder:text-[var(--hh-texto-2)]"
                                    x-on:input="sel = 0"
                                    x-on:keydown.arrow-down.prevent="sel = Math.min(sel + 1, filtrados.length - 1)"
                                    x-on:keydown.arrow-up.prevent="sel = Math.max(sel - 1, 0)"
                                    x-on:keydown.enter.prevent="ir(filtrados[sel])"
                                >
                            </div>
                            <ul class="max-h-[50vh] overflow-y-auto p-2" role="listbox">
                                <template x-for="(item, i) in filtrados" :key="item.url">
                                    <li role="option" :aria-selected="i === sel">
                                        <button type="button" class="flex w-full items-center justify-between gap-3 rounded-xl px-3 py-2.5 text-start text-sm transition-colors" :class="i === sel ? 'bg-[var(--hh-marca-suave)] text-[var(--hh-marca)]' : 'text-[var(--hh-texto)] hover:bg-[var(--hh-superficie-2)]'" x-on:click="ir(item)" x-on:mouseenter="sel = i">
                                            <span class="font-semibold" x-text="item.label"></span>
                                            <span class="text-xs text-[var(--hh-texto-2)]" x-text="item.grupo"></span>
                                        </button>
                                    </li>
                                </template>
                                <li x-show="filtrados.length === 0" class="px-3 py-8 text-center text-sm text-[var(--hh-texto-2)]">No encontramos módulos con “<span x-text="q"></span>”.</li>
                            </ul>
                        </div>
                    </div>
                </template>
            </div>

            {{-- Tema claro, oscuro o del sistema --}}
            <flux:dropdown position="bottom" align="end">
                <flux:button variant="ghost" square aria-label="Cambiar tema">
                    <flux:icon icon="sun" class="size-5 dark:hidden" />
                    <flux:icon icon="moon" class="hidden size-5 dark:block" />
                </flux:button>
                <flux:menu>
                    <flux:menu.item icon="sun" x-on:click="$flux.appearance = 'light'">Claro</flux:menu.item>
                    <flux:menu.item icon="moon" x-on:click="$flux.appearance = 'dark'">Oscuro</flux:menu.item>
                    <flux:menu.item icon="computer-desktop" x-on:click="$flux.appearance = 'system'">Del sistema</flux:menu.item>
                </flux:menu>
            </flux:dropdown>

            <livewire:notification-bell />

            <x-desktop-user-menu />
        </flux:header>

        {{ $slot }}

        @persist('toast')
            <flux:toast.group>
                <flux:toast />
            </flux:toast.group>
        @endpersist

        @fluxScripts
    </body>
</html>

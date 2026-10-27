<!DOCTYPE html>
<html lang="{{ str_replace('_', '-', app()->getLocale()) }}">
    <head>
        @include('partials.head')
    </head>
    <body class="hh-cuerpo antialiased">
        <div class="min-h-svh p-3 sm:p-6 lg:p-8">
            <div class="hh-entra mx-auto grid min-h-[calc(100svh-1.5rem)] max-w-6xl overflow-hidden rounded-[1.75rem] border border-[var(--hh-borde)] bg-[var(--hh-superficie-solida)] shadow-[0_30px_80px_rgb(23_61_114/0.16)] sm:min-h-[calc(100svh-3rem)] lg:min-h-[calc(100svh-4rem)] lg:grid-cols-[1.05fr_0.95fr]">
                {{-- Presentación de la marca (solo escritorio) --}}
                <section
                    class="relative hidden flex-col justify-between overflow-hidden p-10 text-white lg:flex xl:p-12"
                    style="background-image: linear-gradient(165deg, rgba(23, 61, 114, .93) 0%, rgba(40, 100, 180, .82) 55%, rgba(23, 61, 114, .94) 100%), url('{{ asset('images/fondo-huata.jpg') }}'); background-size: cover; background-position: 15% 60%;"
                    aria-label="Ecolácteos Huata"
                >
                    <div class="flex items-center gap-4">
                        <span class="flex size-14 items-center justify-center rounded-2xl bg-white p-1.5 shadow-[0_10px_30px_rgb(0_0_0/0.25)]">
                            <x-app-logo-icon class="size-full" />
                        </span>
                        <div>
                            <p class="font-display text-xl font-bold tracking-tight">{{ config('app.name') }}</p>
                            <p class="text-sm text-sky-100/80">Sistema de gestión</p>
                        </div>
                    </div>

                    <div>
                        <h1 class="max-w-md text-balance font-display text-4xl font-bold leading-[1.08] tracking-tight xl:text-5xl">
                            Del campo a tu mesa, en un solo sistema.
                        </h1>
                        <p class="mt-5 max-w-md text-pretty text-base leading-relaxed text-sky-100/85">
                            Acopio, laboratorio, producción y ventas de la leche de las familias de Huata, con cada paso registrado y a la vista.
                        </p>

                        <ol class="mt-9 flex max-w-sm flex-col" aria-label="Etapas del proceso">
                            @foreach (['Acopio' => 'Recojo diario en las fincas', 'Laboratorio' => 'Control de calidad de cada recojo', 'Producción' => 'Queso, yogurt y más con recetas propias', 'Ventas' => 'Boleta al instante'] as $etapa => $detalle)
                                <li class="relative flex gap-4 pb-5 last:pb-0">
                                    @unless ($loop->last)
                                        <span class="absolute left-[0.55rem] top-5 h-full w-px bg-white/30" aria-hidden="true"></span>
                                    @endunless
                                    <span class="relative mt-1 size-[1.15rem] shrink-0 rounded-full border-2 border-white bg-[#2864b4]" aria-hidden="true"></span>
                                    <span>
                                        <span class="block text-sm font-semibold">{{ $etapa }}</span>
                                        <span class="block text-sm text-sky-100/75">{{ $detalle }}</span>
                                    </span>
                                </li>
                            @endforeach
                        </ol>
                    </div>

                    <a href="{{ route('home') }}" class="inline-flex w-fit items-center gap-2 text-sm font-semibold text-white/90 underline-offset-4 hover:underline">
                        Ver el sitio web
                        <flux:icon icon="arrow-top-right-on-square" class="size-4" />
                    </a>
                </section>

                {{-- Formulario --}}
                <div class="flex flex-col justify-center px-6 py-10 sm:px-12 lg:px-14">
                    <div class="mx-auto flex w-full max-w-sm flex-col gap-8">
                        <div class="flex items-center gap-3 lg:hidden">
                            <span class="flex size-12 items-center justify-center rounded-xl border border-[var(--hh-borde)] bg-white p-1 shadow-sm">
                                <x-app-logo-icon class="size-full" />
                            </span>
                            <div>
                                <p class="font-display text-lg font-bold tracking-tight text-[var(--hh-texto)]">{{ config('app.name') }}</p>
                                <p class="text-xs text-[var(--hh-texto-2)]">Sistema de gestión</p>
                            </div>
                        </div>

                        <div>{{ $slot }}</div>

                        <div class="flex flex-col gap-2 border-t border-[var(--hh-borde)] pt-5 text-center text-xs text-[var(--hh-texto-2)]">
                            <a href="{{ route('home') }}" class="font-semibold text-[var(--hh-marca)] underline-offset-4 hover:underline lg:hidden">Volver al sitio web</a>
                            <p>&copy; {{ now()->year }} {{ config('app.name') }} &mdash; Huata, Puno, Perú</p>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        @persist('toast')
            <flux:toast.group>
                <flux:toast />
            </flux:toast.group>
        @endpersist

        @fluxScripts
    </body>
</html>

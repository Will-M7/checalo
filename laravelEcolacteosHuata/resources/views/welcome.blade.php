<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="csrf-token" content="{{ csrf_token() }}">
    <script>
        document.documentElement.classList.add('js');
        // Si la intro va a verse, se cubre la portada desde el primer pintado (evita el destello antes de que cargue el módulo)
        try {
            if (sessionStorage.getItem('huataIntroVista') !== '1' && !matchMedia('(prefers-reduced-motion: reduce)').matches) {
                document.documentElement.classList.add('con-intro');
            }
        } catch (e) {}
    </script>

    @php
        $titulo = 'Ecolácteos Huata | Lácteos frescos de Huata, Puno';
        $descripcion = 'Queso, yogurt y otros derivados lácteos hechos con la leche fresca de las familias productoras de Huata, Puno. Mira lo disponible y compra desde tu cuenta.';
        $imagenSocial = asset('images/fondo-huata.jpg');
        $nombreEmpresa = $empresa->razon_social ?: 'Ecolácteos Huata';
        $datosEstructurados = array_filter([
            '@context' => 'https://schema.org',
            '@type' => 'LocalBusiness',
            'name' => $nombreEmpresa,
            'description' => $descripcion,
            'url' => url('/'),
            'image' => $imagenSocial,
            'logo' => asset('images/logo-huata.jpg'),
            'telephone' => $empresa->telefono ?: null,
            'address' => [
                '@type' => 'PostalAddress',
                'streetAddress' => $empresa->direccion ?: 'Huata',
                'addressLocality' => 'Huata',
                'addressRegion' => 'Puno',
                'addressCountry' => 'PE',
            ],
        ]);
        // Punto de enfoque por foto del carrusel, para que el recorte horizontal conserve el sujeto.
        $enfoques = ['02-queso.jpg' => '50% 72%', '04-leche-con-frutas.jpg' => '50% 40%', '01-pastizal.jpg' => '35% 50%'];
        $slides = collect([asset('images/fondo-huata.jpg')])
            ->merge(collect(glob(public_path('images/carrusel/*.{jpg,jpeg,png,webp}'), GLOB_BRACE) ?: [])->sort()->map(fn ($ruta) => asset('images/carrusel/'.basename($ruta))))
            ->merge($productos->pluck('imagen')->filter()->take(4))
            ->unique()->values();
        $pluralUnidades = fn (float $n) => abs($n - 1) < 0.001 ? '1 unidad disponible' : rtrim(rtrim(number_format($n, 2), '0'), '.').' unidades disponibles';
        $consultaMapa = urlencode(($empresa->direccion ?: 'Huata').', Puno, Perú');
    @endphp

    <title>{{ $titulo }}</title>
    <meta name="description" content="{{ $descripcion }}">
    <link rel="canonical" href="{{ url('/') }}">
    <meta property="og:type" content="website">
    <meta property="og:locale" content="es_PE">
    <meta property="og:site_name" content="{{ $nombreEmpresa }}">
    <meta property="og:title" content="{{ $titulo }}">
    <meta property="og:description" content="{{ $descripcion }}">
    <meta property="og:url" content="{{ url('/') }}">
    <meta property="og:image" content="{{ $imagenSocial }}">
    <meta name="twitter:card" content="summary_large_image">
    <meta name="theme-color" content="#2864b4">
    <script type="application/ld+json">{!! json_encode($datosEstructurados, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_HEX_TAG) !!}</script>

    <link rel="icon" href="/favicon.ico" sizes="any">
    <link rel="icon" href="/favicon.svg" type="image/svg+xml">
    <link rel="apple-touch-icon" href="/apple-touch-icon.png">

    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Bricolage+Grotesque:opsz,wght@12..96,600;12..96,700;12..96,800&family=Figtree:wght@400;500;600;700&family=Playfair+Display:wght@500;600;700&display=swap" rel="stylesheet">

    @vite(['resources/css/landing.css', 'resources/js/intro3d.js', 'resources/js/hero3d.js', 'resources/js/productos3d.js', 'resources/js/interacciones.js'])
</head>
<body class="min-h-screen">
    {{-- Vaso de leche reutilizable (portada y tarjetas sin foto) --}}
    <svg width="0" height="0" class="absolute" aria-hidden="true" focusable="false">
        <defs>
            <linearGradient id="vl-cristal" x1="0" y1="0" x2="1" y2="0">
                <stop offset="0" stop-color="#fff" stop-opacity=".38"/>
                <stop offset=".35" stop-color="#fff" stop-opacity=".08"/>
                <stop offset=".7" stop-color="#fff" stop-opacity=".05"/>
                <stop offset="1" stop-color="#fff" stop-opacity=".3"/>
            </linearGradient>
            <linearGradient id="vl-leche" x1="0" y1="0" x2="1" y2="0">
                <stop offset="0" stop-color="#f3eedf"/>
                <stop offset=".45" stop-color="#fffef9"/>
                <stop offset="1" stop-color="#e9e2d0"/>
            </linearGradient>
        </defs>
        <symbol id="vaso-leche" viewBox="0 0 200 270">
            <ellipse cx="100" cy="252" rx="52" ry="8" fill="#000" opacity=".18"/>
            <path d="M52 44h96l-10 186q-1 12-14 12H76q-13 0-14-12z" fill="url(#vl-cristal)" stroke="#fff" stroke-opacity=".7" stroke-width="2"/>
            <path d="M55 84q45 12 90 0l-6 144q-1 10-12 10H73q-11 0-12-10z" fill="url(#vl-leche)" opacity=".95"/>
            <ellipse cx="100" cy="84" rx="45" ry="8" fill="#fffef9"/>
            <path d="M62 82q-7-26 2-38 3 20 8 36zM82 84q-3-34 6-48 4 22 6 48zM104 84q2-40 12-52 1 24-4 52zM126 82q6-22 16-28-2 20-10 30zM140 84q8-12 12-14-1 12-8 16z" fill="#fffef9"/>
            <circle cx="72" cy="30" r="4" fill="#fffef9"/>
            <circle cx="122" cy="22" r="3.5" fill="#fffef9"/>
            <circle cx="150" cy="44" r="3" fill="#fffef9"/>
            <rect x="62" y="56" width="7" height="160" rx="3.5" fill="#fff" opacity=".55"/>
            <rect x="136" y="66" width="3" height="120" rx="1.5" fill="#fff" opacity=".4"/>
            <ellipse cx="100" cy="44" rx="48" ry="7" fill="#fff" fill-opacity=".1" stroke="#fff" stroke-opacity=".8" stroke-width="2"/>
        </symbol>
    </svg>

    {{-- Intro 3D: partículas, logotipo y corriente de leche (una vez por visita; resources/js/intro3d.js) --}}
    <div id="intro" class="fixed inset-0 z-[100] hidden bg-cobalto-950 text-white" data-logo="{{ asset('images/logo-huata.jpg') }}" role="dialog" aria-label="Introducción: Ecolácteos Huata, del campo a tu mesa">
        <canvas id="intro-canvas" class="absolute inset-0 size-full" aria-hidden="true"></canvas>
        <div class="pointer-events-none absolute inset-x-0 bottom-[11%] z-10 px-4 text-center" style="perspective: 900px">
            <h2 id="intro-titulo" data-texto="ECOLÁCTEOS HUATA" class="font-display text-[clamp(1.7rem,6.5vw,4.2rem)] font-extrabold tracking-[0.1em]" style="transform-style: preserve-3d"></h2>
            <p id="intro-lema" class="mt-3 text-xs font-semibold tracking-[0.4em] text-white/75 opacity-0 sm:text-sm">DEL CAMPO A TU MESA</p>
        </div>
        <p id="intro-leyenda" class="pointer-events-none absolute inset-x-0 bottom-[4%] z-10 px-4 text-center text-xs font-bold tracking-[0.35em] text-sky-100 opacity-0 sm:text-sm" aria-live="polite"></p>
        <div class="absolute right-4 top-4 z-30 flex items-center gap-2 sm:right-6 sm:top-6">
            <button type="button" id="intro-pausa" class="inline-flex items-center gap-2 rounded-full bg-white/10 px-4 py-2 text-sm font-semibold text-white/90 backdrop-blur-sm hover:bg-white/20" aria-pressed="false">
                <svg class="size-3.5" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path class="ico-pausa" d="M7 5h4v14H7zM13 5h4v14h-4z"/><path class="ico-play hidden" d="M8 5l11 7-11 7z"/></svg><span class="txt">Pausar</span>
            </button>
            <button type="button" id="intro-saltar" class="rounded-full bg-white/10 px-4 py-2 text-sm font-semibold text-white/90 backdrop-blur-sm hover:bg-white/20">Saltar intro</button>
        </div>
    </div>

    <a href="#productos" class="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-50 focus:rounded-lg focus:bg-cobalto-950 focus:px-4 focus:py-2 focus:text-white">Saltar a los productos</a>

    {{-- Navegación --}}
    <header id="cabecera" data-solido="false" class="group fixed inset-x-0 top-0 z-40 border-b border-transparent text-tinta data-[solido=true]:border-tinta/10 data-[solido=true]:bg-leche/95 data-[solido=true]:text-tinta data-[solido=true]:backdrop-blur-sm">
        <nav class="mx-auto flex h-16 max-w-6xl items-center justify-between gap-4 px-4 sm:px-6" aria-label="Principal">
            <a href="#inicio" class="flex items-center gap-3">
                <img src="{{ asset('images/logo-huata.jpg') }}" alt="" width="40" height="44" class="h-10 w-auto rounded-lg bg-white p-0.5">
                <span class="whitespace-nowrap font-display text-lg font-bold tracking-tight">Ecolácteos Huata</span>
            </a>

            <ul class="hidden items-center gap-5 whitespace-nowrap text-[0.9rem] font-medium lg:flex xl:gap-7">
                <li><a href="#inicio" class="py-2 opacity-85 hover:opacity-100">Inicio</a></li>
                <li><a href="#somos" class="py-2 opacity-85 hover:opacity-100">Quiénes somos</a></li>
                <li><a href="#productos" class="py-2 opacity-85 hover:opacity-100">Productos</a></li>
                <li><a href="#proceso" class="py-2 opacity-85 hover:opacity-100">Cómo trabajamos</a></li>
                <li><a href="#contacto" class="py-2 opacity-85 hover:opacity-100">Contacto</a></li>
            </ul>

            <div class="flex items-center gap-2">
                @if ($productos->isNotEmpty())
                    <button type="button" id="carrito-abrir" class="relative grid size-11 place-items-center rounded-full hover:bg-white group-data-[solido=true]:hover:bg-cobalto-600/10" aria-haspopup="dialog" aria-controls="carrito" aria-label="Abrir carrito, vacío">
                        <svg class="size-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 4h2l2.4 11.2a1.5 1.5 0 0 0 1.5 1.200h8.700a1.5 1.5 0 0 0 1.450-1.130L21 8H6"/><circle cx="9.500" cy="20" r="1.300"/><circle cx="17" cy="20" r="1.300"/></svg>
                        <span id="carrito-contador" class="absolute -right-0.5 -top-0.5 hidden min-w-5 rounded-full bg-rojo-600 px-1.5 text-center text-xs font-bold leading-5 text-white" aria-hidden="true">0</span>
                    </button>
                @endif
                <a href="{{ route(auth('cliente')->check() ? 'cliente.cuenta' : 'cliente.login') }}" class="hidden rounded-full border border-current/40 px-4 py-2 text-sm font-semibold hover:border-current sm:inline-block">{{ auth('cliente')->check() ? 'Mi cuenta' : 'Iniciar sesión' }}</a>
                <button type="button" id="menu-btn" class="grid size-11 place-items-center rounded-full hover:bg-white lg:hidden" aria-expanded="false" aria-controls="menu-movil" aria-label="Abrir menú">
                    <svg class="size-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true">
                        <path class="menu-abrir" d="M4 7h16M4 12h16M4 17h16"/>
                        <path class="menu-cerrar hidden" d="M6 6l12 12M18 6L6 18"/>
                    </svg>
                </button>
            </div>
        </nav>

        <div id="menu-movil" class="hidden border-t border-tinta/10 bg-leche text-tinta lg:hidden">
            <ul class="mx-auto flex max-w-6xl flex-col px-4 py-2 text-base font-medium sm:px-6">
                <li><a href="#inicio" class="block py-3">Inicio</a></li>
                <li><a href="#somos" class="block py-3">Quiénes somos</a></li>
                <li><a href="#productos" class="block py-3">Productos</a></li>
                <li><a href="#proceso" class="block py-3">Cómo trabajamos</a></li>
                <li><a href="#contacto" class="block py-3">Contacto</a></li>
                <li><a href="{{ route(auth('cliente')->check() ? 'cliente.cuenta' : 'cliente.login') }}" class="block py-3 text-tinta-suave">{{ auth('cliente')->check() ? 'Mi cuenta' : 'Iniciar sesión' }}</a></li>
            </ul>
        </div>
    </header>

    <main>
        {{-- Portada --}}
        <section id="inicio" class="hero-escena relative bg-suero text-tinta" aria-label="Portada">
          <div class="hero-sticky relative isolate flex min-h-[100svh] items-center overflow-hidden">
            <div id="carrusel" class="hero-bg absolute inset-0 -z-10">
                <div class="paralaje absolute -inset-6">
                    @foreach ($slides as $i => $src)
                        <img src="{{ $src }}" alt="" @if ($i > 0) loading="lazy" @endif
                             style="object-position: {{ $enfoques[basename($src)] ?? '50% 50%' }}"
                             class="slide absolute inset-0 size-full object-cover opacity-0 data-[activo=true]:opacity-100" data-activo="{{ $i === 0 ? 'true' : 'false' }}" aria-hidden="true">
                    @endforeach
                </div>
                <div class="absolute inset-0 bg-[linear-gradient(100deg,rgb(247_250_252/0.97)_0%,rgb(234_243_251/0.9)_46%,rgb(234_243_251/0.3)_100%)]"></div>
            </div>
            <div class="hero-velo pointer-events-none absolute inset-0 -z-[5] bg-white opacity-0" aria-hidden="true"></div>

            <div class="hero-disco pointer-events-none absolute right-[2%] top-1/2 z-[4] hidden size-[40rem] -translate-y-1/2 rounded-full lg:block" aria-hidden="true"></div>

            {{-- Escena 3D: el vaso de leche vive en este canvas (resources/js/hero3d.js) --}}
            <div id="hero3d" class="absolute inset-0 z-[5] cursor-grab touch-pan-y" data-cursor="ARRASTRA PARA EXPLORAR" data-estado="cargando" role="img" aria-label="Vaso de leche en 3D. Muévelo con el mouse o arrástralo para girarlo.">
                {{-- Alternativa si el navegador no puede dibujar 3D --}}
                <div class="hero3d-fallback absolute inset-x-0 bottom-20 flex justify-center lg:inset-y-0 lg:left-[45%] lg:items-center" aria-hidden="true">
                    <div class="esfera relative aspect-square">
                        <div class="esfera-flota absolute inset-0">
                            <div class="esfera-cristal absolute inset-0 rounded-full border border-white/35 bg-white/10 backdrop-blur-md"></div>
                            <svg class="absolute inset-[14%] size-[72%] drop-shadow-[0_10px_18px_rgb(0_0_0/0.25)]" viewBox="0 0 200 270"><use href="#vaso-leche"/></svg>
                            <span class="gota" style="--x:38%;--d:0s"></span>
                            <span class="gota" style="--x:52%;--d:1.1s"></span>
                            <span class="gota" style="--x:64%;--d:2.2s"></span>
                        </div>
                    </div>
                </div>
            </div>

            <div class="pointer-events-none relative z-10 mx-auto w-full max-w-6xl px-4 pb-28 pt-28 sm:px-6">
                <div class="hero-copy pointer-events-auto max-w-2xl">
                    <p class="entra flex items-center gap-4 text-xs font-bold tracking-[0.3em] text-tinta sm:text-sm" style="--i:0"><span class="h-0.5 w-14 bg-sol" aria-hidden="true"></span>HUATA · PUNO · PERÚ</p>
                    <h1 class="entra mt-6 font-display font-extrabold uppercase leading-[0.9] tracking-[-0.04em] text-[clamp(2.9rem,9vw,6rem)]" style="--i:1">
                        El sabor<br>de lo<br><span class="font-normal normal-case italic tracking-normal text-cobalto-600" style="font-family: 'Playfair Display', Georgia, serif">auténtico.</span>
                    </h1>
                    <p class="entra mt-7 max-w-lg text-pretty text-lg leading-relaxed text-tinta/80 sm:text-xl" style="--i:2">
                        Leche fresca de nuestras familias productoras, transformada con cuidado en sabores que reúnen.
                    </p>
                    <div class="entra mt-9 flex flex-col gap-3 sm:flex-row" style="--i:3">
                        <a href="#productos" class="boton-magnetico inline-flex min-h-14 items-center justify-center gap-3 bg-cobalto-600 px-8 text-sm font-extrabold uppercase tracking-[0.12em] text-white shadow-[0_10px_24px_rgb(40_100_180/0.35)] transition-colors hover:bg-cobalto-700">Explorar productos <svg class="size-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.200" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M5 12h14M13 6l6 6-6 6"/></svg></a>
                        <a href="#somos" class="boton-magnetico inline-flex min-h-14 items-center justify-center border border-tinta/30 bg-white/60 px-8 text-sm font-extrabold uppercase tracking-[0.12em] text-tinta transition-colors hover:border-cobalto-600 hover:bg-white">Nuestra historia</a>
                    </div>
                </div>
            </div>

            {{-- Segundo momento del scroll: el vaso pasa al centro y la cámara se acerca --}}
            <div class="hero-caption pointer-events-none absolute inset-x-0 bottom-28 z-10 mx-auto max-w-6xl px-4 text-center sm:px-6" aria-hidden="true">
                <p class="text-4xl italic text-tinta sm:text-6xl" style="font-family: 'Playfair Display', Georgia, serif">De nuestras tierras,<br><span class="text-cobalto-600">a tu mesa.</span></p>
            </div>

            <p class="hero-hint pointer-events-none absolute bottom-16 right-4 z-10 hidden items-center gap-3 text-[0.7rem] font-bold tracking-[0.25em] text-tinta-suave sm:right-6 lg:flex" aria-hidden="true">
                <span class="grid size-11 place-items-center rounded-full border border-tinta/25"><svg class="size-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M8 12h8M8 12l3-3M8 12l3 3M16 12l-3-3M16 12l-3 3"/></svg></span>ARRASTRA PARA EXPLORAR
            </p>

            <svg class="ola-leche pointer-events-none absolute inset-x-0 bottom-[-1px] z-10" viewBox="0 0 1440 80" preserveAspectRatio="none" aria-hidden="true">
                <path fill="currentColor" d="M0 44C150 92 330 8 540 38s330 62 540 22c130-25 260-36 360-8V80H0z"/>
            </svg>

            @if ($slides->count() > 1)
                <div class="absolute inset-x-0 bottom-14 z-10 mx-auto flex max-w-6xl items-center gap-4 px-4 sm:px-6">
                    <ol id="indicadores" class="flex flex-1 gap-2" aria-label="Elegir foto de fondo">
                        @foreach ($slides as $i => $src)
                            <li class="max-w-24 flex-1">
                                <button type="button" data-ir="{{ $i }}" class="block h-6 w-full" aria-label="Foto {{ $i + 1 }} de {{ $slides->count() }}">
                                    <span class="block h-1 overflow-hidden rounded-full bg-tinta/15"><span class="ind-relleno block h-full w-0 bg-cobalto-600"></span></span>
                                </button>
                            </li>
                        @endforeach
                    </ol>
                    <button type="button" id="carrusel-pausa" class="grid size-11 place-items-center rounded-full border border-tinta/25 hover:bg-white" aria-label="Pausar carrusel" aria-pressed="false">
                        <svg class="size-4" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path class="ico-pausa" d="M7 5h4v14H7zM13 5h4v14h-4z"/><path class="ico-play hidden" d="M8 5l11 7-11 7z"/></svg>
                    </button>
                </div>
            @endif
          </div>
        </section>

        {{-- Lo que respalda cada compra --}}
        <section class="bg-leche" aria-label="Lo que respalda cada compra">
            <ul class="mx-auto grid max-w-6xl gap-px px-4 py-6 text-sm font-semibold sm:grid-cols-3 sm:px-6 sm:text-base">
                <li class="flex items-center gap-3 py-2"><svg class="size-6 shrink-0 text-cobalto-600" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 3c3 4 6 6.5 6 10a6 6 0 0 1-12 0c0-3.500 3-6 6-10z"/></svg>Leche acopiada cada día</li>
                <li class="flex items-center gap-3 py-2"><svg class="size-6 shrink-0 text-cobalto-600" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M9 3h6M10 3v6l-5 9a2 2 0 0 0 1.800 3h10.400a2 2 0 0 0 1.800-3l-5-9V3"/></svg>Control de laboratorio en cada recojo</li>
                <li class="flex items-center gap-3 py-2"><svg class="size-6 shrink-0 text-cobalto-600" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M7 3h10v18l-2.500-1.500L12 21l-2.500-1.500L7 21z"/><path d="M10 8h4M10 12h4"/></svg>Boleta en cada venta</li>
            </ul>
        </section>

        {{-- Vitrina 3D: elige un sabor y míralo desde todos los ángulos --}}
        <section id="vitrina" class="relative isolate overflow-hidden text-tinta" aria-label="Nuestros sabores en 3D">
            <div class="vitrina-fondo absolute inset-0 -z-10" style="background-color:#dbe9f7" aria-hidden="true"></div>
            <div class="absolute inset-0 -z-10 bg-[radial-gradient(ellipse_at_70%_45%,rgb(255_255_255/0.18),transparent_60%)]" aria-hidden="true"></div>
            <div class="mx-auto grid max-w-6xl items-center gap-6 px-4 py-16 sm:px-6 lg:grid-cols-[0.8fr_1.2fr] lg:gap-10 lg:py-24">
                <div class="relative z-10">
                    <h2 class="text-balance font-display text-3xl font-extrabold uppercase leading-[0.95] tracking-[-0.03em] sm:text-5xl">Elige tu <span class="font-normal normal-case italic tracking-normal text-cobalto-600" style="font-family: 'Playfair Display', Georgia, serif">sabor</span></h2>

                    <div role="tablist" aria-label="Productos de Ecolácteos Huata" class="mt-8 flex flex-wrap gap-2">
                        @foreach ([['leche', 'Leche'], ['queso', 'Queso'], ['yogur', 'Yogurt'], ['mantequilla', 'Mantequilla']] as $i => [$clave, $rotulo])
                            <button type="button" role="tab" data-vitrina="{{ $clave }}" aria-selected="{{ $i === 0 ? 'true' : 'false' }}" tabindex="{{ $i === 0 ? '0' : '-1' }}" class="boton-magnetico min-h-11 border border-tinta/25 bg-white/50 px-5 text-sm font-extrabold uppercase tracking-[0.12em] transition-colors hover:bg-white aria-selected:border-cobalto-600 aria-selected:bg-cobalto-600 aria-selected:text-tinta">{{ $rotulo }}</button>
                        @endforeach
                    </div>

                    <div id="vitrina-info" class="mt-10" aria-live="polite">
                        <h3 id="vitrina-nombre" class="font-display text-4xl font-extrabold tracking-tight sm:text-5xl">Leche fresca</h3>
                        <p id="vitrina-desc" class="mt-4 max-w-md text-pretty text-lg leading-relaxed text-tinta/80">Leche fresca de las familias productoras de Huata, acopiada cada día.</p>
                        <p id="vitrina-precio" class="tabular mt-5 hidden font-display text-3xl font-extrabold"></p>
                        <button type="button" id="vitrina-agregar" class="boton-magnetico mt-7 inline-flex min-h-14 items-center justify-center gap-3 bg-cobalto-600 px-8 text-sm font-extrabold uppercase tracking-[0.12em] text-white transition-colors hover:bg-cobalto-700"><span>Agregar al carrito</span><svg class="size-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.200" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M5 12h14M13 6l6 6-6 6"/></svg></button>
                    </div>
                </div>

                <div id="vitrina3d" data-cursor="ARRASTRA PARA EXPLORAR" data-estado="cargando" class="relative h-[24rem] cursor-grab touch-pan-y sm:h-[32rem] lg:h-[38rem]" role="img" aria-label="Producto en 3D. Arrástralo para girarlo y cámbialo con los botones de arriba."></div>
            </div>
        </section>

        {{-- Quiénes somos --}}
        <section id="somos" class="mx-auto max-w-6xl px-4 py-16 sm:px-6 md:py-24">
            <div class="rev grid gap-8 md:grid-cols-[0.9fr_1.1fr] md:gap-16">
                <h2 class="text-balance font-display text-3xl font-bold tracking-tight sm:text-4xl">Una asociación de productores de la comunidad de Huata</h2>
                <p class="text-pretty text-lg leading-relaxed text-tinta-suave">
                    Acopiamos la leche fresca de nuestros proveedores todos los días, la transformamos en queso, yogurt y otros derivados en nuestra planta, y la vendemos directamente a nuestra comunidad, cuidando la calidad en cada paso, desde el ordeño hasta el producto final.
                </p>
            </div>
        </section>

        {{-- ¿Cómo se hace la compra? --}}
        <section id="compra" class="bg-suero py-16 md:py-24">
            <div class="mx-auto max-w-6xl px-4 sm:px-6">
                <h2 class="rev max-w-xl text-balance font-display text-3xl font-bold tracking-tight sm:text-4xl">¿Cómo se hace la compra?</h2>
                <ol class="mt-10 grid gap-10 sm:grid-cols-2 lg:grid-cols-4 lg:gap-8">
                    @foreach ([['Elige tus productos', 'Mira abajo lo que tenemos disponible hoy.'], ['Agrégalos al carrito', 'Ajusta las cantidades y revisa tu total.'], ['Confirma desde tu cuenta', 'Elige cómo pagar y consulta el estado de tu pedido.'], ['Recibe o recoge', 'Tras confirmar la compra, consulta tu boleta y contacta al vendedor asignado.']] as $n => [$paso, $detalle])
                        <li class="rev" style="--r:{{ $n }}">
                            <span class="font-display text-5xl font-extrabold text-cobalto-600" aria-hidden="true">{{ $n + 1 }}</span>
                            <h3 class="mt-2 font-display text-xl font-bold">{{ $paso }}</h3>
                            <p class="mt-2 max-w-xs text-pretty text-tinta-suave">{{ $detalle }}</p>
                        </li>
                    @endforeach
                </ol>
            </div>
        </section>

        {{-- Productos disponibles (desde el rol de producción) --}}
        <section id="productos" class="mx-auto max-w-6xl px-4 py-16 sm:px-6 md:py-24">
            <div class="rev flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
                <div>
                    <h2 class="text-balance font-display text-3xl font-bold tracking-tight sm:text-4xl">Productos disponibles</h2>
                    <p class="mt-2 max-w-md text-pretty text-tinta-suave">Agrega lo que necesitas al carrito y completa tu compra desde tu cuenta.</p>
                </div>
                @if ($productos->isNotEmpty())
                    <div class="w-full md:max-w-xs">
                        <label for="buscar-producto" class="sr-only">Buscar un producto</label>
                        <input id="buscar-producto" type="search" autocomplete="off" placeholder="Buscar un producto" class="min-h-12 w-full rounded-full border border-tinta/20 bg-white px-5 text-base placeholder:text-tinta-suave/70">
                    </div>
                @endif
            </div>

            @if ($productos->isEmpty())
                <div class="rev mt-10 rounded-3xl border-2 border-dashed border-tinta/20 px-6 py-14 text-center">
                    <p class="font-display text-xl font-bold">Pronto publicaremos aquí nuestros productos frescos</p>
                    <p class="mx-auto mt-2 max-w-md text-tinta-suave">Producimos a diario; vuelve más tarde.</p>
                </div>
            @else
                <p id="sin-resultados" class="mt-10 hidden rounded-3xl bg-suero px-6 py-10 text-center font-semibold" role="status">No encontramos productos con ese nombre.</p>
                <ul id="lista-productos" class="mt-10 grid gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
                    @foreach ($productos as $producto)
                        @php $maximo = (int) floor($producto['disponible']); @endphp
                        <li class="tarjeta-producto rev relative flex flex-col overflow-hidden rounded-3xl bg-white shadow-[0_2px_4px_rgb(10_31_68/0.05),0_12px_28px_rgb(10_31_68/0.08)]" style="--r:{{ $loop->index % 4 }}" data-id="{{ $producto['id'] }}" data-nombre="{{ mb_strtolower($producto['nombre']) }}">
                            @if ($loop->first)
                                <span class="absolute left-4 top-4 z-10 rounded-full bg-sol px-3 py-1 text-xs font-extrabold tracking-wide text-cobalto-950">DESTACADO</span>
                            @endif
                            @if ($producto['imagen'])
                                <img src="{{ $producto['imagen'] }}" alt="{{ $producto['nombre'] }}" loading="lazy" width="640" height="480" class="aspect-[4/3] w-full object-cover">
                            @else
                                <div class="grid aspect-[4/3] w-full place-items-center bg-[radial-gradient(circle_at_50%_40%,#fff,#dbe7fb)]" aria-hidden="true">
                                    <svg class="h-4/5 w-auto" viewBox="0 0 200 270"><use href="#vaso-leche"/></svg>
                                </div>
                            @endif
                            <div class="flex flex-1 flex-col gap-2 p-5">
                                <h3 class="font-display text-lg font-bold leading-snug">{{ $producto['nombre'] }}</h3>
                                <p class="tabular text-sm font-semibold text-cobalto-700">{{ $pluralUnidades($producto['disponible']) }}</p>
                                <p class="tabular mt-1 font-display text-2xl font-extrabold">S/ {{ number_format($producto['precio'], 2) }} <span class="text-sm font-semibold text-tinta-suave">c/u</span></p>
                                <div class="mt-auto pt-3">
                                    @if ($maximo >= 1)
                                        <button type="button" data-agregar="{{ $producto['id'] }}" class="ctrl-agregar inline-flex min-h-12 w-full items-center justify-center gap-2 rounded-full bg-cobalto-600 px-5 text-base font-bold text-white transition-colors hover:bg-cobalto-700" aria-label="Agregar {{ $producto['nombre'] }} al carrito">
                                            <svg class="size-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.200" stroke-linecap="round" aria-hidden="true"><path d="M12 5v14M5 12h14"/></svg>Agregar
                                        </button>
                                        <div class="ctrl-cantidad hidden items-center justify-between gap-2 rounded-full border-2 border-cobalto-600 p-1">
                                            <button type="button" data-menos="{{ $producto['id'] }}" class="grid size-10 place-items-center rounded-full text-2xl font-bold text-cobalto-700 hover:bg-cielo-50" aria-label="Quitar una unidad de {{ $producto['nombre'] }}">−</button>
                                            <span class="tabular text-base font-bold" aria-live="polite"><span class="cant-en-carrito">1</span> en el carrito</span>
                                            <button type="button" data-mas="{{ $producto['id'] }}" class="grid size-10 place-items-center rounded-full text-2xl font-bold text-cobalto-700 hover:bg-cielo-50 disabled:opacity-35" aria-label="Agregar otra unidad de {{ $producto['nombre'] }}">+</button>
                                        </div>
                                    @else
                                        <p class="rounded-full bg-suero px-5 py-3 text-center text-sm font-semibold text-tinta-suave">Consulta en la tienda</p>
                                    @endif
                                </div>
                            </div>
                        </li>
                    @endforeach
                </ul>
            @endif
        </section>

        {{-- Cómo trabajamos --}}
        <section id="proceso" class="bg-[linear-gradient(135deg,#173d72_0%,#2864b4_100%)] py-16 text-white md:py-24">
            <div class="mx-auto max-w-6xl px-4 sm:px-6">
                <h2 class="rev max-w-xl text-balance font-display text-3xl font-bold tracking-tight sm:text-4xl">Cómo trabajamos</h2>
                <ol class="mt-10 grid divide-y divide-white/15 border-y border-white/15 md:grid-cols-4 md:divide-x md:divide-y-0">
                    @foreach ([['Acopio', 'Recogemos la leche fresca de la finca de cada proveedor, todos los días, por turnos.'], ['Calidad', 'Cada recojo pasa por control de laboratorio antes de entrar a producción.'], ['Producción', 'Elaboramos queso, yogurt y otros derivados con recetas propias.'], ['Venta', 'Vendemos directo a la comunidad, con boleta en el momento.']] as $n => [$etapa, $texto])
                        <li class="rev py-6 md:px-6 md:first:pl-0" style="--r:{{ $n }}">
                            <h3 class="font-display text-xl font-bold text-sol">{{ $etapa }}</h3>
                            <p class="mt-2 text-pretty text-cielo-50/80">{{ $texto }}</p>
                        </li>
                    @endforeach
                </ol>
            </div>
        </section>

        {{-- Contacto --}}
        <section id="contacto" class="mx-auto max-w-6xl px-4 py-16 sm:px-6 md:py-24">
            <div class="rev rounded-[2rem] bg-suero p-8 sm:p-12 md:flex md:items-center md:justify-between md:gap-12">
                <div>
                    <h2 class="text-balance font-display text-3xl font-bold tracking-tight sm:text-4xl">Contacto</h2>
                    <p class="mt-4 max-w-md text-pretty text-tinta-suave">Escríbenos si eres productor de la zona y quieres asociarte, o si tienes alguna consulta.</p>
                </div>
                <dl class="mt-8 grid gap-5 md:mt-0 md:min-w-[18rem]">
                    <div>
                        <dt class="text-sm font-semibold text-tinta-suave">Dirección</dt>
                        <dd class="mt-1 text-lg font-bold">{{ $empresa->direccion ?: 'Huata, Puno, Perú' }}</dd>
                        <dd class="mt-1"><a href="https://www.google.com/maps/search/?api=1&query={{ $consultaMapa }}" target="_blank" rel="noopener" class="text-sm font-semibold text-cobalto-700 underline underline-offset-4 hover:text-cobalto-900">Ver en el mapa</a></dd>
                    </div>
                    @if ($empresa->telefono)
                        <div>
                            <dt class="text-sm font-semibold text-tinta-suave">Teléfono</dt>
                            <dd class="mt-1 text-lg font-bold"><a href="tel:{{ preg_replace('/[^\d+]/', '', $empresa->telefono) }}" class="underline underline-offset-4 hover:text-cobalto-700">{{ $empresa->telefono }}</a></dd>
                        </div>
                    @endif
                </dl>
            </div>
        </section>
    </main>

    <div class="franja-aguayo" aria-hidden="true"></div>
    <footer>
        <div class="mx-auto flex max-w-6xl flex-col gap-4 px-4 pb-28 pt-8 text-sm text-tinta-suave sm:flex-row sm:items-center sm:justify-between sm:px-6 lg:pb-8">
            <p>© {{ now()->year }} {{ $nombreEmpresa }} · Asociación de Productores de Huata, Puno</p>
            <div class="flex flex-wrap items-center gap-x-6 gap-y-2">
                <button type="button" id="ver-intro" class="font-semibold text-tinta hover:text-cobalto-700">Volver a ver la intro</button>
                <a href="{{ route('login') }}" class="font-semibold text-tinta hover:text-cobalto-700">Iniciar sesión (socios y personal)</a>
            </div>
        </div>
    </footer>

    {{-- Carrito de compras --}}
    @if ($productos->isNotEmpty())
        <div id="carrito-fondo" class="fixed inset-0 z-[90] hidden bg-cobalto-950/60 opacity-0 backdrop-blur-[2px] transition-opacity duration-300" aria-hidden="true"></div>
        <aside id="carrito" role="dialog" aria-modal="true" aria-labelledby="carrito-titulo" tabindex="-1" class="fixed right-0 top-0 z-[95] flex h-dvh w-full max-w-md translate-x-full flex-col bg-leche shadow-[-12px_0_40px_rgb(6_27_63/0.25)] transition-transform duration-300 ease-out" inert>
            <div class="flex items-center justify-between gap-3 border-b border-tinta/10 px-5 py-4">
                <h2 id="carrito-titulo" class="font-display text-xl font-bold">Tu carrito <span id="carrito-titulo-cuenta" class="font-semibold text-tinta-suave"></span></h2>
                <button type="button" id="carrito-cerrar" class="grid size-11 place-items-center rounded-full hover:bg-suero" aria-label="Cerrar carrito">
                    <svg class="size-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18"/></svg>
                </button>
            </div>

            <div id="carrito-vacio" class="flex flex-1 flex-col items-center justify-center gap-3 px-8 text-center">
                <svg class="size-16 text-cobalto-600/40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.500" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 4h2l2.400 11.200a1.500 1.500 0 0 0 1.500 1.200h8.700a1.500 1.500 0 0 0 1.450-1.130L21 8H6"/><circle cx="9.500" cy="20" r="1.300"/><circle cx="17" cy="20" r="1.300"/></svg>
                <p class="font-display text-xl font-bold">Tu carrito está vacío</p>
                <p class="text-tinta-suave">Agrega productos y aquí verás el total de tu pedido.</p>
                <button type="button" data-cerrar-carrito class="mt-2 inline-flex min-h-12 items-center rounded-full bg-cobalto-600 px-6 font-bold text-white hover:bg-cobalto-700">Ver productos</button>
            </div>

            <div id="carrito-lleno" class="hidden min-h-0 flex-1 flex-col overflow-y-auto overscroll-contain">
                <ul id="carrito-lineas" class="shrink-0 divide-y divide-tinta/10 px-5"></ul>

                <div class="shrink-0 border-t border-tinta/10 bg-white px-5 pb-5 pt-4">
                    <dl class="flex flex-col gap-1 text-sm">
                        <div class="flex justify-between"><dt class="text-tinta-suave">Productos</dt><dd id="carrito-unidades" class="tabular font-semibold"></dd></div>
                        <div class="flex items-end justify-between border-t border-tinta/10 pt-3"><dt class="font-semibold">Total</dt><dd id="carrito-total" class="tabular font-display text-3xl font-extrabold"></dd></div>
                    </dl>

                    <div class="mt-4 grid gap-3 sm:grid-cols-2">
                        <div>
                            <label for="pedido-nombre" class="text-sm font-semibold">Tu nombre</label>
                            <input value="{{ auth('cliente')->user()?->nombreCompleto() }}" readonly id="pedido-nombre" type="text" autocomplete="name" maxlength="80" required class="mt-1 min-h-12 w-full rounded-xl border border-tinta/20 bg-white px-4 text-base placeholder:text-tinta-suave/70" placeholder="Ej. María Quispe">
                        </div>
                        <div>
                            <label for="pedido-celular" class="text-sm font-semibold">Celular de tu cuenta</label>
                            <input value="{{ auth('cliente')->user()?->celular }}" id="pedido-celular" type="tel" inputmode="tel" autocomplete="tel" maxlength="20" required class="mt-1 min-h-12 w-full rounded-xl border border-tinta/20 bg-white px-4 text-base placeholder:text-tinta-suave/70" placeholder="9xx xxx xxx o +51 9xx xxx xxx">
                        </div>
                    </div>

                    <fieldset class="mt-4">
                        <legend class="text-sm font-semibold">¿Cómo vas a pagar?</legend>
                        <div class="mt-2 grid grid-cols-3 gap-2">
                            @foreach ([['yape', 'Yape'], ['saldo', 'Saldo a favor'], ['plin', 'Plin'], ['efectivo', 'Efectivo']] as $i => [$valor, $nombreMetodo])
                                <label class="metodo-pago relative flex min-h-12 cursor-pointer items-center justify-center rounded-xl border-2 border-tinta/15 px-2 text-sm font-bold has-[:checked]:border-cobalto-600 has-[:checked]:bg-cielo-50 has-[:focus-visible]:outline has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-cobalto-700">
                                    <input type="radio" name="pedido-metodo" value="{{ $valor }}" class="sr-only" @checked($i === 0)>{{ $nombreMetodo }}
                                </label>
                            @endforeach
                        </div>
                        <p id="pago-instrucciones" class="mt-3 rounded-xl bg-cielo-50 p-3 text-sm">Primero guardaremos tu pedido. Después verás el receptor asignado, su QR y el importe exacto.</p>
                        <p class="mt-2 text-sm">No se reserva stock durante el pago. Si el ingreso se confirma sin stock, el importe pasa a tu saldo a favor.</p>
                    </fieldset>

                    <p id="pedido-error" class="mt-3 hidden rounded-xl bg-rojo-600/10 px-4 py-3 text-sm font-semibold text-rojo-700" role="alert"></p>

                    <button type="button" id="pedido-wa" class="sticky bottom-2 z-10 mt-4 inline-flex min-h-12 w-full items-center justify-center rounded-full bg-cobalto-600 px-7 text-base font-bold text-white shadow-[0_8px_24px_rgb(23_61_114/0.35)] transition-colors hover:bg-cobalto-700 disabled:opacity-60">Confirmar pedido</button>
                    <p class="mt-2 text-center text-xs text-tinta-suave">Inicia sesión como comprador para completar tu compra. Tu carrito se conserva al ingresar.</p>
                    <button type="button" id="carrito-vaciar" class="mt-3 w-full py-2 text-sm font-semibold text-tinta-suave underline underline-offset-4 hover:text-tinta">Vaciar carrito</button>
                </div>
            </div>

            <div id="carrito-listo" class="hidden flex-1 flex-col items-center justify-center gap-3 px-8 text-center" role="status">
                <span class="grid size-16 place-items-center rounded-full bg-cielo-50 text-cobalto-600"><svg class="size-9" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.500" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M5 12.500l4.500 4.500L19 7.500"/></svg></span>
                <p class="font-display text-2xl font-bold">¡Pedido enviado!</p>
                <p id="carrito-listo-detalle" class="text-tinta-suave"></p>
                <a id="carrito-listo-boleta" href="#" target="_blank" rel="noopener" class="inline-flex min-h-12 items-center gap-2 rounded-full bg-cobalto-600 px-6 font-bold text-white hover:bg-cobalto-700">
                    <svg class="size-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M7 3h10v18l-2.500-1.500L12 21l-2.500-1.500L7 21z"/><path d="M10 8h4M10 12h4"/></svg>Ver mi comprobante
                </a>
                <a id="carrito-listo-wa" href="#" target="_blank" rel="noopener" class="hidden text-sm font-semibold text-cobalto-700 underline underline-offset-4">Avisar a la tienda por WhatsApp</a>
                <button type="button" data-cerrar-carrito class="mt-2 inline-flex min-h-12 items-center rounded-full bg-cobalto-600 px-6 font-bold text-white hover:bg-cobalto-700">Seguir comprando</button>
            </div>
        </aside>

        {{-- Barra del carrito para el celular --}}
        <div id="barra-carrito" class="fixed inset-x-0 bottom-0 z-30 hidden translate-y-full border-t border-tinta/10 bg-leche/95 px-4 py-3 backdrop-blur-sm transition-transform duration-300 lg:hidden">
            <button type="button" id="barra-carrito-abrir" class="flex min-h-12 w-full items-center justify-between gap-3 rounded-full bg-cobalto-600 px-6 text-base font-bold text-white hover:bg-cobalto-700">
                <span>Ver carrito (<span id="barra-cuenta">0</span>)</span><span id="barra-total" class="tabular"></span>
            </button>
        </div>

        <div id="carrito-aviso" class="pointer-events-none fixed left-1/2 top-20 z-[96] hidden -translate-x-1/2 rounded-full bg-cobalto-950 px-5 py-3 text-sm font-semibold text-white shadow-lg" role="status" aria-live="polite"></div>
    @endif

    <script>
        window.HUATA = {
            productos: @json($productos->values()),
            whatsapp: @json($whatsapp),
            urlPedidos: @json(route('pedidos-web.store')),
            clienteAutenticado: @json(auth('cliente')->check()),
            urlLoginCliente: @json(route('cliente.login'))
        };

        // Volver a ver la intro
        document.getElementById('ver-intro').addEventListener('click', function () {
            try { sessionStorage.removeItem('huataIntroVista'); } catch (e) {}
            window.scrollTo(0, 0);
            window.location.reload();
        });
    </script>
    <script>
        // Red de seguridad: si el módulo de la intro no llegara a cargar, la portada se muestra igual.
        setTimeout(function () { document.body.classList.add('llegado'); }, 14000);

        (function () {
            var cab = document.getElementById('cabecera');
            var btnMenu = document.getElementById('menu-btn');
            function marcar() {
                var abierto = btnMenu.getAttribute('aria-expanded') === 'true';
                cab.dataset.solido = (window.scrollY > 24 || abierto) ? 'true' : 'false';
            }
            window.addEventListener('scroll', marcar, { passive: true });
            window.addEventListener('menu-cambio', marcar);
            marcar();
        })();

        (function () {
            var menu = document.getElementById('menu-movil');
            var btn = document.getElementById('menu-btn');
            function toggle(open) {
                menu.classList.toggle('hidden', !open);
                btn.setAttribute('aria-expanded', open ? 'true' : 'false');
                btn.setAttribute('aria-label', open ? 'Cerrar menú' : 'Abrir menú');
                btn.querySelector('.menu-abrir').classList.toggle('hidden', open);
                btn.querySelector('.menu-cerrar').classList.toggle('hidden', !open);
                window.dispatchEvent(new Event('menu-cambio'));
            }
            btn.addEventListener('click', function () { toggle(btn.getAttribute('aria-expanded') !== 'true'); });
            menu.addEventListener('click', function (e) { if (e.target.closest('a')) toggle(false); });
            document.addEventListener('keydown', function (e) { if (e.key === 'Escape') toggle(false); });
        })();

        (function () {
            var hero = document.getElementById('inicio');
            var reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

            // Parallax suave con el mouse
            if (!reduce && window.matchMedia('(pointer: fine)').matches) {
                hero.addEventListener('mousemove', function (e) {
                    var r = hero.getBoundingClientRect();
                    hero.style.setProperty('--px', ((e.clientX - r.left) / r.width - 0.5).toFixed(3));
                    hero.style.setProperty('--py', ((e.clientY - r.top) / r.height - 0.5).toFixed(3));
                });
            }

            var slides = Array.prototype.slice.call(document.querySelectorAll('#carrusel .slide'));
            if (slides.length < 2) return;
            var rellenos = Array.prototype.slice.call(document.querySelectorAll('.ind-relleno'));
            var btnPausa = document.getElementById('carrusel-pausa');
            var DURACION = 6000, actual = 0, inicio = performance.now(), pausado = false, restante = DURACION;
            function mostrar(n) {
                actual = (n + slides.length) % slides.length;
                slides.forEach(function (el, i) { el.dataset.activo = i === actual ? 'true' : 'false'; });
                rellenos.forEach(function (r, i) { r.style.width = i < actual ? '100%' : '0%'; });
                restante = DURACION; inicio = performance.now();
            }
            function tick(t) {
                if (!pausado) {
                    var transcurrido = t - inicio;
                    rellenos[actual].style.width = (Math.min(1, 1 - (restante - transcurrido) / DURACION) * 100) + '%';
                    if (transcurrido >= restante) mostrar(actual + 1);
                }
                requestAnimationFrame(tick);
            }
            function fijarPausa(v) {
                if (v === pausado) return;
                if (v) { restante = Math.max(0, restante - (performance.now() - inicio)); }
                else { inicio = performance.now(); }
                pausado = v;
                btnPausa.setAttribute('aria-pressed', v ? 'true' : 'false');
                btnPausa.setAttribute('aria-label', v ? 'Reanudar carrusel' : 'Pausar carrusel');
                btnPausa.querySelector('.ico-pausa').classList.toggle('hidden', v);
                btnPausa.querySelector('.ico-play').classList.toggle('hidden', !v);
            }
            document.querySelectorAll('[data-ir]').forEach(function (b) {
                b.addEventListener('click', function () { mostrar(parseInt(b.dataset.ir, 10)); });
            });
            btnPausa.addEventListener('click', function () { fijarPausa(!pausado); });
            document.addEventListener('visibilitychange', function () { if (document.hidden) fijarPausa(true); });
            if (reduce) fijarPausa(true);
            mostrar(0);
            requestAnimationFrame(tick);
        })();

        // Revelado suave al hacer scroll
        (function () {
            var items = document.querySelectorAll('.rev');
            if (!('IntersectionObserver' in window) || window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
                items.forEach(function (el) { el.classList.add('vis'); });
                return;
            }
            var io = new IntersectionObserver(function (entradas) {
                entradas.forEach(function (en) { if (en.isIntersecting) { en.target.classList.add('vis'); io.unobserve(en.target); } });
            }, { threshold: 0.12 });
            items.forEach(function (el) { io.observe(el); });
        })();

        // Carrito de compras
        (function () {
            var productos = window.HUATA.productos || [], wa = window.HUATA.whatsapp;
            var drawer = document.getElementById('carrito');
            if (!productos.length || !drawer) return;

            var KEY = 'huataCarrito';
            var porId = {};
            productos.forEach(function (p) { porId[p.id] = p; });
            var carrito = {};
            var ultimoFoco = null;

            function maximo(p) { return Math.max(0, Math.floor(p.disponible)); }
            function soles(n) { return 'S/ ' + n.toFixed(2); }
            function esc(t) { return String(t).replace(/[&<>"']/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]; }); }
            function $(id) { return document.getElementById(id); }

            try {
                var guardado = JSON.parse(localStorage.getItem(KEY) || '{}');
                Object.keys(guardado).forEach(function (id) {
                    var p = porId[id], n = parseInt(guardado[id], 10);
                    if (p && n > 0 && maximo(p) > 0) carrito[id] = Math.min(n, maximo(p));
                });
            } catch (e) {}

            function guardar() { try { localStorage.setItem(KEY, JSON.stringify(carrito)); } catch (e) {} }
            function ids() { return Object.keys(carrito); }
            function unidades() { return ids().reduce(function (s, id) { return s + carrito[id]; }, 0); }
            function total() { return ids().reduce(function (s, id) { return s + carrito[id] * porId[id].precio; }, 0); }

            function aviso(texto) {
                var a = $('carrito-aviso');
                a.textContent = texto;
                a.classList.remove('hidden');
                clearTimeout(aviso.t);
                aviso.t = setTimeout(function () { a.classList.add('hidden'); }, 1800);
            }

            function pintarLineas() {
                var ul = $('carrito-lineas');
                ul.innerHTML = ids().map(function (id) {
                    var p = porId[id], n = carrito[id];
                    var foto = p.imagen
                        ? '<img src="' + esc(p.imagen) + '" alt="" class="size-full object-cover">'
                        : '<svg class="h-12 w-auto" viewBox="0 0 200 270" aria-hidden="true"><use href="#vaso-leche"/></svg>';
                    return '<li class="flex gap-3 py-4">' +
                        '<div class="grid size-16 shrink-0 place-items-center overflow-hidden rounded-xl bg-cielo-50">' + foto + '</div>' +
                        '<div class="min-w-0 flex-1">' +
                        '<p class="font-bold leading-snug">' + esc(p.nombre) + '</p>' +
                        '<p class="tabular text-sm text-tinta-suave">' + soles(p.precio) + ' c/u</p>' +
                        '<div class="mt-2 flex items-center justify-between gap-2">' +
                        '<div class="flex items-center rounded-full border border-tinta/20">' +
                        '<button type="button" data-menos="' + id + '" class="grid size-9 place-items-center rounded-full text-xl font-bold hover:bg-suero" aria-label="Quitar una unidad de ' + esc(p.nombre) + '">−</button>' +
                        '<span class="tabular w-8 text-center font-bold" aria-label="Cantidad">' + n + '</span>' +
                        '<button type="button" data-mas="' + id + '" class="grid size-9 place-items-center rounded-full text-xl font-bold hover:bg-suero disabled:opacity-35" aria-label="Agregar otra unidad de ' + esc(p.nombre) + '"' + (n >= maximo(p) ? ' disabled' : '') + '>+</button>' +
                        '</div>' +
                        '<span class="tabular font-display text-lg font-extrabold">' + soles(n * p.precio) + '</span>' +
                        '</div></div>' +
                        '<button type="button" data-quitar="' + id + '" class="grid size-9 shrink-0 place-items-center rounded-full text-tinta-suave hover:bg-suero hover:text-rojo-700" aria-label="Quitar ' + esc(p.nombre) + ' del carrito">' +
                        '<svg class="size-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 7h16M10 11v6M14 11v6M6 7l1 12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-12M9 7V4h6v3"/></svg></button>' +
                        '</li>';
                }).join('');
            }

            function pintar() {
                var cuenta = unidades(), tot = total(), vacio = cuenta === 0;
                var cont = $('carrito-contador');
                cont.textContent = cuenta > 99 ? '99+' : cuenta;
                cont.classList.toggle('hidden', vacio);
                $('carrito-abrir').setAttribute('aria-label', vacio ? 'Abrir carrito, vacío' : 'Abrir carrito, ' + cuenta + (cuenta === 1 ? ' producto' : ' productos') + ', ' + soles(tot));
                $('carrito-titulo-cuenta').textContent = vacio ? '' : '(' + cuenta + ')';

                document.querySelectorAll('.tarjeta-producto').forEach(function (card) {
                    var id = card.dataset.id, n = carrito[id] || 0, p = porId[id];
                    var agregar = card.querySelector('.ctrl-agregar'), ctrl = card.querySelector('.ctrl-cantidad');
                    if (!agregar || !ctrl) return;
                    agregar.classList.toggle('hidden', n > 0);
                    ctrl.classList.toggle('hidden', n === 0);
                    ctrl.classList.toggle('flex', n > 0);
                    card.querySelector('.cant-en-carrito').textContent = n;
                    card.querySelector('[data-mas]').disabled = n >= maximo(p);
                });

                var enListado = !$('carrito-listo').classList.contains('flex');
                if (enListado) {
                    $('carrito-vacio').classList.toggle('hidden', !vacio);
                    $('carrito-vacio').classList.toggle('flex', vacio);
                    $('carrito-lleno').classList.toggle('hidden', vacio);
                    $('carrito-lleno').classList.toggle('flex', !vacio);
                }
                if (!vacio) {
                    pintarLineas();
                    $('carrito-unidades').textContent = cuenta;
                    $('carrito-total').textContent = soles(tot);
                }

                var barra = $('barra-carrito');
                barra.classList.toggle('hidden', vacio);
                requestAnimationFrame(function () { barra.classList.toggle('translate-y-full', vacio || drawerAbierto()); });
                $('barra-cuenta').textContent = cuenta;
                $('barra-total').textContent = soles(tot);
                actualizarPago();
            }

            function cambiar(id, delta, avisar) {
                var p = porId[id];
                if (!p) return;
                var actual = carrito[id] || 0, nuevo = Math.max(0, Math.min(maximo(p), actual + delta));
                if (nuevo === actual) {
                    if (delta > 0) aviso('Solo hay ' + maximo(p) + (maximo(p) === 1 ? ' unidad' : ' unidades') + ' de ' + p.nombre);
                    return;
                }
                if (nuevo === 0) delete carrito[id]; else carrito[id] = nuevo;
                guardar();
                pintar();
                if (avisar && actual === 0) aviso('Agregado al carrito: ' + p.nombre);
            }

            function drawerAbierto() { return !drawer.hasAttribute('inert'); }

            function abrir() {
                ultimoFoco = document.activeElement;
                $('carrito-listo').classList.add('hidden');
                $('carrito-listo').classList.remove('flex');
                pintar();
                var fondo = $('carrito-fondo');
                fondo.classList.remove('hidden');
                drawer.removeAttribute('inert');
                requestAnimationFrame(function () {
                    fondo.classList.remove('opacity-0');
                    drawer.classList.remove('translate-x-full');
                });
                document.documentElement.style.overflow = 'hidden';
                $('barra-carrito').classList.add('translate-y-full');
                setTimeout(function () { (drawer.querySelector('[data-cerrar-carrito]:not(.hidden)') && $('carrito-vacio').classList.contains('flex') ? drawer.querySelector('[data-cerrar-carrito]') : $('carrito-cerrar')).focus(); }, 60);
            }

            function cerrar() {
                if (!drawerAbierto()) return;
                var fondo = $('carrito-fondo');
                fondo.classList.add('opacity-0');
                drawer.classList.add('translate-x-full');
                drawer.setAttribute('inert', '');
                document.documentElement.style.overflow = '';
                setTimeout(function () { fondo.classList.add('hidden'); }, 300);
                pintar();
                if (ultimoFoco && ultimoFoco.focus) ultimoFoco.focus();
            }

            document.addEventListener('click', function (e) {
                var t = e.target.closest('[data-agregar],[data-mas],[data-menos],[data-quitar],[data-cerrar-carrito]');
                if (!t) return;
                if (t.hasAttribute('data-cerrar-carrito')) { cerrar(); return; }
                if (t.dataset.agregar) cambiar(t.dataset.agregar, 1, true);
                else if (t.dataset.mas) cambiar(t.dataset.mas, 1, false);
                else if (t.dataset.menos) cambiar(t.dataset.menos, -1, false);
                else if (t.dataset.quitar) { delete carrito[t.dataset.quitar]; guardar(); pintar(); }
            });
            document.addEventListener('huata:agregar', function (ev) { if (ev.detail && porId[ev.detail.id]) cambiar(String(ev.detail.id), 1, true); });
            $('carrito-abrir').addEventListener('click', abrir);
            $('barra-carrito-abrir').addEventListener('click', abrir);
            $('carrito-cerrar').addEventListener('click', cerrar);
            $('carrito-fondo').addEventListener('click', cerrar);
            $('carrito-vaciar').addEventListener('click', function () { carrito = {}; guardar(); pintar(); });
            document.addEventListener('keydown', function (e) {
                if (!drawerAbierto()) return;
                if (e.key === 'Escape') { cerrar(); return; }
                if (e.key !== 'Tab') return;
                var f = Array.prototype.slice.call(drawer.querySelectorAll('button:not([disabled]), a[href], input')).filter(function (el) { return el.offsetParent !== null; });
                if (!f.length) return;
                var primero = f[0], ultimo = f[f.length - 1];
                if (e.shiftKey && document.activeElement === primero) { e.preventDefault(); ultimo.focus(); }
                else if (!e.shiftKey && document.activeElement === ultimo) { e.preventDefault(); primero.focus(); }
            });

            // Buscador de productos
            var buscador = $('buscar-producto');
            if (buscador) {
                buscador.addEventListener('input', function () {
                    var q = buscador.value.trim().toLowerCase(), visibles = 0;
                    document.querySelectorAll('.tarjeta-producto').forEach(function (card) {
                        var coincide = card.dataset.nombre.indexOf(q) !== -1;
                        card.classList.toggle('hidden', !coincide);
                        if (coincide) visibles++;
                    });
                    $('sin-resultados').classList.toggle('hidden', visibles > 0);
                });
            }

            var boton = $('pedido-wa');
            function metodo() { return document.querySelector('input[name="pedido-metodo"]:checked')?.value || 'yape'; }
            function actualizarPago() {
                $('pago-instrucciones').textContent = metodo() === 'yape' ? 'Guardaremos tu pedido y asignaremos un receptor habilitado antes de mostrarte su QR.' : (metodo() === 'saldo' ? 'Usaremos tu saldo si cubre el total. No hay pagos mixtos.' : 'La tienda confirmará este método conforme a sus reglas; no tiene validación automática.');
            }
            document.querySelectorAll('input[name="pedido-metodo"]').forEach(r => r.addEventListener('change', actualizarPago));
            // Conserva el carrito al autenticar y fija una referencia para recuperar reintentos.
            function error(texto) {
                var caja = $('pedido-error');
                caja.textContent = texto || '';
                caja.classList.toggle('hidden', !texto);
            }
            boton.addEventListener('click', async function () {
                if (boton.disabled) return;
                error('');
                if (!ids().length) { error('Tu carrito está vacío.'); return; }
                if (!window.HUATA.clienteAutenticado) { guardar(); location.href = window.HUATA.urlLoginCliente; return; }
                if (!$('pedido-celular').reportValidity()) return;
                let key = localStorage.getItem('huata-checkout-reference');
                const fingerprint = JSON.stringify([carrito, metodo()]);
                if (!key || localStorage.getItem('huata-checkout-fingerprint') !== fingerprint) {
                    key = crypto.randomUUID(); localStorage.setItem('huata-checkout-reference', key); localStorage.setItem('huata-checkout-fingerprint', fingerprint);
                }
                boton.disabled = true; boton.textContent = 'Guardando tu pedido…';
                try {
                    const response = await fetch(window.HUATA.urlPedidos, {method:'POST', headers:{'Content-Type':'application/json','Accept':'application/json','X-CSRF-TOKEN':document.querySelector('meta[name="csrf-token"]').content},
                        body:JSON.stringify({items:ids().map(id => ({produccion_id:porId[id].id,cantidad:carrito[id]})),metodo_pago:metodo(),celular:$('pedido-celular').value,idempotency_key:key})});
                    const data = await response.json();
                    if (!response.ok) throw new Error(data.errors ? Object.values(data.errors).flat()[0] : (data.message || 'No pudimos guardar tu pedido.'));
                    carrito={};guardar();localStorage.removeItem('huata-checkout-reference');localStorage.removeItem('huata-checkout-fingerprint');
                    location.href=data.url_pedido;
                } catch (e) { error(e.message || 'Sin conexión; conservamos el carrito y la referencia.'); }
                finally { boton.disabled=false;boton.textContent='Confirmar pedido'; }
            });
            if (new URLSearchParams(location.search).has('checkout')) abrir();

            pintar();
        })();
    </script>
</body>
</html>

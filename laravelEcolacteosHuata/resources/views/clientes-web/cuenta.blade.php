<!DOCTYPE html>
<html lang="{{ str_replace('_', '-', app()->getLocale()) }}">
    <head>@include('partials.head', ['title' => 'Mi cuenta'])</head>
    <body class="hh-cuerpo antialiased">
        <main class="mx-auto flex min-h-svh w-full max-w-7xl flex-col gap-6 px-4 py-6 sm:px-6 lg:px-8 lg:py-10" aria-label="Cuenta del comprador">
            <header class="flex flex-wrap items-center justify-between gap-4">
                <x-ui.titulo titulo="Mi cuenta">{{ $client->nombreCompleto() }}</x-ui.titulo>
                <div class="flex flex-wrap gap-3">
                    <flux:button as="a" :href="route('home', ['checkout' => 1])" variant="primary">Continuar comprando</flux:button>
                    <form method="POST" action="{{ route('cliente.logout') }}">@csrf <flux:button type="submit">Cerrar sesión</flux:button></form>
                </div>
            </header>
            @if(session('status')) <flux:text role="status">{{ session('status') }}</flux:text> @endif
            @if($errors->any()) <flux:text role="alert">{{ $errors->first() }}</flux:text> @endif

            <div class="grid min-w-0 gap-6 lg:grid-cols-3">
                <div class="flex min-w-0 flex-col gap-6">
                    <section class="huata-cuadro rounded-xl border p-5 sm:p-6" aria-labelledby="saldo-titulo">
                        <h2 id="saldo-titulo" class="text-lg font-bold">Saldo a favor</h2>
                        <p class="mt-2 font-display text-3xl font-bold">S/ {{ number_format($client->saldoMinor() / 100, 2) }}</p>
                        <flux:text class="mt-2">Para compras en Huata. Sin retiros ni transferencias.</flux:text>
                        <form method="POST" action="{{ route('cliente.recarga') }}" class="mt-5 flex flex-col gap-3">@csrf
                            <input type="hidden" name="referencia" value="{{ old('referencia', (string) \Illuminate\Support\Str::uuid()) }}">
                            <flux:input name="importe" label="Recarga con Yape (S/)" type="number" step="0.01" min="1" max="999999.99" required />
                            <flux:button type="submit">Iniciar recarga</flux:button>
                        </form>
                    </section>
                    <section class="huata-cuadro min-w-0 rounded-xl border p-5 sm:p-6" aria-labelledby="perfil-titulo">
                        <h2 id="perfil-titulo" class="text-lg font-bold">Mi perfil</h2>
                        <p class="mt-3 break-words font-semibold">{{ $client->nombreCompleto() }}</p>
                        <flux:text class="break-all">{{ $client->email }}</flux:text>
                        <flux:text>{{ $client->celular }}</flux:text>
                        <details class="mt-4">
                            <summary class="cursor-pointer font-semibold text-[var(--hh-marca)]">Editar perfil</summary>
                            <form method="POST" action="{{ route('cliente.perfil') }}" class="mt-4 flex flex-col gap-3">@csrf
                                <flux:input name="nombres" label="Nombres" :value="old('nombres', $client->nombres)" required />
                                <flux:input name="apellidos" label="Apellidos" :value="old('apellidos', $client->apellidos)" required />
                                <flux:input name="celular" label="Celular" type="tel" :value="old('celular', $client->celular)" required />
                                <flux:input name="email" label="Correo electrónico" type="email" :value="old('email', $client->email)" required />
                                <flux:input name="current_password" label="Contraseña actual" type="password" required autocomplete="current-password" />
                                <flux:button type="submit">Guardar perfil</flux:button>
                            </form>
                        </details>
                    </section>
                </div>
                <div class="flex min-w-0 flex-col gap-6 lg:col-span-2">
                    <section class="huata-cuadro min-w-0 rounded-xl border p-5 sm:p-6" aria-labelledby="pedidos-titulo">
                        <h2 id="pedidos-titulo" class="text-lg font-bold">Mis pedidos</h2>
                        <div class="mt-3 divide-y divide-[var(--hh-borde)]">
                            @forelse($orders as $order)
                                <div class="flex flex-wrap items-center justify-between gap-3 py-3">
                                    <div class="min-w-0">
                                        <flux:link :href="route('cliente.pedido', $order->id)">Pedido #{{ $order->id }}</flux:link>
                                        <flux:text>{{ $order->estado->label() }} · {{ str_replace('_', ' ', $order->pago_estado) }}</flux:text>
                                    </div>
                                    <span class="font-semibold">S/ {{ $order->total }}</span>
                                </div>
                            @empty <flux:text class="py-3">Aún no tienes pedidos.</flux:text> @endforelse
                        </div>
                        @if($orders->hasPages())
                            <nav class="mt-4 flex flex-wrap items-center justify-between gap-3" aria-label="Paginación de pedidos">
                                @if($orders->onFirstPage()) <flux:button size="sm" disabled>Anterior</flux:button>
                                @else <flux:button size="sm" as="a" :href="$orders->previousPageUrl()">Anterior</flux:button> @endif
                                <flux:text>Página {{ $orders->currentPage() }} de {{ $orders->lastPage() }}</flux:text>
                                @if($orders->hasMorePages()) <flux:button size="sm" as="a" :href="$orders->nextPageUrl()">Siguiente</flux:button>
                                @else <flux:button size="sm" disabled>Siguiente</flux:button> @endif
                            </nav>
                        @endif
                    </section>
                    <div class="grid min-w-0 gap-6 xl:grid-cols-2">
                        <section class="huata-cuadro min-w-0 rounded-xl border p-5 sm:p-6" aria-labelledby="pagos-titulo">
                            <h2 id="pagos-titulo" class="text-lg font-bold">Mis pagos</h2>
                            <div class="mt-3 divide-y divide-[var(--hh-borde)]">
                                @forelse($payments as $payment)
                                    <div class="flex flex-wrap justify-between gap-2 py-3">
                                        <div class="min-w-0">
                                            <flux:link :href="route('cliente.pago', $payment->id)">{{ ucfirst($payment->proposito) }} #{{ $payment->id }}</flux:link>
                                            <flux:text>{{ $payment->estado }}</flux:text>
                                        </div>
                                        <span class="font-semibold">S/ {{ number_format($payment->amount_minor / 100, 2) }}</span>
                                    </div>
                                @empty <flux:text class="py-3">Aún no tienes pagos.</flux:text> @endforelse
                            </div>
                        </section>
                        <section class="huata-cuadro min-w-0 rounded-xl border p-5 sm:p-6" aria-labelledby="movimientos-titulo">
                            <h2 id="movimientos-titulo" class="text-lg font-bold">Movimientos</h2>
                            <div class="mt-3 divide-y divide-[var(--hh-borde)]">
                                @forelse($movements as $movement)
                                    <div class="flex flex-wrap justify-between gap-2 py-3">
                                        <div class="min-w-0">
                                            <p class="text-sm">{{ str_replace('_', ' ', $movement->tipo) }}</p>
                                            <flux:text>{{ $movement->created_at->format('d/m/Y H:i') }}</flux:text>
                                        </div>
                                        <span class="font-semibold">S/ {{ number_format($movement->amount_minor / 100, 2) }}</span>
                                    </div>
                                @empty <flux:text class="py-3">Sin movimientos.</flux:text> @endforelse
                            </div>
                        </section>
                    </div>
                </div>
            </div>
        </main>
        @fluxScripts
    </body>
</html>

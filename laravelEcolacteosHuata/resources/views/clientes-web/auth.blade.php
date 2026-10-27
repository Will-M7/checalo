<x-layouts::auth :title="match($mode) { 'register' => 'Crear cuenta de comprador', 'forgot' => 'Recuperar acceso', 'reset' => 'Restablecer contraseña', default => 'Ingresar como comprador' }">
    <div class="flex flex-col gap-5">
        <x-auth-header :title="match($mode) { 'register' => 'Crear mi cuenta', 'forgot' => 'Recuperar acceso', 'reset' => 'Nueva contraseña', default => 'Bienvenido a la tienda' }" description="Tu cuenta para comprar y consultar tus pedidos y saldo." />
        <x-auth-session-status :status="session('status')" />
        @if($errors->any()) <div role="alert" class="text-sm text-red-600">{{ $errors->first() }}</div> @endif
        <form method="POST" action="{{ route(match($mode) { 'register' => 'cliente.register.store', 'forgot' => 'cliente.password.email', 'reset' => 'cliente.password.update', default => 'cliente.login.store' }) }}" class="flex flex-col gap-4">
            @csrf
            @if($mode === 'register')
                <flux:input name="nombres" label="Nombres" :value="old('nombres')" required maxlength="80" autocomplete="given-name" />
                <flux:input name="apellidos" label="Apellidos" :value="old('apellidos')" required maxlength="80" autocomplete="family-name" />
                <flux:input name="celular" label="Celular peruano" :value="old('celular')" required autocomplete="tel" placeholder="9XXXXXXXX" />
            @endif
            <flux:input name="email" label="Correo electrónico" type="email" :value="old('email', $email ?? '')" required autocomplete="email" />
            @if($mode !== 'forgot') <flux:input name="password" label="Contraseña" type="password" required :autocomplete="$mode === 'login' ? 'current-password' : 'new-password'" viewable /> @endif
            @if(in_array($mode, ['register', 'reset']))
                <flux:input name="password_confirmation" label="Confirma la contraseña" type="password" required autocomplete="new-password" />
                <flux:text>Al menos 12 caracteres, con letras y números.</flux:text>
            @endif
            @if($mode === 'reset') <input type="hidden" name="token" value="{{ $token }}"> @endif
            <flux:button type="submit" variant="primary">{{ match($mode) { 'register' => 'Crear cuenta', 'forgot' => 'Enviar enlace de recuperación', 'reset' => 'Actualizar contraseña', default => 'Ingresar y volver a mi carrito' } }}</flux:button>
        </form>
        <flux:link :href="route('cliente.register')">Crear cuenta de comprador</flux:link>
        <flux:link :href="route('cliente.login')">Ya tengo una cuenta</flux:link>
        <flux:link :href="route('cliente.password.request')">Olvidé mi contraseña</flux:link>
        @if($mode === 'login')
            <flux:link :href="route('login')">Colaborador</flux:link>
        @endif
        <flux:link :href="route('home')">Volver al catálogo</flux:link>
    </div>
</x-layouts::auth>

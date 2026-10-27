<x-layouts::auth :title="__('Iniciar sesión')">
    <div class="flex flex-col gap-6">
        <x-auth-header
            title="Bienvenido de nuevo"
            description="Ingresa tu usuario y contraseña para entrar a tu panel."
        />

        <x-auth-session-status class="text-center" :status="session('status')" />

        <form method="POST" action="{{ route('login.store') }}" class="flex flex-col gap-6">
            @csrf

            <flux:input
                name="usuario"
                label="Usuario"
                :value="old('usuario')"
                type="text"
                required
                autofocus
                autocomplete="username"
                placeholder="usuario"
            />

            <div class="relative">
                <flux:input
                    name="password"
                    :label="__('Password')"
                    type="password"
                    required
                    autocomplete="current-password"
                    :placeholder="__('Password')"
                    viewable
                />

                @if (Route::has('password.request'))
                    <flux:link class="absolute top-0 text-sm end-0" :href="route('password.request')" wire:navigate>
                        {{ __('Forgot your password?') }}
                    </flux:link>
                @endif
            </div>

            <flux:checkbox name="remember" :label="__('Remember me')" :checked="old('remember')" />

            <flux:button variant="primary" type="submit" class="w-full !min-h-12 !text-base" data-test="login-button">
                Iniciar sesión
            </flux:button>
        </form>
    </div>
</x-layouts::auth>

@props(['showTeam' => true])

<flux:dropdown position="bottom" align="end">
    <button type="button" class="group flex items-center gap-2.5 rounded-xl p-1 pe-2 transition-colors hover:bg-[var(--hh-superficie-2)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--hh-marca)]" data-test="user-menu-button">
        <flux:avatar :name="auth()->user()->nombreCompleto()" size="sm" color="auto" :color:seed="auth()->id()" class="ring-2 ring-[var(--hh-marca-suave)]" />
        <div class="hidden text-start text-sm leading-tight sm:grid">
            <span class="max-w-40 truncate font-semibold text-[var(--hh-texto)]">{{ auth()->user()->nombreCompleto() }}</span>
            <span class="truncate text-xs text-[var(--hh-texto-2)]">{{ \App\Authorization\Autorizacion::roles(auth()->user())->pluck('nombre')->join(', ') }}</span>
        </div>
        <flux:icon name="chevron-down" variant="micro" class="hidden size-4 text-[var(--hh-texto-2)] transition-transform group-aria-expanded:rotate-180 sm:block" />
    </button>

    <flux:menu>
        <div class="flex items-center gap-2 px-1 py-1.5 text-start text-sm">
            <flux:avatar
                :name="auth()->user()->nombreCompleto()"
            />
            <div class="grid flex-1 text-start text-sm leading-tight">
                <flux:heading class="truncate">{{ auth()->user()->nombreCompleto() }}</flux:heading>
                <flux:text class="truncate">{{ \App\Authorization\Autorizacion::roles(auth()->user())->pluck('nombre')->join(', ') }}</flux:text>
            </div>
        </div>
        <flux:menu.separator />
        <flux:menu.radio.group>
            <flux:menu.item :href="route('profile.edit')" icon="cog" wire:navigate>
                {{ __('Settings') }}
            </flux:menu.item>
            <form method="POST" action="{{ route('logout') }}" class="w-full">
                @csrf
                <flux:menu.item
                    as="button"
                    type="submit"
                    icon="arrow-right-start-on-rectangle"
                    class="w-full cursor-pointer"
                    data-test="logout-button"
                >
                    {{ __('Log out') }}
                </flux:menu.item>
            </form>
        </flux:menu.radio.group>
    </flux:menu>
</flux:dropdown>

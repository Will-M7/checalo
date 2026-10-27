<?php

use App\Concerns\ProfileValidationRules;
use App\Enums\Rol;
use App\Models\Acopiador;
use App\Models\Proveedor;
use Illuminate\Contracts\Auth\MustVerifyEmail;
use Flux\Flux;
use Illuminate\Database\Eloquent\Collection;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Facades\Session;
use Livewire\Attributes\Computed;
use Livewire\Attributes\Title;
use Livewire\Component;

new #[Title('Profile settings')] class extends Component {
    use ProfileValidationRules;

    public string $name = '';
    public string $email = '';

    public string $finca_ubicacion = '';
    public string $finca_referencia = '';
    public ?float $latitud = null;
    public ?float $longitud = null;
    public ?int $acopiador_id = null;

    /**
     * Mount the component.
     */
    public function mount(): void
    {
        $this->name = Auth::user()->name;
        $this->email = Auth::user()->email;

        if ($proveedor = Auth::user()->proveedor) {
            $this->finca_ubicacion = $proveedor->finca_ubicacion;
            $this->finca_referencia = $proveedor->finca_referencia;
            $this->latitud = $proveedor->latitud ? (float) $proveedor->latitud : null;
            $this->longitud = $proveedor->longitud ? (float) $proveedor->longitud : null;
            $this->acopiador_id = $proveedor->acopiador_id;
            $this->mostrarRutasDelAcopiador();
        }
    }

    public function updatedAcopiadorId(): void
    {
        $this->mostrarRutasDelAcopiador();
    }

    /**
     * Envía al mapa el camino de las rutas activas del acopiador elegido, para que el proveedor
     * ubique su finca sobre (o cerca de) la ruta por donde recoge.
     */
    private function mostrarRutasDelAcopiador(): void
    {
        $this->dispatch('mapa-ubicacion-rutas',
            lat: $this->latitud,
            lng: $this->longitud,
            rutas: Acopiador::find($this->acopiador_id)?->rutasParaMapa() ?? [],
        );
    }

    /**
     * @return Collection<int, Acopiador>
     */
    #[Computed]
    public function acopiadores(): Collection
    {
        return Acopiador::with('user')->get()->sortBy(fn (Acopiador $acopiador) => $acopiador->user->nombreCompleto())->values();
    }

    /**
     * Actualiza la ubicación de la finca y el acopiador del proveedor autenticado.
     */
    public function actualizarUbicacion(): void
    {
        $data = $this->validate([
            'finca_ubicacion' => ['required', 'string', 'max:255'],
            'finca_referencia' => ['nullable', 'string', 'max:255'],
            'latitud' => ['nullable', 'numeric', 'between:-90,90'],
            'longitud' => ['nullable', 'numeric', 'between:-180,180'],
            'acopiador_id' => ['required', 'exists:acopiadores,id'],
        ]);

        abort_if(! Auth::user()->proveedor, 404);

        $proveedor = Proveedor::guardarElegidoPorProveedor(Auth::id(), [
            ...$data,
            'finca_referencia' => $data['finca_referencia'] ?? '',
        ]);

        $this->mostrarRutasDelAcopiador();

        Flux::toast(variant: 'success', text: $proveedor->rutas()->exists() ? 'Ubicación guardada. Ya estás en la ruta de tu acopiador.' : 'Ubicación guardada. Tu acopiador aún no tiene ruta: se avisó a planta.');
    }

    /**
     * Update the profile information for the currently authenticated user.
     */
    public function updateProfileInformation(): void
    {
        $user = Auth::user();

        $validated = $this->validate($this->profileRules($user->id));

        $user->fill($validated);

        if ($user->isDirty('email')) {
            $user->email_verified_at = null;
        }

        $user->save();

        Flux::toast(variant: 'success', text: __('Profile updated.'));
    }

    /**
     * Send an email verification notification to the current user.
     */
    public function resendVerificationNotification(): void
    {
        $user = Auth::user();

        if ($user->hasVerifiedEmail()) {
            $this->redirectIntended(default: route('dashboard', absolute: false));

            return;
        }

        $user->sendEmailVerificationNotification();

        Session::flash('status', 'verification-link-sent');
    }

    #[Computed]
    public function hasUnverifiedEmail(): bool
    {
        return Auth::user() instanceof MustVerifyEmail && ! Auth::user()->hasVerifiedEmail();
    }

    #[Computed]
    public function showDeleteUser(): bool
    {
        return ! Auth::user() instanceof MustVerifyEmail
            || (Auth::user() instanceof MustVerifyEmail && Auth::user()->hasVerifiedEmail());
    }
}; ?>

<section class="w-full">
    @include('partials.settings-heading')

    <flux:heading level="2" class="sr-only">{{ __('Profile settings') }}</flux:heading>

    <x-pages::settings.layout :heading="__('Profile')" :subheading="__('Update your name and email address')">
        <form wire:submit="updateProfileInformation" class="my-6 w-full space-y-6">
            <flux:input wire:model="name" :label="__('Name')" type="text" required autofocus autocomplete="name" />

            <div>
                <flux:input wire:model="email" :label="__('Email')" type="email" required autocomplete="email" />

                @if ($this->hasUnverifiedEmail)
                    <div>
                        <flux:text class="mt-4">
                            {{ __('Your email address is unverified.') }}

                            <flux:link class="text-sm cursor-pointer" wire:click.prevent="resendVerificationNotification">
                                {{ __('Click here to re-send the verification email.') }}
                            </flux:link>
                        </flux:text>

                        @if (session('status') === 'verification-link-sent')
                            <flux:text class="mt-2 font-medium !dark:text-green-400 !text-green-600">
                                {{ __('A new verification link has been sent to your email address.') }}
                            </flux:text>
                        @endif
                    </div>
                @endif
            </div>

            <div class="flex items-center gap-4">
                <div class="flex items-center justify-end">
                    <flux:button variant="primary" type="submit" class="w-full" data-test="update-profile-button">
                        {{ __('Save') }}
                    </flux:button>
                </div>

            </div>
        </form>

        @if (auth()->user()->rol === Rol::Proveedor)
            <flux:separator class="my-6" />

            <flux:heading size="lg">Ubicación de mi finca</flux:heading>
            <flux:subheading class="mb-4">El acopiador la usa para ubicar tu finca en la ruta de recojo.</flux:subheading>

            <form wire:submit="actualizarUbicacion" class="flex flex-col gap-6">
                <flux:input label="Ubicación / dirección" wire:model="finca_ubicacion" placeholder="Comunidad de Huata" />
                <flux:input label="Referencia" wire:model="finca_referencia" placeholder="A 200m de la escuela..." />

                <flux:select label="Acopiador que recoge tu leche" wire:model.live="acopiador_id">
                    <flux:select.option value="">Selecciona...</flux:select.option>
                    @foreach ($this->acopiadores as $acopiador)
                        <flux:select.option value="{{ $acopiador->id }}" wire:key="acopiador-{{ $acopiador->id }}">{{ $acopiador->user->nombreCompleto() }}</flux:select.option>
                    @endforeach
                </flux:select>

                @if ($acopiador_id)
                    <p class="-mt-4 text-xs text-zinc-500">
                        <span class="mr-1 inline-block h-1 w-5 rounded bg-blue-600 align-middle"></span>
                        La línea azul es la ruta por donde recoge este acopiador. Marca tu finca y guarda: quedarás en su ruta automáticamente.
                    </p>
                @endif

                <x-mapa-ubicacion :lat="$latitud" :lng="$longitud" :editable="true" lat-model="latitud" lng-model="longitud" />

                <div class="flex justify-end">
                    <flux:button type="submit" variant="primary">Guardar ubicación</flux:button>
                </div>
            </form>
        @endif

        @if ($this->showDeleteUser)
            <livewire:pages::settings.delete-user-form />
        @endif
    </x-pages::settings.layout>
</section>

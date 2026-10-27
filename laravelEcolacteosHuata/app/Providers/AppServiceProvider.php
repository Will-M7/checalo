<?php

namespace App\Providers;

use App\Authorization\AutorizarComponente;
use App\Http\Middleware\EnsureRole;
use App\Http\Middleware\EnsureTeamMembership;
use App\Models\User;
use App\Services\Payments\HttpPaymentCore;
use App\Services\Payments\PaymentCore;
use Carbon\CarbonImmutable;
use Illuminate\Support\Facades\Date;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Gate;
use Illuminate\Support\ServiceProvider;
use Illuminate\Validation\Rules\Password;
use Livewire\Livewire;

class AppServiceProvider extends ServiceProvider
{
    /**
     * Register any application services.
     */
    public function register(): void
    {
        $this->app->bind(PaymentCore::class, HttpPaymentCore::class);
    }

    /**
     * Bootstrap any application services.
     */
    public function boot(): void
    {
        $this->configureDefaults();
        Livewire::componentHook(AutorizarComponente::class);
        Livewire::addPersistentMiddleware([EnsureTeamMembership::class, EnsureRole::class]);

        Gate::define('tomar-asistencia', fn (User $user): bool => $user->puedeTomarAsistencia());
    }

    /**
     * Configure default behaviors for production-ready applications.
     */
    protected function configureDefaults(): void
    {
        Date::use(CarbonImmutable::class);

        DB::prohibitDestructiveCommands(
            app()->isProduction(),
        );

        Password::defaults(fn (): ?Password => app()->isProduction()
            ? Password::min(12)
                ->mixedCase()
                ->letters()
                ->numbers()
                ->symbols()
                ->uncompromised()
            : null,
        );
    }
}

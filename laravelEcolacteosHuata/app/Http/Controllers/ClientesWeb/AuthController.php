<?php

namespace App\Http\Controllers\ClientesWeb;

use App\Http\Controllers\Controller;
use App\Models\ClienteWeb;
use App\Services\Payments\TelefonoPeru;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Facades\Password;
use Illuminate\Support\Facades\RateLimiter;
use Illuminate\Support\Str;
use Illuminate\Validation\Rules\Password as PasswordRule;
use Illuminate\Validation\ValidationException;

class AuthController extends Controller
{
    public function loginForm()
    {
        return Auth::guard('cliente')->check() ? redirect()->route('cliente.cuenta') : view('clientes-web.auth', ['mode' => 'login']);
    }

    public function registerForm()
    {
        return view('clientes-web.auth', ['mode' => 'register']);
    }

    public function register(Request $r)
    {
        $r->merge(['celular' => TelefonoPeru::normalizar((string) $r->input('celular')), 'email' => mb_strtolower(trim((string) $r->input('email')))]);
        $data = $r->validate(['nombres' => 'required|string|max:80', 'apellidos' => 'required|string|max:80', 'celular' => 'required|unique:clientes_web,celular', 'email' => 'required|email|max:255|unique:clientes_web,email', 'password' => ['required', 'confirmed', PasswordRule::min(12)->letters()->numbers()]]);
        $client = ClienteWeb::create($data);
        Auth::guard('cliente')->login($client);
        $r->session()->regenerate();

        return redirect()->route('home', ['checkout' => 1]);
    }

    public function login(Request $r)
    {
        $data = $r->validate(['email' => 'required|email', 'password' => 'required|string']);
        $data['email'] = mb_strtolower(trim($data['email']));
        $key = 'cliente-login:'.hash('sha256', $data['email'].'|'.$r->ip());
        if (RateLimiter::tooManyAttempts($key, 5)) {
            throw ValidationException::withMessages(['email' => 'Espera un minuto antes de volver a intentar.']);
        }
        RateLimiter::hit($key, 60);
        if (! Auth::guard('cliente')->attempt($data, $r->boolean('remember'))) {
            throw ValidationException::withMessages(['email' => 'Las credenciales no son válidas.']);
        }
        RateLimiter::clear($key);
        $r->session()->regenerate();

        return redirect()->route('home', ['checkout' => 1]);
    }

    public function logout(Request $r)
    {
        Auth::guard('cliente')->logout();
        $r->session()->regenerate();
        $r->session()->regenerateToken();

        return redirect()->route('home');
    }

    public function forgotForm()
    {
        return view('clientes-web.auth', ['mode' => 'forgot']);
    }

    public function forgot(Request $r)
    {
        $r->validate(['email' => 'required|email']);
        if (! app()->runningUnitTests() && in_array(config('mail.default'), ['log', 'array'], true)) {
            return back()->withErrors(['email' => 'La recuperación por correo aún no está configurada. Contacta a la tienda.']);
        }
        Password::broker('clientes_web')->sendResetLink(['email' => mb_strtolower(trim($r->input('email')))]);

        return back()->with('status', 'Si existe la cuenta, recibirás un enlace de recuperación.');
    }

    public function resetForm(Request $r, string $token)
    {
        return view('clientes-web.auth', ['mode' => 'reset', 'token' => $token, 'email' => $r->query('email')]);
    }

    public function reset(Request $r)
    {
        $data = $r->validate(['email' => 'required|email', 'token' => 'required|string', 'password' => ['required', 'confirmed', PasswordRule::min(12)->letters()->numbers()]]);
        $status = Password::broker('clientes_web')->reset($data, function (ClienteWeb $client, string $password) {
            $client->forceFill(['password' => $password, 'remember_token' => Str::random(60)])->save();
        });
        if ($status !== Password::PasswordReset) {
            return back()->withErrors(['email' => 'El enlace es inválido o venció. Solicita otro.']);
        }

        return redirect()->route('cliente.login')->with('status', 'Contraseña actualizada.');
    }
}

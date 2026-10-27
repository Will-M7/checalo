<?php

use App\Enums\Rol;
use App\Models\ClienteWeb;
use App\Models\PedidoWeb;
use App\Models\User;
use Illuminate\Auth\Notifications\ResetPassword;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Notification;
use Illuminate\Support\Facades\Password;
use Livewire\Livewire;

function compradorWeb(string $email = 'buyer@example.test'): ClienteWeb
{
    return ClienteWeb::create(['nombres' => 'Comprador', 'apellidos' => 'Web', 'celular' => '519'.str_pad((string) random_int(1, 99999999), 8, '0', STR_PAD_LEFT), 'email' => $email, 'password' => 'TestPassword-123']);
}
test('login de compradores ofrece Colaborador hacia el formulario original del personal', function () {
    expect(route('login', absolute: false))->toBe('/login');
    $this->get(route('cliente.login'))->assertOk()->assertViewIs('clientes-web.auth')
        ->assertSeeInOrder(['href="'.route('login').'"', 'Colaborador'], false);
    $this->get(route('login'))->assertOk()->assertViewIs('pages::auth.login')
        ->assertSee('name="usuario"', false);
});
test('colaboradores conservan autenticación y redirección originales sin iniciar sesión de comprador', function () {
    $user = User::factory()->create();
    $this->post(route('login.store'), ['usuario' => $user->usuario, 'password' => 'password'])
        ->assertSessionHasNoErrors()->assertRedirect(route('dashboard', absolute: false));
    $this->assertAuthenticatedAs($user, 'web');
    $this->assertGuest('cliente');
});
test('registro de comprador no crea personal ni equipos y normaliza celular', function () {
    $this->post(route('cliente.register.store'), ['nombres' => 'Ana', 'apellidos' => 'Quispe', 'email' => 'ANA@example.test', 'celular' => '+51 999 123 456', 'password' => 'TestPassword-123', 'password_confirmation' => 'TestPassword-123'])->assertRedirect();
    expect(ClienteWeb::first()->celular)->toBe('51999123456')->and(ClienteWeb::first()->email)->toBe('ana@example.test')->and(User::count())->toBe(0)->and(DB::table('teams')->count())->toBe(0);
    $this->assertAuthenticated('cliente');
    $this->assertGuest('web');
});
test('comprador inicia cierra sesión y no autentica el guard interno', function () {
    compradorWeb();
    $this->post(route('cliente.login.store'), ['email' => 'buyer@example.test', 'password' => 'TestPassword-123'])->assertRedirect();
    $this->assertAuthenticated('cliente');
    $this->assertGuest('web');
    $this->post(route('cliente.logout'))->assertRedirect();
    $this->assertGuest('cliente');
});
test('recuperación usa broker y tokens separados del personal', function () {
    $buyer = compradorWeb();
    $token = Password::broker('clientes_web')->createToken($buyer);
    $this->post(route('cliente.password.update'), ['email' => $buyer->email, 'token' => $token, 'password' => 'NewPassword-456', 'password_confirmation' => 'NewPassword-456'])->assertRedirect(route('cliente.login'));
    expect(Hash::check('NewPassword-456', $buyer->fresh()->password))->toBeTrue();
    expect(DB::table('password_reset_tokens')->count())->toBe(0);
});
test('recuperación emite notificación nativa con enlace exclusivo del comprador', function () {
    $buyer = compradorWeb();
    Notification::fake();
    $this->post(route('cliente.password.email'), ['email' => $buyer->email])->assertRedirect()->assertSessionHas('status');
    $sent = Notification::sentNotifications();
    $notification = array_values($sent[ClienteWeb::class][$buyer->id])[0][0]['notification'];
    expect($notification)->toBeInstanceOf(ResetPassword::class);
    expect($notification->toMail($buyer)->actionUrl)->toContain('/mi-cuenta/restablecer/', 'email=buyer%40example.test');
    expect(DB::table('clientes_web_password_reset_tokens')->count())->toBe(1)->and(DB::table('password_reset_tokens')->count())->toBe(0);
});
test('comprar requiere sesión de comprador y no basta sesión de personal', function () {
    $seller = User::factory()->rol(Rol::Vendedor)->create();
    $this->actingAs($seller, 'web')->postJson(route('pedidos-web.store'), [])->assertUnauthorized();
});
test('no se pueden consultar pedidos ni boletas de otro comprador', function () {
    $a = compradorWeb();
    $b = compradorWeb('other@example.test');
    $order = PedidoWeb::factory()->create(['cliente_web_id' => $a->id]);
    $this->actingAs($b, 'cliente')->get(route('cliente.pedido', $order->id))->assertNotFound();
    $this->get($order->urlBoleta())->assertNotFound();
});
test('pantallas de compradores y catálogo conservan acceso separado', function () {
    $this->get(route('cliente.login'))->assertOk();
    $this->get(route('cliente.register'))->assertOk();
    $this->actingAs(compradorWeb(), 'cliente')->get(route('cliente.cuenta'))->assertOk();
    $this->get('/ecolacteos-huata/vendedor/ventas')->assertRedirect(route('login'));
});
test('un vendedor no configura receptores de otro vendedor', function () {
    $seller = User::factory()->rol(Rol::Vendedor)->create();
    $other = User::factory()->rol(Rol::Vendedor)->create();
    $this->actingAs($seller, 'web');
    Livewire::withQueryParams(['vendedor' => $other->id])->test('pages::vendedor.configuracion-pagos')->assertForbidden();
});

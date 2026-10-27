<?php

/**
 * Explicitly isolated HTTP + SQLite integration circuit. Never uses the operational database,
 * actual receivers, phone or bank. Requires the previously built Payment Core distribution.
 * Run: PAYMENTS_INTEGRATION_ALLOWED=1 PAYMENT_CORE_BINARY=/absolute/path/bin/qr php this-file.php
 */

use App\Enums\EstadoProduccion;
use App\Enums\Rol;
use App\Models\ClienteWeb;
use App\Models\IntentoPago;
use App\Models\MovimientoSaldo;
use App\Models\PedidoWeb;
use App\Models\Produccion;
use App\Models\ReceptorPago;
use App\Models\Receta;
use App\Models\User;
use App\Models\Venta;
use App\Services\Payments\CobrosService;
use App\Services\Payments\Dinero;
use App\Services\Payments\PaymentCore;
use App\Services\Payments\StockInsuficiente;
use App\Services\Payments\VentasService;
use Illuminate\Contracts\Console\Kernel as ConsoleKernel;
use Illuminate\Contracts\Http\Kernel as HttpKernel;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Artisan;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Storage;
use Illuminate\Support\Str;
use Illuminate\Validation\ValidationException;
use Symfony\Component\HttpFoundation\Response;

if (getenv('PAYMENTS_INTEGRATION_ALLOWED') !== '1' || ! function_exists('pcntl_fork')) {
    fwrite(STDERR, "Se requiere autorización explícita del harness y pcntl.\n");
    exit(2);
}
$binary = getenv('PAYMENT_CORE_BINARY');
if (! $binary || ! is_executable($binary)) {
    fwrite(STDERR, "Compila Payment Core y especifica PAYMENT_CORE_BINARY.\n");
    exit(2);
}
$root = sys_get_temp_dir().'/huata-circuit-'.bin2hex(random_bytes(8));
mkdir($root, 0700);
touch($root.'/application.sqlite');
foreach (['APP_ENV' => 'testing', 'APP_DEBUG' => 'false', 'APP_KEY' => 'base64:'.base64_encode(random_bytes(32)), 'DB_CONNECTION' => 'sqlite', 'DB_DATABASE' => $root.'/application.sqlite', 'DB_URL' => '', 'CACHE_STORE' => 'array', 'SESSION_DRIVER' => 'array', 'QUEUE_CONNECTION' => 'sync', 'MAIL_MAILER' => 'array', 'LOG_CHANNEL' => 'single', 'LOG_STACK' => 'single', 'BCRYPT_ROUNDS' => '4'] as $key => $value) {
    putenv($key.'='.$value);
    $_ENV[$key] = $value;
    $_SERVER[$key] = $value;
}
require dirname(__DIR__, 2).'/vendor/autoload.php';
$app = require dirname(__DIR__, 2).'/bootstrap/app.php';
$app->make(ConsoleKernel::class)->bootstrap();
config(['database.connections.sqlite.busy_timeout' => 10000, 'logging.channels.single.path' => $root.'/laravel.log', 'filesystems.disks.public.root' => $root.'/public', 'app.url' => 'http://localhost']);
Storage::forgetDisk('public');
$checks = [];
$process = null;
$exitCode = 0;
function verify(bool $ok, string $label): void
{
    global $checks;
    if (! $ok) {
        throw new RuntimeException('Falló: '.$label);
    }
    $checks[] = $label;
}
function httpWeb(string $method, string $route, array $data = []): Response
{
    $request = Request::create($route, $method, [], [], [], ['HTTP_ACCEPT' => 'application/json', 'CONTENT_TYPE' => 'application/json', 'REMOTE_ADDR' => '127.0.0.1'], json_encode($data, JSON_THROW_ON_ERROR));
    $kernel = app(HttpKernel::class);
    $response = $kernel->handle($request);
    $kernel->terminate($request, $response);

    return $response;
}
function useFixtureDatabase(string $path): void
{
    if (! is_file($path)) {
        touch($path);
    }
    DB::purge();
    config(['database.connections.sqlite.database' => $path]);
}
function parallelCircuit(string $name, int $count, Closure $work): array
{
    global $root;
    DB::disconnect();
    $pids = [];
    for ($n = 0; $n < $count; $n++) {
        $pid = pcntl_fork();
        if ($pid === -1) {
            throw new RuntimeException('No se pudo crear proceso de prueba.');
        }
        if ($pid === 0) {
            DB::purge();
            $deadline = microtime(true) + 10;
            while (! file_exists($root.'/'.$name.'.start') && microtime(true) < $deadline) {
                usleep(1000);
            }
            try {
                $result = ['ok' => true, 'value' => $work($n)];
            } catch (Throwable $e) {
                $result = ['ok' => false, 'error' => $e::class];
            }
            file_put_contents($root.'/'.$name.'.'.$n.'.json', json_encode($result));
            exit(0);
        }
        $pids[] = $pid;
    }
    touch($root.'/'.$name.'.start');
    foreach ($pids as $pid) {
        pcntl_waitpid($pid, $status);
        if (pcntl_wexitstatus($status) !== 0) {
            throw new RuntimeException('Falló un proceso de prueba.');
        }
    }
    DB::purge();

    return array_map(fn ($n) => json_decode(file_get_contents($root.'/'.$name.'.'.$n.'.json'), true, flags: JSON_THROW_ON_ERROR), range(0, $count - 1));
}
function startFixtureCore(): void
{
    global $binary, $root, $process, $port;
    $environment = getenv();
    $environment['JAVA_HOME'] = '/usr/lib/jvm/java-21-openjdk-amd64';
    $environment['PAYMENT_MODE'] = 'commercial';
    $environment['PAYMENT_HOST'] = '127.0.0.1';
    $environment['PAYMENT_PORT'] = (string) $port;
    $environment['QR_LAB_PORT'] = (string) $port;
    $environment['PAYMENT_CONFIG_FILE'] = $root.'/core.json';
    $environment['PAYMENT_DB_PATH'] = $root.'/core.sqlite';
    $process = proc_open([$binary, 'server'], [0 => ['pipe', 'r'], 1 => ['file', $root.'/core.log', 'a'], 2 => ['file', $root.'/core.log', 'a']], $pipes, $root, $environment);
    if (! is_resource($process)) {
        throw new RuntimeException('No se pudo iniciar el motor de prueba.');
    }
    fclose($pipes[0]);
    for ($n = 0; $n < 80; $n++) {
        try {
            if (Http::timeout(1)->get('http://127.0.0.1:'.$port.'/health')->successful()) {
                return;
            }
        } catch (Throwable) {
        }
        usleep(100000);
    }
    throw new RuntimeException('El motor aislado no inició. Consulta core.log.');
}
function stopFixtureCore(): void
{
    global $process;
    if (is_resource($process)) {
        proc_terminate($process);
        proc_close($process);
        $process = null;
    }
}
function sendFixtureEvidence(IntentoPago $payment, string $code, string $id): array
{
    global $port, $private;
    $receiver = $payment->receptor_snapshot['external_reference'];
    $device = collect($private['devices'])->firstWhere('receiverId', $receiver);

    return Http::withToken($device['key'])->timeout(5)->post('http://127.0.0.1:'.$port.'/api/bridge/notifications', [
        'bridgeEventId' => hash('sha256', $id), 'receiverId' => $receiver, 'packageName' => 'com.bcp.innovacxion.yapeapp',
        'postedAt' => now()->toISOString(), 'text' => 'Yape! PERSONA DE PRUEBA '.$id.' te envió un pago por S/ '.Dinero::decimal($payment->amount_minor), 'securityCode' => $code,
    ])->throw()->json();
}

try {
    // Apply the new migration on a populated pre-integration schema, then check its safe rollback.
    useFixtureDatabase($root.'/incremental.sqlite');
    $newMigration = database_path('migrations/2026_10_26_160000_integrate_canonical_payments.php');
    $oldMigrations = array_values(array_filter(glob(database_path('migrations/*.php')), fn ($file) => $file !== $newMigration));
    verify(Artisan::call('migrate', ['--path' => $oldMigrations, '--realpath' => true, '--force' => true]) === 0, 'Esquema anterior creado con migraciones históricas');
    $oldSale = Venta::factory()->create(['numero_boleta' => 'B001-004201']);
    $oldOrder = PedidoWeb::factory()->create();
    $migration = require $newMigration;
    $migration->up();
    verify(Venta::find($oldSale->id)->numero_boleta === 'B001-004201' && PedidoWeb::find($oldOrder->id)->canal === 'legacy', 'Migración incremental preserva boletas y pedidos existentes');
    verify((int) DB::table('integracion_secuencias')->where('clave', 'boleta')->value('valor') === 4201, 'Correlativo inicial usa máximo histórico y no conteo');
    $migration->down();
    verify(Venta::count() === 1 && PedidoWeb::count() === 1, 'Rollback sin historial financiero conserva registros previos');
    useFixtureDatabase($root.'/application.sqlite');
    verify(Artisan::call('migrate', ['--force' => true]) === 0, 'Migraciones completas ejecutadas exclusivamente en SQLite temporal');

    $qr = imagecreatetruecolor(300, 300);
    imagestring($qr, 5, 25, 140, 'TEST ONLY - NO PAYMENT', imagecolorallocate($qr, 255, 255, 255));
    mkdir($root.'/public', 0700);
    imagepng($qr, $root.'/public/test-only.png');
    $hash = hash_file('sha256', $root.'/public/test-only.png');
    $private = ['matchSecret' => bin2hex(random_bytes(32)), 'allowLocalHttp' => true, 'clients' => [['id' => 'isolated_shop', 'key' => bin2hex(random_bytes(32)), 'receivers' => ['test_receiver_1', 'test_receiver_2']]], 'receivers' => [], 'devices' => []];
    $sellers = [];
    for ($n = 1; $n <= 2; $n++) {
        $seller = User::factory()->rol(Rol::Vendedor)->create(['activo' => true]);
        $sellers[] = $seller;
        $receiver = ['id' => 'test_receiver_'.$n, 'name' => 'TEST ONLY receiver '.$n, 'phone' => '5199999999'.$n, 'qrSha256' => $hash, 'verified' => true, 'enabled' => true];
        $private['receivers'][] = $receiver;
        $private['devices'][] = ['id' => 'isolated_device_'.$n, 'key' => bin2hex(random_bytes(32)), 'receiverId' => $receiver['id']];
        ReceptorPago::create(['user_id' => $seller->id, 'nombre_receptor' => $receiver['name'], 'celular' => $receiver['phone'], 'whatsapp' => '5198888888'.$n, 'referencia_externa' => $receiver['id'], 'qr_path' => 'test-only.png', 'qr_sha256' => $hash, 'enabled' => true, 'verified_at' => now()]);
    }
    file_put_contents($root.'/core.json', json_encode($private, JSON_THROW_ON_ERROR));
    chmod($root.'/core.json', 0600);
    $socket = stream_socket_server('tcp://127.0.0.1:0');
    $port = (int) substr(strrchr(stream_socket_get_name($socket, false), ':'), 1);
    fclose($socket);
    startFixtureCore();
    config(['payments.enabled' => true, 'payments.url' => 'http://127.0.0.1:'.$port, 'payments.key' => $private['clients'][0]['key'], 'payments.allow_local_http' => true]);
    foreach ($private['devices'] as $device) {
        verify(Http::withToken($device['key'])->post(config('payments.url').'/api/bridge/heartbeat', ['listenerConnected' => true])->successful(), 'Heartbeat autenticado '.$device['id']);
    }
    verify(Http::get(config('payments.url').'/api/receivers')->status() === 401, 'HTTP real rechaza llamadas sin autenticación');
    verify(Http::withToken($private['clients'][0]['key'])->post(config('payments.url').'/api/lab/yape/inject', [])->status() === 404, 'HTTP comercial no monta inyección LAB');

    $response = httpWeb('POST', route('cliente.register.store'), ['nombres' => 'CLIENTE', 'apellidos' => 'PRUEBA AISLADA', 'celular' => '977777777', 'email' => 'circuit@example.test', 'password' => 'TestingOnly-42', 'password_confirmation' => 'TestingOnly-42']);
    verify($response->getStatusCode() === 302, 'Registro HTTP de comprador con guard separado');
    $buyer = ClienteWeb::where('email', 'circuit@example.test')->firstOrFail();
    verify(Auth::guard('cliente')->id() === $buyer->id && ! Auth::guard('web')->check(), 'Comprador no obtiene sesión del personal');
    $recipe = Receta::factory()->create(['precio_venta' => '7.00']);
    $product = Produccion::factory()->create(['receta_id' => $recipe->id, 'cantidad_producida' => 10, 'cantidad_vendida' => 0, 'estado' => EstadoProduccion::Completada]);
    $payload = ['items' => [['produccion_id' => $product->id, 'cantidad' => 2, 'precio_unitario' => 0.01]], 'metodo_pago' => 'yape', 'idempotency_key' => (string) Str::uuid(), 'nombre' => 'Spoof', 'celular' => '911111111'];
    $response = httpWeb('POST', route('pedidos-web.store'), $payload);
    verify($response->getStatusCode() === 201, 'Checkout HTTP crea pedido e intención mediante adaptador HTTP real');
    $order = PedidoWeb::findOrFail(json_decode($response->getContent(), true)['id']);
    $payment = IntentoPago::where('pedido_web_id', $order->id)->firstOrFail();
    verify($order->total === '14.00' && $order->celular === $buyer->celular && $product->fresh()->cantidadDisponible() === 10.0, 'Precio y contacto del servidor; stock sin reserva');
    $retry = httpWeb('POST', route('pedidos-web.store'), $payload);
    verify(json_decode($retry->getContent(), true)['id'] === $order->id && IntentoPago::count() === 1, 'Reintento HTTP conserva pedido vendedor QR e intención');
    $response = httpWeb('POST', route('cliente.pago.validar', $payment->id), ['codigo' => '381']);
    verify(json_decode($response->getContent(), true)['estado'] === 'PENDING' && Venta::count() === 0, 'Código sin evidencia no confirma ni vende');
    $ack = sendFixtureEvidence($payment, '381', 'web_purchase');
    verify(($ack['accepted'] ?? false) && $ack['eventId'] === hash('sha256', 'web_purchase'), 'Evento de prueba autenticado recibe ACK exacto');
    $response = httpWeb('POST', route('cliente.pago.validar', $payment->id), ['codigo' => '381']);
    verify(json_decode($response->getContent(), true)['aplicado'] === true && Venta::count() === 1 && $product->fresh()->cantidadDisponible() === 8.0, 'Código y evidencia producen una venta y descuentan stock una vez');
    $copies = parallelCircuit('same_paid_order', 4, fn () => app(CobrosService::class)->consultar(IntentoPago::findOrFail($payment->id))->venta_id);
    verify(count(array_filter($copies, fn ($r) => $r['ok'])) === 4 && count(array_unique(array_column($copies, 'value'))) === 1 && Venta::count() === 1 && $product->fresh()->cantidadDisponible() === 8.0, 'Cuatro consultas concurrentes no duplican venta ni stock');
    $pdf = httpWeb('GET', $order->fresh()->urlBoleta());
    verify($pdf->getStatusCode() === 200 && str_starts_with($pdf->getContent(), '%PDF-'), 'Boleta PDF real generada para propietario');
    file_put_contents($root.'/boleta-prueba.pdf', $pdf->getContent());

    $service = app(CobrosService::class);
    $topup = $service->iniciar($service->recarga($buyer, 2100, (string) Str::uuid()));
    sendFixtureEvidence($topup, '492', 'topup');
    $service->validar($topup, '492', 'cliente:'.$buyer->id, '127.0.0.2');
    $service->consultar($topup->fresh());
    verify($buyer->saldoMinor() === 2100 && MovimientoSaldo::where('tipo', 'recarga')->count() === 1, 'Recarga HTTP verificada acredita una sola vez');
    $balanceKey = (string) Str::uuid();
    $items = [['produccion_id' => $product->id, 'cantidad' => 2]];
    $service->pedido($buyer, $items, 'saldo', $balanceKey);
    $service->pedido($buyer, $items, 'saldo', $balanceKey);
    verify($buyer->saldoMinor() === 700 && Venta::count() === 2 && $product->fresh()->cantidadDisponible() === 6.0, 'Compra con saldo debita y emite boleta exactamente una vez');

    $shortOrder = $service->pedido($buyer, $items, 'yape', (string) Str::uuid());
    $short = $service->iniciar(IntentoPago::where('pedido_web_id', $shortOrder->id)->firstOrFail());
    $product->update(['cantidad_vendida' => 10]);
    sendFixtureEvidence($short, '527', 'stock_lost');
    app(PaymentCore::class)->resolve($short->external_id, '527'); // remote PAID, local process interrupted before application
    DB::disconnect();
    verify(Artisan::call('pagos:reconciliar') === 0, 'Reconciliación recupera PAID remoto no aplicado tras interrupción');
    $service->consultar($short->fresh());
    verify($short->fresh()->estado === 'PAID' && $short->fresh()->applied_at !== null && $shortOrder->fresh()->pago_estado === 'saldo_acreditado' && $buyer->saldoMinor() === 2100 && Venta::count() === 2 && MovimientoSaldo::where('tipo', 'pedido_sin_stock')->count() === 1, 'Ingreso sin stock se conserva y acredita una sola vez sin venta ficticia');

    $concurrentCredit = $service->iniciar($service->recarga($buyer, 700, (string) Str::uuid()));
    sendFixtureEvidence($concurrentCredit, '638', 'parallel_credit');
    app(PaymentCore::class)->resolve($concurrentCredit->external_id, '638');
    $results = parallelCircuit('same_credit', 4, fn () => app(CobrosService::class)->consultar(IntentoPago::findOrFail($concurrentCredit->id))->estado);
    verify(count(array_filter($results, fn ($r) => $r['ok'])) === 4 && $buyer->saldoMinor() === 2800 && MovimientoSaldo::where('intento_pago_id', $concurrentCredit->id)->count() === 1, 'Cuatro aplicaciones concurrentes generan un solo crédito');

    $expensive = Produccion::factory()->create(['receta_id' => Receta::factory()->create(['precio_venta' => '28.00'])->id, 'cantidad_producida' => 10, 'cantidad_vendida' => 0, 'estado' => EstadoProduccion::Completada]);
    $before = Venta::count();
    $results = parallelCircuit('double_spend', 2, fn () => app(CobrosService::class)->pedido(ClienteWeb::findOrFail($buyer->id), [['produccion_id' => $expensive->id, 'cantidad' => 1]], 'saldo', (string) Str::uuid())->id);
    verify(count(array_filter($results, fn ($r) => $r['ok'])) === 1 && count(array_filter($results, fn ($r) => ($r['error'] ?? '') === ValidationException::class)) === 1 && $buyer->saldoMinor() === 0 && Venta::count() === $before + 1 && $expensive->fresh()->cantidadDisponible() === 9.0, 'Doble gasto concurrente: una compra y un rechazo; saldo no negativo');

    $last = Produccion::factory()->create(['receta_id' => $recipe->id, 'cantidad_producida' => 1, 'cantidad_vendida' => 0, 'estado' => EstadoProduccion::Completada]);
    $quote = app(VentasService::class)->cotizar([['produccion_id' => $last->id, 'cantidad' => 1]]);
    $before = Venta::count();
    $results = parallelCircuit('last_stock', 2, fn ($n) => app(VentasService::class)->registrar($quote, User::findOrFail($sellers[$n]->id), 'efectivo', cash: 700)->id);
    verify(count(array_filter($results, fn ($r) => $r['ok'])) === 1 && count(array_filter($results, fn ($r) => ($r['error'] ?? '') === StockInsuficiente::class)) === 1 && Venta::count() === $before + 1 && $last->fresh()->cantidadDisponible() === 0.0, 'Última unidad concurrente: una boleta sin venta parcial ni stock negativo');
    $manual = Produccion::factory()->create(['receta_id' => $recipe->id, 'cantidad_producida' => 1, 'cantidad_vendida' => 0, 'estado' => EstadoProduccion::Completada]);
    $manualReference = (string) Str::uuid();
    $before = Venta::count();
    $results = parallelCircuit('manual_retries', 4, fn () => app(VentasService::class)->presencialManual(User::findOrFail($sellers[0]->id), [['produccion_id' => $manual->id, 'cantidad' => 1]], [], 'efectivo', [], 700, $manualReference)->id);
    verify(count(array_filter($results, fn ($r) => $r['ok'])) === 4 && count(array_unique(array_column($results, 'value'))) === 1 && Venta::count() === $before + 1 && $manual->fresh()->cantidadDisponible() === 0.0, 'Cuatro reintentos concurrentes del POS manual conservan una boleta');
    verify(Venta::distinct()->count('numero_boleta') === Venta::count(), 'Correlativos de boletas únicos bajo concurrencia');

    $rrProduct = Produccion::factory()->create(['receta_id' => $recipe->id, 'cantidad_producida' => 10, 'cantidad_vendida' => 0, 'estado' => EstadoProduccion::Completada]);
    $results = parallelCircuit('round_robin', 6, fn () => app(CobrosService::class)->pedido(ClienteWeb::findOrFail($buyer->id), [['produccion_id' => $rrProduct->id, 'cantidad' => 1]], 'yape', (string) Str::uuid())->vendedor_asignado_id);
    $counts = array_count_values(array_column(array_filter($results, fn ($r) => $r['ok']), 'value'));
    verify(count($counts) === 2 && array_sum($counts) === 6 && min($counts) === 3 && max($counts) === 3, 'Round Robin de seis procesos reparte tres pedidos por receptor');
    $sameKey = (string) Str::uuid();
    $before = PedidoWeb::count();
    $results = parallelCircuit('same_reference', 4, fn () => app(CobrosService::class)->pedido(ClienteWeb::findOrFail($buyer->id), [['produccion_id' => $rrProduct->id, 'cantidad' => 1]], 'yape', $sameKey)->id);
    verify(count(array_filter($results, fn ($r) => $r['ok'])) === 4 && count(array_unique(array_column($results, 'value'))) === 1 && PedidoWeb::count() === $before + 1, 'Creación simultánea por referencia produce un solo pedido');

    stopFixtureCore();
    startFixtureCore();
    $remote = app(PaymentCore::class)->get($payment->external_id);
    verify($remote['status'] === 'PAID' && $remote['evidenceId'] === hash('sha256', 'web_purchase'), 'Reinicio real del motor conserva PAID y evidencia aplicada');
    $coreDb = new PDO('sqlite:'.$root.'/core.sqlite');
    verify($coreDb->query('SELECT COUNT(*) FROM canonical_evidence WHERE payment_id IS NOT NULL')->fetchColumn() == 4 && $coreDb->query('SELECT COUNT(*) FROM canonical_evidence WHERE code_hmac IS NOT NULL AND length(code_hmac) != 64')->fetchColumn() == 0, 'Motor conserva cuatro evidencias consumidas únicas y códigos únicamente HMAC');
    $report = ['result' => 'passed', 'checks' => count($checks), 'evidence' => $checks, 'directory' => $root, 'bank_transfer' => false, 'notification_source' => 'isolated_authenticated_fixture'];
    file_put_contents($root.'/report.json', json_encode($report, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE));
    echo json_encode($report, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE)."\n";
} catch (Throwable $e) {
    $report = ['result' => 'failed', 'checks_passed' => count($checks), 'failure' => $e::class, 'message' => $e->getMessage(), 'directory' => $root];
    file_put_contents($root.'/report.json', json_encode($report, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE));
    fwrite(STDERR, json_encode($report, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE)."\n");
    $exitCode = 1;
} finally {
    stopFixtureCore();
}
exit($exitCode);

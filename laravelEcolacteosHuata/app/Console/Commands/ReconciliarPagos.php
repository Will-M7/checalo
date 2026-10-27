<?php

namespace App\Console\Commands;

use App\Models\IntentoPago;
use App\Services\Payments\CobrosService;
use Illuminate\Console\Command;

class ReconciliarPagos extends Command
{
    protected $signature = 'pagos:reconciliar {--limit=100}';

    protected $description = 'Recupera intenciones pendientes y pagos confirmados todavía no aplicados, sin crear cobros nuevos.';

    public function handle(CobrosService $service): int
    {
        $ok = 0;
        $pending = 0;
        IntentoPago::whereNull('applied_at')->whereIn('estado', ['PENDING', 'PAID'])->orderBy('updated_at')->limit(max(1, min(1000, (int) $this->option('limit'))))->get()->each(function ($i) use ($service, &$ok, &$pending) {
            try {
                $service->consultar($i);
                $ok++;
            } catch (\Throwable) {
                $pending++;
            }
        });
        $this->info("Consultadas: {$ok}; conservadas para reintento: {$pending}.");

        return $pending ? self::FAILURE : self::SUCCESS;
    }
}

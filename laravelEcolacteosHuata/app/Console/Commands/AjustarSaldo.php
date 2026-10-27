<?php

namespace App\Console\Commands;

use App\Models\ClienteWeb;
use App\Models\MovimientoSaldo;
use App\Models\User;
use App\Services\Payments\SaldoService;
use Illuminate\Console\Command;

class AjustarSaldo extends Command
{
    protected $signature = 'saldo:ajustar {cliente : ID del cliente web} {importe : Unidades menores con signo} {--actor= : ID del administrador responsable} {--referencia= : Motivo/referencia única de al menos diez caracteres} {--reverso= : ID del movimiento a revertir exactamente}';

    protected $description = 'Registra un ajuste o reverso administrativo auditable; no efectúa transferencias ni retiros bancarios.';

    public function handle(SaldoService $service): int
    {
        if (! preg_match('/^-?[1-9][0-9]{0,8}$/', (string) $this->argument('importe'))) {
            $this->error('El importe debe ser un entero no nulo en unidades menores.');

            return self::FAILURE;
        }
        try {
            $movement = $service->ajuste(ClienteWeb::findOrFail($this->argument('cliente')), (int) $this->argument('importe'), (string) $this->option('referencia'),
                User::findOrFail($this->option('actor')), $this->option('reverso') ? MovimientoSaldo::findOrFail($this->option('reverso')) : null);
        } catch (\Throwable) {
            $this->error('No se registró el ajuste. Revisa autorización, referencia, reverso y saldo disponible.');

            return self::FAILURE;
        }
        $this->info('Movimiento administrativo registrado: '.$movement->id.'.');

        return self::SUCCESS;
    }
}

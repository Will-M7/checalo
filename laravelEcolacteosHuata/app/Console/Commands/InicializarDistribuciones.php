<?php

namespace App\Console\Commands;

use App\Authorization\Distribuciones;
use Illuminate\Console\Command;

class InicializarDistribuciones extends Command
{
    protected $signature = 'huata:inicializar-distribuciones';

    protected $description = 'Registra las plantillas base una sola vez; Inicial se aplica únicamente antes del primer usuario.';

    public function handle(): int
    {
        Distribuciones::registrarBases();
        $this->info('Plantillas registradas. No se sobrescriben asignaciones de instalaciones existentes.');

        return self::SUCCESS;
    }
}

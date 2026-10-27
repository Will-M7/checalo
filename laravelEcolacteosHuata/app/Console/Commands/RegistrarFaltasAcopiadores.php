<?php

namespace App\Console\Commands;

use App\Models\HorarioAsistencia;
use Illuminate\Console\Attributes\Description;
use Illuminate\Console\Attributes\Signature;
use Illuminate\Console\Command;

#[Signature('asistencia:registrar-faltas-acopiadores')]
#[Description('Registra falta a los acopiadores que no marcaron asistencia en un horario que ya cerró')]
class RegistrarFaltasAcopiadores extends Command
{
    /**
     * Execute the console command.
     */
    public function handle(): int
    {
        $creadas = HorarioAsistencia::registrarFaltasVencidas();

        $this->info("Faltas registradas: {$creadas}");

        return self::SUCCESS;
    }
}

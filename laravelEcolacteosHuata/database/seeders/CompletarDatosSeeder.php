<?php

namespace Database\Seeders;

use App\Enums\EstadoLeche;
use App\Enums\EstadoProduccion;
use App\Enums\MetodoPago;
use App\Enums\Periodo;
use App\Enums\TipoComunicado;
use App\Models\Acopiador;
use App\Models\Cliente;
use App\Models\Compra;
use App\Models\CompraDetalle;
use App\Models\Comunicado;
use App\Models\EntregaPlanta;
use App\Models\Insumo;
use App\Models\PagoProveedor;
use App\Models\PrecioLeche;
use App\Models\Produccion;
use App\Models\Proveedor;
use App\Models\Receta;
use App\Models\RecetaDetalle;
use App\Models\Recojo;
use App\Models\ResultadoLaboratorio;
use App\Models\Ruta;
use App\Models\Turno;
use App\Models\UnidadMedida;
use App\Models\User;
use App\Models\Venta;
use App\Models\VentaItem;
use App\Notifications\ComunicadoPublicado;
use Illuminate\Database\Seeder;
use Illuminate\Support\Facades\Notification;

class CompletarDatosSeeder extends Seeder
{
    /**
     * Completa con datos de ejemplo las tablas que todavía están vacías en la base de datos,
     * reutilizando los usuarios, acopiadores y proveedor que ya se crearon manualmente desde la
     * app (Jhon/admin, alej/personal_planta, yeni y yeni2/acopiadores, juan/proveedor, pepe/vendedor).
     * No modifica ni borra nada de lo que ya existe.
     */
    public function run(): void
    {
        $admin = User::where('usuario', 'admin')->firstOrFail();
        $planta = User::where('usuario', 'ale')->firstOrFail();
        $vendedor = User::where('usuario', 'pepe')->firstOrFail();

        $acopiadorYeni = Acopiador::where('user_id', User::where('usuario', 'yeni')->value('id'))->firstOrFail();
        $acopiadorYeni2 = Acopiador::where('user_id', User::where('usuario', 'yeni2')->value('id'))->firstOrFail();
        $proveedor = Proveedor::firstOrFail();

        // --- Turnos (lista compartida entre acopiadores/rutas) ---
        $turnoManana = Turno::firstOrCreate(
            ['nombre' => 'Mañana', 'es_produccion' => false],
            ['hora_inicio' => '06:00', 'hora_fin' => '09:00']
        );
        $turnoTarde = Turno::firstOrCreate(
            ['nombre' => 'Tarde', 'es_produccion' => false],
            ['hora_inicio' => '15:00', 'hora_fin' => '17:30']
        );
        $turnoProduccion = Turno::firstOrCreate(
            ['nombre' => 'Turno único', 'es_produccion' => true],
            ['hora_inicio' => '08:00', 'hora_fin' => '16:00']
        );

        // --- Rutas (una por acopiador existente) ---
        $rutaYeni = Ruta::create([
            'acopiador_id' => $acopiadorYeni->id,
            'nombre' => 'Ruta Huata Centro',
            'activa' => true,
        ]);
        $rutaYeni->turnos()->syncWithoutDetaching([$turnoManana->id, $turnoTarde->id]);

        $rutaYeni2 = Ruta::create([
            'acopiador_id' => $acopiadorYeni2->id,
            'nombre' => 'Ruta Huata Lago',
            'activa' => true,
        ]);
        $rutaYeni2->turnos()->syncWithoutDetaching([$turnoManana->id, $turnoTarde->id]);
        $rutaYeni2->agregarProveedor($proveedor->id);

        // --- Recojos de los últimos 7 días del proveedor existente ---
        $precioVigente = (float) (PrecioLeche::vigente()?->precio_litro ?? 1.8);

        $recojos = collect();
        foreach (range(6, 0) as $diasAtras) {
            $litros = fake()->randomFloat(2, 8, 25);

            $recojos->push(Recojo::create([
                'proveedor_id' => $proveedor->id,
                'acopiador_id' => $proveedor->acopiador_id,
                'turno_id' => $turnoManana->id,
                'fecha' => now()->subDays($diasAtras)->toDateString(),
                'litros' => $litros,
                'litros_entregados' => $litros,
                'estado_leche' => EstadoLeche::Normal,
                'precio_litro' => $precioVigente,
                'total' => round($litros * $precioVigente, 2),
                'pagado' => $diasAtras > 1,
                'metodo_pago' => $diasAtras > 1 ? MetodoPago::Efectivo : null,
                'entregado' => true,
            ]));
        }

        // --- Entregas a planta del acopiador (resumen de los recojos de ruta) ---
        EntregaPlanta::create([
            'acopiador_id' => $proveedor->acopiador_id,
            'turno_id' => $turnoManana->id,
            'fecha' => now()->subDay()->toDateString(),
            'litros_total' => (float) $recojos->last()->litros,
            'total_pagado' => (float) $recojos->last()->total,
            'total_deuda' => 0,
            'proveedores_no_recogidos' => 0,
            'guia' => 'G-'.now()->subDay()->format('Ymd').'-001',
        ]);

        // --- Laboratorio: un resultado conforme y uno no conforme, ligados al último recojo ---
        ResultadoLaboratorio::create([
            'proveedor_id' => $proveedor->id,
            'recojo_id' => $recojos->last()->id,
            'fecha' => now()->toDateString(),
            'turno_id' => $turnoManana->id,
            'litros' => (float) $recojos->last()->litros,
            'resultado' => 'conforme',
            'densidad' => 1.031,
            'acidez' => 0.15,
            'temperatura' => 4.2,
            'grasa' => 4.50,
            'sng' => 8.70,
            'proteina' => 3.40,
            'lactosa' => 4.70,
            'sales' => 0.70,
            'total_solidos' => 13.20,
            'agua_anadida' => 0.00,
            'punto_congelacion' => -0.551,
            'ph' => 6.70,
            'conforme' => true,
        ]);

        ResultadoLaboratorio::create([
            'proveedor_id' => $proveedor->id,
            'recojo_id' => $recojos[$recojos->count() - 2]->id,
            'fecha' => now()->subDay()->toDateString(),
            'turno_id' => $turnoManana->id,
            'litros' => (float) $recojos[$recojos->count() - 2]->litros,
            'resultado' => 'aguado',
            'densidad' => 1.024,
            'acidez' => 0.19,
            'temperatura' => 9.5,
            'grasa' => 2.10,
            'sng' => 7.20,
            'proteina' => 2.60,
            'lactosa' => 3.90,
            'sales' => 0.65,
            'total_solidos' => 9.30,
            'agua_anadida' => 8.50,
            'punto_congelacion' => -0.480,
            'ph' => 6.80,
            'observaciones' => 'Se detectó leche aguada en el recojo de la mañana.',
            'conforme' => false,
        ]);
        $proveedor->registrarAlteracion();

        // --- Pagos a proveedores ---
        foreach ([7, 14] as $diasAtras) {
            PagoProveedor::create([
                'proveedor_id' => $proveedor->id,
                'fecha' => now()->subDays($diasAtras)->toDateString(),
                'monto' => fake()->randomFloat(2, 80, 250),
                'metodo_pago' => MetodoPago::Efectivo,
                'periodo' => Periodo::Semana,
            ]);
        }

        // --- Clientes del punto de venta ---
        $clienteFrecuente = Cliente::create([
            'nombres' => 'Rosa', 'apellidos' => 'Mamani Quispe', 'documento' => '70300001',
            'compras_count' => 12,
        ]);
        Cliente::create([
            'nombres' => 'Bodega', 'apellidos' => 'San Martín E.I.R.L.', 'documento' => '20456789012',
            'compras_count' => 6,
        ]);
        Cliente::create(['nombres' => 'Juan', 'apellidos' => 'Pérez Condori', 'documento' => '70300002', 'compras_count' => 2]);

        // --- Unidades de medida e insumos (módulo de producción) ---
        UnidadMedida::create(['nombre' => 'Kilogramo', 'simbolo' => 'kg']);
        $litro = UnidadMedida::create(['nombre' => 'Litro', 'simbolo' => 'L']);
        $gramo = UnidadMedida::create(['nombre' => 'Gramo', 'simbolo' => 'g']);
        $mililitro = UnidadMedida::create(['nombre' => 'Mililitro', 'simbolo' => 'ml']);
        UnidadMedida::create(['nombre' => 'Unidad', 'simbolo' => 'un']);

        $insumoLeche = Insumo::create(['nombre' => 'Leche fresca', 'es_leche' => true, 'unidad_medida_id' => $litro->id, 'costo_unitario' => $precioVigente, 'stock_actual' => 300]);
        $insumoCuajo = Insumo::create(['nombre' => 'Cuajo líquido', 'unidad_medida_id' => $mililitro->id, 'costo_unitario' => 0.08, 'stock_actual' => 500]);
        $insumoSal = Insumo::create(['nombre' => 'Sal', 'unidad_medida_id' => $gramo->id, 'costo_unitario' => 0.003, 'stock_actual' => 5000]);
        Insumo::create(['nombre' => 'Cultivo láctico', 'unidad_medida_id' => $gramo->id, 'costo_unitario' => 0.15, 'stock_actual' => 200]);

        // --- Recetas ---
        $receta = Receta::create(['nombre' => 'Queso fresco artesanal']);
        RecetaDetalle::create(['receta_id' => $receta->id, 'insumo_id' => $insumoLeche->id, 'cantidad' => 10, 'unidad_medida_id' => $litro->id]);
        RecetaDetalle::create(['receta_id' => $receta->id, 'insumo_id' => $insumoCuajo->id, 'cantidad' => 5, 'unidad_medida_id' => $mililitro->id]);
        RecetaDetalle::create(['receta_id' => $receta->id, 'insumo_id' => $insumoSal->id, 'cantidad' => 100, 'unidad_medida_id' => $gramo->id]);

        Receta::create(['nombre' => 'Queso andino madurado']);
        Receta::create(['nombre' => 'Yogurt natural']);

        // --- Producciones de ejemplo (responsable: personal de planta existente) ---
        $produccionDemo = Produccion::create([
            'fecha' => now()->subDay()->toDateString(),
            'nombre' => 'Queso fresco - lote 1',
            'receta_id' => $receta->id,
            'cantidad_producida' => 5,
            'lote' => 'L-'.now()->subDay()->format('Ymd').'-001',
            'turno_id' => $turnoProduccion->id,
            'responsable_id' => $planta->id,
            'estado' => EstadoProduccion::Planificada,
        ]);
        $produccionDemo->completar();

        Produccion::create([
            'fecha' => now()->toDateString(),
            'nombre' => 'Queso fresco - lote 2',
            'receta_id' => $receta->id,
            'cantidad_producida' => 3,
            'lote' => 'L-'.now()->format('Ymd').'-002',
            'turno_id' => $turnoProduccion->id,
            'responsable_id' => $planta->id,
            'estado' => EstadoProduccion::Planificada,
        ]);

        // --- Compra de insumo no ligado a leche ---
        $compra = Compra::create([
            'fecha' => now()->subDays(3)->toDateString(),
            'descripcion' => 'Compra de insumos para producción',
            'registrado_por' => $admin->id,
        ]);
        CompraDetalle::create([
            'compra_id' => $compra->id,
            'insumo_id' => $insumoCuajo->id,
            'cantidad' => 100,
            'costo_unitario' => 0.08,
            'subtotal' => 8.00,
        ]);

        // --- Ventas de ejemplo (una con cliente frecuente, otra libre del vendedor) ---
        $venta = Venta::create([
            'numero_boleta' => Venta::siguienteNumeroBoleta(),
            'cliente_id' => $clienteFrecuente->id,
            'vendedor_id' => $planta->id,
            'fecha' => now()->toDateString(),
            'subtotal' => 50.00,
            'descuento' => 5.00,
            'descuento_tipo' => 'Cliente frecuente',
            'total' => 45.00,
            'metodo_pago' => MetodoPago::Efectivo,
            'efectivo_recibido' => 50,
            'vuelto' => 5.00,
        ]);
        VentaItem::create([
            'venta_id' => $venta->id, 'produccion_id' => $produccionDemo->id, 'nombre' => $produccionDemo->nombre,
            'precio_unitario' => 25.00, 'cantidad' => 2, 'subtotal' => 50.00,
        ]);

        $ventaVendedor = Venta::create([
            'numero_boleta' => Venta::siguienteNumeroBoleta(),
            'vendedor_id' => $vendedor->id,
            'fecha' => now()->toDateString(),
            'subtotal' => 19.00,
            'descuento' => 0,
            'descuento_tipo' => '',
            'total' => 19.00,
            'metodo_pago' => MetodoPago::Yape,
        ]);
        VentaItem::create([
            'venta_id' => $ventaVendedor->id, 'produccion_id' => $produccionDemo->id, 'nombre' => $produccionDemo->nombre,
            'precio_unitario' => 9.50, 'cantidad' => 2, 'subtotal' => 19.00,
        ]);

        // --- Comunicados ---
        $comunicado1 = Comunicado::create([
            'tipo' => TipoComunicado::Comunicado,
            'titulo' => 'Cambio de horario de recojo',
            'descripcion' => 'A partir del lunes el recojo de la mañana se adelanta 30 minutos.',
            'fecha' => now()->subDays(2)->toDateString(),
            'autor_id' => $planta->id,
        ]);
        $comunicado2 = Comunicado::create([
            'tipo' => TipoComunicado::Asesoramiento,
            'titulo' => 'Buenas prácticas de ordeño',
            'descripcion' => 'Recuerden lavar el equipo de ordeño antes y después de cada uso para evitar alteraciones en la leche.',
            'fecha' => now()->subDay()->toDateString(),
            'autor_id' => $planta->id,
        ]);

        $destinatarios = User::where('activo', true)->where('id', '!=', $planta->id)->get();
        Notification::send($destinatarios, new ComunicadoPublicado($comunicado1));
        Notification::send($destinatarios, new ComunicadoPublicado($comunicado2));
    }
}

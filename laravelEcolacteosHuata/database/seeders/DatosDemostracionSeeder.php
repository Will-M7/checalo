<?php

namespace Database\Seeders;

use App\Enums\EstadoLeche;
use App\Enums\EstadoProduccion;
use App\Enums\MetodoPago;
use App\Enums\Periodo;
use App\Enums\Rol;
use App\Enums\TeamRole;
use App\Enums\TipoComunicado;
use App\Models\Acopiador;
use App\Models\Cliente;
use App\Models\Comunicado;
use App\Models\Descuento;
use App\Models\Empresa;
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
use App\Models\Team;
use App\Models\Turno;
use App\Models\UnidadMedida;
use App\Models\User;
use App\Models\Venta;
use App\Models\VentaItem;
use App\Notifications\ComunicadoPublicado;
use Illuminate\Database\Seeder;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Notification;

class DatosDemostracionSeeder extends Seeder
{
    /**
     * Seed the application's database con datos de demostración de Ecolácteos Huata,
     * equivalentes a los sembrados en la app móvil (mismos usuarios/contraseña 1234).
     */
    public function run(): void
    {
        if (! app()->environment(['local', 'testing'])) {
            throw new \RuntimeException('Los datos de demostración y sus contraseñas no se permiten fuera de local/testing.');
        }
        if (User::where('usuario', '!=', 'admin')->exists() || Produccion::exists() || Recojo::exists() || Venta::exists()) {
            $this->command?->warn('La base ya contiene datos. No se duplican ni sobrescriben datos de demostración.');

            return;
        }
        $this->call(AdminInicialSeeder::class);
        $team = Team::principal();

        $crearUsuario = function (array $datos) use ($team): User {
            $user = User::firstOrCreate(['usuario' => $datos['usuario']], [
                ...$datos,
                'password' => Hash::make('1234'),
                'email_verified_at' => now(),
                'activo' => true,
            ]);

            $team->members()->syncWithoutDetaching([$user->id => ['role' => TeamRole::Owner->value]]);
            $user->switchTeam($team);

            return $user;
        };

        Empresa::query()->firstOrCreate([], [
            'razon_social' => 'Asociación Ecolácteos Huata',
            'ruc' => '20123456789',
            'telefono' => '951234567',
            'direccion' => 'Comunidad de Huata, Puno, Perú',
        ]);

        PrecioLeche::query()->create([
            'precio_litro' => 1.80,
            'vigente_desde' => now()->subDays(30)->toDateString(),
        ]);

        // --- Usuarios de demostración (mismos que la app móvil) ---
        $admin = $crearUsuario([
            'name' => 'Nilda Huanca Cutipa',
            'nombres' => 'Nilda',
            'apellidos' => 'Huanca Cutipa',
            'documento' => '70123451',
            'celular' => '951111111',
            'rol' => Rol::Administrador,
            'usuario' => 'admin',
            'email' => 'admin@ecolacteoshuata.pe',
        ]);

        $plantaUser = $crearUsuario([
            'name' => 'Teodoro Cachi Larico',
            'nombres' => 'Teodoro',
            'apellidos' => 'Cachi Larico',
            'documento' => '70123452',
            'celular' => '951111112',
            'rol' => Rol::PersonalPlanta,
            'usuario' => 'planta',
            'email' => 'planta@ecolacteoshuata.pe',
        ]);

        $acopioUser = $crearUsuario([
            'name' => 'Rufino Quispe Mamani',
            'nombres' => 'Rufino',
            'apellidos' => 'Quispe Mamani',
            'documento' => '70123453',
            'celular' => '951111113',
            'rol' => Rol::Acopiador,
            'usuario' => 'acopio',
            'email' => 'acopio@ecolacteoshuata.pe',
        ]);

        $proveedorUser = $crearUsuario([
            'name' => 'Basilia Yupanqui Chura',
            'nombres' => 'Basilia',
            'apellidos' => 'Yupanqui Chura',
            'documento' => '70123454',
            'celular' => '951111114',
            'rol' => Rol::Proveedor,
            'usuario' => 'proveedor',
            'email' => 'proveedor@ecolacteoshuata.pe',
        ]);

        $vendedorUser = $crearUsuario([
            'name' => 'Flora Ticona Apaza',
            'nombres' => 'Flora',
            'apellidos' => 'Ticona Apaza',
            'documento' => '70123456',
            'celular' => '951111116',
            'rol' => Rol::Vendedor,
            'usuario' => 'vendedor',
            'email' => 'vendedor@ecolacteoshuata.pe',
        ]);

        $produccionUser = $crearUsuario([
            'name' => 'Hilario Mamani Quispe',
            'nombres' => 'Hilario',
            'apellidos' => 'Mamani Quispe',
            'documento' => '70123457',
            'celular' => '951111117',
            'rol' => Rol::Produccion,
            'usuario' => 'produccion',
            'email' => 'produccion@ecolacteoshuata.pe',
        ]);

        // --- Acopiadores y turnos ---
        $acopiador = Acopiador::create(['user_id' => $acopioUser->id, 'ruta_nombre' => 'Ruta Huata Centro']);

        $acopiadorSegundo = Acopiador::create([
            'user_id' => $crearUsuario([
                'name' => 'Marcelino Apaza Coila',
                'nombres' => 'Marcelino',
                'apellidos' => 'Apaza Coila',
                'documento' => '70123455',
                'celular' => '951111115',
                'rol' => Rol::Acopiador,
                'usuario' => 'acopio2',
                'email' => 'acopio2@ecolacteoshuata.pe',
            ])->id,
            'ruta_nombre' => 'Ruta Huata Lago',
        ]);

        // Los turnos de ruta son una lista compartida: cualquier acopiador los usa para sus
        // recojos y entregas, y cualquier ruta puede tenerlos asignados.
        $turnoManana = Turno::create(['nombre' => 'Mañana', 'hora_inicio' => '06:00', 'hora_fin' => '09:00']);
        $turnoTarde = Turno::create(['nombre' => 'Tarde', 'hora_inicio' => '15:00', 'hora_fin' => '17:30']);

        // --- Proveedores ---
        $proveedor = Proveedor::create([
            'user_id' => $proveedorUser->id,
            'finca_ubicacion' => 'Chillora, comunidad de Huata',
            'finca_referencia' => 'A 200m de la escuela primaria',
            'latitud' => -15.8215,
            'longitud' => -70.0187,
            'acopiador_id' => $acopiador->id,
        ]);

        $proveedores = collect([$proveedor]);

        foreach ([
            ['Herminia', 'Condori Flores', '70200001', -15.8267, -70.0231],
            ['Faustino', 'Mamani Ticona', '70200002', -15.8149, -70.0102],
            ['Epifania', 'Choque Huanca', '70200003', -15.8302, -70.0056],
        ] as [$nombres, $apellidos, $doc, $lat, $lng]) {
            $u = $crearUsuario([
                'name' => "{$nombres} {$apellidos}",
                'nombres' => $nombres,
                'apellidos' => $apellidos,
                'documento' => $doc,
                'celular' => '9512222'.substr($doc, -2),
                'rol' => Rol::Proveedor,
                'usuario' => 'proveedor_'.strtolower($nombres),
                'email' => strtolower($nombres).'@ecolacteoshuata.pe',
            ]);

            $proveedores->push(Proveedor::create([
                'user_id' => $u->id,
                'finca_ubicacion' => "{$apellidos}, comunidad de Huata",
                'finca_referencia' => '',
                'latitud' => $lat,
                'longitud' => $lng,
                'acopiador_id' => $acopiador->id,
            ]));
        }

        $ruta = Ruta::create(['acopiador_id' => $acopiador->id, 'nombre' => 'Ruta Huata Centro', 'activa' => true]);
        $ruta->proveedores()->attach($proveedores->pluck('id'));

        // --- Recojos de los últimos 7 días (para paneles y reportes) ---
        $precioVigente = (float) PrecioLeche::vigente()->precio_litro;

        foreach (range(6, 0) as $diasAtras) {
            foreach ($proveedores as $p) {
                $litros = fake()->randomFloat(2, 8, 25);

                Recojo::create([
                    'proveedor_id' => $p->id,
                    'acopiador_id' => $acopiador->id,
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
                ]);
            }
        }

        // --- Laboratorio: un resultado no conforme para ver el conteo de alteraciones ---
        ResultadoLaboratorio::create([
            'proveedor_id' => $proveedores->last()->id,
            'fecha' => now()->subDay()->toDateString(),
            'turno_id' => $turnoManana->id,
            'litros' => 18.50,
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
        $proveedores->last()->registrarAlteracion();

        ResultadoLaboratorio::create([
            'proveedor_id' => $proveedor->id,
            'fecha' => now()->toDateString(),
            'turno_id' => $turnoManana->id,
            'litros' => 22.00,
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

        // --- Pagos a proveedores ---
        foreach ($proveedores as $p) {
            PagoProveedor::create([
                'proveedor_id' => $p->id,
                'fecha' => now()->subDays(7)->toDateString(),
                'monto' => fake()->randomFloat(2, 80, 250),
                'metodo_pago' => MetodoPago::Efectivo,
                'periodo' => Periodo::Semana,
            ]);
        }

        // --- Descuentos disponibles en el punto de venta ---
        Descuento::create(['nombre' => 'Descuento por volumen', 'porcentaje' => 5, 'activo' => true]);
        Descuento::create(['nombre' => 'Descuento de temporada', 'porcentaje' => 10, 'activo' => true]);
        Descuento::create(['nombre' => 'Cliente frecuente', 'porcentaje' => 8, 'activo' => true]);

        // --- Clientes ---
        $clienteFrecuente = Cliente::create([
            'nombres' => 'Rosa', 'apellidos' => 'Mamani Quispe', 'documento' => '70300001',
            'compras_count' => 12,
        ]);
        Cliente::create([
            'nombres' => 'Bodega', 'apellidos' => 'San Martín E.I.R.L.', 'documento' => '20456789012',
            'compras_count' => 6,
        ]);
        Cliente::create(['nombres' => 'Juan', 'apellidos' => 'Pérez Condori', 'documento' => '70300002', 'compras_count' => 2]);

        // --- Insumos y receta (módulo de producción) ---
        UnidadMedida::create(['nombre' => 'Kilogramo', 'simbolo' => 'kg']);
        $litro = UnidadMedida::create(['nombre' => 'Litro', 'simbolo' => 'L']);
        $gramo = UnidadMedida::create(['nombre' => 'Gramo', 'simbolo' => 'g']);
        $mililitro = UnidadMedida::create(['nombre' => 'Mililitro', 'simbolo' => 'ml']);
        UnidadMedida::create(['nombre' => 'Unidad', 'simbolo' => 'un']);

        $insumoLeche = Insumo::create(['nombre' => 'Leche fresca', 'es_leche' => true, 'unidad_medida_id' => $litro->id, 'costo_unitario' => $precioVigente, 'stock_actual' => 300]);
        $insumoCuajo = Insumo::create(['nombre' => 'Cuajo líquido', 'unidad_medida_id' => $mililitro->id, 'costo_unitario' => 0.08, 'stock_actual' => 500]);
        $insumoSal = Insumo::create(['nombre' => 'Sal', 'unidad_medida_id' => $gramo->id, 'costo_unitario' => 0.003, 'stock_actual' => 5000]);
        Insumo::create(['nombre' => 'Cultivo láctico', 'unidad_medida_id' => $gramo->id, 'costo_unitario' => 0.15, 'stock_actual' => 200]);

        $receta = Receta::create(['nombre' => 'Queso fresco artesanal']);
        RecetaDetalle::create(['receta_id' => $receta->id, 'insumo_id' => $insumoLeche->id, 'cantidad' => 10, 'unidad_medida_id' => $litro->id]);
        RecetaDetalle::create(['receta_id' => $receta->id, 'insumo_id' => $insumoCuajo->id, 'cantidad' => 5, 'unidad_medida_id' => $mililitro->id]);
        RecetaDetalle::create(['receta_id' => $receta->id, 'insumo_id' => $insumoSal->id, 'cantidad' => 100, 'unidad_medida_id' => $gramo->id]);

        Receta::create(['nombre' => 'Queso andino madurado']);

        Receta::create(['nombre' => 'Yogurt natural']);

        // --- Producción de ejemplo ---
        $produccionDemo = Produccion::create([
            'fecha' => now()->subDay()->toDateString(),
            'nombre' => 'Queso fresco - lote 1',
            'receta_id' => $receta->id,
            'cantidad_producida' => 5,
            'lote' => 'L-'.now()->subDay()->format('Ymd').'-001',
            'turno_id' => $turnoManana->id,
            'responsable_id' => $produccionUser->id,
            'estado' => EstadoProduccion::Planificada,
        ]);
        $produccionDemo->completar();

        Produccion::create([
            'fecha' => now()->toDateString(),
            'nombre' => 'Queso fresco - lote 2',
            'receta_id' => $receta->id,
            'cantidad_producida' => 3,
            'lote' => 'L-'.now()->format('Ymd').'-002',
            'turno_id' => $turnoTarde->id,
            'responsable_id' => $produccionUser->id,
            'estado' => EstadoProduccion::Planificada,
        ]);

        // --- Venta de ejemplo (a partir de producción publicada) ---
        $venta = Venta::create([
            'numero_boleta' => Venta::siguienteNumeroBoleta(),
            'cliente_id' => $clienteFrecuente->id,
            'vendedor_id' => $plantaUser->id,
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

        // --- Venta de ejemplo del vendedor (para historial y panel del rol Vendedor) ---
        $ventaVendedor = Venta::create([
            'numero_boleta' => Venta::siguienteNumeroBoleta(),
            'vendedor_id' => $vendedorUser->id,
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
        // Igual que al publicar desde la pantalla de Comunicados, se notifica a los usuarios
        // activos (menos al autor) para que la campanita no quede vacía con datos de ejemplo.
        $comunicado1 = Comunicado::create([
            'tipo' => TipoComunicado::Comunicado,
            'titulo' => 'Cambio de horario de recojo',
            'descripcion' => 'A partir del lunes el recojo de la mañana se adelanta 30 minutos.',
            'fecha' => now()->subDays(2)->toDateString(),
            'autor_id' => $plantaUser->id,
        ]);
        $comunicado2 = Comunicado::create([
            'tipo' => TipoComunicado::Asesoramiento,
            'titulo' => 'Buenas prácticas de ordeño',
            'descripcion' => 'Recuerden lavar el equipo de ordeño antes y después de cada uso para evitar alteraciones en la leche.',
            'fecha' => now()->subDay()->toDateString(),
            'autor_id' => $plantaUser->id,
        ]);

        $destinatariosComunicados = User::where('activo', true)->where('id', '!=', $plantaUser->id)->get();
        Notification::send($destinatariosComunicados, new ComunicadoPublicado($comunicado1));
        Notification::send($destinatariosComunicados, new ComunicadoPublicado($comunicado2));

        // Deja al admin como usuario actual tras sembrar (útil para `tinker`/depuración manual).
        $admin->switchTeam($team);
    }
}

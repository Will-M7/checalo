<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <style>
        body { font-family: Helvetica, Arial, sans-serif; font-size: 11px; color: #061b3f; }
        .membrete { border-bottom: 2px solid #0b4fa3; padding-bottom: 10px; margin-bottom: 14px; }
        .membrete h1 { font-size: 15px; margin: 0 0 4px; color: #061b3f; }
        .membrete p { margin: 0; font-size: 9px; color: #444; }
        h2.titulo { font-size: 13px; margin: 0 0 4px; color: #083b7d; text-align: center; }
        p.numero { text-align: center; font-size: 11px; font-weight: bold; margin: 0 0 12px; }
        .proveedor { margin-bottom: 10px; font-size: 10px; }
        table.datos { width: 100%; border-collapse: collapse; margin-bottom: 10px; }
        table.datos th { background: #0b4fa3; color: #fff; text-align: left; padding: 5px 6px; font-size: 9px; }
        table.datos td { padding: 4px 6px; border-bottom: 1px solid #dce8fb; font-size: 9px; }
        .estado { text-align: center; margin: 14px 0; font-size: 12px; font-weight: bold; }
        .estado.conforme { color: #1a8049; }
        .estado.no-conforme { color: #b42318; }
        .pie { margin-top: 24px; font-size: 8px; color: #888; text-align: center; }
    </style>
</head>
<body>
    <div class="membrete">
        <h1>{{ $empresa->razon_social }}</h1>
        <p>RUC: {{ $empresa->ruc ?: '—' }} &middot; {{ $empresa->direccion }}</p>
        <p>Tel: {{ $empresa->telefono ?: '—' }}</p>
    </div>

    <h2 class="titulo">Análisis de laboratorio</h2>
    <p class="numero">N.° {{ str_pad((string) $resultado->id, 6, '0', STR_PAD_LEFT) }}</p>

    <div class="proveedor">
        <p><strong>Fecha:</strong> {{ $resultado->fecha->format('d/m/Y') }} @if ($resultado->turno) &middot; {{ $resultado->turno->nombre }} @endif</p>
        <p><strong>Proveedor:</strong> {{ $resultado->proveedor->user->nombreCompleto() }}</p>
        <p><strong>DNI/RUC:</strong> {{ $resultado->proveedor->user->documento ?? '—' }} &middot; <strong>Celular:</strong> {{ $resultado->proveedor->user->celular ?? '—' }}</p>
        <p><strong>Litraje que llegó a planta:</strong> {{ number_format((float) $resultado->litros, 2) }} L</p>
    </div>

    <table class="datos">
        <thead>
            <tr>
                <th>Parámetro</th>
                <th>Valor</th>
                <th>Parámetro</th>
                <th>Valor</th>
            </tr>
        </thead>
        <tbody>
            <tr>
                <td>SN (analizador)</td><td>{{ $resultado->sn ?? '—' }}</td>
                <td>Mode</td><td>{{ $resultado->modo ?? '—' }}</td>
            </tr>
            <tr>
                <td>Temperatura</td><td>{{ $resultado->temperatura !== null ? number_format((float) $resultado->temperatura, 2).' °C' : '—' }}</td>
                <td>Grasa</td><td>{{ $resultado->grasa !== null ? number_format((float) $resultado->grasa, 2).' %' : '—' }}</td>
            </tr>
            <tr>
                <td>SNG</td><td>{{ $resultado->sng !== null ? number_format((float) $resultado->sng, 2).' %' : '—' }}</td>
                <td>Densidad</td><td>{{ $resultado->densidad !== null ? number_format((float) $resultado->densidad, 3) : '—' }}</td>
            </tr>
            <tr>
                <td>Proteína</td><td>{{ $resultado->proteina !== null ? number_format((float) $resultado->proteina, 2).' %' : '—' }}</td>
                <td>Lactosa</td><td>{{ $resultado->lactosa !== null ? number_format((float) $resultado->lactosa, 2).' %' : '—' }}</td>
            </tr>
            <tr>
                <td>Sales</td><td>{{ $resultado->sales !== null ? number_format((float) $resultado->sales, 2).' %' : '—' }}</td>
                <td>Total sólidos</td><td>{{ $resultado->total_solidos !== null ? number_format((float) $resultado->total_solidos, 2).' %' : '—' }}</td>
            </tr>
            <tr>
                <td>Agua añadida</td><td>{{ $resultado->agua_anadida !== null ? number_format((float) $resultado->agua_anadida, 2).' %' : '—' }}</td>
                <td>Punto de congelación</td><td>{{ $resultado->punto_congelacion !== null ? number_format((float) $resultado->punto_congelacion, 3).' °C' : '—' }}</td>
            </tr>
            <tr>
                <td>pH</td><td>{{ $resultado->ph !== null ? number_format((float) $resultado->ph, 2) : '—' }}</td>
                <td>Hora y fecha del análisis</td><td>{{ $resultado->horaFechaAnalisis() ?? '—' }}</td>
            </tr>
        </tbody>
    </table>

    <p class="estado {{ $resultado->conforme ? 'conforme' : 'no-conforme' }}">
        {{ ucfirst($resultado->resultado) }} — {{ $resultado->conforme ? 'CONFORME' : 'NO CONFORME' }}
    </p>

    @if ($resultado->observaciones)
        <p><strong>Observaciones:</strong> {{ $resultado->observaciones }}</p>
    @endif

    <p class="pie">{{ config('app.name') }} &mdash; Análisis generado el {{ now()->format('d/m/Y H:i') }}</p>
</body>
</html>

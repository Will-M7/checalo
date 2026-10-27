<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <style>
        body { font-family: Helvetica, Arial, sans-serif; font-size: 11px; color: #061b3f; }
        .membrete { margin-bottom: 16px; border-bottom: 2px solid #0b4fa3; padding-bottom: 10px; }
        .membrete h1 { font-size: 16px; margin: 0 0 4px; color: #061b3f; }
        .membrete p { margin: 0; font-size: 10px; color: #444; }
        h2.titulo { font-size: 13px; margin: 0 0 12px; color: #083b7d; }
        table.datos { width: 100%; border-collapse: collapse; }
        table.datos th { background: #0b4fa3; color: #fff; text-align: left; padding: 6px 8px; font-size: 10px; }
        table.datos td { padding: 5px 8px; border-bottom: 1px solid #dce8fb; font-size: 10px; }
        table.datos tr:nth-child(even) td { background: #eef4fd; }
        .total { margin-top: 12px; font-size: 12px; font-weight: bold; text-align: right; }
        .pie { margin-top: 24px; font-size: 9px; color: #888; text-align: center; }
    </style>
</head>
<body>
    <div class="membrete">
        <h1>{{ $empresa->razon_social }}</h1>
        <p>RUC: {{ $empresa->ruc ?: '—' }} &middot; {{ $empresa->direccion }}</p>
    </div>

    <h2 class="titulo">
        Inventario de {{ $acopiador->user->nombreCompleto() }} ({{ $desde->format('d/m/Y') }} - {{ $hasta->format('d/m/Y') }})
    </h2>

    <table class="datos">
        <thead>
            <tr>
                <th>Fecha</th>
                <th>Proveedor</th>
                <th>Turno</th>
                <th>Litros</th>
                <th>Estado</th>
                <th>Total</th>
            </tr>
        </thead>
        <tbody>
            @forelse ($recojos as $recojo)
                <tr>
                    <td>{{ $recojo->fecha->format('d/m/Y') }}</td>
                    <td>{{ $recojo->proveedor->user->nombreCompleto() }}</td>
                    <td>{{ $recojo->turno->nombre }}</td>
                    <td>{{ number_format((float) $recojo->litros, 2) }}</td>
                    <td>{{ ucfirst($recojo->estado_leche) }}</td>
                    <td>S/ {{ number_format((float) $recojo->total, 2) }}</td>
                </tr>
            @empty
                <tr><td colspan="6">Sin recojos en este período.</td></tr>
            @endforelse
        </tbody>
    </table>

    <p class="total">Total: {{ number_format((float) $recojos->sum('litros'), 2) }} L &mdash; S/ {{ number_format((float) $recojos->sum('total'), 2) }}</p>

    <p class="pie">Generado el {{ now()->format('d/m/Y H:i') }} &mdash; {{ config('app.name') }}</p>
</body>
</html>

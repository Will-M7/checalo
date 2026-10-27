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
        p.numero { text-align: center; font-size: 12px; font-weight: bold; margin: 0 0 12px; }
        table.datos { width: 100%; border-collapse: collapse; margin-bottom: 10px; }
        table.datos th { background: #0b4fa3; color: #fff; text-align: left; padding: 5px 6px; font-size: 9px; }
        table.datos td { padding: 4px 6px; border-bottom: 1px solid #dce8fb; font-size: 9px; }
        .totales td { border: none; padding: 2px 6px; font-size: 10px; }
        .totales .label { text-align: right; color: #555; }
        .totales .valor { text-align: right; font-weight: bold; width: 90px; }
        .grand-total { font-size: 13px; color: #061b3f; }
        .cliente { margin-bottom: 10px; font-size: 10px; }
        .pie { margin-top: 24px; font-size: 8px; color: #888; text-align: center; }
    </style>
</head>
<body>
    <div class="membrete">
        <h1>{{ $empresa->razon_social }}</h1>
        <p>RUC: {{ $empresa->ruc ?: '—' }} &middot; {{ $empresa->direccion }}</p>
        <p>Tel: {{ $empresa->telefono ?: '—' }}</p>
    </div>

    <h2 class="titulo">Boleta de venta</h2>
    <p class="numero">{{ $venta->numero_boleta }}</p>

    <div class="cliente">
        <p><strong>Fecha:</strong> {{ $venta->fecha->format('d/m/Y') }}</p>
        <p><strong>Cliente:</strong> {{ $venta->cliente ? trim($venta->cliente->nombres.' '.$venta->cliente->apellidos) : ($venta->cliente_nombre_libre ?: 'Cliente no registrado') }}</p>
        @if ($venta->cliente?->documento || $venta->cliente_doc_libre)
            <p><strong>DNI/RUC:</strong> {{ $venta->cliente?->documento ?? $venta->cliente_doc_libre }}</p>
        @endif
        @if ($venta->vendedor)
            <p><strong>Atendido por:</strong> {{ $venta->vendedor->nombreCompleto() }}</p>
        @endif
    </div>

    <table class="datos">
        <thead>
            <tr>
                <th>Producto</th>
                <th>P. unit.</th>
                <th>Cant.</th>
                <th>Subtotal</th>
            </tr>
        </thead>
        <tbody>
            @foreach ($venta->items as $item)
                <tr>
                    <td>{{ $item->nombre }}</td>
                    <td>S/ {{ number_format((float) $item->precio_unitario, 2) }}</td>
                    <td>{{ $item->cantidad }}</td>
                    <td>S/ {{ number_format((float) $item->subtotal, 2) }}</td>
                </tr>
            @endforeach
        </tbody>
    </table>

    <table class="totales" style="width: 100%;">
        <tr>
            <td class="label">Subtotal</td>
            <td class="valor">S/ {{ number_format((float) $venta->subtotal, 2) }}</td>
        </tr>
        @if ((float) $venta->descuento > 0)
            <tr>
                <td class="label">Descuento</td>
                <td class="valor">- S/ {{ number_format((float) $venta->descuento, 2) }}</td>
            </tr>
        @endif
        <tr>
            <td class="label grand-total">Total</td>
            <td class="valor grand-total">S/ {{ number_format((float) $venta->total, 2) }}</td>
        </tr>
        <tr>
            <td class="label">Método de pago</td>
            <td class="valor">{{ ucfirst($venta->metodo_pago) }}</td>
        </tr>
        @if ($venta->efectivo_recibido !== null)
            <tr>
                <td class="label">Efectivo recibido</td>
                <td class="valor">S/ {{ number_format((float) $venta->efectivo_recibido, 2) }}</td>
            </tr>
            <tr>
                <td class="label">Vuelto</td>
                <td class="valor">S/ {{ number_format((float) ($venta->vuelto ?? 0), 2) }}</td>
            </tr>
        @endif
    </table>

    <p class="pie">Gracias por su compra &mdash; {{ config('app.name') }}</p>
</body>
</html>

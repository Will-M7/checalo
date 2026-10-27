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
        p.numero { text-align: center; font-size: 12px; font-weight: bold; margin: 0 0 8px; }
        p.estado { text-align: center; margin: 0 0 12px; font-size: 9px; color: #b81d32; font-weight: bold; }
        table.datos { width: 100%; border-collapse: collapse; margin-bottom: 10px; }
        table.datos th { background: #0b4fa3; color: #fff; text-align: left; padding: 5px 6px; font-size: 9px; }
        table.datos td { padding: 4px 6px; border-bottom: 1px solid #dce8fb; font-size: 9px; }
        .totales td { border: none; padding: 2px 6px; font-size: 10px; }
        .totales .label { text-align: right; color: #555; }
        .totales .valor { text-align: right; font-weight: bold; width: 110px; }
        .grand-total { font-size: 13px; color: #061b3f; }
        .cliente { margin-bottom: 10px; font-size: 10px; }
        .aviso { margin-top: 14px; padding: 8px; background: #e6eefb; font-size: 9px; }
        .pie { margin-top: 20px; font-size: 8px; color: #888; text-align: center; }
    </style>
</head>
<body>
    <div class="membrete">
        <h1>{{ $empresa->razon_social }}</h1>
        <p>RUC: {{ $empresa->ruc ?: '—' }} &middot; {{ $empresa->direccion }}</p>
        <p>Tel: {{ $empresa->telefono ?: '—' }}</p>
    </div>

    <h2 class="titulo">Comprobante de pedido</h2>
    <p class="numero">Pedido N.º {{ $pedido->id }}</p>
    <p class="estado">PAGO PENDIENTE DE CONFIRMACIÓN</p>

    <div class="cliente">
        <p><strong>Fecha:</strong> {{ $pedido->created_at->timezone(config('app.zona_horaria_local'))->format('d/m/Y H:i') }}</p>
        <p><strong>Cliente:</strong> {{ $pedido->nombre }}</p>
        @if ($pedido->celular)
            <p><strong>Celular:</strong> {{ $pedido->celular }}</p>
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
            @foreach ($pedido->items as $item)
                <tr>
                    <td>{{ $item->producto }}</td>
                    <td>S/ {{ number_format((float) $item->precio_unitario, 2) }}</td>
                    <td>{{ $item->cantidad }}</td>
                    <td>S/ {{ number_format((float) $item->subtotal, 2) }}</td>
                </tr>
            @endforeach
        </tbody>
    </table>

    <table class="totales" style="width: 100%;">
        <tr>
            <td class="label grand-total">Total a pagar</td>
            <td class="valor grand-total">S/ {{ number_format((float) $pedido->total, 2) }}</td>
        </tr>
        <tr>
            <td class="label">Forma de pago</td>
            <td class="valor">{{ ucfirst($pedido->metodo_pago) }}</td>
        </tr>
        @if ($pedido->codigo_operacion)
            <tr>
                <td class="label">N.º de operación</td>
                <td class="valor">{{ $pedido->codigo_operacion }}</td>
            </tr>
        @endif
    </table>

    <p class="aviso">
        Guardamos tu pedido. Cuando confirmemos tu pago te entregaremos tu boleta de venta con su numeración oficial;
        este mismo enlace mostrará la boleta.
    </p>

    <p class="pie">Gracias por tu pedido &mdash; {{ config('app.name') }}</p>
</body>
</html>

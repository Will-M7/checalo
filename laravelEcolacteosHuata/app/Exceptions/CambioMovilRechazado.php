<?php

namespace App\Exceptions;

use RuntimeException;

/**
 * Un cambio subido desde la app móvil que Laravel no puede aplicar. El mensaje se le muestra
 * tal cual al usuario del teléfono.
 */
class CambioMovilRechazado extends RuntimeException {}

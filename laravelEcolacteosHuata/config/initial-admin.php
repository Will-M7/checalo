<?php

return [
    // Solo se usa al crear la primera cuenta en una base vacía. Nunca rota cuentas existentes.
    'password' => env('HUATA_INITIAL_ADMIN_PASSWORD'),
];

<?php

return [
    'required' => 'El campo :attribute es obligatorio.',
    'unique' => 'Ya existe un registro con ese :attribute.',
    'email' => 'El campo :attribute debe ser un correo electrónico válido.',
    'numeric' => 'El campo :attribute debe ser un número.',
    'min' => [
        'numeric' => 'El campo :attribute debe ser mayor o igual a :min.',
        'string' => 'El campo :attribute debe tener al menos :min caracteres.',
    ],
    'confirmed' => 'La confirmación de :attribute no coincide.',

    'attributes' => [
        'usuario' => 'usuario',
        'password' => 'contraseña',
        'nombres' => 'nombres',
        'apellidos' => 'apellidos',
        'documento' => 'DNI/RUC',
        'celular' => 'celular',
        'rol' => 'rol',
    ],
];

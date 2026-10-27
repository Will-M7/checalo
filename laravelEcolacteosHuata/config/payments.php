<?php

return [
    'enabled' => env('PAYMENT_CORE_ENABLED', false),
    'url' => env('PAYMENT_CORE_URL', 'http://127.0.0.1:8087'),
    'key_file' => env('PAYMENT_CORE_KEY_FILE'),
    'key' => env('PAYMENT_CORE_KEY'),
    'allow_local_http' => env('PAYMENT_CORE_ALLOW_LOCAL_HTTP', false),
];

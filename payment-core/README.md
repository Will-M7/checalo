# Payment Core de Huata

Esta copia conserva el laboratorio y el motor comercial actual en el mismo proyecto Kotlin/JVM.
El modo comercial monta exclusivamente sus rutas comerciales. No es una integración bancaria
verificada por el hecho de compilar o arrancar. Consulta [COMMERCIAL.md](COMMERCIAL.md).

Requiere JDK 21. Desde este directorio: `./gradlew test installDist`.
Para configuración nueva, consulta [examples/README.md](examples/README.md). No se incluyen
bases, secretos, receptores operativos ni distribuciones compiladas de la laptop de origen.
`build/install/qr/bin/qr server` ejecuta la distribución generada usando las variables PAYMENT_*.
El Core debe escuchar en loopback 8087 y Laravel será su puerta de entrada autenticada.

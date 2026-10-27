plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
    application
}

group = "pe.willian.qrlab"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // ZXing: lectura y generación de QR.
    // - core: codificación/decodificación común a todas las plataformas.
    // - javase: utilidades Java SE (BufferedImage, MatrixToImageWriter/Reader)
    //   necesarias para leer QR desde archivos de imagen y escribir QR a PNG.
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.zxing:javase:3.5.3")

    // JUnit 5 (Jupiter) solo para pruebas. Se importa el BOM para alinear
    // las versiones de jupiter/api/params/engine.
    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Ktor 3.2.0: servidor HTTP sintético del laboratorio.
    // SQLite local vía JDBC como único almacenamiento (sin ORM, sin servidores externos).
    implementation(platform("io.ktor:ktor-bom:3.2.0"))
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-server-status-pages")
    implementation("io.ktor:ktor-server-cors")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("org.xerial:sqlite-jdbc:3.49.1.0")
    testImplementation("io.ktor:ktor-server-test-host")
}

kotlin {
    // Toolchain 21: Gradle descarga/detecta un JDK 21 para compilar y correr
    // los tests, aunque el daemon corra en otro JDK.
    jvmToolchain(21)
}

application {
    mainClass.set("pe.willian.qrlab.MainKt")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

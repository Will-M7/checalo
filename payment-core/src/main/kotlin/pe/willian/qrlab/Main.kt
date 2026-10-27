package pe.willian.qrlab

import pe.willian.qrlab.server.commercialModule

import pe.willian.qrlab.emv.EmvDictionary
import pe.willian.qrlab.emv.TlvParseException
import pe.willian.qrlab.money.MoneyFormatter
import pe.willian.qrlab.payment.InMemoryPaymentRepository
import pe.willian.qrlab.payment.PaymentIntent
import pe.willian.qrlab.payment.PaymentRepository
import pe.willian.qrlab.payment.PaymentStateMachine
import pe.willian.qrlab.payment.PaymentStatus
import pe.willian.qrlab.payment.synthetic.SyntheticPaymentProvider
import pe.willian.qrlab.payment.synthetic.SyntheticPaymentSimulator
import pe.willian.qrlab.qr.QrImageWriter
import pe.willian.qrlab.qr.QrPayloadAnalyzer
import pe.willian.qrlab.qr.QrPayloadComparator
import pe.willian.qrlab.qr.QrSampleMatrix
import pe.willian.qrlab.synthetic.SyntheticPaymentQrBuilder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

private fun printUsage() {
    println("Uso:")
    println("  ./gradlew run --args=\"analyze archivo.txt\"")
    println("  ./gradlew run --args=\"analyze-string PAYLOAD\"")
    println("  ./gradlew run --args=\"compare archivoA.txt archivoB.txt\"")
    println("  ./gradlew run --args=\"matrix archivoA.txt archivoB.txt [archivoC.txt ...]\"")
    println("  ./gradlew run --args=\"generate-synthetic 700 output/qr-7.png\"")
    println("  ./gradlew run --args=\"create-payment SALE-001 700\"")
    println("  ./gradlew run --args=\"payment-status PAY-000001\"")
    println("  ./gradlew run --args=\"simulate-payment PAY-000001\"")
    println("  ./gradlew run --args=\"simulate-failure PAY-000001\"")
    println("  ./gradlew run --args=\"simulate-expiration PAY-000001\"")
    println("  ./gradlew run --args=\"cancel-payment PAY-000001\"")
    println("  ./gradlew run --args=\"payment-demo\"")
    println("  ./gradlew run --args=\"server\"")
    println()
    println("El archivo debe contener solamente el payload.")
    println("Para payloads largos se recomienda usar archivos en lugar de analyze-string.")
    println("Los comandos de pago usan un repositorio en memoria por proceso; payment-demo corre el flujo completo.")
}

/**
 * CLI del laboratorio QR.
 *
 * Comandos:
 * - analyze <archivo>      : lee el payload desde un archivo (solo el payload).
 * - analyze-string <payload>: analiza el payload pasado como argumento.
 * - compare <a> <b>        : compara dos payloads desde archivos.
 * - matrix <a> <b> [...]   : matriz comparativa de N>=2 muestras (solo lectura).
 * - generate-synthetic <minorUnits> <png> : genera un QR SINTÉTICO de laboratorio
 *   (QR LAB, MCC 9999). No es un QR real de pago. No lee samples/private/.
 * - create-payment / payment-status / simulate-* / cancel-payment / payment-demo:
 *   payment engine con proveedor sintético local (in-memory por proceso).
 * - server : servidor Ktor en 127.0.0.1:8080, solo pagos sintéticos.
 */
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        printUsage()
        exitProcess(2)
    }
    when (args[0]) {
        "analyze" -> {
            if (args.size != 2) {
                System.err.println("Error: \"analyze\" requiere exactamente la ruta de un archivo.")
                printUsage()
                exitProcess(2)
            }
            val payload = Files.readString(Path.of(args[1])).trim()
            runAnalysis(payload)
        }
        "analyze-string" -> {
            if (args.size != 2) {
                System.err.println("Error: \"analyze-string\" requiere exactamente un payload como argumento.")
                printUsage()
                exitProcess(2)
            }
            runAnalysis(args[1])
        }
        "compare" -> {
            if (args.size != 3) {
                System.err.println("Error: \"compare\" requiere exactamente dos archivos con payloads.")
                printUsage()
                exitProcess(2)
            }
            runComparison(args[1], args[2])
        }
        "matrix" -> {
            if (args.size < 3) {
                System.err.println("Error: \"matrix\" requiere al menos dos archivos con payloads.")
                printUsage()
                exitProcess(2)
            }
            runMatrix(args.drop(1))
        }
        "generate-synthetic" -> {
            if (args.size != 3) {
                System.err.println("Error: \"generate-synthetic\" requiere monto en minor units y ruta PNG.")
                printUsage()
                exitProcess(2)
            }
            runGenerateSynthetic(args[1], args[2])
        }
        "create-payment" -> {
            if (args.size != 3) {
                System.err.println("Error: \"create-payment\" requiere <reference> <amountMinor>.")
                printUsage()
                exitProcess(2)
            }
            runCreatePayment(args[1], args[2])
        }
        "payment-status" -> {
            if (args.size != 2) {
                System.err.println("Error: \"payment-status\" requiere <paymentId>.")
                printUsage()
                exitProcess(2)
            }
            runPaymentStatus(args[1])
        }
        "simulate-payment" -> {
            if (args.size != 2) {
                System.err.println("Error: \"simulate-payment\" requiere <paymentId>.")
                printUsage()
                exitProcess(2)
            }
            runSimulate(args[1], "confirm")
        }
        "simulate-failure" -> {
            if (args.size != 2) {
                System.err.println("Error: \"simulate-failure\" requiere <paymentId>.")
                printUsage()
                exitProcess(2)
            }
            runSimulate(args[1], "fail")
        }
        "simulate-expiration" -> {
            if (args.size != 2) {
                System.err.println("Error: \"simulate-expiration\" requiere <paymentId>.")
                printUsage()
                exitProcess(2)
            }
            runSimulate(args[1], "expire")
        }
        "cancel-payment" -> {
            if (args.size != 2) {
                System.err.println("Error: \"cancel-payment\" requiere <paymentId>.")
                printUsage()
                exitProcess(2)
            }
            runCancelPayment(args[1])
        }
        "payment-demo" -> {
            if (args.size != 1) {
                System.err.println("Error: \"payment-demo\" no recibe argumentos.")
                printUsage()
                exitProcess(2)
            }
            runPaymentDemo()
        }
        "server" -> {
            if (args.size != 1) {
                System.err.println("Error: \"server\" no recibe argumentos.")
                printUsage()
                exitProcess(2)
            }
            // Puerto por defecto 8080; QR_LAB_PORT solo para conflictos locales
            // de desarrollo (p. ej. otro servicio ocupando 8080).
            // Host por defecto 127.0.0.1; QR_LAB_HOST=0.0.0.0 expone el
            // servidor a la red local (p. ej. yape-bridge Android).
            // Persistencia SQLite: QR_LAB_DB_PATH o data/qr-lab.db.
            val port = (System.getenv("PAYMENT_PORT") ?: System.getenv("QR_LAB_PORT"))?.toIntOrNull() ?: 8080
            val host = System.getenv("PAYMENT_HOST") ?: pe.willian.qrlab.server.PaymentServer.defaultHost()
            require(System.getenv("PAYMENT_MODE") in listOf(null, "commercial", "lab")) { "Unknown PAYMENT_MODE" }
            println("Payment server: $host:$port (${System.getenv("PAYMENT_MODE") ?: "commercial"})")
            if (System.getenv("PAYMENT_MODE") == "lab") {
                require(System.getenv("QR_LAB_DB_PATH")?.contains("lab") == true) { "LAB requires an explicitly separate laboratory database path" }
                pe.willian.qrlab.server.PaymentServer.start(host = host, port = port)
            } else {
                val db = java.nio.file.Paths.get(System.getenv("PAYMENT_DB_PATH") ?: "data/commercial-payments.db")
                require(db.toAbsolutePath().normalize() != pe.willian.qrlab.server.PaymentServer.dbPath().toAbsolutePath().normalize()) { "Commercial and LAB storage must differ" }
                val canonical = pe.willian.qrlab.server.CanonicalPayments(
                    pe.willian.qrlab.payment.SqlitePaymentRepository(db), pe.willian.qrlab.server.CommercialConfig.fromEnv())
                io.ktor.server.engine.embeddedServer(io.ktor.server.netty.Netty, host=host, port=port) {
                    commercialModule(canonical)
                }.start(wait=true)
            }
        }
        else -> {
            System.err.println("Error: comando desconocido \"${args[0]}\".")
            printUsage()
            exitProcess(2)
        }
    }
}

private fun runAnalysis(payload: String) {
    try {
        print(QrPayloadAnalyzer.analyzeAndRender(payload))
    } catch (e: TlvParseException) {
        System.err.println("Payload inválido: ${e.message}")
        exitProcess(1)
    }
}

private fun labelOf(path: String): String {
    val name = Path.of(path).fileName.toString()
    return if ("." in name) name.substringBeforeLast(".") else name
}

private fun runComparison(pathA: String, pathB: String) {
    try {
        val payloadA = Files.readString(Path.of(pathA)).trim()
        val payloadB = Files.readString(Path.of(pathB)).trim()
        val labelA = labelOf(pathA)
        val labelB = labelOf(pathB)
        val comparison = QrPayloadComparator.compare(
            QrPayloadAnalyzer.analyze(payloadA),
            QrPayloadAnalyzer.analyze(payloadB),
            labelA,
            labelB,
        )
        print(QrPayloadComparator.render(comparison))
    } catch (e: TlvParseException) {
        System.err.println("Payload inválido: ${e.message}")
        exitProcess(1)
    }
}

private fun runMatrix(paths: List<String>) {
    try {
        val labels = paths.map { labelOf(it) }
        val analyses = paths.map { Files.readString(Path.of(it)).trim() }
            .map { QrPayloadAnalyzer.analyze(it) }
        print(QrSampleMatrix.render(QrSampleMatrix.build(analyses, labels)))
    } catch (e: TlvParseException) {
        System.err.println("Payload inválido: ${e.message}")
        exitProcess(1)
    }
}

private fun runGenerateSynthetic(amountArg: String, pngArg: String) {
    val amount = amountArg.toLongOrNull()
    if (amount == null || amount < 0) {
        System.err.println("Error: monto inválido \"$amountArg\": se requieren minor units >= 0.")
        exitProcess(2)
    }
    try {
        val payload = SyntheticPaymentQrBuilder.buildWithLabDefaults(amount)
        val analysis = QrPayloadAnalyzer.analyze(payload)
        val pngPath = Path.of(pngArg)
        QrImageWriter.write(payload, pngPath)
        val roundTrip = QrImageWriter.read(pngPath)
        val ok = roundTrip == payload
        println("SYNTHETIC LAB QR - NOT A REAL PAYMENT QR")
        println()
        println("SYNTHETIC QR")
        println("Amount minor : $amount")
        println("Display      : ${MoneyFormatter.format(amount)}")
        println("Payload      : $payload")
        println("CRC          : ${analysis.crcExpected}")
        println("PNG          : $pngArg")
        println("Round trip   : ${if (ok) "OK" else "MISMATCH"}")
        println()
        print(QrPayloadAnalyzer.render(analysis))
        if (!ok) exitProcess(1)
    } catch (e: TlvParseException) {
        System.err.println("Payload sintético inválido: ${e.message}")
        exitProcess(1)
    }
}

private fun currencyLabel(currency: String): String =
    EmvDictionary.decodeCurrency(currency) ?: currency

private fun printPayment(intent: PaymentIntent) {
    println("ID        : ${intent.id}")
    println("Reference : ${intent.reference}")
    println("Amount    : ${MoneyFormatter.format(intent.amountMinor)}")
    println("Currency  : ${currencyLabel(intent.currency)}")
    println("Status    : ${intent.status}")
    println("QR        : output/payments/${intent.id}.png")
    intent.paidAt?.let { println("PaidAt    : $it") }
    intent.providerOperationReference?.let { println("Operation : $it") }
}

private fun freshEngine(): Triple<PaymentRepository, SyntheticPaymentProvider, SyntheticPaymentSimulator> {
    val repository: PaymentRepository = InMemoryPaymentRepository()
    return Triple(repository, SyntheticPaymentProvider(repository), SyntheticPaymentSimulator(repository))
}

private fun runCreatePayment(reference: String, amountArg: String) {
    val amount = amountArg.toLongOrNull()
    if (amount == null || amount < 0) {
        System.err.println("Error: monto inválido \"$amountArg\": se requieren minor units >= 0.")
        exitProcess(2)
    }
    try {
        println("SYNTHETIC LAB QR - NOT A REAL PAYMENT QR")
        println()
        println("PAYMENT CREATED")
        println("---------------")
        printPayment(freshEngine().second.createPayment(reference, amount))
    } catch (e: IllegalArgumentException) {
        System.err.println("Error: ${e.message}")
        exitProcess(1)
    } catch (e: IllegalStateException) {
        System.err.println("Error: ${e.message}")
        exitProcess(1)
    }
}

private fun runPaymentStatus(paymentId: String) {
    val intent = freshEngine().second.getPayment(paymentId)
    if (intent == null) {
        System.err.println("Pago no encontrado en este proceso: $paymentId (repositorio in-memory por proceso).")
        exitProcess(1)
    }
    println("SYNTHETIC LAB QR - NOT A REAL PAYMENT QR")
    println()
    println("PAYMENT STATUS")
    println("--------------")
    printPayment(intent)
}

private fun runSimulate(paymentId: String, action: String) {
    try {
        val (_, _, simulator) = freshEngine()
        // Nota: repositorio in-memory por proceso; estos comandos solo ven
        // pagos creados dentro del mismo proceso (ver payment-demo).
        val intent = when (action) {
            "confirm" -> simulator.confirm(paymentId)
            "fail" -> simulator.fail(paymentId)
            "expire" -> simulator.expire(paymentId)
            else -> throw IllegalArgumentException("acción desconocida: $action")
        }
        println("SYNTHETIC LAB QR - NOT A REAL PAYMENT QR")
        println()
        printPayment(intent)
    } catch (e: NoSuchElementException) {
        System.err.println("Error: ${e.message} (repositorio in-memory por proceso).")
        exitProcess(1)
    } catch (e: IllegalStateException) {
        System.err.println("Error: ${e.message}")
        exitProcess(1)
    }
}

private fun runCancelPayment(paymentId: String) {
    try {
        val (_, provider, _) = freshEngine()
        println("SYNTHETIC LAB QR - NOT A REAL PAYMENT QR")
        println()
        printPayment(provider.cancelPayment(paymentId))
    } catch (e: NoSuchElementException) {
        System.err.println("Error: ${e.message} (repositorio in-memory por proceso).")
        exitProcess(1)
    } catch (e: IllegalStateException) {
        System.err.println("Error: ${e.message}")
        exitProcess(1)
    }
}

private fun runPaymentDemo() {
    val (_, provider, simulator) = freshEngine()
    println("SYNTHETIC LAB QR - NOT A REAL PAYMENT QR")
    println()
    println("PAYMENT DEMO (proveedor sintético, un solo proceso)")
    println("---------------------------------------------------")
    println("1. crear pago SALE-DEMO-001 700")
    val created = provider.createPayment("SALE-DEMO-001", 700)
    printPayment(created)
    println()
    println("2. verificar PENDING + QR")
    check(created.status == PaymentStatus.PENDING) { "se esperaba PENDING" }
    val payload = created.qrPayload ?: throw IllegalStateException("falta qrPayload")
    val analysis = QrPayloadAnalyzer.analyze(payload)
    check(analysis.crcValid == true) { "CRC inválido en QR sintético" }
    val png = Paths.get("output", "payments", "${created.id}.png")
    check(QrImageWriter.read(png) == payload) { "round-trip ZXing falló" }
    println("QR        : verificado (CRC ${analysis.crcExpected} válido, round-trip OK)")
    println()
    println("3. simular confirmación")
    val paid = simulator.confirm(created.id)
    printPayment(paid)
    check(paid.status == PaymentStatus.PAID) { "se esperaba PAID" }
    println()
    println("4. transición inválida PAID -> CANCELLED")
    try {
        provider.cancelPayment(paid.id)
        System.err.println("ERROR: la transición inválida fue aceptada")
        exitProcess(1)
    } catch (e: IllegalStateException) {
        println("Rechazada : ${e.message}")
    }
    println("5. transición inválida PAID -> PENDING")
    try {
        PaymentStateMachine.validate(PaymentStatus.PAID, PaymentStatus.PENDING)
        System.err.println("ERROR: la transición inválida fue aceptada")
        exitProcess(1)
    } catch (e: IllegalStateException) {
        println("Rechazada : ${e.message}")
    }
    println()
    println("DEMO OK: CREATED -> PENDING -> PAID; inválidas bloqueadas.")
}

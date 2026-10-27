package pe.willian.qrlab.bridge

/**
 * Almacenamiento de notificaciones entrantes.
 *
 * Implementaciones: [InMemoryBridgeStore] (tests) y [SqliteBridgeStore]
 * (persistente; sobrevive reinicios para no cobrar dos veces).
 */
interface BridgeStore {

    fun save(record: IncomingNotificationRecord): IncomingNotificationRecord

    fun findById(id: String): IncomingNotificationRecord?

    fun findByBridgeEventId(bridgeEventId: String): IncomingNotificationRecord?

    fun findByFingerprint(fingerprint: String): IncomingNotificationRecord?

    fun findByStatus(status: MatchStatus): List<IncomingNotificationRecord>

    fun findAll(): List<IncomingNotificationRecord>

    fun count(): Long
}

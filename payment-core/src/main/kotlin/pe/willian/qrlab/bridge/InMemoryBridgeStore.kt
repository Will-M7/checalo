package pe.willian.qrlab.bridge

import java.util.concurrent.ConcurrentHashMap

/** Bridge store en memoria. Sin persistencia (tests y demos efímeras). */
class InMemoryBridgeStore : BridgeStore {

    private val store = ConcurrentHashMap<String, IncomingNotificationRecord>()

    @Synchronized
    override fun save(record: IncomingNotificationRecord): IncomingNotificationRecord {
        store[record.id] = record
        return record
    }

    override fun findById(id: String): IncomingNotificationRecord? = store[id]

    override fun findByBridgeEventId(bridgeEventId: String): IncomingNotificationRecord? =
        store.values.firstOrNull { it.bridgeEventId == bridgeEventId }

    override fun findByFingerprint(fingerprint: String): IncomingNotificationRecord? =
        store.values.firstOrNull { it.rawFingerprint == fingerprint }

    override fun findByStatus(status: MatchStatus): List<IncomingNotificationRecord> =
        store.values.filter { it.matchStatus == status }

    override fun findAll(): List<IncomingNotificationRecord> = store.values.toList()

    override fun count(): Long = store.size.toLong()
}

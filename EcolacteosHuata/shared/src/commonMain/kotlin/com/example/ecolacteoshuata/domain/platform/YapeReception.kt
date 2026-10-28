package com.example.ecolacteoshuata.domain.platform

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow

data class YapeReceptionRequest(val id: String, val name: String, val phone: String, val qrUrl: String)
data class YapeReceptionEvent(val time: String, val status: String, val attempts: Int, val detail: String? = null)
data class YapeReceptionState(
    val authorized: Boolean = false, val status: String = "Desactivada", val receiver: String = "",
    val permission: Boolean = false, val listener: Boolean = false, val enabled: Boolean = false,
    val lastCommunication: String? = null, val pending: Int = 0, val review: Int = 0,
    val error: String? = null, val busy: Boolean = false,
    val requests: List<YapeReceptionRequest> = emptyList(), val online: Boolean = false,
    val phone: String = "", val sent: Int = 0, val recoverable: Int = 0,
    val recent: List<YapeReceptionEvent> = emptyList(),
)
interface YapeReception {
    val state: StateFlow<YapeReceptionState>
    fun refresh()
    fun activate()
    fun pause()
    fun permissions()
    fun decide(requestId: String, accept: Boolean) {}
    suspend fun qr(request: YapeReceptionRequest): ByteArray? = null
}
/** iOS no ofrece un listener de notificaciones de otras aplicaciones. */
class UnsupportedYapeReception : YapeReception {
    override val state = MutableStateFlow(YapeReceptionState())
    override fun refresh() {}
    override fun activate() {}
    override fun pause() {}
    override fun permissions() {}
}

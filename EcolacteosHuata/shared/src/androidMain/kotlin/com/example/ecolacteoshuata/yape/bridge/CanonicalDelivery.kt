package com.example.ecolacteoshuata.yape.bridge

import com.example.ecolacteoshuata.yape.store.BridgeOutboxEvent

/** Pure delivery decision, used by both immediate dispatch and durable WorkManager retries. */
data class DeliveryAck(val status:Int,val accepted:Any?,val eventId:Any?)
object CanonicalDelivery {
    fun accepts(eventId: String, ack: DeliveryAck): Boolean = ack.status in 200..299 && ack.accepted == true && ack.eventId == eventId
    fun drain(events:List<BridgeOutboxEvent>,deliver:(BridgeOutboxEvent)->Boolean):Boolean {
        var complete=true
        events.forEach { if(!runCatching { deliver(it) }.getOrDefault(false)) complete=false }
        return complete
    }
    fun deliver(event:BridgeOutboxEvent,receiver:String,credential:String,
                markAttempt:(String)->Unit,transmit:(String)->DeliveryAck,markSent:(String)->Unit):Boolean {
        if(event.status==BridgeOutboxEvent.Status.SENT) return true
        if(event.status!=BridgeOutboxEvent.Status.PENDING || event.receiverId.isBlank() || event.receiverId!=receiver || credential.length<32) return false
        markAttempt(event.eventId)
        return runCatching {
            val ack=transmit(CanonicalProtocol.json(event))
            val accepted=accepts(event.eventId, ack)
            if(accepted) markSent(event.eventId)
            accepted
        }.getOrDefault(false)
    }
}

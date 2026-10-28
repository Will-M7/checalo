package com.example.ecolacteoshuata.yape

/** Last known authorization permits local offline queueing only. Server authorization is mandatory for every send. */
object ReceptionCaptureGate {
    const val MAX_OFFLINE_AGE_MS = 24 * 60 * 60 * 1000L
    fun allowed(authorized: Boolean, consent: Boolean, permission: Boolean, currentScope: String, authorizedScope: String, checkedAt: Long, now: Long): Boolean =
        authorized && consent && permission && currentScope.isNotEmpty() && currentScope == authorizedScope && checkedAt > 0 && now >= checkedAt && now - checkedAt <= MAX_OFFLINE_AGE_MS
}

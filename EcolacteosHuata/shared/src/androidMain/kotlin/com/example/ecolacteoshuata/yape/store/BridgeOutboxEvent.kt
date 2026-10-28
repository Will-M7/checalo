package com.example.ecolacteoshuata.yape.store

data class BridgeOutboxEvent(
    val eventId: String,
    val createdAt: Long,
    val packageName: String,
    val postedAt: Long,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val subText: String?,
    val summaryText: String?,
    val receiverId: String = "",
    val securityCode: String? = null,
    val attemptCount: Int = 0,
    val lastAttemptAt: Long? = null,
    val status: Status = Status.PENDING,
) {
    enum class Status { PENDING, SENT, LEGACY_REVIEW, LOCAL_REVIEW, REMOTE_REVIEW }
}

package pe.willian.yapebridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.willian.yapebridge.model.CapturedNotification
import pe.willian.yapebridge.store.NotificationHistory

class NotificationHistoryTest {

    private fun sample(i: Int) = CapturedNotification(
        packageName = "com.example.app",
        notificationKey = "key-$i",
        postedAt = i.toLong(),
        title = "t$i",
        text = "x$i",
        bigText = null,
        subText = null,
        summaryText = null
    )

    @Test
    fun `keeps most recent first`() {
        val h = NotificationHistory(maxSize = 5)
        h.add(sample(1))
        h.add(sample(2))
        val snap = h.snapshot()
        assertEquals("key-2", snap[0].notificationKey)
        assertEquals("key-1", snap[1].notificationKey)
    }

    @Test
    fun `enforces max size keeping newest`() {
        val h = NotificationHistory(maxSize = 30)
        repeat(40) { h.add(sample(it)) }
        assertEquals(30, h.size())
        val snap = h.snapshot()
        assertEquals("key-39", snap.first().notificationKey)
        assertEquals("key-10", snap.last().notificationKey)
        assertTrue(snap.none { it.notificationKey == "key-0" })
    }

    @Test
    fun `clear empties history`() {
        val h = NotificationHistory()
        h.add(sample(1))
        h.add(sample(2))
        h.clear()
        assertEquals(0, h.size())
        assertTrue(h.snapshot().isEmpty())
    }

    @Test
    fun `handles nullable fields`() {
        val h = NotificationHistory()
        h.add(
            CapturedNotification(
                packageName = "p",
                notificationKey = null,
                postedAt = 0L,
                title = null,
                text = null,
                bigText = null,
                subText = null,
                summaryText = null,
                extras = emptyMap()
            )
        )
        assertEquals(1, h.size())
    }
}

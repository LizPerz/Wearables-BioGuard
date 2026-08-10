package com.example.bioguard_wearos.data.bluetooth

import com.example.bioguard_wearos.data.local.db.OutboundMessageDao
import com.example.bioguard_wearos.data.local.db.OutboundMessageEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PriorityMessageQueueTest {
    @Test
    fun `successful delivery removes durable message`() = runTest {
        val dao = FakeOutboundMessageDao()
        val queue = PriorityMessageQueue(dao)

        queue.enqueue("alert-1", "/sensors/alerts", "{}", MessagePriority.EMERGENCY) { _, _ -> true }

        assertEquals(0, dao.count())
    }

    @Test
    fun `failed delivery remains durable with attempts`() = runTest {
        val dao = FakeOutboundMessageDao()
        val queue = PriorityMessageQueue(dao)

        queue.enqueue("alert-2", "/sensors/alerts", "{}", MessagePriority.EMERGENCY) { _, _ -> false }

        assertEquals(1, dao.count())
        assertNotNull(dao.next())
        assertEquals(5, dao.next()?.attempts)
    }
}

private class FakeOutboundMessageDao : OutboundMessageDao {
    private val messages = linkedMapOf<String, OutboundMessageEntity>()

    override suspend fun insert(message: OutboundMessageEntity): Long {
        if (messages.containsKey(message.id)) return -1
        messages[message.id] = message
        return 1
    }

    override suspend fun next(): OutboundMessageEntity? =
        messages.values.minWithOrNull(compareBy<OutboundMessageEntity> { it.priority }.thenBy { it.createdAt })

    override suspend fun delete(id: String): Int = if (messages.remove(id) != null) 1 else 0

    override suspend fun incrementAttempts(id: String): Int {
        val current = messages[id] ?: return 0
        messages[id] = current.copy(attempts = current.attempts + 1)
        return 1
    }

    override suspend fun count(): Int = messages.size
}

package com.hqcard.record

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 对应 modules/record/docs/verify.md。
 * Robolectric + Room 内存库。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SwipeEventRepositoryTest {

    private lateinit var db: HqCardDatabase
    private lateinit var repo: SwipeEventRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HqCardDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = RoomSwipeEventRepository(db.swipeEventDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ---- insert ----

    @Test
    fun `insert returns event with generated id`() = runTest {
        val saved = repo.insert(1000L, "读卡器选中本机虚拟卡")
        assertTrue(saved.id > 0)
        assertEquals(1000L, saved.swipedAt)
        assertEquals("读卡器选中本机虚拟卡", saved.detail)
    }

    @Test
    fun `ids are increasing and unique`() = runTest {
        val a = repo.insert(1000L, "A")
        val b = repo.insert(2000L, "B")
        assertTrue(b.id > a.id)
    }

    @Test
    fun `blank detail throws`() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            runTest { repo.insert(1000L, "") }
        }
        assertEquals("detail must not be blank", e.message)
    }

    @Test
    fun `whitespace detail throws`() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            runTest { repo.insert(1000L, "   ") }
        }
        assertEquals("detail must not be blank", e.message)
    }

    @Test
    fun `zero swipedAt throws`() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            runTest { repo.insert(0L, "A") }
        }
        assertEquals("swipedAt must be positive", e.message)
    }

    @Test
    fun `negative swipedAt throws`() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            runTest { repo.insert(-1L, "A") }
        }
        assertEquals("swipedAt must be positive", e.message)
    }

    // ---- observeAll ----

    @Test
    fun `observeAll emits empty list when database is empty`() = runTest {
        assertEquals(emptyList<SwipeEvent>(), repo.observeAll().first())
    }

    @Test
    fun `observeAll orders by swipedAt descending`() = runTest {
        repo.insert(1000L, "A")
        repo.insert(3000L, "B")
        repo.insert(2000L, "C")
        val list = repo.observeAll().first()
        assertEquals(listOf("B", "C", "A"), list.map { it.detail })
    }

    @Test
    fun `observeAll orders same-millisecond events by id descending`() = runTest {
        repo.insert(1000L, "A")
        repo.insert(1000L, "B")
        val list = repo.observeAll().first()
        assertEquals(listOf("B", "A"), list.map { it.detail })
    }

    @Test
    fun `observeAll re-emits after insert`() = runBlocking {
        // Room 的 Flow 在真实后台线程发射，此处用真实时间避免虚拟时间竞态
        val emissions = mutableListOf<List<SwipeEvent>>()
        val job = launch { repo.observeAll().take(2).toList(emissions) }
        // 先等首次（空库）发射，再插入
        withTimeout(5_000) { while (emissions.isEmpty()) delay(10) }
        repo.insert(1000L, "A")
        withTimeout(5_000) { job.join() }

        assertEquals(2, emissions.size)
        assertEquals(emptyList<SwipeEvent>(), emissions[0])
        assertEquals(listOf("A"), emissions[1].map { it.detail })
    }

    // ---- delete ----

    @Test
    fun `delete removes only the target event`() = runTest {
        val a = repo.insert(1000L, "A")
        repo.insert(2000L, "B")
        repo.delete(a.id)
        val list = repo.observeAll().first()
        assertEquals(listOf("B"), list.map { it.detail })
    }

    @Test
    fun `delete non-existent id is a safe no-op`() = runTest {
        repo.insert(1000L, "A")
        repo.delete(999L)
        assertEquals(1, repo.observeAll().first().size)
    }

    // ---- clear ----

    @Test
    fun `clear empties the table`() = runTest {
        repo.insert(1000L, "A")
        repo.insert(2000L, "B")
        repo.clear()
        assertEquals(emptyList<SwipeEvent>(), repo.observeAll().first())
    }
}

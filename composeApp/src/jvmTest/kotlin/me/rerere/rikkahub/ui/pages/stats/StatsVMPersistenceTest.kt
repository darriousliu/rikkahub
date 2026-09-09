package me.rerere.rikkahub.ui.pages.stats

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.lifecycle.ViewModelStore
import androidx.room3.Room
import androidx.room3.RoomRawQuery
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.AppDatabaseConstructor
import me.rerere.rikkahub.data.db.buildAppDatabase
import me.rerere.rikkahub.data.db.dao.ConversationDAO
import me.rerere.rikkahub.data.db.dao.MessageDayCount
import me.rerere.rikkahub.data.db.dao.MessageNodeDAO
import me.rerere.rikkahub.data.db.dao.MessageTokenStats
import me.rerere.rikkahub.data.db.entity.ConversationEntity
import me.rerere.rikkahub.data.db.entity.MessageNodeEntity
import me.rerere.rikkahub.data.db.fts.MessageFtsDialect
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

class StatsVMPersistenceTest {
    private val dispatcher = StandardTestDispatcher()
    private val fixtures = mutableListOf<Fixture>()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() {
        fixtures.forEach {
            it.viewModels.clear()
            it.scope.cancel()
            it.database.close()
        }
        fixtures.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `initial loading and fifty millisecond delay precede the empty database result`() = runTest(dispatcher) {
        val fixture = fixture()
        val vm = fixture.createVM()
        assertEquals(AppStats(), vm.stats.value)
        runCurrent()
        advanceTimeBy(49)
        runCurrent()
        assertTrue(vm.stats.value.isLoading)
        assertTrue(fixture.calls.isEmpty())

        advanceTimeBy(1)
        assertEquals(AppStats(isLoading = false, launchCount = 37), loaded(vm))
        assertEquals(listOf("days", "conversations", "tokens"), fixture.calls)
    }

    @Test
    fun `all stored roles branches and assistants count with nullable and sixty four bit token totals`() =
        runTest(dispatcher) {
            val fixture = fixture()
            val first = fixture.addConversation()
            val second = fixture.addConversation(assistantId = Uuid.random().toString(), pinned = true)
            fixture.addConversation() // An empty conversation still counts.
            fixture.addNode(first, message("user", "2026-09-10", Triple(4_000_000_000L, 0, 0)))
            fixture.addNode(
                first,
                message("assistant", "2026-09-10", Triple(11, 7, 3)),
                message("assistant", "2026-09-10", Triple(13, 17, 5)),
                selected = 1,
            )
            fixture.addNode(second, message("system", "2026-09-10", Triple(1, 2, 1)))
            fixture.addNode(second, message("tool", "2026-09-10"))
            fixture.addNode(second, message("user", "2026-09-09"))
            fixture.addNode(second) // Empty JSON array does not contribute messages.

            assertEquals(
                AppStats(
                    isLoading = false,
                    totalConversations = 3,
                    totalMessages = 6,
                    totalPromptTokens = 4_000_000_025L,
                    totalCompletionTokens = 26,
                    totalCachedTokens = 9,
                    conversationsPerDay = mapOf(LocalDate(2026, 9, 9) to 1, LocalDate(2026, 9, 10) to 1),
                    launchCount = 37,
                ),
                loaded(fixture.createVM()),
            )
        }

    @Test
    fun `heatmap cutoff matches the original Sunday rule across weekdays leap days and year boundaries`() =
        runTest(dispatcher) {
            val dates = (6..12).map { "2026-09-${it.toString().padStart(2, '0')}" } +
                listOf("2024-02-29", "2025-01-01", "2024-12-31")
            for (date in dates) {
                val originalToday = java.time.LocalDate.parse(date)
                val start = originalToday.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)).minusWeeks(52)
                val fixture = fixture(Instant.parse("${date}T12:00:00Z"))
                val id = fixture.addConversation()
                fixture.addNode(id, message("user", start.minusDays(1).toString()))
                fixture.addNode(id, message("user", start.toString()), message("user", start.toString()))
                fixture.addNode(id, message("assistant", date))
                fixture.addNode(id, message("user", date))
                fixture.addNode(id, message("user", originalToday.plusDays(1).toString()))
                fixture.addNode(id, message("user", "9999-99-99"))

                val actual = loaded(fixture.createVM())
                assertEquals(
                    mapOf(
                        LocalDate.parse(start.toString()) to 2,
                        LocalDate.parse(date) to 1,
                        LocalDate.parse(originalToday.plusDays(1).toString()) to 1,
                    ),
                    actual.conversationsPerDay,
                    date,
                )
                // SQL still counts all messages, including the invalid-date and out-of-window messages.
                assertEquals(7, actual.totalMessages)
            }
        }

    @Test
    fun `timezone selects the local Sunday without reinterpreting stored message dates`() = runTest(dispatcher) {
        val instant = Instant.parse("2026-09-06T00:30:00Z")
        val cases = listOf(
            TimeZone.UTC to mapOf(LocalDate(2025, 9, 7) to 1),
            TimeZone.of("America/Los_Angeles") to mapOf(
                LocalDate(2025, 8, 31) to 1,
                LocalDate(2025, 9, 6) to 1,
                LocalDate(2025, 9, 7) to 1,
            ),
        )
        for ((zone, expected) in cases) {
            val fixture = fixture(instant, zone)
            val id = fixture.addConversation()
            listOf("2025-08-31", "2025-09-06", "2025-09-07").forEach { day ->
                fixture.addNode(id, message("user", day))
            }
            val actual = loaded(fixture.createVM())
            assertEquals(expected, actual.conversationsPerDay, zone.id)
            assertEquals(3, actual.totalMessages)
        }
    }

    @Test
    fun `query order and launch count read after token aggregation are preserved`() = runTest(dispatcher) {
        val fixture = fixture()
        fixture.beforeTokenStats = { fixture.settings.update { it.copy(launchCount = 99) } }
        val actual = loaded(fixture.createVM())
        assertEquals(listOf("days", "conversations", "tokens"), fixture.calls)
        assertEquals(99, actual.launchCount)
    }

    @Test
    fun `loaded view model stays a snapshot and a new view model loads updated data`() = runTest(dispatcher) {
        val fixture = fixture()
        val vm = fixture.createVM()
        val empty = loaded(vm)
        val id = fixture.addConversation()
        fixture.addNode(id, message("user", "2026-09-10"))
        fixture.settings.update { it.copy(launchCount = 100) }
        runCurrent()
        assertEquals(empty, vm.stats.value)

        val reentered = loaded(fixture.createVM())
        assertEquals(1, reentered.totalConversations)
        assertEquals(1, reentered.totalMessages)
        assertEquals(100, reentered.launchCount)
        assertFalse(reentered.isLoading)
    }

    @Test
    fun `clearing the view model before the delay prevents database queries`() = runTest(dispatcher) {
        val fixture = fixture()
        val vm = fixture.createVM()
        runCurrent()
        fixture.viewModels.clear()
        advanceTimeBy(100)
        runCurrent()
        assertTrue(vm.stats.value.isLoading)
        assertTrue(fixture.calls.isEmpty())
    }

    private fun fixture(
        instant: Instant = Instant.parse("2026-09-10T12:00:00Z"),
        zone: TimeZone = TimeZone.UTC,
    ) = Fixture(object : Clock { override fun now() = instant }, zone).also { fixtures += it }

    private suspend fun loaded(vm: StatsVM): AppStats = withContext(Dispatchers.Default) {
        withTimeout(10.seconds) { vm.stats.first { !it.isLoading } }
    }

    private class Fixture(val clock: Clock, val zone: TimeZone) {
        val database: AppDatabase = buildAppDatabase(
            Room.inMemoryDatabaseBuilder<AppDatabase>(AppDatabaseConstructor::initialize),
            BundledSQLiteDriver(),
            MessageFtsDialect.UNICODE61,
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val settings = SettingsStore(MemoryPreferences(), scope)
        val viewModels = ViewModelStore()
        val calls = mutableListOf<String>()
        var beforeTokenStats: suspend () -> Unit = {}
        private val conversations = object : ConversationDAO by database.conversationDao() {
            override suspend fun countAll(): Int {
                calls += "conversations"
                return database.conversationDao().countAll()
            }
        }
        private val messages = object : MessageNodeDAO by database.messageNodeDao() {
            override suspend fun getMessageCountPerDayRaw(query: RoomRawQuery): List<MessageDayCount> {
                calls += "days"
                return database.messageNodeDao().getMessageCountPerDayRaw(query)
            }

            override suspend fun getTokenStatsRaw(query: RoomRawQuery): MessageTokenStats {
                calls += "tokens"
                beforeTokenStats()
                return database.messageNodeDao().getTokenStatsRaw(query)
            }
        }

        fun createVM(): StatsVM = StatsVM(
            conversationDAO = conversations,
            messageNodeDAO = messages,
            settingsStore = settings,
            clock = clock,
            timeZone = zone,
        ).also { viewModels.put(Uuid.random().toString(), it) }

        suspend fun addConversation(
            assistantId: String = Uuid.random().toString(),
            pinned: Boolean = false,
        ): String {
            val id = Uuid.random().toString()
            database.conversationDao().insert(
                ConversationEntity(
                    id = id,
                    assistantId = assistantId,
                    title = "Stats test",
                    nodes = "[]",
                    createAt = 0,
                    updateAt = 0,
                    chatSuggestions = "[]",
                    isPinned = pinned,
                ),
            )
            return id
        }

        suspend fun addNode(conversation: String, vararg messages: JsonObject, selected: Int = 0) {
            database.messageNodeDao().insert(
                MessageNodeEntity(
                    id = Uuid.random().toString(),
                    conversationId = conversation,
                    nodeIndex = 0,
                    messages = buildJsonArray { messages.forEach(::add) }.toString(),
                    selectIndex = selected,
                ),
            )
        }
    }

    private class MemoryPreferences : DataStore<Preferences> {
        override val data = MutableStateFlow(preferencesOf(SettingsStore.LAUNCH_COUNT to 37))
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }

    private companion object {
        fun message(role: String, day: String, usage: Triple<Long, Long, Long>? = null): JsonObject = buildJsonObject {
            put("role", role)
            put("createdAt", "${day}T00:00:00")
            if (usage != null) {
                put("usage", buildJsonObject {
                    put("promptTokens", usage.first)
                    put("completionTokens", usage.second)
                    put("cachedTokens", usage.third)
                })
            }
        }
    }
}

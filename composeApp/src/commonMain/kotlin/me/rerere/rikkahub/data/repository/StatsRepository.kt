package me.rerere.rikkahub.data.repository

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import me.rerere.common.time.today
import me.rerere.rikkahub.data.db.dao.ConversationDAO
import me.rerere.rikkahub.data.db.dao.MessageDayCount
import me.rerere.rikkahub.data.db.dao.MessageNodeDAO
import me.rerere.rikkahub.data.db.dao.MessageTokenStats
import me.rerere.rikkahub.data.db.dao.getMessageCountPerDay
import me.rerere.rikkahub.data.db.dao.getTokenStats
import me.rerere.rikkahub.data.model.AppStats
import kotlin.time.Clock

interface StatsQueries {
    suspend fun countConversations(): Int

    suspend fun getMessageCountPerDay(startDate: String): List<MessageDayCount>

    suspend fun getTokenStats(): MessageTokenStats
}

class RoomStatsQueries(
    private val conversationDAO: ConversationDAO,
    private val messageNodeDAO: MessageNodeDAO,
) : StatsQueries {
    override suspend fun countConversations(): Int = conversationDAO.countAll()

    override suspend fun getMessageCountPerDay(startDate: String): List<MessageDayCount> =
        messageNodeDAO.getMessageCountPerDay(startDate)

    override suspend fun getTokenStats(): MessageTokenStats = messageNodeDAO.getTokenStats()
}

class StatsRepository(
    private val queries: StatsQueries,
    private val launchCountProvider: () -> Int,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    suspend fun loadStats(): AppStats {
        val startDate = heatmapStartDate(clock.today(timeZone)).toString()
        val conversationsPerDay = queries.getMessageCountPerDay(startDate)
            .mapNotNull { entry ->
                runCatching { LocalDate.parse(entry.day) to entry.count }.getOrNull()
            }
            .toMap()
        val totalConversations = queries.countConversations()
        val tokenStats = queries.getTokenStats()

        return AppStats(
            isLoading = false,
            totalConversations = totalConversations,
            totalMessages = tokenStats.totalMessages,
            totalPromptTokens = tokenStats.promptTokens,
            totalCompletionTokens = tokenStats.completionTokens,
            totalCachedTokens = tokenStats.cachedTokens,
            conversationsPerDay = conversationsPerDay,
            launchCount = launchCountProvider(),
        )
    }
}

fun heatmapStartDate(today: LocalDate): LocalDate {
    val daysSinceSunday = (today.dayOfWeek.ordinal - DayOfWeek.SUNDAY.ordinal + 7) % 7
    return today.minus(daysSinceSunday + 52 * 7, DateTimeUnit.DAY)
}

package com.fuso.core.intelligence

import com.fuso.core.common.dispatcher.ApplicationScope
import com.fuso.core.database.dao.UsageDao
import com.fuso.core.database.entity.UsageEventEntity
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Singleton
class UsageTracker @Inject constructor(
    private val usageDao: UsageDao,
    @ApplicationScope private val scope: CoroutineScope,
) {

    private val zone: ZoneId = ZoneId.systemDefault()

    fun logAppOpen() = log(UsageEventEntity.TYPE_APP_OPEN)

    fun logEntrySaved(wordCount: Int, entryId: String) =
        log(UsageEventEntity.TYPE_ENTRY_SAVED, wordCount = wordCount, detail = entryId)

    private fun log(type: String, wordCount: Int = 0, detail: String = "") {
        scope.launch {
            runCatching {
                usageDao.insertEvent(
                    UsageEventEntity.of(
                        type = type,
                        atMillis = System.currentTimeMillis(),
                        zone = zone,
                        wordCount = wordCount,
                        detail = detail,
                    ),
                )
            }
        }
    }

    suspend fun lastEntrySavedAt(): Instant? =
        usageDao.lastEntrySavedAtMillis()?.let { Instant.ofEpochMilli(it) }

    suspend fun entriesSavedSince(since: Instant): Int =
        usageDao.entriesSavedSince(since.toEpochMilli())
}

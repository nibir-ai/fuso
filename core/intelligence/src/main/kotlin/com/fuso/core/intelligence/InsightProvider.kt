package com.fuso.core.intelligence

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class GeminiConfig(
    val apiKey: String,
    val model: String = "gemini-3.6-flash",
)

@Serializable
data class RhythmSignals(
    val peakHour: Int? = null,
    val peakDay: String? = null,
    val streakDays: Int = 0,
    val entriesThisWeek: Int = 0,
    val totalWords: Int = 0,
    val daysSinceLastEntry: Int = 0,
)

interface InsightProvider {

    suspend fun nudgeMessage(signals: RhythmSignals): String?

    suspend fun weeklyInsight(signals: RhythmSignals): String?
}

@Singleton
class GeminiInsightService @Inject constructor(
    private val config: GeminiConfig,
) : InsightProvider {

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient()
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    override suspend fun nudgeMessage(signals: RhythmSignals): String? =
        generate(
            systemInstruction = NUDGE_SYSTEM,
            userPrompt = json.encodeToString(RhythmSignals.serializer(), signals),
            maxWords = 14,
        )

    override suspend fun weeklyInsight(signals: RhythmSignals): String? =
        generate(
            systemInstruction = WEEKLY_SYSTEM,
            userPrompt = json.encodeToString(RhythmSignals.serializer(), signals),
            maxWords = 16,
        )

    private suspend fun generate(systemInstruction: String, userPrompt: String, maxWords: Int): String? {
        if (config.apiKey.isBlank()) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val payload = buildJsonObject {
                    put("system_instruction", partsObject(systemInstruction))
                    put("contents", contentsArray(userPrompt))
                    put(
                        "generation_config",
                        buildJsonObject {
                            put("temperature", JsonPrimitive(0.9))
                            put("max_output_tokens", JsonPrimitive(60))
                        },
                    )
                }
                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/${config.model}:generateContent?key=${config.apiKey}")
                    .post(json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), payload).toRequestBody(jsonMedia))
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching null
                    val body = response.body?.string() ?: return@runCatching null
                    val parsed = json.decodeFromString(GenerateResponse.serializer(), body)
                    parsed.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                        ?.trim()
                        ?.takeIf { it.isNotBlank() && it.split(' ').size <= maxWords + 4 }
                }
            }.getOrNull()
        }
    }

    private fun partsObject(text: String): kotlinx.serialization.json.JsonObject =
        kotlinx.serialization.json.buildJsonObject {
            put(
                "parts",
                kotlinx.serialization.json.buildJsonArray {
                    add(textPart(text))
                },
            )
        }

    private fun contentsArray(text: String): kotlinx.serialization.json.JsonArray =
        kotlinx.serialization.json.buildJsonArray {
            add(
                kotlinx.serialization.json.buildJsonObject {
                    put(
                        "parts",
                        kotlinx.serialization.json.buildJsonArray {
                            add(textPart(text))
                        },
                    )
                },
            )
        }

    private fun textPart(text: String): kotlinx.serialization.json.JsonObject =
        kotlinx.serialization.json.buildJsonObject {
            put("text", JsonPrimitive(text))
        }

    @Serializable
    data class GenerateResponse(val candidates: List<Candidate> = emptyList())

    @Serializable
    data class Candidate(val content: Content? = null)

    @Serializable
    data class Content(val parts: List<Part> = emptyList())

    @Serializable
    data class Part(val text: String = "")

    private companion object {
        const val NUDGE_SYSTEM =
            "You write a single gentle sentence nudging someone to journal. " +
                "Warm, human, specific to the patterns given. Never mention AI, apps, or data. " +
                "No emoji, no quotes, at most 12 words."
        const val WEEKLY_SYSTEM =
            "You write one gentle observation about someone's week, based only on the aggregated " +
                "writing patterns given. Warm and human, like a caring friend noticed something. " +
                "Never mention AI, tracking, or data. No emoji, no quotes, at most 15 words."
    }
}

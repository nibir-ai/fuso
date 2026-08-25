package com.fuso.core.intelligence

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiResponseParsingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes standard generateContent response`() {
        val payload = """
        {"candidates":[{"content":{"parts":[{"text":"Sunday mornings seem to be when your pages breathe."}]}}]}
        """.trimIndent()

        val parsed = json.decodeFromString(GeminiInsightService.GenerateResponse.serializer(), payload)

        assertEquals(
            "Sunday mornings seem to be when your pages breathe.",
            parsed.candidates.first().content?.parts?.first()?.text,
        )
    }

    @Test
    fun `tolerates extra fields and missing candidates`() {
        val empty = json.decodeFromString(
            GeminiInsightService.GenerateResponse.serializer(),
            """{"usageMetadata":{"totalTokenCount":42},"promptFeedback":{}}""",
        )
        assertTrue(empty.candidates.isEmpty())
    }
}

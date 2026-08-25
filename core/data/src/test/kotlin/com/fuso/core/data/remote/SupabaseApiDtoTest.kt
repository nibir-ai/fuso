package com.fuso.core.data.remote

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseApiDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes remote entry list`() {
        val payload = """
        [
          {
            "id": "seed-journal-0",
            "user_id": "11111111-2222-3333-4444-555555555555",
            "type": "JOURNAL",
            "title": "The light came back",
            "blocks_json": [{"kind":"paragraph","text":"Morning poured over the balcony rail"}],
            "tags_json": ["morning","gratitude"],
            "is_pinned": false,
            "is_archived": false,
            "created_at": "2026-08-25T08:12:00Z",
            "updated_at": "2026-08-25T08:12:00.123Z"
          }
        ]
        """.trimIndent()

        val entry = json.decodeFromString(ListSerializer(RemoteEntry.serializer()), payload).single()

        assertEquals("seed-journal-0", entry.id)
        assertEquals("JOURNAL", entry.type)
        assertEquals("The light came back", entry.title)
        assertTrue(entry.blocks_json.toString().contains("balcony"))
        assertEquals(
            listOf("morning", "gratitude"),
            json.decodeFromString(ListSerializer(String.serializer()), entry.tags_json.toString()),
        )
        assertNull(entry.deleted_at)
    }

    @Test
    fun `decodes auth tokens with user`() {
        val payload = """
        {
          "access_token": "jwt.access.token",
          "refresh_token": "refresh-me",
          "expires_at": 1893456000,
          "user": {"id":"u-1","email":"me@example.com"}
        }
        """.trimIndent()

        val tokens = json.decodeFromString(AuthTokens.serializer(), payload)
        assertEquals("jwt.access.token", tokens.access_token)
        assertEquals("refresh-me", tokens.refresh_token)
        assertEquals(1893456000L, tokens.expires_at)
        assertEquals("u-1", tokens.user?.id)
        assertEquals("me@example.com", tokens.user?.email)
    }

    @Test
    fun `tolerates unknown fields in responses`() {
        val payload = """{"access_token":"a","some_future_field":{"nested":true}}"""
        val tokens = json.decodeFromString(AuthTokens.serializer(), payload)
        assertEquals("a", tokens.access_token)
    }
}

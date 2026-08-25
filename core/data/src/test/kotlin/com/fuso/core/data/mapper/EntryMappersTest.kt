package com.fuso.core.data.mapper

import com.fuso.core.model.BlockContent
import com.fuso.core.model.EntryType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EntryMappersTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `block round trip preserves content`() {
        val blocks: List<BlockContent> = listOf(
            BlockContent.Paragraph("Hello world"),
            BlockContent.Heading(level = 2, text = "A heading"),
            BlockContent.Todo("Buy milk", isChecked = true),
            BlockContent.Bullet("first"),
            BlockContent.Numbered("second", index = 2),
            BlockContent.Quote("So it goes", attribution = "Vonnegut"),
            BlockContent.Divider,
        )

        val encoded = blocks.map { EntryMappers.encodeBlock(it) }
        val decoded = encoded.map { EntryMappers.parseBlock(it) }

        assertEquals(blocks, decoded)
    }

    @Test
    fun `parseBlock returns null on garbage`() {
        assertNull(EntryMappers.parseBlock("not json at all"))
        assertNull(EntryMappers.parseBlock("{\"kind\":\"unknown_kind\",\"x\":1}"))
    }

    @Test
    fun `parseBlock ignores unknown keys`() {
        val jsonWithExtra = """
            {"kind":"paragraph","text":"hi","futureField":123}
        """.trimIndent()
        assertEquals(BlockContent.Paragraph("hi"), EntryMappers.parseBlock(jsonWithExtra))
    }

    @Test
    fun `derivePreview picks first non blank text`() {
        val preview = EntryMappers.derivePreview(
            listOf(
                BlockContent.Divider,
                BlockContent.Paragraph("  "),
                BlockContent.Bullet("the real preview"),
            ),
        )
        assertEquals("the real preview", preview)
    }

    @Test
    fun `buildSearchText joins title body and tags`() {
        val text = EntryMappers.buildSearchText(
            title = "Rain day",
            blocks = listOf(BlockContent.Paragraph("petrichor outside")),
            tags = listOf("weather"),
        )
        assertEquals("Rain day\npetrichor outside\nweather", text)
    }

    @Test
    fun `toEntity keeps timestamps and computes searchText`() {
        val entity = EntryMappers.toEntity(
            id = "e1",
            type = EntryType.JOURNAL,
            title = "Title",
            blocks = listOf(BlockContent.Paragraph("Body")),
            tags = listOf("tag"),
            createdAtMillis = 1000L,
            updatedAtMillis = 2000L,
            isPinned = true,
            isArchived = false,
            deletedAtMillis = null,
        )
        assertEquals("e1", entity.id)
        assertEquals("JOURNAL", entity.type)
        assertEquals(1000L, entity.createdAtEpochMillis)
        assertEquals(2000L, entity.updatedAtEpochMillis)
        assertEquals(true, entity.isPinned)
        assertEquals(null, entity.deletedAtEpochMillis)
        assertEquals("Title\nBody\ntag", entity.searchText)
    }

    @Test
    fun `blockJson round trips through json element trees`() {
        val element = buildJsonArray {
            add(
                buildJsonObject {
                    put("kind", put("paragraph"))
                    put("text", put("nested test"))
                },
            )
        }
        val decoded = EntryMappers.blockJson.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(BlockContent.serializer()),
            element.toString(),
        )
        assertEquals(listOf(BlockContent.Paragraph("nested test")), decoded)
    }

    private fun put(value: String): kotlinx.serialization.json.JsonPrimitive =
        kotlinx.serialization.json.JsonPrimitive(value)
}

package com.fuso.feature.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HighlightTest {

    @Test
    fun `query tokens drop short words`() {
        assertEquals(listOf("rain", "of", "books"), queryTokens("a rain of  books"))
    }

    @Test
    fun `highlight finds case insensitive matches`() {
        val result = highlight("Rain on the Studio Window", listOf("studio"))!!
        assertEquals("Rain on the Studio Window", result.text)
        val bold = result.spanStyles
        assertEquals(1, bold.size)
        assertEquals(12, bold.first().start)
        assertEquals(18, bold.first().end)
    }

    @Test
    fun `highlight merges overlapping ranges`() {
        val result = highlight("petrichor", listOf("petri", "richor"))!!
        assertEquals(1, result.spanStyles.size)
        assertEquals(0, result.spanStyles.first().start)
        assertEquals(9, result.spanStyles.first().end)
    }

    @Test
    fun `highlight returns null when nothing matches`() {
        assertNull(highlight("sunny day", listOf("rain")))
    }
}

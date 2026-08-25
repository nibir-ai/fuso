package com.fuso.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

enum class FusoArtworkKind { BLANK_PAGE, PINBOARD }

@Composable
fun FusoArtwork(
    kind: FusoArtworkKind,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme_artworkAccent(),
    ink: Color = MaterialTheme_artworkInk(),
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.045f, cap = StrokeCap.Round)
        when (kind) {
            FusoArtworkKind.BLANK_PAGE -> {
                val pageWidth = w * 0.52f
                val pageHeight = h * 0.72f
                val left = (w - pageWidth) / 2f
                val top = (h - pageHeight) / 2f
                drawRoundRect(
                    color = ink.copy(alpha = 0.14f),
                    topLeft = Offset(left, top),
                    size = Size(pageWidth, pageHeight),
                    cornerRadius = CornerRadius(w * 0.05f),
                    style = stroke,
                )
                listOf(0.22f, 0.38f, 0.54f).forEachIndexed { index, fraction ->
                    drawLine(
                        color = ink.copy(alpha = 0.32f - index * 0.08f),
                        start = Offset(left + pageWidth * 0.18f, top + pageHeight * fraction),
                        end = Offset(
                            left + pageWidth * (if (index == 2) 0.55f else 0.82f),
                            top + pageHeight * fraction,
                        ),
                        strokeWidth = w * 0.03f,
                        cap = StrokeCap.Round,
                    )
                }
                val path = Path().apply {
                    moveTo(left + pageWidth * 0.62f, top + pageHeight * 0.78f)
                    cubicTo(
                        left + pageWidth * 0.78f, top + pageHeight * 0.62f,
                        left + pageWidth * 0.95f, top + pageHeight * 0.78f,
                        left + pageWidth * 0.80f, top + pageHeight * 0.94f,
                    )
                    close()
                }
                drawPath(path, color = accent)
            }
            FusoArtworkKind.PINBOARD -> {
                val boardW = w * 0.66f
                val boardH = h * 0.6f
                val left = (w - boardW) / 2f
                val top = (h - boardH) / 2f
                drawRoundRect(
                    color = ink.copy(alpha = 0.14f),
                    topLeft = Offset(left, top),
                    size = Size(boardW, boardH),
                    cornerRadius = CornerRadius(w * 0.06f),
                    style = stroke,
                )
                data class Card(val cx: Float, val cy: Float, val cw: Float, val ch: Float, val tilt: Float)
                val cards = listOf(
                    Card(0.30f, 0.34f, 0.26f, 0.26f, -0.06f),
                    Card(0.68f, 0.40f, 0.24f, 0.24f, 0.05f),
                    Card(0.46f, 0.68f, 0.28f, 0.24f, 0.02f),
                )
                cards.forEach { card ->
                    val cw = boardW * card.cw
                    val chh = boardH * card.ch
                    val cx = left + boardW * card.cx
                    val cy = top + boardH * card.cy
                    drawRoundRect(
                        color = ink.copy(alpha = 0.10f),
                        topLeft = Offset(cx - cw / 2f, cy - chh / 2f),
                        size = Size(cw, chh),
                        cornerRadius = CornerRadius(w * 0.03f),
                        style = stroke,
                    )
                    drawCircle(
                        color = accent.copy(alpha = 0.9f),
                        radius = w * 0.018f,
                        center = Offset(cx, cy - chh / 2f + w * 0.03f),
                    )
                }
                drawLine(
                    color = accent.copy(alpha = 0.75f),
                    start = Offset(left + boardW * 0.36f, top + boardH * 0.34f),
                    end = Offset(left + boardW * 0.60f, top + boardH * 0.34f),
                    strokeWidth = w * 0.022f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun MaterialTheme_artworkAccent(): Color =
    androidx.compose.material3.MaterialTheme.colorScheme.primary

@Composable
private fun MaterialTheme_artworkInk(): Color =
    androidx.compose.material3.MaterialTheme.colorScheme.onSurface

private val ArtworkSpacing = 0.dp

package eu.kanade.tachiyomi.ui.reader.epub

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.epub.EpubContentBlock
import eu.kanade.tachiyomi.util.epub.EpubReaderSettings
import eu.kanade.tachiyomi.util.epub.ReaderTheme
import eu.kanade.tachiyomi.util.epub.TextAlignment

// Data class to hold fullscreen image info

data class FullscreenImageData(
    val src: String?,
    val data: ByteArray?,
    val alt: String?,
)

/**
 * Composable function to display reflowable EPUB content with cross-paragraph selection
 */
@Composable
fun EpubReflowableContent(
    contentBlocks: List<EpubContentBlock>,
    settings: EpubReaderSettings,
    modifier: Modifier = Modifier,
    chapterTitle: String = "",
    onTap: ((Offset) -> Unit)? = null,
    onImageClick: ((imageData: FullscreenImageData) -> Unit)? = null,
) {
    val backgroundColor = when (settings.theme) {
        ReaderTheme.LIGHT -> Color.White
        ReaderTheme.SEPIA -> Color(0xFFFFE4C7)
        ReaderTheme.MINT -> Color(0xFFDDE7E3)
        ReaderTheme.BLUE_GRAY -> Color(0xFF2B2B38)
        ReaderTheme.BLACK -> Color(0xFF000000)
    }

    val textColor = when (settings.theme) {
        ReaderTheme.LIGHT -> Color(0xFF222222)
        ReaderTheme.SEPIA -> Color(0xFF6B4F1D)
        ReaderTheme.MINT -> Color(0xFF2B3A35)
        ReaderTheme.BLUE_GRAY -> Color(0xFFE6E6F2)
        ReaderTheme.BLACK -> Color(0xFFECECEC)
    }

    val textAlign = when (settings.alignment) {
        TextAlignment.LEFT -> TextAlign.Start
        TextAlignment.CENTER -> TextAlign.Center
        TextAlignment.RIGHT -> TextAlign.End
        TextAlignment.JUSTIFY -> TextAlign.Justify
    }

    val fontFamily = when (settings.fontFamily) {
        "lora" -> FontFamily(Font(R.font.lora))
        "open_sans" -> FontFamily(Font(R.font.open_sans))
        "arbutus_slab" -> FontFamily(Font(R.font.arbutus_slab))
        "lato" -> FontFamily(Font(R.font.lato))
        else -> FontFamily.Default
    }

    Box(
        modifier = modifier
            .padding(horizontal = settings.margins.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(top = 85.dp, bottom = 32.dp)
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            onTap?.invoke(offset)
                        },
                    )
                },
            verticalArrangement = Arrangement.Top,
        ) {
            // Wrap all content in a single SelectionContainer to allow cross-paragraph selection
            SelectionContainer {
                Column {
                    contentBlocks.forEach { block ->
                        when (block) {
                            is EpubContentBlock.Text -> {
                                BasicText(
                                    text = block.content,
                                    style = TextStyle(
                                        color = textColor,
                                        fontSize = settings.fontSize.sp,
                                        fontFamily = fontFamily,
                                        textAlign = textAlign,
                                        lineHeight = settings.lineSpacing.em,
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                )
                            }
                            is EpubContentBlock.Paragraph -> {
                                BasicText(
                                    text = block.content,
                                    style = TextStyle(
                                        color = textColor,
                                        fontSize = settings.fontSize.sp,
                                        fontFamily = fontFamily,
                                        textAlign = textAlign,
                                        lineHeight = settings.lineSpacing.em,
                                        textIndent = TextIndent(firstLine = 1.2.em),
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                )
                            }
                            EpubContentBlock.LineBreak -> {
                                Spacer(modifier = Modifier.height(settings.lineSpacing.dp))
                            }
                            is EpubContentBlock.BlockQuote -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp, horizontal = 16.dp)
                                        .background(
                                            color = textColor.copy(alpha = 0.1f),
                                            shape = MaterialTheme.shapes.small,
                                        )
                                        .padding(8.dp),
                                ) {
                                    Column {
                                        block.content.forEach { nestedBlock ->
                                            when (nestedBlock) {
                                                is EpubContentBlock.Text -> {
                                                    BasicText(
                                                        text = nestedBlock.content,
                                                        style = TextStyle(
                                                            color = textColor,
                                                            fontSize = settings.fontSize.sp,
                                                            fontFamily = fontFamily,
                                                            textAlign = textAlign,
                                                            lineHeight = settings.lineSpacing.em,
                                                        ),
                                                    )
                                                }
                                                is EpubContentBlock.Paragraph -> {
                                                    BasicText(
                                                        text = nestedBlock.content,
                                                        style = TextStyle(
                                                            color = textColor,
                                                            fontSize = settings.fontSize.sp,
                                                            fontFamily = fontFamily,
                                                            textAlign = textAlign,
                                                            lineHeight = settings.lineSpacing.em,
                                                            textIndent = TextIndent(firstLine = 1.2.em),
                                                        ),
                                                    )
                                                }
                                                // Handle other nested block types if needed
                                                else -> { /* Skip other block types */ }
                                            }
                                        }
                                    }
                                }
                            }
                            is EpubContentBlock.Header -> {
                                val headerSize = when (block.level) {
                                    1 -> settings.fontSize * 1.6f
                                    2 -> settings.fontSize * 1.4f
                                    3 -> settings.fontSize * 1.3f
                                    4 -> settings.fontSize * 1.2f
                                    5 -> settings.fontSize * 1.1f
                                    else -> settings.fontSize
                                }

                                BasicText(
                                    text = block.content,
                                    style = TextStyle(
                                        color = textColor,
                                        fontSize = headerSize.sp,
                                        fontFamily = fontFamily,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = textAlign,
                                        lineHeight = settings.lineSpacing.em,
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                )
                            }
                            // For non-text blocks, use the existing implementation
                            else -> {
                                NonSelectableBlock(
                                    block = block,
                                    settings = settings,
                                    textColor = textColor,
                                    fontFamily = fontFamily,
                                    textAlign = textAlign,
                                    onImageClick = onImageClick,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NonSelectableBlock(
    block: EpubContentBlock,
    settings: EpubReaderSettings,
    textColor: Color,
    fontFamily: FontFamily,
    textAlign: TextAlign,
    onImageClick: ((imageData: FullscreenImageData) -> Unit)? = null,
) {
    when (block) {
        is EpubContentBlock.Image -> {
            val context = LocalContext.current
            val imageModifier = Modifier
                .fillMaxWidth(0.9f)
                .pointerInput(block) {
                    detectTapGestures(
                        onTap = {
                            onImageClick?.invoke(
                                FullscreenImageData(
                                    src = block.src,
                                    data = block.data,
                                    alt = block.alt,
                                ),
                            )
                        },
                    )
                }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (block.src.startsWith("content://") || block.src.startsWith("file://")) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(block.src)
                            .crossfade(true)
                            .build(),
                        contentDescription = block.alt,
                        contentScale = ContentScale.FillWidth,
                        modifier = imageModifier,
                    )
                } else if (block.data != null) {
                    val imageBitmap = remember(block.data) {
                        block.data?.let { data ->
                            BitmapFactory.decodeByteArray(data, 0, data.size)?.asImageBitmap()
                        }
                    }

                    if (imageBitmap != null) {
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = block.alt,
                            contentScale = ContentScale.FillWidth,
                            modifier = imageModifier,
                        )
                    } else {
                        Text(
                            text = "[Image failed to load: ${block.alt ?: "No description"}]",
                            color = textColor.copy(alpha = 0.6f),
                            fontSize = settings.fontSize.sp,
                            fontFamily = fontFamily,
                            textAlign = TextAlign.Center,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                        )
                    }
                } else if (block.src.startsWith("data:image")) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(block.src)
                            .crossfade(true)
                            .build(),
                        contentDescription = block.alt,
                        contentScale = ContentScale.FillWidth,
                        modifier = imageModifier,
                    )
                } else {
                    Text(
                        text = "[Image: ${block.alt ?: block.src}]",
                        color = textColor.copy(alpha = 0.6f),
                        fontSize = settings.fontSize.sp,
                        fontFamily = fontFamily,
                        textAlign = TextAlign.Center,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    )
                }
            }
        }
        // Handle other non-text block types as needed
        else -> { /* Skip other block types */ }
    }
}

@Composable
private fun Row(
    cells: List<String>,
    textColor: Color,
    settings: EpubReaderSettings,
    fontFamily: FontFamily,
    isHeader: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        cells.forEach { cell ->
            Text(
                text = cell,
                color = textColor,
                fontSize = settings.fontSize.sp,
                fontFamily = fontFamily,
                fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Start,
                lineHeight = settings.lineSpacing.em,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
            )
        }
    }
}

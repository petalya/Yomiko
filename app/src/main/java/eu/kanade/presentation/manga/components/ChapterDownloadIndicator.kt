package eu.kanade.presentation.manga.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.model.Download
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.IconButtonTokens
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.secondaryItemAlpha

enum class ChapterDownloadAction {
    START,
    START_NOW,
    CANCEL,
    DELETE,
}

@Composable
fun ChapterDownloadIndicator(
    enabled: Boolean,
    downloadStateProvider: () -> Download.State,
    downloadProgressProvider: () -> Int,
    onClick: (ChapterDownloadAction) -> Unit,
    modifier: Modifier = Modifier,
    isMorphing: MutableState<Boolean>? = null,
) {
    // Track previous state to detect transition
    var prevState by remember { mutableStateOf(downloadStateProvider()) }
    val currentState = downloadStateProvider()
    val internalMorphing = remember { mutableStateOf(false) }
    val morphingState = isMorphing ?: internalMorphing // Use provided or fallback
    val progress = downloadProgressProvider()

    // Detect transition from DOWNLOADING to DOWNLOADED
    LaunchedEffect(currentState) {
        if (prevState == Download.State.DOWNLOADING && currentState == Download.State.DOWNLOADED) {
            morphingState.value = true
        }
        prevState = currentState
    }

    when (currentState) {
        Download.State.NOT_DOWNLOADED, Download.State.QUEUE, Download.State.DOWNLOADING -> {
            Crossfade(
                targetState = currentState != Download.State.NOT_DOWNLOADED,
                label = "download_indicator_crossfade",
            ) { showProgress ->
                if (!showProgress) {
                    NotDownloadedIndicator(
                        enabled = enabled,
                        modifier = modifier,
                        onClick = onClick,
                    )
                } else {
                    // Show circular progress bar
                    Box(
                        modifier = modifier
                            .size(IconButtonTokens.StateLayerSize)
                            .commonClickable(
                                enabled = enabled,
                                hapticFeedback = LocalHapticFeedback.current,
                                onLongClick = {
                                    if (currentState == Download.State.QUEUE || currentState == Download.State.DOWNLOADING) {
                                        onClick(ChapterDownloadAction.CANCEL)
                                    } else {
                                        onClick(ChapterDownloadAction.START_NOW)
                                    }
                                },
                                onClick = {
                                    if (currentState == Download.State.QUEUE || currentState == Download.State.DOWNLOADING) {
                                        onClick(ChapterDownloadAction.CANCEL)
                                    } else {
                                        onClick(ChapterDownloadAction.START)
                                    }
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.size(IndicatorSize),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            strokeWidth = IndicatorStrokeWidth,
                        )
                    }
                }
            }
        }
        Download.State.DOWNLOADED -> MorphingDownloadIndicator(
            enabled = enabled,
            modifier = modifier,
            progress = 100,
            isDownloaded = true,
            isMorphing = morphingState,
            onClick = onClick,
        )
        Download.State.ERROR -> ErrorIndicator(
            enabled = enabled,
            modifier = modifier,
            onClick = onClick,
        )
    }
}

@Composable
private fun NotDownloadedIndicator(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: (ChapterDownloadAction) -> Unit,
) {
    Box(
        modifier = modifier
            .size(IconButtonTokens.StateLayerSize)
            .commonClickable(
                enabled = enabled,
                hapticFeedback = LocalHapticFeedback.current,
                onLongClick = { onClick(ChapterDownloadAction.START_NOW) },
                onClick = { onClick(ChapterDownloadAction.START) },
            )
            .secondaryItemAlpha(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_download_chapter_24dp),
            contentDescription = stringResource(MR.strings.manga_download),
            modifier = Modifier.size(IndicatorSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MorphingDownloadIndicator(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    progress: Int,
    isDownloaded: Boolean,
    isMorphing: MutableState<Boolean>,
    onClick: (ChapterDownloadAction) -> Unit,
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    val strokeColor = MaterialTheme.colorScheme.onSurfaceVariant
    val density = LocalDensity.current
    val indicatorSizePx = with(density) { IndicatorSize.toPx() }
    val strokeWidthPx = with(density) { IndicatorStrokeWidth.toPx() }

    // Animation progress: 0f = arc, 1f = check
    val morphAnim = remember { Animatable(0f) }
    var wasDownloaded by remember { mutableStateOf(isDownloaded) }

    // Animate when morphing is triggered
    LaunchedEffect(isDownloaded, isMorphing.value) {
        if (isMorphing.value && isDownloaded) {
            morphAnim.animateTo(1f, animationSpec = tween(durationMillis = 600))
            isMorphing.value = false
        } else if (!isDownloaded && wasDownloaded) {
            // Only reset if we were previously downloaded and now not
            morphAnim.snapTo(0f)
        } else if (isDownloaded && !isMorphing.value) {
            morphAnim.snapTo(1f)
        }
        wasDownloaded = isDownloaded
    }

    Box(
        modifier = modifier
            .size(IconButtonTokens.StateLayerSize)
            .commonClickable(
                enabled = enabled,
                hapticFeedback = LocalHapticFeedback.current,
                onLongClick = {
                    if (!isDownloaded) {
                        onClick(ChapterDownloadAction.CANCEL)
                    } else {
                        isMenuExpanded = true
                    }
                },
                onClick = { isMenuExpanded = true },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = IndicatorModifier) {
            val arcSweep = if (isDownloaded) 360f else (progress / 100f) * 360f
            val morph = morphAnim.value
            // Draw arc (shrinks as morph progresses)
            if (morph < 1f) {
                drawArc(
                    color = strokeColor,
                    startAngle = -90f,
                    sweepAngle = arcSweep * (1 - morph),
                    useCenter = false,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
                )
            }
            // Draw check mark (grows as morph progresses)
            if (morph > 0f) {
                // Move checkmark a little to the left by subtracting from x coordinates
                val offsetX = indicatorSizePx * 0.08f
                val start = Offset(indicatorSizePx * 0.28f - offsetX, indicatorSizePx * 0.55f)
                val mid = Offset(indicatorSizePx * 0.45f - offsetX, indicatorSizePx * 0.72f)
                val end = Offset(indicatorSizePx * 0.75f - offsetX, indicatorSizePx * 0.32f)
                val checkPath = Path().apply {
                    moveTo(start.x, start.y)
                    lineTo(
                        lerp(start, mid, morph).x,
                        lerp(start, mid, morph).y,
                    )
                    if (morph > 0.5f) {
                        lineTo(
                            lerp(mid, end, (morph - 0.5f) * 2f).x,
                            lerp(mid, end, (morph - 0.5f) * 2f).y,
                        )
                    }
                }
                drawPath(
                    path = checkPath,
                    color = strokeColor,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
        DropdownMenu(expanded = isMenuExpanded, onDismissRequest = { isMenuExpanded = false }) {
            if (!isDownloaded) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(MR.strings.action_start_downloading_now)) },
                    onClick = {
                        onClick(ChapterDownloadAction.START_NOW)
                        isMenuExpanded = false
                    },
                )
                DropdownMenuItem(
                    text = { Text(text = stringResource(MR.strings.action_cancel)) },
                    onClick = {
                        onClick(ChapterDownloadAction.CANCEL)
                        isMenuExpanded = false
                    },
                )
            } else {
                DropdownMenuItem(
                    text = { Text(text = stringResource(MR.strings.action_delete)) },
                    onClick = {
                        onClick(ChapterDownloadAction.DELETE)
                        isMenuExpanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ErrorIndicator(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: (ChapterDownloadAction) -> Unit,
) {
    Box(
        modifier = modifier
            .size(IconButtonTokens.StateLayerSize)
            .commonClickable(
                enabled = enabled,
                hapticFeedback = LocalHapticFeedback.current,
                onLongClick = { onClick(ChapterDownloadAction.START) },
                onClick = { onClick(ChapterDownloadAction.START) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = stringResource(MR.strings.chapter_error),
            modifier = Modifier.size(IndicatorSize),
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

private fun Modifier.commonClickable(
    enabled: Boolean,
    hapticFeedback: HapticFeedback,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
) = this.combinedClickable(
    enabled = enabled,
    onLongClick = {
        onLongClick()
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    },
    onClick = onClick,
    role = Role.Button,
    interactionSource = null,
    indication = ripple(
        bounded = false,
        radius = IconButtonTokens.StateLayerSize / 2,
    ),
)

private val IndicatorSize = 26.dp
private val IndicatorPadding = 2.dp

// To match composable parameter name when used later
private val IndicatorStrokeWidth = IndicatorPadding

private val IndicatorModifier = Modifier
    .size(IndicatorSize)
    .padding(IndicatorPadding)
private val ArrowModifier = Modifier
    .size(IndicatorSize - 7.dp)

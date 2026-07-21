package com.beautifulquran.ui.reader

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.beautifulquran.playback.PlayerUiState
import com.beautifulquran.ui.theme.LocalQuranAccents

/**
 * Flat playback controls that sit on the same sheet of paper as the text —
 * no elevation, no card. The reading column fades out just above it.
 *
 * The reciter name gets its own centered line above the transport row, so the
 * row itself stays symmetric — two controls either side of play — and the
 * play button lands exactly on the page's center line.
 */
@Composable
fun PlayerBar(
    state: PlayerUiState,
    isThisSurahLoaded: Boolean,
    chromeAlpha: () -> Float,
    reciterName: String,
    onPlayPause: () -> Unit,
    onFastBackward: () -> Unit,
    onFastForward: () -> Unit,
    onRepeatClick: () -> Unit,
    onSpeed: () -> Unit,
    onReciterClick: () -> Unit,
    /** Gather mode — ordered verse selection for sharing (docs/SHARE.md). */
    gathering: Boolean = false,
    gatherCount: Int = 0,
    onGatherClick: () -> Unit = {},
) {
    val gold = LocalQuranAccents.current.gold
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            TextButton(
                onClick = onReciterClick,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                modifier = Modifier.graphicsLayer { alpha = chromeAlpha() },
            ) {
                Text(
                    text = reciterName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Mode signal: quiet gold line so gather is never "invisible mode".
            if (gathering) {
                Text(
                    text = if (gatherCount > 0) {
                        "Gathering · $gatherCount"
                    } else {
                        "Gathering — tap verses"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = gold.copy(alpha = 0.9f),
                    maxLines = 1,
                    modifier = Modifier
                        .graphicsLayer { alpha = chromeAlpha() }
                        .padding(bottom = 2.dp),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 4.dp),
            ) {
                val rangeActive = state.repeatRange != null
                val singleAyahRange = state.repeatRange?.let { it.first == it.last } == true
                GatherControl(
                    gathering = gathering,
                    gatherCount = gatherCount,
                    chromeAlpha = chromeAlpha,
                    gold = gold,
                    onClick = onGatherClick,
                )
                IconButton(
                    onClick = onRepeatClick,
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer { alpha = chromeAlpha() },
                ) {
                    Icon(
                        imageVector = if (state.repeatMode == Player.REPEAT_MODE_ONE || singleAyahRange) {
                            Icons.Rounded.RepeatOne
                        } else {
                            Icons.Rounded.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = if (state.repeatMode == Player.REPEAT_MODE_OFF && !rangeActive) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
                IconButton(
                    onClick = onFastBackward,
                    enabled = isThisSurahLoaded,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Rounded.FastRewind,
                        contentDescription = "Fast backward",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onPlayPause, modifier = Modifier.size(56.dp)) {
                    if (state.isBuffering && isThisSurahLoaded) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Icon(
                            imageVector = if (state.isPlaying && isThisSurahLoaded) {
                                Icons.Rounded.Pause
                            } else {
                                Icons.Rounded.PlayArrow
                            },
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                }
                IconButton(
                    onClick = onFastForward,
                    enabled = isThisSurahLoaded,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Rounded.FastForward,
                        contentDescription = "Fast forward",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = onSpeed,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer { alpha = chromeAlpha() },
                ) {
                    Text(
                        text = "${if (state.speed % 1f == 0f) state.speed.toInt() else state.speed}×",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state.speed == 1f) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }
        }
    }
}

/**
 * Gather control states (visual QA):
 * - idle: full transport ink (discoverable door, not 0.45 chrome)
 * - gathering empty: gold + soft pulse
 * - ready to send: gold + count badge
 */
@Composable
private fun GatherControl(
    gathering: Boolean,
    gatherCount: Int,
    chromeAlpha: () -> Float,
    gold: Color,
    onClick: () -> Unit,
) {
    val pulseAlpha by rememberInfiniteTransition(label = "gatherPulse").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "gatherPulseAlpha",
    )
    val iconTint = when {
        gathering && gatherCount > 0 -> gold
        gathering -> gold.copy(alpha = pulseAlpha)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer { alpha = chromeAlpha() },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.FormatListNumbered,
                contentDescription = when {
                    gathering && gatherCount > 0 -> "Send $gatherCount gathered verses"
                    gathering -> "Leave gather mode"
                    else -> "Gather verses to share"
                },
                tint = iconTint,
            )
            if (gatherCount > 0) {
                Text(
                    text = gatherCount.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        lineHeight = 10.sp,
                    ),
                    color = gold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 2.dp, bottom = 2.dp),
                )
            }
        }
    }
}

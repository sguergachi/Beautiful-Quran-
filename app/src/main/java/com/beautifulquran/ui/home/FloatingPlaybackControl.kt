package com.beautifulquran.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.beautifulquran.playback.PlayerUiState
import com.beautifulquran.ui.theme.quietClickable

/**
 * Bottom clearance shared with the reader's floating Back-to / return-to-ayah
 * controls ([com.beautifulquran.ui.reader.BackToOriginPill],
 * [com.beautifulquran.ui.theme.IslamicReturnToAyahButton]). Keeping the same
 * inset means the home float and those ink lines sit on one vertical rhythm
 * when the paper stack turns between cover and reader.
 */
val FloatingPlaybackBottomInset: Dp = 10.dp

/** Extra list padding so the last surah rows clear the floating transport. */
val FloatingPlaybackListClearance: Dp = 96.dp

/**
 * Paper-native floating transport for the chapter list. Same controls as the
 * reader's embedded [com.beautifulquran.ui.reader.PlayerBar], but it lives as
 * quiet ink over the cover sheet — no card, elevation, or border — and slides
 * up only while a verse is loaded (playing or paused mid-session). An opaque
 * paper [Surface] masks the list beneath, matching the embedded bar.
 */
@Composable
fun FloatingPlaybackControl(
    visible: Boolean,
    state: PlayerUiState,
    chapterLabel: String,
    ayahLabel: String,
    reciterName: String,
    onOpenNowPlaying: () -> Unit,
    onReciterClick: () -> Unit,
    onPlayPause: () -> Unit,
    onFastBackward: () -> Unit,
    onFastForward: () -> Unit,
    onRepeatClick: () -> Unit,
    onSpeed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(280)) + slideInVertically(
            animationSpec = tween(320),
            initialOffsetY = { it },
        ),
        exit = fadeOut(tween(220)) + slideOutVertically(
            animationSpec = tween(260),
            targetOffsetY = { it / 2 },
        ),
        modifier = modifier,
    ) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = FloatingPlaybackBottomInset),
            ) {
            TextButton(
                onClick = onReciterClick,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
            ) {
                Text(
                    text = reciterName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .quietClickable(onClick = onOpenNowPlaying)
                    .padding(horizontal = 28.dp, vertical = 2.dp)
                    .semantics {
                        contentDescription = "Open $chapterLabel · $ayahLabel"
                        role = Role.Button
                    },
            ) {
                Text(
                    text = chapterLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 200.dp),
                )
                Text(
                    text = "  ·  ",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
                )
                Text(
                    text = ayahLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    maxLines = 1,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 4.dp),
            ) {
                val rangeActive = state.repeatRange != null
                val singleAyahRange = state.repeatRange?.let { it.first == it.last } == true
                IconButton(onClick = onRepeatClick, modifier = Modifier.size(48.dp)) {
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
                IconButton(onClick = onFastBackward, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Rounded.FastRewind,
                        contentDescription = "Fast backward",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onPlayPause, modifier = Modifier.size(56.dp)) {
                    if (state.isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Icon(
                            imageVector = if (state.isPlaying) {
                                Icons.Rounded.Pause
                            } else {
                                Icons.Rounded.PlayArrow
                            },
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                }
                IconButton(onClick = onFastForward, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Rounded.FastForward,
                        contentDescription = "Fast forward",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = onSpeed,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(48.dp),
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
}

/** True while a verse is loaded in the session — playing or paused mid-ayah. */
internal fun shouldShowFloatingPlayback(nowPlayingPresent: Boolean): Boolean = nowPlayingPresent

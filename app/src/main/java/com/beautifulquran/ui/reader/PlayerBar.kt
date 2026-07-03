package com.beautifulquran.ui.reader

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
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
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.beautifulquran.playback.PlayerUiState

/**
 * Flat playback controls that sit on the same sheet of paper as the text —
 * no elevation, no card. The reading column fades out just above it.
 */
@Composable
fun PlayerBar(
    state: PlayerUiState,
    isThisSurahLoaded: Boolean,
    reciterName: String,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRepeat: () -> Unit,
    onSpeed: () -> Unit,
    onReciterClick: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            TextButton(onClick = onReciterClick, modifier = Modifier.weight(1f)) {
                Text(
                    text = reciterName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 2,
                )
            }
            IconButton(onClick = onPrevious, enabled = isThisSurahLoaded) {
                Icon(
                    Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous ayah",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onPlayPause, modifier = Modifier.size(56.dp)) {
                if (state.isBuffering && isThisSurahLoaded) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(
                        imageVector = if (state.isPlaying && isThisSurahLoaded) {
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
            IconButton(onClick = onNext, enabled = isThisSurahLoaded) {
                Icon(
                    Icons.Rounded.SkipNext,
                    contentDescription = "Next ayah",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRepeat) {
                Icon(
                    imageVector = if (state.repeatMode == Player.REPEAT_MODE_ONE) {
                        Icons.Rounded.RepeatOne
                    } else {
                        Icons.Rounded.Repeat
                    },
                    contentDescription = "Repeat mode",
                    tint = if (state.repeatMode == Player.REPEAT_MODE_OFF) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            TextButton(onClick = onSpeed) {
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

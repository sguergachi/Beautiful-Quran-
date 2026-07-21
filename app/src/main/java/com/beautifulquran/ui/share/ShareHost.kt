package com.beautifulquran.ui.share

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beautifulquran.data.ThemeMode
import com.beautifulquran.ui.theme.InkRevealOverlay
import com.beautifulquran.ui.theme.absorbPointerEvents
import com.beautifulquran.ui.theme.contrastingOverlayColorScheme

/**
 * Owns gather/send back handling and the Send ink-bleed so MainActivity does
 * not grow another cluster of overlay booleans.
 *
 * Always composed while the reader sheet exists so system-back can exit
 * gather mode even before the Send page opens.
 */
@Composable
fun ShareHost(
    viewModel: ShareViewModel,
    themeMode: ThemeMode,
    onOverlayRenderedChange: (Boolean) -> Unit = {},
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var sendRendered by remember { mutableStateOf(false) }

    BackHandler(enabled = ui.sendOpen) { viewModel.closeSend() }
    BackHandler(enabled = ui.gathering && !ui.sendOpen) { viewModel.exitGather() }

    LaunchedEffect(ui.pendingShareText) {
        val text = ui.pendingShareText ?: return@LaunchedEffect
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, null))
        viewModel.consumePendingShareText()
    }

    LaunchedEffect(sendRendered) {
        onOverlayRenderedChange(sendRendered)
    }

    val overlayColors = contrastingOverlayColorScheme(themeMode)
    InkRevealOverlay(
        visible = ui.sendOpen,
        backgroundColor = overlayColors.background,
        // Gather control sits in the player bar — bloom from the lower page.
        originX = 0.5f,
        originY = 0.92f,
        modifier = Modifier.zIndex(4.5f),
        onRenderedChange = { sendRendered = it },
    ) {
        MaterialTheme(colorScheme = overlayColors, typography = MaterialTheme.typography) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.matchParentSize().absorbPointerEvents())
                ShareComposeSheet(
                    verseLines = ui.verseLines,
                    preparingText = ui.preparingText,
                    error = ui.error,
                    onBack = viewModel::closeSend,
                    onShareText = { viewModel.shareAsText() },
                    onRemove = viewModel::remove,
                )
            }
        }
    }
}

package com.beautifulquran.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beautifulquran.share.AyahRef
import com.beautifulquran.ui.theme.HafsFontFamily
import com.beautifulquran.ui.theme.LocalQuranAccents
import com.beautifulquran.ui.theme.quietClickable
import com.beautifulquran.ui.theme.verticalFadingEdges

/**
 * Send page for a gathered selection — text share only in PR1.
 * Hosted inside [ShareHost]'s ink bleed; no cards, dialogs, or elevation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareComposeSheet(
    verseLines: List<ShareVerseLine>,
    preparingText: Boolean,
    error: String?,
    onBack: () -> Unit,
    onShareText: () -> Unit,
    onRemove: (AyahRef) -> Unit,
) {
    val gold = LocalQuranAccents.current.gold
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Send",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Close send page",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 28.dp,
                    end = 28.dp,
                    top = 8.dp,
                    bottom = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .verticalFadingEdges(
                        color = MaterialTheme.colorScheme.background,
                        top = 16.dp,
                        bottom = 24.dp,
                    ),
            ) {
                itemsIndexed(
                    items = verseLines,
                    key = { _, line -> "${line.ref.surahId}:${line.ref.ayah}" },
                ) { index, line ->
                    GatheredVerseLine(
                        ordinal = index + 1,
                        line = line,
                        onRemove = { onRemove(line.ref) },
                    )
                }
            }

            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 6.dp),
                )
            }

            // Single output for PR1 — quiet text action, not a Material button bar.
            Text(
                text = if (preparingText) "Preparing…" else "Share as text",
                style = MaterialTheme.typography.titleMedium,
                color = if (preparingText || verseLines.isEmpty()) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                } else {
                    gold
                },
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .padding(horizontal = 28.dp, vertical = 20.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Share as text"
                    }
                    .then(
                        if (preparingText || verseLines.isEmpty()) {
                            Modifier
                        } else {
                            Modifier.quietClickable(onClick = onShareText)
                        },
                    ),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun GatheredVerseLine(
    ordinal: Int,
    line: ShareVerseLine,
    onRemove: () -> Unit,
) {
    val gold = LocalQuranAccents.current.gold
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        Text(
            text = ordinal.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = gold,
            modifier = Modifier
                .padding(end = 14.dp, top = 2.dp)
                .width(22.dp),
            textAlign = TextAlign.End,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = line.arabic,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = HafsFontFamily,
                    fontSize = 20.sp,
                    lineHeight = 30.sp,
                    textDirection = TextDirection.Rtl,
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = line.reference,
                style = MaterialTheme.typography.labelMedium,
                color = gold.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Gold × — stronger than a faint "Remove" label (visual QA).
        Text(
            text = "×",
            style = MaterialTheme.typography.headlineSmall,
            color = gold.copy(alpha = 0.88f),
            modifier = Modifier
                .padding(start = 10.dp, top = 0.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "Remove ${line.reference}"
                }
                .quietClickable(onClick = onRemove),
        )
    }
}

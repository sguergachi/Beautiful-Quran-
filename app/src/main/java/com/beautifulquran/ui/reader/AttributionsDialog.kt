package com.beautifulquran.ui.reader

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

private val ATTRIBUTIONS = """
Quran text (Uthmani script) and Saheeh International translation via the quran-json project (github.com/risan/quran-json), from Tanzil and Al Quran Cloud.

Word-by-word translation and transliteration from the Quran.com dataset, via the holy-quran-word-by-word data package.

Word-level audio timing data © the quran-align project contributors (github.com/cpfair/quran-align), licensed CC-BY 4.0.

Recitation audio streamed from everyayah.com. All rights to the recitations belong to the respective reciters.

Arabic typeface: KFGQPC HAFS Uthmanic Script © King Fahd Glorious Quran Printing Complex, Madinah.

This app is free, ad-free, and collects no data.
""".trimIndent()

@Composable
fun AttributionsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = { Text("About & attributions") },
        text = {
            Text(
                text = ATTRIBUTIONS,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
    )
}

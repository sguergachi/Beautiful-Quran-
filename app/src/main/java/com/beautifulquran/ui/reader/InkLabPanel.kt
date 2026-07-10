package com.beautifulquran.ui.reader

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.beautifulquran.ui.theme.quietClickable
import kotlin.math.roundToInt

/**
 * Developer-mode overlay for tuning the highlight feel live: sliders bound
 * straight to [InkEngine.tuning], so every change is visible on the page
 * behind it while a recitation plays. Session-only — nothing here persists;
 * "Log values" dumps the current [InkEngine.Tuning] to Logcat (tag `InkLab`)
 * so tuned numbers can be transcribed into the defaults in InkEngine.kt.
 *
 * Enabled from Settings → Developer → "Ink Lab overlay" (developer mode
 * itself unlocks by tapping the Settings logo). See docs/INK_ENGINE.md.
 */
@Composable
fun InkLabPanel(modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.End,
        modifier = modifier.widthIn(max = 340.dp),
    ) {
        // Collapsed the panel is just its name — a quiet ink label that
        // expands into the sliders, so the page stays readable while tuning.
        Text(
            text = if (expanded) "Ink Lab ▾" else "Ink Lab ▸",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f))
                .quietClickable { expanded = !expanded }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
        if (!expanded) return@Column
        Spacer(Modifier.height(6.dp))
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f))
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .heightIn(max = 380.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            val t = InkEngine.tuning
            TuningSlider("Upcoming ink", t.upcomingAlpha, 0.05f..0.6f) {
                InkEngine.tuning = t.copy(upcomingAlpha = it)
            }
            TuningSlider("Ink fade ms", t.inkFadeMs.toFloat(), 0f..1200f, integer = true) {
                InkEngine.tuning = t.copy(inkFadeMs = it.roundToInt())
            }
            TuningSlider("Mark fade ms", t.ayahMarkFadeMs.toFloat(), 0f..1200f, integer = true) {
                InkEngine.tuning = t.copy(ayahMarkFadeMs = it.roundToInt())
            }
            TuningSlider("Recess ms", t.recessMs.toFloat(), 0f..600f, integer = true) {
                InkEngine.tuning = t.copy(recessMs = it.roundToInt())
            }
            TuningSlider("Min sweep ms", t.minSweepMs.toFloat(), 40f..600f, integer = true) {
                InkEngine.tuning = t.copy(minSweepMs = it.roundToInt())
            }
            TuningSlider("Max sweep ms", t.maxSweepMs.toFloat(), 1000f..12000f, integer = true) {
                InkEngine.tuning = t.copy(maxSweepMs = it.roundToInt())
            }
            TuningSlider("Repeat sweep ms", t.repeatSweepMs.toFloat(), 100f..1500f, integer = true) {
                InkEngine.tuning = t.copy(repeatSweepMs = it.roundToInt())
            }
            TuningSlider("Repeat fade ms", t.repeatFadeOutMs.toFloat(), 100f..2400f, integer = true) {
                InkEngine.tuning = t.copy(repeatFadeOutMs = it.roundToInt())
            }
            TuningSlider("Wash feather", t.washFeather, 0.2f..3f) {
                InkEngine.tuning = t.copy(washFeather = it)
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(
                    text = "Reset",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .quietClickable { InkEngine.tuning = InkEngine.Tuning() }
                        .padding(vertical = 6.dp),
                )
                Text(
                    text = "Log values",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .quietClickable { Log.d("InkLab", InkEngine.tuning.toString()) }
                        .padding(vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun TuningSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    integer: Boolean = false,
    onChange: (Float) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(112.dp),
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.primaryContainer,
            ),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (integer) value.roundToInt().toString() else "%.2f".format(value),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp),
        )
    }
}

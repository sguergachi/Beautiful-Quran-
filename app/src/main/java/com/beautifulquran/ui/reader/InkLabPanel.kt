package com.beautifulquran.ui.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.beautifulquran.ui.theme.DisclosureChevron
import com.beautifulquran.ui.theme.quietClickable
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Developer-mode overlay for tuning the highlight feel live: sliders bound
 * straight to [InkEngine.tuning], so every change is visible on the page
 * behind it while a recitation plays. Session-only — nothing here persists;
 * "Copy values" puts a paste-ready [InkEngine.Tuning] constructor on the
 * clipboard (and Logcat tag `InkLab`) so tuned numbers can land in the
 * defaults in InkEngine.kt.
 *
 * Enabled from Settings → Developer → "Ink Lab overlay" (developer mode
 * itself unlocks by tapping the Settings logo). See docs/INK_ENGINE.md.
 */
@Composable
fun InkLabPanel(modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    var copyNote by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.End,
        modifier = modifier.widthIn(max = 340.dp),
    ) {
        // Collapsed the panel is just its name — a quiet ink label that
        // expands into the sliders, so the page stays readable while tuning.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f))
                .quietClickable { expanded = !expanded }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                text = "Ink Lab",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(4.dp))
            DisclosureChevron(expanded = expanded)
        }
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
            TuningSlider("Recess ms", t.recessMs.toFloat(), 0f..1400f, integer = true) {
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
            TuningSlider("Repeat ink", t.repeatInkAlpha, 0.2f..1f) {
                InkEngine.tuning = t.copy(repeatInkAlpha = it)
            }
            TuningSlider("Glitter time ms", t.glintFadeMs.toFloat(), 100f..2400f, integer = true) {
                InkEngine.tuning = t.copy(glintFadeMs = it.roundToInt())
            }
            TuningSlider("Glint tint", t.glintTintAlpha, 0f..1f) {
                InkEngine.tuning = t.copy(glintTintAlpha = it)
            }
            TuningSlider("Halo strength", t.glintGlowAlpha, 0f..1f) {
                InkEngine.tuning = t.copy(glintGlowAlpha = it)
            }
            TuningSlider("Halo blur", t.glintGlowRadius, 0f..10f) {
                InkEngine.tuning = t.copy(glintGlowRadius = it)
            }
            TuningSlider("Wash feather", t.washFeather, 0.2f..3f) {
                InkEngine.tuning = t.copy(washFeather = it)
            }
            // Letter-level tajweed pacing of the active sweep — experimental,
            // auditioned here before it ships on (docs/TAJWEED_PACING.md).
            TuningToggle("Tajweed pacing", t.tajweedPacing) {
                Log.d("InkLabDbg", "toggle tap: tajweedPacing $it (was ${t.tajweedPacing})")
                InkEngine.tuning = t.copy(tajweedPacing = it)
            }
            TuningSlider("Paced feather", t.pacedFeatherPerLetter, 0.5f..6f) {
                InkEngine.tuning = t.copy(pacedFeatherPerLetter = it)
            }
            TuningSlider("Pacing contrast", t.pacingContrast, 0f..1f) {
                InkEngine.tuning = t.copy(pacingContrast = it)
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(
                    text = "Reset",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .quietClickable {
                            InkEngine.tuning = InkEngine.Tuning()
                            copyNote = null
                        }
                        .padding(vertical = 6.dp),
                )
                Text(
                    text = "Copy values",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .quietClickable {
                            val text = formatTuningCopy(InkEngine.tuning)
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText("Ink Lab tuning", text))
                            Log.d("InkLab", text)
                            copyNote = "Copied"
                        }
                        .padding(vertical = 6.dp),
                )
            }
            copyNote?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/**
 * Paste-ready Kotlin for the current lab values — drop into
 * [InkEngine.Tuning] defaults or a `InkEngine.tuning = …` call.
 * Sweep-easing control points are included even though the panel has no
 * sliders for them, so a full snapshot never silently drops fields.
 */
internal fun formatTuningCopy(t: InkEngine.Tuning): String {
    fun f(v: Float): String {
        val s = "%.4f".format(Locale.US, v).trimEnd('0').trimEnd('.')
        return "${s}f"
    }
    return buildString {
        appendLine("// InkEngine.Tuning — paste into defaults or a tuning assignment")
        appendLine("InkEngine.Tuning(")
        appendLine("    upcomingAlpha = ${f(t.upcomingAlpha)},")
        appendLine("    inkFadeMs = ${t.inkFadeMs},")
        appendLine("    ayahMarkFadeMs = ${t.ayahMarkFadeMs},")
        appendLine("    recessMs = ${t.recessMs},")
        appendLine("    minSweepMs = ${t.minSweepMs},")
        appendLine("    maxSweepMs = ${t.maxSweepMs},")
        appendLine("    repeatSweepMs = ${t.repeatSweepMs},")
        appendLine("    repeatFadeOutMs = ${t.repeatFadeOutMs},")
        appendLine("    glintFadeMs = ${t.glintFadeMs},")
        appendLine("    glintTintAlpha = ${f(t.glintTintAlpha)},")
        appendLine("    glintGlowAlpha = ${f(t.glintGlowAlpha)},")
        appendLine("    glintGlowRadius = ${f(t.glintGlowRadius)},")
        appendLine("    washFeather = ${f(t.washFeather)},")
        appendLine("    sweepEaseX1 = ${f(t.sweepEaseX1)},")
        appendLine("    sweepEaseY1 = ${f(t.sweepEaseY1)},")
        appendLine("    sweepEaseX2 = ${f(t.sweepEaseX2)},")
        appendLine("    sweepEaseY2 = ${f(t.sweepEaseY2)},")
        appendLine("    tajweedPacing = ${t.tajweedPacing},")
        appendLine("    pacedFeatherPerLetter = ${f(t.pacedFeatherPerLetter)},")
        appendLine("    pacingContrast = ${f(t.pacingContrast)},")
        append(")")
    }
}

/** On/off knob in the panel's quiet-ink idiom — a word, not a switch. The
 * whole row is the tap target: the value word alone is too small to hit
 * reliably, and with no ripple a missed tap gives no feedback at all. */
@Composable
private fun TuningToggle(
    label: String,
    value: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .quietClickable { onChange(!value) }
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(112.dp),
        )
        Text(
            text = if (value) "on" else "off",
            style = MaterialTheme.typography.labelLarge,
            color = if (value) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 2.dp),
        )
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

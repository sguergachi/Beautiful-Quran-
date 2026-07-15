package com.beautifulquran.ui.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.beautifulquran.assistant.AssistantAction
import com.beautifulquran.assistant.AssistantIntents
import com.beautifulquran.assistant.VoiceRoutines
import java.util.Locale

/**
 * In-app speech → [AssistantAction]. Works without Google App Actions review;
 * the user speaks short phrases while the app is open.
 */
data class VoiceListenState(
    val note: String?,
    val start: () -> Unit,
    /** Lets a host surface dissolve the note after showing it for a while. */
    val clearNote: () -> Unit = {},
)

@Composable
fun rememberVoiceListen(
    onAction: (AssistantAction) -> Unit,
): VoiceListenState {
    val context = LocalContext.current
    var note by remember { mutableStateOf<String?>(null) }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            .orEmpty()
        val action = spoken.firstNotNullOfOrNull { AssistantIntents.parseSpokenCommand(it) }
        if (action != null) {
            note = null
            onAction(action)
        } else {
            val heard = spoken.firstOrNull().orEmpty()
            note = if (heard.isBlank()) {
                "Didn't catch that — try again"
            } else {
                "Heard “$heard” — try open chapter 2 or bookmark this"
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            launchSpeech(context.packageManager, speechLauncher::launch) { note = it }
        } else {
            note = "Microphone permission is needed to listen"
        }
    }

    val start = remember(micPermissionLauncher, speechLauncher) {
        {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                launchSpeech(context.packageManager, speechLauncher::launch) { note = it }
            }
        }
    }

    return VoiceListenState(note = note, start = start, clearNote = { note = null })
}

private fun launchSpeech(
    packageManager: PackageManager,
    launch: (Intent) -> Unit,
    onError: (String) -> Unit,
) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
        )
        // EXTRA_LANGUAGE expects an IETF tag string; a Serializable Locale is
        // ignored (or trips up) recognizers.
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_PROMPT, VoiceRoutines.LISTEN_HINT)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
    }
    if (intent.resolveActivity(packageManager) == null) {
        onError("No speech recognition app is installed on this device")
        return
    }
    runCatching { launch(intent) }
        .onFailure { onError("Couldn't start the microphone") }
}

package com.defang.launcher.service.nfc

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlin.math.roundToInt
import com.defang.launcher.R
import com.defang.launcher.data.local.datastore.PreferencesDataStore
import com.defang.launcher.ui.theme.DefangTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The activity that actually reads an NFC tag — reader mode requires a resumed
 * Activity, which the gate's overlay (living in the AccessibilityService) is not.
 *
 * Two modes ([NfcUnlock.MODE_REGISTER] / [NfcUnlock.MODE_UNLOCK]):
 *  - REGISTER (from settings): capture the tapped tag's UID, store it, finish.
 *  - UNLOCK (from the gate): compare the tapped tag to the stored UID; on a match
 *    broadcast [NfcUnlock.ACTION_NFC_UNLOCKED] so the service opens the app.
 *    Backing out broadcasts [NfcUnlock.ACTION_NFC_GOBACK] — the same "Go back"
 *    the slide gate offers, i.e. the app is left unopened. There is deliberately
 *    no slide fallback here: NFC is available (the service only launches this
 *    when it is), so the only ways out are the right tag or backing out.
 */
@AndroidEntryPoint
class NfcUnlockActivity : ComponentActivity(), NfcAdapter.ReaderCallback {

    @Inject lateinit var prefs: PreferencesDataStore

    private var adapter: NfcAdapter? = null

    private val mode: String by lazy {
        intent.getStringExtra(NfcUnlock.EXTRA_MODE) ?: NfcUnlock.MODE_UNLOCK
    }
    private val appLabel: String by lazy {
        intent.getStringExtra(NfcUnlock.EXTRA_APP_LABEL).orEmpty()
    }

    private sealed interface Status {
        data object Waiting : Status
        data object WrongTag : Status
        data object RandomTag : Status
        data object NfcOff : Status
    }

    private var status by mutableStateOf<Status>(Status.Waiting)
    // Guards against double-handling a tag while the coroutine that finishes the
    // activity is still in flight.
    private var handled = false
    // Revealed after a grace period with no successful scan: a slide-to-open
    // backup so an app whose NFC the platform won't hand us (Snapchat hijacks the
    // reader on some devices) can still be opened rather than being locked out.
    private var showSlide by mutableStateOf(false)
    // Tracks a genuine focus loss, so we only re-arm the reader when focus truly
    // returns — arming twice in quick succession while a card is already in the
    // field makes the platform drop reader mode ("active polling is forced to
    // disable now" → "Binder was never registered").
    private var lostFocus = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = NfcAdapter.getDefaultAdapter(this)

        // Unlock only: after a grace period without a successful scan, reveal the
        // slide-to-open backup. Registration has no such fallback.
        if (mode == NfcUnlock.MODE_UNLOCK) {
            lifecycleScope.launch {
                kotlinx.coroutines.delay(NFC_FALLBACK_MS)
                if (!handled) showSlide = true
            }
        }

        setContent {
            DefangTheme {
                BackHandler { goBack() }
                UnlockScreen(
                    isRegister = mode == NfcUnlock.MODE_REGISTER,
                    appLabel = appLabel,
                    nfcOff = status is Status.NfcOff,
                    wrongTag = status is Status.WrongTag,
                    randomTag = status is Status.RandomTag,
                    showSlide = showSlide,
                    onSlideComplete = { unlockViaFallback() },
                    onBack = { goBack() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        armReader()
    }

    /**
     * Re-arm on regained window focus too. Reader mode is bound to the resumed,
     * focused activity; some apps (Snapchat) briefly steal focus without a full
     * pause/resume, and the platform revokes our reader mode when that happens.
     * Re-enabling here recovers as soon as focus comes back.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) {
            lostFocus = true
        } else if (lostFocus) {
            lostFocus = false
            armReader()   // only re-arm after a real focus loss, never redundantly
        }
    }

    private fun armReader() {
        if (handled) return
        val a = adapter
        if (a == null || !a.isEnabled) {
            status = Status.NfcOff
            return
        }
        status = if (status is Status.NfcOff) Status.Waiting else status
        a.enableReaderMode(
            this,
            this,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null,
        )
    }

    override fun onPause() {
        super.onPause()
        adapter?.disableReaderMode(this)
    }

    /**
     * The activity is noHistory, so being stopped means it's finishing — the user
     * left (Home, app switch, screen off) without scanning. Treat that as "Go
     * back" so the gate resolves instead of wedging as permanently pending. The
     * success and explicit-back paths set [handled] first, so they skip this.
     */
    override fun onStop() {
        super.onStop()
        if (!handled) {
            handled = true
            if (mode == NfcUnlock.MODE_UNLOCK) {
                sendBroadcast(Intent(NfcUnlock.ACTION_NFC_GOBACK).setPackage(packageName))
            }
        }
    }

    /** Reader-mode callback — runs on a binder thread, so hop to the main scope. */
    override fun onTagDiscovered(tag: Tag) {
        val uid = NfcUnlock.uidHex(tag.id)
        // Reject randomized-UID cards up front: they present a different UID every
        // tap, so registering one guarantees a "wrong tag" on the very next scan.
        // Keep reading so the user can try a proper tag without leaving.
        if (mode == NfcUnlock.MODE_REGISTER && NfcUnlock.isRandomUid(tag.id)) {
            runOnUiThread { status = Status.RandomTag }
            return
        }
        lifecycleScope.launch {
            if (handled) return@launch
            when (mode) {
                NfcUnlock.MODE_REGISTER -> {
                    handled = true
                    prefs.setNfcTagUid(uid)
                    setResult(RESULT_OK)
                    finish()
                }
                else -> {
                    val stored = prefs.nfcTagUid.first()
                    if (stored != null && stored.equals(uid, ignoreCase = true)) {
                        handled = true
                        sendBroadcast(
                            Intent(NfcUnlock.ACTION_NFC_UNLOCKED).setPackage(packageName)
                        )
                        finish()
                    } else {
                        // Wrong tag — keep reading so the user can try the right one.
                        status = Status.WrongTag
                    }
                }
            }
        }
    }

    private fun goBack() {
        if (handled) return
        handled = true
        if (mode == NfcUnlock.MODE_UNLOCK) {
            sendBroadcast(Intent(NfcUnlock.ACTION_NFC_GOBACK).setPackage(packageName))
        }
        finish()
    }

    /** Slide backup completed — unlock exactly as a successful tag scan would. */
    private fun unlockViaFallback() {
        if (handled) return
        handled = true
        sendBroadcast(Intent(NfcUnlock.ACTION_NFC_UNLOCKED).setPackage(packageName))
        finish()
    }

    private companion object {
        /** Grace period before the slide-to-open backup appears. */
        const val NFC_FALLBACK_MS = 10_000L
    }
}

@Composable
private fun UnlockScreen(
    isRegister: Boolean,
    appLabel: String,
    nfcOff: Boolean,
    wrongTag: Boolean,
    randomTag: Boolean,
    showSlide: Boolean,
    onSlideComplete: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val isError = wrongTag || randomTag

            val title = when {
                nfcOff -> stringResource(R.string.nfc_off_title)
                randomTag -> stringResource(R.string.nfc_random_title)
                isRegister -> stringResource(R.string.nfc_register_title)
                else -> stringResource(R.string.nfc_unlock_title, appLabel)
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                text = when {
                    nfcOff -> stringResource(R.string.nfc_off_body)
                    randomTag -> stringResource(R.string.nfc_random_body)
                    wrongTag -> stringResource(R.string.nfc_wrong_tag)
                    isRegister -> stringResource(R.string.nfc_register_body)
                    else -> stringResource(R.string.nfc_unlock_body)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (isError)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )

            if (showSlide && !isRegister) {
                Text(
                    text = stringResource(R.string.nfc_slide_fallback),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 40.dp, bottom = 8.dp),
                )
                SlideToOpen(onComplete = onSlideComplete)
            }

            TextButton(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
                Text(
                    text = if (isRegister)
                        stringResource(R.string.action_cancel)
                    else
                        stringResource(R.string.gate_go_back),
                )
            }
        }
    }
}

/** Minimal slide-to-open: drag the thumb to the far end to confirm. */
@Composable
private fun SlideToOpen(onComplete: () -> Unit) {
    val trackHeight = 64.dp
    val thumb = 56.dp
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var done by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val thumbPx = with(density) { thumb.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(trackHeight)
            .clip(RoundedCornerShape(trackHeight / 2))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .onSizeChanged { trackWidthPx = it.width.toFloat() },
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = stringResource(R.string.nfc_slide_label),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .padding(4.dp)
                .size(thumb - 8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = { if (!done) offsetX = 0f },
                        onHorizontalDrag = { change, drag ->
                            change.consume()
                            val max = (trackWidthPx - thumbPx).coerceAtLeast(0f)
                            offsetX = (offsetX + drag).coerceIn(0f, max)
                            if (!done && offsetX >= max - 2f) {
                                done = true
                                onComplete()
                            }
                        },
                    )
                },
        )
    }
}

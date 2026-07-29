package com.defang.launcher.service.qr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.defang.launcher.R
import com.defang.launcher.data.local.datastore.PreferencesDataStore
import com.defang.launcher.ui.theme.DefangTheme
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * The activity that actually scans a QR/barcode — a live camera preview requires
 * a resumed Activity, which the gate's overlay (living in the AccessibilityService)
 * is not.
 *
 * Two modes ([QrUnlock.MODE_REGISTER] / [QrUnlock.MODE_UNLOCK]):
 *  - REGISTER (from settings): capture the scanned value, store it, finish.
 *  - UNLOCK (from the gate): compare a scan to the stored value; on a match
 *    broadcast [QrUnlock.ACTION_QR_UNLOCKED] so the service opens the app.
 *    Backing out broadcasts [QrUnlock.ACTION_QR_GOBACK] — the same "Go back" the
 *    slide gate offers.
 *
 * Unlike NFC, the camera can fail in ways that must never lock the user out
 * (permission denied, camera busy, or a code that simply won't decode). So in
 * UNLOCK mode a slide-to-open backup is revealed after [QR_FALLBACK_MS] — or
 * immediately if the camera is unavailable — and completing it opens the app
 * exactly as a successful scan would.
 */
@AndroidEntryPoint
class QrUnlockActivity : ComponentActivity() {

    @Inject lateinit var prefs: PreferencesDataStore

    private val mode: String by lazy {
        intent.getStringExtra(QrUnlock.EXTRA_MODE) ?: QrUnlock.MODE_UNLOCK
    }
    private val appLabel: String by lazy {
        intent.getStringExtra(QrUnlock.EXTRA_APP_LABEL).orEmpty()
    }

    private sealed interface Status {
        data object Scanning : Status
        /** UNLOCK: a code decoded but did not match the registered one. */
        data object WrongCode : Status
        /** REGISTER: the scanned value was empty or absurdly long. */
        data object BadCode : Status
        /** No camera permission / no camera hardware. */
        data object NoCamera : Status
    }

    private var status by mutableStateOf<Status>(Status.Scanning)
    private var hasCameraPermission by mutableStateOf(false)
    private var showSlide by mutableStateOf(false)
    private var torchOn by mutableStateOf(false)

    // Guards against double-handling while the coroutine that finishes the
    // activity is still in flight.
    private var handled = false
    // The registered code, loaded once up front so the decode callback can
    // compare synchronously without hitting DataStore every frame.
    private var storedValue: String? = null
    // The live scanner view, kept so the torch toggle can reach it.
    private var barcodeView: DecoratedBarcodeView? = null

    private val requestCamera =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasCameraPermission = granted
            if (granted) {
                status = Status.Scanning
            } else {
                // Denied: never lock the user out. On unlock, drop straight to the
                // slide backup; on register, explain (there is nothing to fall back to).
                status = Status.NoCamera
                if (mode == QrUnlock.MODE_UNLOCK) showSlide = true
            }
        }

    private val barcodeCallback = object : BarcodeCallback {
        override fun barcodeResult(result: BarcodeResult) = onDecoded(result.text)
        override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (mode == QrUnlock.MODE_UNLOCK) {
            lifecycleScope.launch { storedValue = prefs.qrValue.first() }
            // After a grace period with no successful scan, reveal the slide backup.
            lifecycleScope.launch {
                delay(QR_FALLBACK_MS)
                if (!handled) showSlide = true
            }
        }

        // No camera hardware at all: skip straight to the fallback (defensive —
        // settings and the service already gate on hasCamera()).
        if (!QrUnlock.hasCamera(this)) {
            status = Status.NoCamera
            if (mode == QrUnlock.MODE_UNLOCK) showSlide = true
        } else {
            ensureCameraPermission()
        }

        setContent {
            DefangTheme {
                BackHandler { goBack() }
                ScanScreen(
                    isRegister = mode == QrUnlock.MODE_REGISTER,
                    appLabel = appLabel,
                    hasCameraPermission = hasCameraPermission,
                    wrongCode = status is Status.WrongCode,
                    badCode = status is Status.BadCode,
                    noCamera = status is Status.NoCamera,
                    torchOn = torchOn,
                    showSlide = showSlide,
                    barcodeCallback = barcodeCallback,
                    onViewCreated = { barcodeView = it },
                    onToggleTorch = ::toggleTorch,
                    onSlideComplete = { unlockViaFallback() },
                    onBack = { goBack() },
                )
            }
        }
    }

    private fun ensureCameraPermission() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            hasCameraPermission = true
        } else {
            requestCamera.launch(Manifest.permission.CAMERA)
        }
    }

    private fun toggleTorch() {
        torchOn = !torchOn
        if (torchOn) barcodeView?.setTorchOn() else barcodeView?.setTorchOff()
    }

    /** Handle one decoded value from the continuous scanner. */
    private fun onDecoded(text: String?) {
        if (handled || text == null) return
        when (mode) {
            QrUnlock.MODE_REGISTER -> {
                val cleaned = text.trim()
                if (cleaned.isEmpty() || cleaned.length > QrUnlock.MAX_VALUE_LENGTH) {
                    status = Status.BadCode  // keep scanning; let them try a better code
                    return
                }
                handled = true
                barcodeView?.pause()
                lifecycleScope.launch {
                    prefs.setQrValue(cleaned)
                    setResult(RESULT_OK)
                    finish()
                }
            }
            else -> {
                val stored = storedValue
                if (stored != null && stored == text) {
                    handled = true
                    barcodeView?.pause()
                    sendBroadcast(Intent(QrUnlock.ACTION_QR_UNLOCKED).setPackage(packageName))
                    finish()
                } else {
                    // Wrong code — keep scanning so the user can try the right one.
                    status = Status.WrongCode
                }
            }
        }
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
            if (mode == QrUnlock.MODE_UNLOCK) {
                sendBroadcast(Intent(QrUnlock.ACTION_QR_GOBACK).setPackage(packageName))
            }
        }
    }

    private fun goBack() {
        if (handled) return
        handled = true
        if (mode == QrUnlock.MODE_UNLOCK) {
            sendBroadcast(Intent(QrUnlock.ACTION_QR_GOBACK).setPackage(packageName))
        }
        finish()
    }

    /** Slide backup completed — unlock exactly as a successful scan would. */
    private fun unlockViaFallback() {
        if (handled) return
        handled = true
        sendBroadcast(Intent(QrUnlock.ACTION_QR_UNLOCKED).setPackage(packageName))
        finish()
    }

    private companion object {
        /**
         * Grace period before the slide-to-open backup appears. The camera is live
         * and actively trying, so this is really "give up decoding and offer the
         * slide" — long enough that the slide feels like a rescue, not a shortcut.
         */
        const val QR_FALLBACK_MS = 10_000L
    }
}

@Composable
private fun ScanScreen(
    isRegister: Boolean,
    appLabel: String,
    hasCameraPermission: Boolean,
    wrongCode: Boolean,
    badCode: Boolean,
    noCamera: Boolean,
    torchOn: Boolean,
    showSlide: Boolean,
    barcodeCallback: BarcodeCallback,
    onViewCreated: (DecoratedBarcodeView) -> Unit,
    onToggleTorch: () -> Unit,
    onSlideComplete: () -> Unit,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (hasCameraPermission) {
            CameraPreview(barcodeCallback = barcodeCallback, onViewCreated = onViewCreated)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            val isError = wrongCode || badCode || noCamera

            val title = when {
                noCamera -> stringResource(R.string.qr_no_camera_title)
                isRegister -> stringResource(R.string.qr_register_title)
                else -> stringResource(R.string.qr_unlock_title, appLabel)
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Text(
                text = when {
                    noCamera -> stringResource(R.string.qr_no_camera_body)
                    badCode -> stringResource(R.string.qr_bad_code)
                    wrongCode -> stringResource(R.string.qr_wrong_code)
                    isRegister -> stringResource(R.string.qr_register_body)
                    else -> stringResource(R.string.qr_scan_hint)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (isError)
                    MaterialTheme.colorScheme.error
                else
                    Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )

            if (hasCameraPermission) {
                TextButton(onClick = onToggleTorch, modifier = Modifier.padding(top = 16.dp)) {
                    Text(
                        text = if (torchOn)
                            stringResource(R.string.qr_torch_off)
                        else
                            stringResource(R.string.qr_torch_on),
                        color = Color.White,
                    )
                }
            }

            if (showSlide && !isRegister) {
                Text(
                    text = stringResource(R.string.qr_slide_fallback),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 32.dp, bottom = 8.dp),
                )
                SlideToOpen(onComplete = onSlideComplete)
            }

            TextButton(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
                Text(
                    text = if (isRegister)
                        stringResource(R.string.action_cancel)
                    else
                        stringResource(R.string.gate_go_back),
                    color = Color.White,
                )
            }
        }
    }
}

/**
 * Hosts ZXing's [DecoratedBarcodeView] (a classic Android View) inside Compose,
 * driving its camera resume/pause off the current lifecycle so the preview
 * releases the camera when the activity pauses and re-acquires it on resume —
 * including when the camera permission is granted mid-flow.
 */
@Composable
private fun CameraPreview(
    barcodeCallback: BarcodeCallback,
    onViewCreated: (DecoratedBarcodeView) -> Unit,
) {
    var view by remember { mutableStateOf<DecoratedBarcodeView?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            DecoratedBarcodeView(ctx).apply {
                setStatusText("")            // hide the built-in status; we overlay our own
                decodeContinuous(barcodeCallback)
                view = this
                onViewCreated(this)
            }
        },
    )

    DisposableEffect(lifecycleOwner, view) {
        val v = view
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> v?.resume()
                Lifecycle.Event.ON_PAUSE -> v?.pause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            v?.pause()
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
            text = stringResource(R.string.qr_slide_label),
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

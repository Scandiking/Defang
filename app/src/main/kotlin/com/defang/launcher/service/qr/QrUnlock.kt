package com.defang.launcher.service.qr

import android.content.Context
import android.content.pm.PackageManager

/**
 * Shared constants for the QR/barcode unlock flow, plus a small capability
 * helper.
 *
 * Like the NFC unlock, this is a physical-friction gate: instead of the
 * slide-to-open at the end of the intent gate, the user scans a specific code
 * (registered once in settings) with the camera to open a watched app. A live
 * camera preview needs a resumed Activity — the gate itself is a View overlay
 * inside the AccessibilityService and can't host one — so [QrUnlockActivity]
 * does the scanning and reports the result back to the service by broadcast.
 *
 * QR is mutually exclusive with NFC (one unlock method at a time; enforced in
 * the settings ViewModel). Note a QR/barcode can be photographed and reused off
 * a screen, so it is weaker friction than a physical NFC tag — the registration
 * UI says so.
 */
object QrUnlock {
    /** Intent extra: which mode [QrUnlockActivity] runs in. */
    const val EXTRA_MODE = "com.defang.launcher.qr.MODE"

    /** Register a new code (from settings): capture the scanned value and store it. */
    const val MODE_REGISTER = "register"

    /** Verify the registered code to open a watched app (from the gate). */
    const val MODE_UNLOCK = "unlock"

    /** Intent extra: the watched app's label, shown on the unlock screen. */
    const val EXTRA_APP_LABEL = "com.defang.launcher.qr.APP_LABEL"

    /** Broadcast (to our own package): the registered code was scanned — open. */
    const val ACTION_QR_UNLOCKED = "com.defang.launcher.qr.UNLOCKED"

    /** Broadcast: the user backed out of the unlock screen — treat as "Go back". */
    const val ACTION_QR_GOBACK = "com.defang.launcher.qr.GOBACK"

    /**
     * Max stored value length. A QR can encode a kilobyte of text; an unlock code
     * never needs that, and storing an enormous payload is pointless. Well above
     * any sane code (URLs, UUIDs, short strings).
     */
    const val MAX_VALUE_LENGTH = 512

    /** Whether the device has any camera at all (regardless of permission). */
    fun hasCamera(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
}

package com.defang.launcher.service.nfc

import android.content.Context
import android.nfc.NfcAdapter

/**
 * Shared constants for the NFC-tag unlock flow, plus a couple of small helpers.
 *
 * The unlock is a physical-friction gate: instead of the slide-to-open at the
 * end of the intent gate, the user must tap a specific NFC tag (registered once
 * in settings) to open a watched app. Reading a tag requires a resumed Activity
 * — the gate itself is a View overlay inside the AccessibilityService and can't
 * read NFC — so [NfcUnlockActivity] does the reading and reports the result back
 * to the service by broadcast.
 */
object NfcUnlock {
    /** Intent extra: which mode [NfcUnlockActivity] runs in. */
    const val EXTRA_MODE = "com.defang.launcher.nfc.MODE"

    /** Register a new tag (from settings): capture its UID and store it. */
    const val MODE_REGISTER = "register"

    /** Verify the registered tag to open a watched app (from the gate). */
    const val MODE_UNLOCK = "unlock"

    /** Intent extra: the watched app's label, shown on the unlock screen. */
    const val EXTRA_APP_LABEL = "com.defang.launcher.nfc.APP_LABEL"

    /** Broadcast (to our own package): the registered tag was scanned — open. */
    const val ACTION_NFC_UNLOCKED = "com.defang.launcher.nfc.UNLOCKED"

    /** Broadcast: the user backed out of the unlock screen — treat as "Go back". */
    const val ACTION_NFC_GOBACK = "com.defang.launcher.nfc.GOBACK"

    /** True if this device has NFC hardware and it is currently switched on. */
    fun isUsable(context: Context): Boolean {
        val adapter = NfcAdapter.getDefaultAdapter(context) ?: return false
        return adapter.isEnabled
    }

    /** Whether the device has NFC hardware at all (regardless of on/off). */
    fun hasHardware(context: Context): Boolean =
        NfcAdapter.getDefaultAdapter(context) != null

    /** Tag UID as uppercase hex, the form stored in prefs and compared against. */
    fun uidHex(id: ByteArray): String = id.joinToString("") { "%02X".format(it) }

    /**
     * True if the UID is a *randomized* one, regenerated on every tap — the form
     * secure cards (national eID, passports, most current contactless bank cards,
     * and phone/watch HCE wallets like Garmin Pay) present for anti-tracking. Per
     * ISO/IEC 14443-3, a single-size 4-byte UID beginning with 0x08 is a random
     * number, not a fixed identity, so it can never be matched against later and
     * is useless as a key. 7- and 10-byte UIDs are always manufacturer-fixed.
     */
    fun isRandomUid(id: ByteArray): Boolean =
        id.size == 4 && id[0] == 0x08.toByte()
}

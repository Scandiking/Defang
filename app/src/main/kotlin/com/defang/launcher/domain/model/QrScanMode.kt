package com.defang.launcher.domain.model

/**
 * How a QR/barcode scan relates to the intent-gate countdown. Stored by ordinal
 * — append only.
 */
enum class QrScanMode {
    /**
     * The scanner only becomes available once the countdown reaches zero — the
     * same point the slide-to-open and NFC unlock appear. The wait is preserved
     * as friction; the scan is an extra deliberate act on top of it. Default.
     */
    AFTER_COUNTDOWN,

    /**
     * The scanner opens immediately and a valid code opens the app without
     * waiting out the countdown. The friction here is physically reaching the
     * code (placed out of the way), not the timer.
     */
    IMMEDIATE;

    companion object {
        fun fromOrdinal(value: Int): QrScanMode =
            entries.getOrElse(value) { AFTER_COUNTDOWN }
    }
}

package com.defang.launcher.domain.model

/**
 * Determines which awareness content track is shown on the intent gate,
 * end-card, and cool-down screen.
 */
enum class ContentTrack {
    /** Default — covers dopamine mechanics, prefrontal impairment, FOMO, Hebbian learning. */
    GENERAL,

    /** For watched social apps and social browser domains. Includes slot machine mechanics,
     *  notification timing, business-model transparency, algorithmic radicalization. */
    SOCIAL,

    /** For browser adult domains. Covers Coolidge effect, superstimuli, DeltaFosB, PIED. */
    ADULT,
}

package com.flexkeys.service

import com.flexkeys.layout.KeyType

/**
 * Keyboard feel presets: how each kind of key feels under the finger, made only of vibration (no sounds).
 * Each feel is a short pattern of tiny pulses per key type. Pure data; [Feedback] turns it into vibration.
 */
object KeyFeel {
    const val CLASSIC = 0
    const val SOFT = 1
    const val CLICKY = 2
    const val MECHANICAL = 3
    const val SILKY = 4

    val names = listOf("Classic (uses the style below)", "Soft", "Clicky", "Mechanical", "Silky")

    /** The kinds of pulse, from the phone's own vibration primitives. */
    enum class Prim { CLICK, TICK, LOW_TICK, THUD }

    /** One pulse: its kind, strength 0..1 and how long after the previous one it comes. */
    class Step(val prim: Prim, val scale: Float, val delayMs: Int = 0)

    /** The pattern for [type] under [feel]; empty for Classic, which keeps the older haptic style settings. */
    fun steps(feel: Int, type: KeyType, long: Boolean): List<Step> {
        if (feel !in SOFT..SILKY) return emptyList()
        if (long) return when (feel) {
            SOFT -> listOf(Step(Prim.THUD, 0.6f))
            CLICKY -> listOf(Step(Prim.CLICK, 1f), Step(Prim.THUD, 0.4f, 20))
            MECHANICAL -> listOf(Step(Prim.THUD, 1f), Step(Prim.CLICK, 0.5f, 20))
            else -> listOf(Step(Prim.THUD, 0.45f))
        }
        val kind = when (type) {
            KeyType.SPACE -> Kind.SPACE
            KeyType.BACKSPACE -> Kind.BACKSPACE
            KeyType.ENTER -> Kind.ENTER
            KeyType.CHAR -> Kind.LETTER
            else -> Kind.OTHER
        }
        return when (feel) {
            SOFT -> when (kind) {
                Kind.LETTER -> listOf(Step(Prim.LOW_TICK, 0.55f))
                Kind.SPACE -> listOf(Step(Prim.LOW_TICK, 0.7f))
                Kind.BACKSPACE -> listOf(Step(Prim.LOW_TICK, 0.5f))
                Kind.ENTER -> listOf(Step(Prim.THUD, 0.5f))
                Kind.OTHER -> listOf(Step(Prim.LOW_TICK, 0.45f))
            }
            CLICKY -> when (kind) {
                Kind.LETTER -> listOf(Step(Prim.CLICK, 0.6f))
                Kind.SPACE -> listOf(Step(Prim.CLICK, 0.85f))
                Kind.BACKSPACE -> listOf(Step(Prim.TICK, 0.8f))
                Kind.ENTER -> listOf(Step(Prim.CLICK, 1f), Step(Prim.TICK, 0.5f, 25))
                Kind.OTHER -> listOf(Step(Prim.TICK, 0.6f))
            }
            MECHANICAL -> when (kind) {
                // The press, then the key bottoming out a moment later.
                Kind.LETTER -> listOf(Step(Prim.CLICK, 0.75f), Step(Prim.THUD, 0.35f, 14))
                Kind.SPACE -> listOf(Step(Prim.THUD, 0.9f), Step(Prim.LOW_TICK, 0.5f, 18))
                Kind.BACKSPACE -> listOf(Step(Prim.CLICK, 0.7f), Step(Prim.THUD, 0.3f, 12))
                Kind.ENTER -> listOf(Step(Prim.CLICK, 1f), Step(Prim.THUD, 0.75f, 16))
                Kind.OTHER -> listOf(Step(Prim.CLICK, 0.5f), Step(Prim.THUD, 0.2f, 12))
            }
            else -> when (kind) { // Silky: barely there
                Kind.LETTER -> listOf(Step(Prim.LOW_TICK, 0.3f))
                Kind.SPACE -> listOf(Step(Prim.LOW_TICK, 0.45f))
                Kind.BACKSPACE -> listOf(Step(Prim.LOW_TICK, 0.3f))
                Kind.ENTER -> listOf(Step(Prim.LOW_TICK, 0.6f), Step(Prim.LOW_TICK, 0.3f, 30))
                Kind.OTHER -> listOf(Step(Prim.LOW_TICK, 0.25f))
            }
        }
    }

    private enum class Kind { LETTER, SPACE, BACKSPACE, ENTER, OTHER }

    /** For phones without vibration primitives: a rough pulse length (ms) and strength (1..255) for each kind. */
    fun fallbackPulse(prim: Prim): Pair<Long, Int> = when (prim) {
        Prim.CLICK -> 14L to 200
        Prim.TICK -> 8L to 120
        Prim.LOW_TICK -> 6L to 70
        Prim.THUD -> 22L to 180
    }
}

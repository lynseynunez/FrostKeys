package com.flexkeys.ui

import android.graphics.PointF
import android.graphics.Shader
import android.view.View
import com.flexkeys.theme.GlassBackdrop
import com.flexkeys.theme.GlassPainter
import com.flexkeys.theme.GlassTheme

/**
 * Implemented by the root keyboard view. Children use it to draw glass that lines up with
 * the shared backdrop and to draw popups that may extend beyond their own bounds.
 */
interface GlassHost {
    val theme: GlassTheme
    val backdrop: GlassBackdrop
    val painter: GlassPainter

    /** Writes the position of [view]'s top-left corner within the backdrop into [out]. */
    fun backdropOffset(view: View, out: PointF)

    /** The blue/purple/pink gradient across the whole keyboard, in backdrop coordinates, or null. */
    fun glassGradient(): Shader?

    /** How far the glass shimmer has shifted with the phone's tilt, -1..1 (0 when the shimmer is off). */
    val shimmerX: Float get() = 0f
    val shimmerY: Float get() = 0f

    /** Requests a redraw of the overlay layer (popups and key previews). */
    fun invalidateOverlay()
}

/** Finds the nearest [GlassHost] ancestor of this view, if any. */
fun View.findGlassHost(): GlassHost? {
    var p = parent
    while (p != null) {
        if (p is GlassHost) return p
        p = p.parent
    }
    return null
}

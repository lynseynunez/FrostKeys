package com.flexkeys.edit

import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout
import com.flexkeys.keyboard.Icon
import com.flexkeys.ui.GlassPanelLayout
import com.flexkeys.ui.TileButton

/** Text editing tools, like Gboard's: cursor keys, selection, clipboard actions. */
enum class EditAction { LEFT, RIGHT, UP, DOWN, HOME, END, SELECT_ALL, COPY, CUT, PASTE, BACKSPACE }

class EditPanel(context: Context, private val listener: Listener) : GlassPanelLayout(context) {

    interface Listener {
        /** [selecting] is true while "Select" is on, so cursor keys extend the selection. */
        fun onEditAction(action: EditAction, selecting: Boolean)
        fun onPanelClosed()
    }

    private var selecting = false
    private lateinit var selectTile: TileButton

    init {
        setPadding(dp(6f), dp(6f), dp(6f), dp(6f))
        val main = LinearLayout(context).apply { orientation = HORIZONTAL }

        // Left: cursor pad.
        val pad = LinearLayout(context).apply { orientation = VERTICAL }
        pad.addView(row(spacer(), tile(Icon.ARROW_UP, EditAction.UP), spacer()))
        selectTile = TileButton(context, label = "Select").apply {
            onClick = {
                selecting = !selecting
                active = selecting
            }
        }
        pad.addView(row(tile(Icon.ARROW_LEFT, EditAction.LEFT), selectTile, tile(Icon.ARROW_RIGHT, EditAction.RIGHT)))
        pad.addView(row(labelTile("Start", EditAction.HOME), tile(Icon.ARROW_DOWN, EditAction.DOWN),
            labelTile("End", EditAction.END)))
        main.addView(pad, LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 3f))

        // Right: clipboard actions.
        val side = LinearLayout(context).apply { orientation = VERTICAL }
        for ((label, action) in listOf("Select all" to EditAction.SELECT_ALL, "Copy" to EditAction.COPY,
            "Cut" to EditAction.CUT, "Paste" to EditAction.PASTE)) {
            side.addView(labelTile(label, action), LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        main.addView(side, LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.6f))
        addView(main, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val bottom = row(
            TileButton(context, label = "ABC").apply { onClick = { listener.onPanelClosed() } },
            TileButton(context, icon = Icon.BACKSPACE).apply {
                repeatWhileHeld = true
                onClick = { listener.onEditAction(EditAction.BACKSPACE, false) }
            },
        )
        addView(bottom, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52f)))
    }

    private fun row(vararg views: android.view.View): LinearLayout {
        val r = LinearLayout(context).apply { orientation = HORIZONTAL }
        for (v in views) r.addView(v, LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        r.layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        return r
    }

    private fun spacer() = android.view.View(context)

    private fun tile(icon: Icon, action: EditAction) =
        TileButton(context, icon = icon).apply {
            repeatWhileHeld = true
            onClick = { listener.onEditAction(action, selecting) }
        }

    private fun labelTile(label: String, action: EditAction) =
        TileButton(context, label = label).apply { onClick = { listener.onEditAction(action, selecting) } }
}

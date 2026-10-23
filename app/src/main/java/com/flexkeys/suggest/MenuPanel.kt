package com.flexkeys.suggest

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.TextView
import com.flexkeys.keyboard.Icons
import com.flexkeys.suggest.SuggestionStripView.Action
import com.flexkeys.theme.Prefs
import com.flexkeys.ui.GlassPanelLayout

/**
 * Every toolbar action in a grid. Tap one to use it. In edit mode, tapping shows or hides it in the
 * top row; the numbers show the order (hide and show again to move one to the end).
 */
class MenuPanel(
    context: Context,
    private val listener: Listener,
    private val onToolbarChanged: () -> Unit,
) : GlassPanelLayout(context) {

    interface Listener {
        fun onMenuAction(action: Action)
    }

    private val prefs = Prefs.get(context)
    private val title = TextView(context)
    private val editChip = chip()
    private val resetChip = chip()
    private val grid = GridView(context)
    private val adapter = ActionAdapter()
    private var editing = false
    private var dragging: Action? = null

    private fun chip() = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setPadding(dp(14f), 0, dp(14f), 0)
    }

    init {
        setPadding(dp(8f), dp(6f), dp(8f), dp(4f))
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        title.setPadding(dp(8f), 0, 0, 0)
        header.addView(title, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        resetChip.text = "Reset"
        resetChip.setOnClickListener {
            prefs.toolbarItems = ToolbarItems.format(ToolbarItems.defaults)
            changed()
        }
        header.addView(resetChip, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34f)).apply { marginEnd = dp(6f) })
        editChip.setOnClickListener {
            editing = !editing
            changed()
        }
        header.addView(editChip, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34f)))
        addView(header, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42f)))

        grid.numColumns = 4
        grid.adapter = adapter
        grid.isVerticalScrollBarEnabled = false
        grid.selector = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
        grid.setOnItemClickListener { _, _, position, _ ->
            val action = adapter.items[position]
            if (editing) {
                prefs.toolbarItems = ToolbarItems.format(ToolbarItems.toggle(ToolbarItems.parse(prefs.toolbarItems), action))
                changed()
            } else {
                listener.onMenuAction(action)
            }
        }
        // In edit mode, holding a tile picks it up; dropping it on another tile moves it there.
        grid.setOnItemLongClickListener { _, view, position, _ ->
            if (!editing) return@setOnItemLongClickListener false
            val a = adapter.items[position]
            dragging = a
            view.startDragAndDrop(android.content.ClipData.newPlainText("tool", a.name), View.DragShadowBuilder(view), null, 0)
        }
        addView(grid, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    override fun onHostAttached() = refresh()

    private fun changed() {
        refresh()
        onToolbarChanged()
    }

    private fun refresh() {
        val t = host?.theme
        title.setTextColor(t?.textColor ?: Color.BLACK)
        title.text = if (editing) "Tap to show or hide. Hold and drag to reorder" else "Tools"
        resetChip.visibility = if (editing) View.VISIBLE else View.GONE
        editChip.text = if (editing) "Done" else "Edit"
        for (c in listOf(resetChip, editChip)) {
            c.setTextColor(t?.textColor ?: Color.BLACK)
            c.background = GradientDrawable().apply {
                cornerRadius = dp(17f).toFloat()
                setColor(t?.keyFill ?: 0x33FFFFFF)
            }
        }
        editChip.background = GradientDrawable().apply {
            cornerRadius = dp(17f).toFloat()
            setColor(if (editing) t?.accentFill ?: 0x66FFFFFF else t?.keyFill ?: 0x33FFFFFF)
        }
        if (editing) editChip.setTextColor(t?.onAccent ?: Color.WHITE)
        adapter.notifyDataSetChanged()
    }

    private inner class ActionAdapter : BaseAdapter() {
        val items = Action.values().toList()
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val tile = (convertView as? Tile) ?: Tile(context)
            val action = items[position]
            val order = ToolbarItems.parse(prefs.toolbarItems).indexOf(action)
            tile.bind(action, if (editing) order + 1 else 0, editing && order < 0, host?.theme)
            tile.action = action
            return tile
        }
    }

    /** One grid cell: an icon on a glass tile with the action's name underneath. */
    private inner class Tile(context: Context) : LinearLayout(context) {
        private val icon = IconView(context)
        private val label = TextView(context)
        var action: Action? = null

        init {
            setOnDragListener { _, e ->
                when (e.action) {
                    android.view.DragEvent.ACTION_DRAG_STARTED -> true
                    android.view.DragEvent.ACTION_DROP -> {
                        val moved = dragging
                        val target = action
                        if (moved != null && target != null) {
                            prefs.toolbarItems = ToolbarItems.format(
                                ToolbarItems.move(ToolbarItems.parse(prefs.toolbarItems), moved, target))
                            changed()
                        }
                        true
                    }
                    android.view.DragEvent.ACTION_DRAG_ENDED -> {
                        dragging = null
                        true
                    }
                    else -> true
                }
            }
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = android.widget.AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(78f))
            addView(icon, LayoutParams(dp(64f), dp(48f)).apply { topMargin = dp(4f) })
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            label.gravity = Gravity.CENTER
            label.maxLines = 1
            addView(label, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2f) })
        }

        fun bind(action: Action, number: Int, dimmed: Boolean, theme: com.flexkeys.theme.GlassTheme?) {
            label.text = action.label
            label.setTextColor(theme?.textColor ?: Color.BLACK)
            icon.set(action, number, theme)
            alpha = if (dimmed) 0.45f else 1f
        }
    }

    private inner class IconView(context: Context) : View(context) {
        private var action = Action.SETTINGS
        private var number = 0
        private var theme: com.flexkeys.theme.GlassTheme? = null
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        fun set(action: Action, number: Int, theme: com.flexkeys.theme.GlassTheme?) {
            this.action = action
            this.number = number
            this.theme = theme
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val t = theme
            paint.style = Paint.Style.FILL
            paint.color = t?.keyFill ?: 0x44FFFFFF
            val r = dp(16f).toFloat()
            canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, paint)
            Icons.draw(canvas, action.icon, width / 2f, height / 2f, height * 0.5f, t?.textColor ?: Color.BLACK)
            if (number > 0) {
                val br = dp(9f).toFloat()
                val cx = width - br - dp(3f)
                val cy = br + dp(3f)
                paint.color = t?.accentFill ?: Color.BLUE
                canvas.drawCircle(cx, cy, br, paint)
                paint.color = t?.onAccent ?: Color.WHITE
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = dp(11f).toFloat()
                canvas.drawText(number.toString(), cx, cy - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f, paint)
            }
        }
    }
}

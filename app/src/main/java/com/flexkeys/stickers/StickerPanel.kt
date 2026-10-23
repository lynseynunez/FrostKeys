package com.flexkeys.stickers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.flexkeys.clipboard.ClipboardPanel
import com.flexkeys.keyboard.Icon
import com.flexkeys.ui.GlassButton
import com.flexkeys.ui.GlassPanelLayout
import java.io.File

/** The user's stickers in a grid: tap one to send it as an image. */
class StickerPanel(context: Context, private val listener: Listener) : GlassPanelLayout(context) {

    interface Listener : ClipboardPanel.Listener {
        fun onStickerPicked(file: File)
        /** Opens the screen where stickers are added and removed. */
        fun onEditStickers()
    }

    private val grid = GridView(context)
    private val empty = TextView(context)
    private val title = TextView(context)
    private val adapter = StickerAdapter()
    private val thumbs = object : LruCache<String, Bitmap>(48) {}

    init {
        setPadding(dp(8f), dp(6f), dp(8f), dp(6f))
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(GlassButton(context, icon = Icon.KEYBOARD).apply { onClick = listener::onPanelClosed },
            LayoutParams(dp(48f), dp(44f)))
        title.text = "Stickers"
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        header.addView(title, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(GlassButton(context, icon = Icon.SETTINGS).apply { onClick = listener::onEditStickers },
            LayoutParams(dp(48f), dp(44f)))
        addView(header)

        val body = FrameLayout(context)
        grid.numColumns = 4
        grid.adapter = adapter
        grid.isVerticalScrollBarEnabled = false
        grid.selector = ColorDrawable(Color.TRANSPARENT)
        grid.setOnItemClickListener { _, _, position, _ -> listener.onStickerPicked(adapter.items[position]) }
        body.addView(grid)
        empty.gravity = Gravity.CENTER
        empty.setPadding(dp(24f), 0, dp(24f), 0)
        empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        empty.text = "Add your own stickers from your gallery.\nTap the gear to add some."
        body.addView(empty, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(body, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    override fun onHostAttached() {
        host?.theme?.let {
            title.setTextColor(it.textColor)
            empty.setTextColor(it.secondaryTextColor)
        }
        adapter.items = StickerStore.list(context)
        adapter.notifyDataSetChanged()
        empty.visibility = if (adapter.items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun thumb(file: File): Bitmap? = thumbs.get(file.name) ?: run {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= THUMB_PX) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.also { thumbs.put(file.name, it) }
    }

    private inner class StickerAdapter : BaseAdapter() {
        var items: List<File> = emptyList()
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = (convertView as? ImageView) ?: ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(dp(6f), dp(6f), dp(6f), dp(6f))
                layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(76f))
            }
            view.setImageBitmap(thumb(items[position]))
            return view
        }
    }

    private companion object {
        const val THUMB_PX = 160
    }
}

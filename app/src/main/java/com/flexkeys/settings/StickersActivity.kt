package com.flexkeys.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.flexkeys.stickers.StickerStore
import com.flexkeys.theme.GlassTheme
import com.flexkeys.theme.Prefs

/** Add stickers from the gallery and remove the ones you don't want. */
class StickersActivity : Activity() {

    private lateinit var palette: SettingsPalette
    private lateinit var grid: GridLayout
    private lateinit var count: TextView

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs.get(this)
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        palette = SettingsPalette(dark, prefs.accentColor)

        val page = FrameLayout(this)
        page.addView(BackdropView(this).apply {
            val t = GlassTheme.from(context, prefs)
            backdrop.setTheme(GlassTheme(dark, t.accent, t.glassOpacity, 0f, 48f * t.density, t.cornerRadiusPx,
                t.keySpacingPx, t.fontScale, t.heightScale, t.density))
            scrim = palette.scrim
        })
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(16f), dp(16f), dp(24f))
        }
        page.addView(ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(column)
        })
        column.addView(label("‹  Settings", 15f, palette.secondary).apply {
            setPadding(0, dp(4f), 0, dp(8f))
            setOnClickListener { finish() }
        })
        column.addView(label("Stickers", 30f, palette.text).apply { typeface = Typeface.create("sans-serif", Typeface.BOLD) })
        count = label("", 14f, palette.secondary).apply { setPadding(0, dp(2f), 0, dp(12f)) }
        column.addView(count)
        column.addView(PillButton(this, "Add stickers from gallery", true, palette).apply { onClick = { pick() } },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        column.addView(label("Tip: transparent PNGs keep their transparency. Tap a sticker below to remove it.", 13f, palette.secondary)
            .apply { setPadding(0, dp(10f), 0, dp(10f)) })
        grid = GridLayout(this).apply { columnCount = 4 }
        column.addView(grid)
        setContentView(page)
        refresh()
    }

    private fun label(text: String, sp: Float, color: Int) = TextView(this).apply {
        this.text = text
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
    }

    private fun pick() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        try {
            startActivityForResult(intent, REQUEST_PICK)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No photo picker found", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PICK || resultCode != RESULT_OK || data == null) return
        val uris = ArrayList<android.net.Uri>()
        data.data?.let { uris.add(it) }
        data.clipData?.let { clip -> for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri) }
        val app = applicationContext
        Thread {
            val added = uris.count { StickerStore.import(app, it) }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (added < uris.size) Toast.makeText(this, "Couldn't add ${uris.size - added} image(s)", Toast.LENGTH_SHORT).show()
                refresh()
            }
        }.start()
    }

    private fun refresh() {
        val files = StickerStore.list(this)
        count.text = if (files.isEmpty()) "No stickers yet" else "${files.size} stickers, stored only on this phone"
        grid.removeAllViews()
        val size = (resources.displayMetrics.widthPixels - dp(32f)) / 4
        for (f in files) {
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            val bmp = BitmapFactory.decodeFile(f.path, opts) ?: continue
            grid.addView(ImageView(this).apply {
                setImageBitmap(bmp)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(dp(6f), dp(6f), dp(6f), dp(6f))
                background = GradientDrawable().apply {
                    cornerRadius = dp(14f).toFloat()
                    setColor(palette.card)
                }
                setOnClickListener {
                    AlertDialog.Builder(this@StickersActivity)
                        .setMessage("Remove this sticker?")
                        .setPositiveButton("Remove") { _, _ ->
                            StickerStore.delete(f)
                            refresh()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
                layoutParams = GridLayout.LayoutParams().apply {
                    width = size - dp(6f)
                    height = size - dp(6f)
                    setMargins(dp(3f), dp(3f), dp(3f), dp(3f))
                }
            })
        }
    }

    private companion object {
        const val REQUEST_PICK = 1
    }
}

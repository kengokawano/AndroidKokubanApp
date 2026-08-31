package jp.saitama.orange.drawkokuban2

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils

/**
 * 黒板の上に貼り付けるピン留めメモ（クリップボードのテキスト）。
 * ビットマップ本体には焼き込まず、別レイヤーとして保持する。
 * 座標はキャンバス（＝ビットマップ）ピクセル基準の左上位置。
 */
data class PinnedMemo(
    val id: Long,
    val text: String,
    val bitmap: Bitmap,
    val x: Float,
    val y: Float
) {
    val width: Int get() = bitmap.width
    val height: Int get() = bitmap.height

    fun contains(px: Float, py: Float): Boolean =
        px >= x && px <= x + width && py >= y && py <= y + height
}

private const val MEMO_MAX_LINES = 12
private const val MEMO_BG_COLOR = 0xE6103726.toInt()
private const val MEMO_BORDER_COLOR = 0xFFFFD700.toInt() // ゴールド（ピン留めの見た目）

/** クリップボードのテキストを取得する。テキストが無ければ null。 */
fun readClipboardText(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        ?: return null
    val clip = clipboard.primaryClip ?: return null
    if (clip.itemCount == 0) return null

    // テキスト状態でなくてもURI等は coerceToText で文字列化できる
    val text = (0 until clip.itemCount)
        .mapNotNull { index ->
            runCatching { clip.getItemAt(index).coerceToText(context)?.toString() }.getOrNull()
        }
        .joinToString("\n")
        .trim()

    return text.ifEmpty { null }
}

/**
 * メモの見た目をビットマップとして作る。
 * 画面表示と保存/書き出しで同じビットマップを使うので、見た目が必ず一致する。
 */
fun createMemoBitmap(context: Context, text: String, canvasWidth: Int): Bitmap {
    val density = context.resources.displayMetrics.density
    val padding = 12f * density
    val radius = 8f * density
    val borderWidth = 2f * density

    val textPaint = TextPaint().apply {
        isAntiAlias = true
        color = Color.WHITE
        textSize = 16f * density
        typeface = Typeface.DEFAULT_BOLD
    }

    val maxTextWidth = ((canvasWidth * 0.6f) - padding * 2f)
        .toInt()
        .coerceAtLeast((80f * density).toInt())

    val layout = StaticLayout.Builder
        .obtain(text, 0, text.length, textPaint, maxTextWidth)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setIncludePad(false)
        .setMaxLines(MEMO_MAX_LINES)
        .setEllipsize(TextUtils.TruncateAt.END)
        .build()

    // 実際に使われている行幅に合わせて余白を詰める
    var contentWidth = 0f
    for (line in 0 until layout.lineCount) {
        contentWidth = maxOf(contentWidth, layout.getLineWidth(line))
    }
    contentWidth = contentWidth.coerceAtMost(maxTextWidth.toFloat())

    val bitmapWidth = (contentWidth + padding * 2f).toInt().coerceAtLeast(1)
    val bitmapHeight = (layout.height + padding * 2f).toInt().coerceAtLeast(1)

    val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val rect = RectF(
        borderWidth / 2f,
        borderWidth / 2f,
        bitmapWidth - borderWidth / 2f,
        bitmapHeight - borderWidth / 2f
    )
    canvas.drawRoundRect(rect, radius, radius, Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = MEMO_BG_COLOR
    })
    canvas.drawRoundRect(rect, radius, radius, Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = borderWidth
        color = MEMO_BORDER_COLOR
    })

    canvas.save()
    canvas.translate(padding, padding)
    layout.draw(canvas)
    canvas.restore()

    return bitmap
}

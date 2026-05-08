package jp.saitama.orange.drawkokuban2

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.BlendMode
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.compose.ui.geometry.Offset
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sqrt

// ビットマップ準備 & 背景
fun createChalkboardBitmap(width: Int, height: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    // Mutableであることを確認
    return if (bitmap.isMutable) {
        bitmap
    } else {
        bitmap.copy(Bitmap.Config.ARGB_8888, true)
    }
}

private val bgPaint = Paint().apply {
    isAntiAlias = true
    style = Paint.Style.FILL
    color = Color.rgb(11, 46, 26) // 黒板系の深緑
}

// 木目テクスチャのキャッシュ
private var woodTexture: Bitmap? = null

fun fillChalkboardBackground(target: Bitmap, context: Context? = null) {
    Log.d("ChalkboardUtils", "fillChalkboardBackground called - using pure chalkboard color")
    val c = Canvas(target)

    // 描画エリアは純粋な黒板色（緑）のみ
    c.drawRect(0f, 0f, target.width.toFloat(), target.height.toFloat(), bgPaint)
}

fun clearAll(target: Bitmap, context: Context? = null) {
    // 全消し＝背景から塗り直す運用
    fillChalkboardBackground(target, context)
}

// ペン描画（白/赤 × 細/太）
private fun makePenPaint(color: Int, thicknessPx: Float): Paint {
    return Paint().apply {
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = thicknessPx
        this.color = color
    }
}

fun drawStroke(
    target: Bitmap,
    points: List<Offset>,
    color: Int,        // 例: Color.White.toArgb()
    thicknessPx: Float
) {
    if (points.size < 2) return

    val c = Canvas(target)
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
    }
    c.drawPath(path, makePenPaint(color, thicknessPx))
}

// 距離(=速度の代理)→ 太さ倍率。レンジは 0.3×〜1.5×(チョーク感を強めに)
private fun velocityToMult(dist: Float): Float = when {
    dist < 2f -> 1.5f
    dist < 8f -> 1.5f - (dist - 2f) / 6f * 0.5f          // 1.5 → 1.0
    dist < 30f -> 1.0f - (dist - 8f) / 22f * 0.7f         // 1.0 → 0.3
    else -> 0.3f
}

// 速度可変ストローク。チョーク手書き風(ゆっくり=太い、速い=細い)。
// 平滑化:
//   1) 各頂点の太さ倍率は EMA で平滑化(隣接セグメントの段差を解消)
//   2) 各セグメントを subdivisions で細分化し、頂点間でリニア補間して滑らかにテーパーさせる
// 連続性:
//   initialMult に前回呼び出しの末尾倍率を渡し、戻り値を次回に引き継ぐとイベント間で太さが連続する
fun drawStrokeVariable(
    target: Bitmap,
    points: List<Offset>,
    color: Int,
    baseThicknessPx: Float,
    initialMult: Float = 1f,
    subdivisions: Int = 4
): Float {
    if (points.size < 2) return initialMult

    val c = Canvas(target)
    val paint = Paint().apply {
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        this.color = color
    }

    // 頂点ごとの倍率を EMA で算出
    val alpha = 0.35f
    val n = points.size
    val mults = FloatArray(n)
    mults[0] = initialMult
    var ema = initialMult
    for (i in 1 until n) {
        val a = points[i - 1]
        val b = points[i]
        val dx = b.x - a.x
        val dy = b.y - a.y
        val desired = velocityToMult(sqrt(dx * dx + dy * dy))
        ema += (desired - ema) * alpha
        mults[i] = ema
    }

    // セグメントを細分化して幅をリニア補間しながら描画
    val sub = subdivisions.coerceAtLeast(1)
    for (i in 1 until n) {
        val a = points[i - 1]
        val b = points[i]
        val w1 = mults[i - 1] * baseThicknessPx
        val w2 = mults[i] * baseThicknessPx
        val dx = b.x - a.x
        val dy = b.y - a.y
        for (k in 0 until sub) {
            val t1 = k.toFloat() / sub
            val t2 = (k + 1).toFloat() / sub
            val x1 = a.x + dx * t1
            val y1 = a.y + dy * t1
            val x2 = a.x + dx * t2
            val y2 = a.y + dy * t2
            // セグメント内の中点幅を採用
            val w = w1 + (w2 - w1) * ((t1 + t2) * 0.5f)
            paint.strokeWidth = w
            c.drawLine(x1, y1, x2, y2, paint)
        }
    }

    return ema
}

// 黒板消し（背景色で上塗りして消す）
private fun makeEraserPaint(radiusPx: Float): Paint {
    return Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = radiusPx * 2f // 半径→直径
        // 背景色で上塗りして消す（透明化ではなく）
        color = Color.rgb(11, 46, 26) // 黒板の緑色
    }
}

fun erasePath(
    target: Bitmap,
    points: List<Offset>,
    radiusPx: Float
) {
    if (points.size < 2) return
    val c = Canvas(target)
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
    }
    c.drawPath(path, makeEraserPaint(radiusPx))
}

// サムネイル & 保存/読込
data class SlotMeta(
    val filePath: String,
    val updatedAtMillis: Long
)

fun makeThumbnail(src: Bitmap, maxW: Int, maxH: Int): Bitmap {
    val ratio = minOf(maxW.toFloat() / src.width, maxH.toFloat() / src.height)
    val w = (src.width * ratio).toInt().coerceAtLeast(1)
    val h = (src.height * ratio).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(src, w, h, true)
}

fun savePng(target: Bitmap, file: File): SlotMeta {
    FileOutputStream(file).use { fos ->
        target.compress(Bitmap.CompressFormat.PNG, 100, fos)
    }
    return SlotMeta(file.absolutePath, System.currentTimeMillis())
}

fun loadPng(file: File): Bitmap? {
    if (!file.exists()) return null
    val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
    // ロードしたビットマップがMutableであることを確認
    return if (bitmap.isMutable) {
        bitmap
    } else {
        bitmap.copy(Bitmap.Config.ARGB_8888, true)
    }
}


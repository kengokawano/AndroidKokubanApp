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

// 黒板消し（CLEAR描画で「なぞった所だけ」消す）
private fun makeEraserPaint(radiusPx: Float): Paint {
    return Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = radiusPx * 2f // 半径→直径
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                blendMode = BlendMode.CLEAR
            } else {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
        } catch (e: Exception) {
            // フォールバック: 背景色で上塗り
            color = Color.rgb(11, 46, 26)
        }
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


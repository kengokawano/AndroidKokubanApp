package jp.saitama.orange.drawkokuban2

import android.content.Context
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import java.io.File

enum class PenColor {
    WHITE,
    RED
}


data class ChalkboardState(
    val penColor: PenColor = PenColor.WHITE,
    val isThick: Boolean = false,
    val isEraser: Boolean = false,
    val bitmap: Bitmap? = null,
    val currentPath: List<Offset> = emptyList(),
    val isDrawing: Boolean = false,
    val showClearAllDialog: Boolean = false,
    val isLoading: Boolean = true
)

class ChalkboardViewModel : ViewModel() {

    var state by mutableStateOf(ChalkboardState())
        private set

    private var currentPath = mutableListOf<Offset>()

    fun initializeBitmap(width: Int, height: Int, context: Context? = null) {
        android.util.Log.d("ChalkboardViewModel", "initializeBitmap called with context: $context")
        val bitmap = createChalkboardBitmap(width, height)
        fillChalkboardBackground(bitmap, context)
        state = state.copy(bitmap = bitmap, isLoading = false)
    }

    fun createNewBitmap(context: Context? = null) {
        state.bitmap?.let { currentBitmap ->
            val newBitmap = createChalkboardBitmap(currentBitmap.width, currentBitmap.height)
            fillChalkboardBackground(newBitmap, context)
            state = state.copy(bitmap = newBitmap, isLoading = false)
        }
    }

    fun selectPenColor(color: PenColor) {
        state = state.copy(penColor = color, isEraser = false)
    }

    fun toggleThickness() {
        state = state.copy(isThick = !state.isThick)
    }

    fun selectEraser() {
        state = state.copy(isEraser = true)
    }

    fun showClearAllDialog() {
        state = state.copy(showClearAllDialog = true)
    }

    fun hideClearAllDialog() {
        state = state.copy(showClearAllDialog = false)
    }

    fun startDrawing(point: Offset) {
        currentPath.clear()
        currentPath.add(point)
        state = state.copy(
            isDrawing = true,
            currentPath = currentPath.toList()
        )
    }

    fun continueDrawing(point: Offset, context: Context) {
        if (!state.isDrawing) return

        val bitmap = state.bitmap ?: return

        // 点の間引き
        val minStep = if (state.isEraser) {
            24f * 0.6f
        } else {
            if (state.isThick) 12f * 0.6f else 6f * 0.6f
        }

        if (currentPath.isNotEmpty()) {
            val lastPoint = currentPath.last()
            val dx = point.x - lastPoint.x
            val dy = point.y - lastPoint.y
            if (dx * dx + dy * dy < minStep * minStep) {
                return
            }
        }

        currentPath.add(point)

        // リアルタイム描画
        if (currentPath.size >= 2) {
            if (state.isEraser) {
                val eraserSize = getEraserRadius(context)
                erasePath(bitmap, currentPath.takeLast(2), eraserSize)
            } else {
                val color = when (state.penColor) {
                    PenColor.WHITE -> Color.WHITE
                    PenColor.RED -> AppColors.RED.toArgb()
                }
                val thickness = getPenThickness(context)
                drawStroke(bitmap, currentPath.takeLast(2), color, thickness)
            }

        }

        state = state.copy(currentPath = currentPath.toList())
    }

    fun endDrawing() {
        state = state.copy(
            isDrawing = false,
            currentPath = emptyList()
        )
        currentPath.clear()
    }

    fun drawPoint(point: Offset, context: Context) {
        Log.d("ChalkboardViewModel", "drawPoint called at (${point.x}, ${point.y})")
        val bitmap = state.bitmap ?: return.also {
            Log.e("ChalkboardViewModel", "bitmap is null")
        }

        if (state.isEraser) {
            val thickness = getEraserRadius(context)
            Log.d("ChalkboardViewModel", "Drawing eraser point with radius: $thickness")
            drawCircle(bitmap, point, thickness, Color.BLACK)
        } else {
            val color = when (state.penColor) {
                PenColor.WHITE -> Color.WHITE
                PenColor.RED -> AppColors.RED.toArgb()
            }
            val thickness = getPenThickness(context) / 2f
            Log.d("ChalkboardViewModel", "Drawing pen point with thickness: $thickness, color: $color")
            drawCircle(bitmap, point, thickness, color)
        }

        // 新しいbitmapインスタンスを作成してComposeに変更を通知
        val newBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(newBitmap)
        canvas.drawBitmap(bitmap, 0f, 0f, null)

        state = state.copy(bitmap = newBitmap)
        Log.d("ChalkboardViewModel", "drawPoint completed with new bitmap instance")
    }

    fun clearAll(context: Context? = null) {
        val bitmap = state.bitmap ?: return
        clearAll(bitmap, context)
        state = state.copy(
            showClearAllDialog = false,
            penColor = PenColor.WHITE,
            isEraser = false
        )
    }

    fun saveBitmap(context: Context, slotNumber: Int? = null): SlotMeta? {
        Log.d("ChalkboardViewModel", "saveBitmap called with slotNumber: $slotNumber")
        val bitmap = state.bitmap ?: return null.also {
            Log.e("ChalkboardViewModel", "bitmap is null, cannot save")
        }

        // スロット番号が指定されていない場合は、空きスロットを探す
        val targetSlot = slotNumber ?: findNextAvailableSlot(context)
        Log.d("ChalkboardViewModel", "Saving to slot: $targetSlot")

        val file = File(context.filesDir, "chalkboard_$targetSlot.png")
        Log.d("ChalkboardViewModel", "File path: ${file.absolutePath}")

        return try {
            val result = savePng(bitmap, file)
            Log.d("ChalkboardViewModel", "Save successful: ${file.exists()}, size: ${file.length()}")
            result
        } catch (e: Exception) {
            Log.e("ChalkboardViewModel", "Save failed", e)
            null
        }
    }

    private fun findNextAvailableSlot(context: Context): Int {
        for (i in 1..30) {
            val file = File(context.filesDir, "chalkboard_$i.png")
            if (!file.exists()) {
                return i
            }
        }
        return 1 // すべて埋まっている場合は1番を上書き
    }

    fun loadBitmap(context: Context, slotNumber: Int) {
        val file = File(context.filesDir, "chalkboard_$slotNumber.png")
        val bitmap = loadPng(file)
        if (bitmap != null) {
            state = state.copy(bitmap = bitmap, isLoading = false)
        }
    }

    fun resizeBitmapToCanvas(width: Int, height: Int, context: Context) {
        val currentBitmap = state.bitmap ?: return
        if (currentBitmap.width == width && currentBitmap.height == height) {
            // サイズが同じ場合もローディング終了
            state = state.copy(isLoading = false)
            return
        }

        // 新しいサイズのビットマップを作成
        val newBitmap = createChalkboardBitmap(width, height)
        fillChalkboardBackground(newBitmap, context)

        // 既存のビットマップを中央に配置してコピー
        val canvas = android.graphics.Canvas(newBitmap)
        val left = (width - currentBitmap.width) / 2f
        val top = (height - currentBitmap.height) / 2f
        canvas.drawBitmap(currentBitmap, left, top, null)

        state = state.copy(bitmap = newBitmap, isLoading = false)
    }

    fun createThumbnail(maxW: Int = 200, maxH: Int = 150): Bitmap? {
        val bitmap = state.bitmap ?: return null
        return makeThumbnail(bitmap, maxW, maxH)
    }

    private fun getPenThickness(context: Context): Float {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val thinPenSize = prefs.getFloat("thin_pen_size", 6f)
        val thickPenSize = prefs.getFloat("thick_pen_size", 18f)
        return if (state.isThick) thickPenSize else thinPenSize
    }

    private fun getEraserRadius(context: Context): Float {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        return prefs.getFloat("eraser_radius", 48f)
    }

    fun exportToPng(context: Context): Boolean {
        val bitmap = state.bitmap ?: return false

        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val exportWithBackground = prefs.getBoolean("export_with_background", true)

        // 現在の日時を使ってファイル名を生成
        val dateFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
        val timeStamp = dateFormat.format(Date())
        val fileName = "renraku_$timeStamp.png"

        return try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/KokubanRenraku")
            }

            val uri: Uri? = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )

            uri?.let { imageUri ->
                context.contentResolver.openOutputStream(imageUri)?.use { outputStream ->
                    val exportBitmap = if (exportWithBackground) {
                        // 背景ありの場合はそのまま保存
                        bitmap
                    } else {
                        // 背景を透過させる場合
                        createTransparentBitmap(bitmap)
                    }
                    exportBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
                true
            } ?: false
        } catch (e: Exception) {
            Log.e("ChalkboardViewModel", "Export failed", e)
            false
        }
    }

    private fun createTransparentBitmap(originalBitmap: Bitmap): Bitmap {
        val transparentBitmap = Bitmap.createBitmap(
            originalBitmap.width,
            originalBitmap.height,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(transparentBitmap)

        // 元のビットマップを描画
        canvas.drawBitmap(originalBitmap, 0f, 0f, null)

        // 黒板色（緑）を透明に置き換え
        val pixels = IntArray(originalBitmap.width * originalBitmap.height)
        originalBitmap.getPixels(pixels, 0, originalBitmap.width, 0, 0, originalBitmap.width, originalBitmap.height)

        val chalkboardColor = Color.rgb(11, 46, 26) // 黒板の緑色

        for (i in pixels.indices) {
            if (pixels[i] == chalkboardColor) {
                pixels[i] = Color.TRANSPARENT
            }
        }

        transparentBitmap.setPixels(pixels, 0, originalBitmap.width, 0, 0, originalBitmap.width, originalBitmap.height)
        return transparentBitmap
    }

    private fun drawCircle(bitmap: Bitmap, point: Offset, radius: Float, color: Int) {
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            this.color = color
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        canvas.drawCircle(point.x, point.y, radius, paint)
    }

}
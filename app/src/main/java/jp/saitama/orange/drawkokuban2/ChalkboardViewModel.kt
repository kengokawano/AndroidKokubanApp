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
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import java.io.File
import java.io.FileOutputStream

enum class PenColor {
    WHITE,
    RED
}



data class ChalkboardState(
    val penColor: PenColor = PenColor.WHITE,
    val isThick: Boolean = false,
    val isEraser: Boolean = false,
    val bitmap: Bitmap? = null,
    // Image再描画を促すための変更通知用カウンタ。bitmapは破壊的に書き換えるためインスタンスは変わらない
    val drawVersion: Int = 0,
    val isDrawing: Boolean = false,
    val showClearAllDialog: Boolean = false,
    val isLoading: Boolean = true,
    // ピン留めメモ（ビットマップとは別レイヤー。保存/書き出し時にだけ重ねて描く）
    val memos: List<PinnedMemo> = emptyList()
)

class ChalkboardViewModel : ViewModel() {

    var state by mutableStateOf(ChalkboardState())
        private set

    private var lastDrawnPoint: Offset? = null
    private var nextMemoId: Long = 1L
    // 速度可変ストローク用：イベント間で太さの連続性を保つキャリーオーバー
    private var lastVelocityMult: Float = 1f

    fun initializeBitmap(width: Int, height: Int, context: Context? = null) {
        android.util.Log.d("ChalkboardViewModel", "initializeBitmap called with context: $context")
        val bitmap = createChalkboardBitmap(width, height)
        fillChalkboardBackground(bitmap, context)
        state = state.copy(bitmap = bitmap, isLoading = false, memos = emptyList())
    }

    fun createNewBitmap(context: Context? = null) {
        state.bitmap?.let { currentBitmap ->
            val newBitmap = createChalkboardBitmap(currentBitmap.width, currentBitmap.height)
            fillChalkboardBackground(newBitmap, context)
            state = state.copy(bitmap = newBitmap, isLoading = false, memos = emptyList())
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

    fun startDrawing(point: Offset, context: Context) {
        val bitmap = state.bitmap ?: return
        lastDrawnPoint = point
        lastVelocityMult = 1f

        // Down時点で1点描画。タップだけでも点が残り、小さい文字の始点もズレない
        if (state.isEraser) {
            drawCircle(bitmap, point, getEraserRadius(context), AppColors.CHALKBOARD.toArgb())
        } else {
            val color = when (state.penColor) {
                PenColor.WHITE -> Color.WHITE
                PenColor.RED -> AppColors.RED.toArgb()
            }
            drawCircle(bitmap, point, getPenThickness(context) / 2f, color)
        }

        state = state.copy(
            isDrawing = true,
            drawVersion = state.drawVersion + 1
        )
    }

    fun continueDrawing(points: List<Offset>, context: Context) {
        if (!state.isDrawing) return
        if (points.isEmpty()) return
        val bitmap = state.bitmap ?: return

        val starting = lastDrawnPoint ?: points.first()
        val segment = ArrayList<Offset>(points.size + 1)
        segment.add(starting)
        var prev = starting
        for (p in points) {
            // サブピクセル重複は除外（描画負荷軽減）
            val dx = p.x - prev.x
            val dy = p.y - prev.y
            if (dx * dx + dy * dy < 1f) continue
            segment.add(p)
            prev = p
        }
        if (segment.size < 2) return

        if (state.isEraser) {
            erasePath(bitmap, segment, getEraserRadius(context))
        } else {
            val color = when (state.penColor) {
                PenColor.WHITE -> Color.WHITE
                PenColor.RED -> AppColors.RED.toArgb()
            }
            val thickness = getPenThickness(context)
            // 設定で速度可変ストローク（チョーク風）の有効/無効を切り替え
            if (isVelocityVariableStrokeEnabled(context)) {
                lastVelocityMult = drawStrokeVariable(
                    bitmap, segment, color, thickness, lastVelocityMult
                )
            } else {
                drawStroke(bitmap, segment, color, thickness)
            }
        }

        lastDrawnPoint = prev
        state = state.copy(drawVersion = state.drawVersion + 1)
    }

    fun endDrawing() {
        state = state.copy(isDrawing = false)
        lastDrawnPoint = null
        lastVelocityMult = 1f
    }

    fun clearAll(context: Context? = null) {
        val bitmap = state.bitmap ?: return
        clearAll(bitmap, context)
        state = state.copy(
            showClearAllDialog = false,
            penColor = PenColor.WHITE,
            isEraser = false,
            // メモも一緒に消す
            memos = emptyList()
        )
    }

    /**
     * クリップボードの内容をピン留めメモとして貼り付ける。
     * 貼り付けられた場合のみ true。
     */
    fun pasteMemoFromClipboard(context: Context, position: Offset): Boolean {
        val bitmap = state.bitmap ?: return false
        val text = readClipboardText(context) ?: return false

        val memoBitmap = createMemoBitmap(context, text, bitmap.width)
        // タップ位置を中心に置き、キャンバスからはみ出さないように収める
        val x = (position.x - memoBitmap.width / 2f)
            .coerceIn(0f, maxOf(0f, (bitmap.width - memoBitmap.width).toFloat()))
        val y = (position.y - memoBitmap.height / 2f)
            .coerceIn(0f, maxOf(0f, (bitmap.height - memoBitmap.height).toFloat()))

        val memo = PinnedMemo(
            id = nextMemoId++,
            text = text,
            bitmap = memoBitmap,
            x = x,
            y = y
        )
        state = state.copy(memos = state.memos + memo)
        return true
    }

    /** 指定座標にあるメモのID（上に載っているものを優先）。無ければ null。 */
    fun hitTestMemo(point: Offset): Long? =
        state.memos.lastOrNull { it.contains(point.x, point.y) }?.id

    /** メモを相対移動する。キャンバス外にはみ出さないように収める。 */
    fun moveMemo(id: Long, dx: Float, dy: Float) {
        val bitmap = state.bitmap ?: return
        val memos = state.memos.map { memo ->
            if (memo.id != id) {
                memo
            } else {
                memo.copy(
                    x = (memo.x + dx)
                        .coerceIn(0f, maxOf(0f, (bitmap.width - memo.width).toFloat())),
                    y = (memo.y + dy)
                        .coerceIn(0f, maxOf(0f, (bitmap.height - memo.height).toFloat()))
                )
            }
        }
        state = state.copy(memos = memos)
    }

    /** メモを重ねた状態のビットマップを返す。メモが無ければ元のビットマップをそのまま返す。 */
    private fun flattenWithMemos(base: Bitmap): Bitmap {
        if (state.memos.isEmpty()) return base

        val flattened = base.copy(Bitmap.Config.ARGB_8888, true) ?: return base
        val canvas = Canvas(flattened)
        state.memos.forEach { memo ->
            canvas.drawBitmap(memo.bitmap, memo.x, memo.y, null)
        }
        return flattened
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
            // メモを重ねた状態で保存する
            val flattened = flattenWithMemos(bitmap)
            val result = try {
                savePng(flattened, file)
            } finally {
                if (flattened !== bitmap) flattened.recycle()
            }
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
            state = state.copy(bitmap = bitmap, isLoading = false, memos = emptyList())
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

        // メモも同じだけずらし、新しいキャンバスの中に収める
        val movedMemos = state.memos.map { memo ->
            memo.copy(
                x = (memo.x + left).coerceIn(0f, maxOf(0f, (width - memo.width).toFloat())),
                y = (memo.y + top).coerceIn(0f, maxOf(0f, (height - memo.height).toFloat()))
            )
        }

        state = state.copy(bitmap = newBitmap, isLoading = false, memos = movedMemos)
    }

    fun createThumbnail(maxW: Int = 200, maxH: Int = 150): Bitmap? {
        val bitmap = state.bitmap ?: return null
        val flattened = flattenWithMemos(bitmap)
        return try {
            makeThumbnail(flattened, maxW, maxH)
        } finally {
            if (flattened !== bitmap) flattened.recycle()
        }
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

    private fun isVelocityVariableStrokeEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        return prefs.getBoolean("pen_velocity_variable", false)
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
                    // メモを重ねた状態で書き出す
                    val flattened = flattenWithMemos(bitmap)
                    val exportBitmap = if (exportWithBackground) {
                        // 背景ありの場合はそのまま保存
                        flattened
                    } else {
                        // 背景を透過させる場合
                        createTransparentBitmap(flattened)
                    }
                    try {
                        exportBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    } finally {
                        if (exportBitmap !== flattened) exportBitmap.recycle()
                        if (flattened !== bitmap) flattened.recycle()
                    }
                }
                true
            } ?: false
        } catch (e: Exception) {
            Log.e("ChalkboardViewModel", "Export failed", e)
            false
        }
    }

    fun createClipboardPngUri(context: Context): Uri? {
        val bitmap = state.bitmap ?: return null
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val exportWithBackground = prefs.getBoolean("export_with_background", true)
        val shareDirectory = File(context.cacheDir, "shared_images")
        val shareFile = File(shareDirectory, "latest_chalkboard.png")

        return try {
            if (!shareDirectory.exists() && !shareDirectory.mkdirs()) {
                return null
            }

            // メモを重ねた状態でクリップボードへ渡す
            val flattened = flattenWithMemos(bitmap)
            val exportBitmap = if (exportWithBackground) {
                flattened
            } else {
                createTransparentBitmap(flattened)
            }

            try {
                FileOutputStream(shareFile).use { outputStream ->
                    if (!exportBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)) {
                        throw IllegalStateException("PNG compression failed")
                    }
                }
            } finally {
                if (exportBitmap !== flattened) {
                    exportBitmap.recycle()
                }
                if (flattened !== bitmap) {
                    flattened.recycle()
                }
            }

            FileProvider.getUriForFile(
                context,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                shareFile
            )
        } catch (e: Exception) {
            shareFile.delete()
            Log.e("ChalkboardViewModel", "Clipboard PNG creation failed", e)
            null
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

        for (i in pixels.indices) {
            if (pixels[i] == AppColors.CHALKBOARD.toArgb()) {
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

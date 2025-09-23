package jp.saitama.orange.drawkokuban2

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
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
    val showClearAllDialog: Boolean = false
)

class ChalkboardViewModel : ViewModel() {

    var state by mutableStateOf(ChalkboardState())
        private set

    private var currentPath = mutableListOf<Offset>()

    fun initializeBitmap(width: Int, height: Int, context: Context? = null) {
        android.util.Log.d("ChalkboardViewModel", "initializeBitmap called with context: $context")
        val bitmap = createChalkboardBitmap(width, height)
        fillChalkboardBackground(bitmap, context)
        state = state.copy(bitmap = bitmap)
    }

    fun createNewBitmap(context: Context? = null) {
        state.bitmap?.let { currentBitmap ->
            val newBitmap = createChalkboardBitmap(currentBitmap.width, currentBitmap.height)
            fillChalkboardBackground(newBitmap, context)
            state = state.copy(bitmap = newBitmap)
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

    fun continueDrawing(point: Offset) {
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
                erasePath(bitmap, currentPath.takeLast(2), 48f)
            } else {
                val color = when (state.penColor) {
                    PenColor.WHITE -> Color.WHITE
                    PenColor.RED -> Color.RED
                }
                val thickness = if (state.isThick) 18f else 6f
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

    fun clearAll(context: Context? = null) {
        val bitmap = state.bitmap ?: return
        clearAll(bitmap, context)
        state = state.copy(showClearAllDialog = false)
    }

    fun saveBitmap(context: Context, slotNumber: Int? = null): SlotMeta? {
        val bitmap = state.bitmap ?: return null

        // スロット番号が指定されていない場合は、空きスロットを探す
        val targetSlot = slotNumber ?: findNextAvailableSlot(context)

        val file = File(context.filesDir, "chalkboard_$targetSlot.png")
        return savePng(bitmap, file)
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
            // ロードしたビットマップに木目背景を適用
            fillChalkboardBackground(bitmap, context)
            state = state.copy(bitmap = bitmap)
        }
    }

    fun createThumbnail(maxW: Int = 200, maxH: Int = 150): Bitmap? {
        val bitmap = state.bitmap ?: return null
        return makeThumbnail(bitmap, maxW, maxH)
    }
}
package jp.saitama.orange.drawkokuban2

import androidx.compose.ui.graphics.Color

/**
 * アプリ全体で使用するカラーパレット
 */
object AppColors {
    // 赤色（手書き・ゲーム共通）
    val RED = Color(247, 171, 173)

    // その他の色（今後追加予定）
    val WHITE = Color.White
    val BOARD_BACKGROUND = Color(0xFF0F3D20)
    val MAIN_BACKGROUND = Color(0xFF0B2E1A)

    // 勝利時のハイライト色
    val WINNING_WHITE = Color(0xFFFFFFAA) // 黄色がかった白
    val WINNING_RED = RED.copy(alpha = 0.8f) // 少し透明にした赤

    // UI用の色
    val VALID_MOVE_HIGHLIGHT = Color(0x40FFFF00) // 薄い黄色
    val GRID_LINE = Color(0x40FFFFFF) // 薄い白
    val BOTTOM_LINE_HIGHLIGHT = Color(0xFFFFFFFF) // 完全な白
}
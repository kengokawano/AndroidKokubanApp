package jp.saitama.orange.drawkokuban2

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ChalkboardScreen(
    viewModel: ChalkboardViewModel = viewModel()
) {
    val context = LocalContext.current
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B2E1A))
    ) {
        // ツールバー
        @OptIn(ExperimentalMaterial3Api::class)
        TopAppBar(
            title = { Text(stringResource(R.string.app_name), color = Color.White) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xFF0B2E1A)
            )
        )

        // ツール選択
        ToolSelector(
            penColor = viewModel.state.penColor,
            isThick = viewModel.state.isThick,
            isEraser = viewModel.state.isEraser,
            onColorSelected = { viewModel.selectPenColor(it) },
            onThicknessToggled = { viewModel.toggleThickness() },
            onEraserSelected = { viewModel.selectEraser() },
            onClearAllRequested = { viewModel.showClearAllDialog() }
        )

        // キャンバス
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0B2E1A))
                .border(2.dp, Color.Gray, RoundedCornerShape(8.dp))
                .onGloballyPositioned { coordinates ->
                    canvasSize = Size(
                        coordinates.size.width.toFloat(),
                        coordinates.size.height.toFloat()
                    )
                    if (viewModel.state.bitmap == null && canvasSize.width > 0 && canvasSize.height > 0) {
                        viewModel.initializeBitmap(
                            canvasSize.width.toInt(),
                            canvasSize.height.toInt()
                        )
                    }
                }
                .pointerInput(viewModel.state.penColor, viewModel.state.isThick, viewModel.state.isEraser) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            viewModel.startDrawing(offset)
                        },
                        onDrag = { change, _ ->
                            viewModel.continueDrawing(change.position)
                        },
                        onDragEnd = {
                            viewModel.endDrawing()
                        }
                    )
                }
        ) {
            // ビットマップ表示
            viewModel.state.bitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "黒板",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 全消し確認ダイアログ
        if (viewModel.state.showClearAllDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.hideClearAllDialog() },
                title = { Text(stringResource(R.string.dialog_clear_all_title)) },
                text = { Text(stringResource(R.string.dialog_clear_all_message)) },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.clearAll() }
                    ) {
                        Text(stringResource(R.string.dialog_ok))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.hideClearAllDialog() }
                    ) {
                        Text(stringResource(R.string.dialog_cancel))
                    }
                }
            )
        }
    }
}

@Composable
fun ToolSelector(
    penColor: PenColor,
    isThick: Boolean,
    isEraser: Boolean,
    onColorSelected: (PenColor) -> Unit,
    onThicknessToggled: () -> Unit,
    onEraserSelected: () -> Unit,
    onClearAllRequested: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A3A2A)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 白ボタン
            ColorButton(
                text = stringResource(R.string.tool_white),
                isSelected = !isEraser && penColor == PenColor.WHITE,
                color = Color.White,
                onClick = { onColorSelected(PenColor.WHITE) }
            )

            // 赤ボタン
            ColorButton(
                text = stringResource(R.string.tool_red),
                isSelected = !isEraser && penColor == PenColor.RED,
                color = Color.Red,
                onClick = { onColorSelected(PenColor.RED) }
            )

            // 太さ切り替えボタン
            Button(
                onClick = onThicknessToggled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!isEraser) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .height(36.dp)
            ) {
                Text(
                    text = if (isThick) stringResource(R.string.tool_thick) else stringResource(R.string.tool_thin),
                    fontSize = 14.sp,
                    fontWeight = if (isThick) FontWeight.Bold else FontWeight.Normal
                )
            }

            // 消しゴムボタン
            Button(
                onClick = onEraserSelected,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isEraser) Color.Gray.copy(alpha = 0.3f) else Color.Transparent,
                    contentColor = Color.Gray
                ),
                border = if (isEraser) ButtonDefaults.outlinedButtonBorder.copy(
                    brush = SolidColor(Color.Gray)
                ) else null,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .height(36.dp)
            ) {
                Text(
                    text = stringResource(R.string.tool_eraser),
                    fontSize = 14.sp
                )
            }

            // 全消しボタン
            Button(
                onClick = onClearAllRequested,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red.copy(alpha = 0.3f),
                    contentColor = Color.Red
                ),
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .height(36.dp)
            ) {
                Text(
                    text = stringResource(R.string.tool_clear_all),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}


@Composable
fun ColorButton(
    text: String,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) color.copy(alpha = 0.3f) else Color.Transparent,
            contentColor = color
        ),
        border = if (isSelected) ButtonDefaults.outlinedButtonBorder.copy(
            brush = SolidColor(color)
        ) else null,
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .height(36.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
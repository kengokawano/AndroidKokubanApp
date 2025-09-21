package jp.saitama.orange.drawkokuban2

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Save
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

enum class Screen {
    CHALKBOARD,
    FILE_MANAGER
}

enum class EditMode {
    NEW,      // 新規作成モード（空きスロットに保存）
    EDIT      // 編集モード（指定スロットに保存）
}

@Composable
fun AppNavigation() {
    var currentScreen by remember { mutableStateOf(Screen.CHALKBOARD) }
    var currentSlot by remember { mutableStateOf<Int?>(null) }
    var editMode by remember { mutableStateOf(EditMode.NEW) }
    val viewModel: ChalkboardViewModel = viewModel()
    val context = LocalContext.current

    when (currentScreen) {
        Screen.CHALKBOARD -> {
            ChalkboardScreenWithControls(
                viewModel = viewModel,
                currentSlot = currentSlot,
                editMode = editMode,
                onNavigateToFileManager = {
                    currentScreen = Screen.FILE_MANAGER
                },
                onSave = {
                    when (editMode) {
                        EditMode.NEW -> viewModel.saveBitmap(context)
                        EditMode.EDIT -> currentSlot?.let { viewModel.saveBitmap(context, it) }
                    }
                },
                onNewFile = {
                    editMode = EditMode.NEW
                    currentSlot = null
                    viewModel.createNewBitmap()
                }
            )
        }
        Screen.FILE_MANAGER -> {
            FileManagerScreen(
                onFileSelected = { slotNumber ->
                    currentSlot = slotNumber
                    editMode = EditMode.EDIT
                    val file = java.io.File(context.filesDir, "chalkboard_$slotNumber.png")
                    if (file.exists()) {
                        viewModel.loadBitmap(context, slotNumber)
                    } else {
                        viewModel.createNewBitmap()
                    }
                    currentScreen = Screen.CHALKBOARD
                },
                onBackPressed = {
                    currentScreen = Screen.CHALKBOARD
                }
            )
        }
    }
}

@Composable
fun ChalkboardScreenWithControls(
    viewModel: ChalkboardViewModel,
    currentSlot: Int?,
    editMode: EditMode,
    onNavigateToFileManager: () -> Unit,
    onSave: () -> Unit,
    onNewFile: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // メインの黒板画面
        ChalkboardScreenContent(viewModel = viewModel)

        // フローティングアクションボタン群
        FloatingActionButtons(
            modifier = Modifier.align(Alignment.BottomEnd),
            onSave = onSave,
            onNavigateToFileManager = onNavigateToFileManager,
            onNewFile = onNewFile
        )

        // 現在のスロット番号表示
        Card(
            modifier = Modifier.align(Alignment.TopEnd),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1A3A2A).copy(alpha = 0.8f)
            )
        ) {
            Text(
                text = when (editMode) {
                    EditMode.NEW -> "新規作成"
                    EditMode.EDIT -> "スロット ${currentSlot ?: "?"}"
                },
                color = Color.White,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChalkboardScreenContent(viewModel: ChalkboardViewModel) {
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
            title = { Text("黒板太一2", color = Color.White) },
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
                title = { Text("全消し確認") },
                text = { Text("すべての内容を消去しますか？") },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.clearAll() }
                    ) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.hideClearAllDialog() }
                    ) {
                        Text("キャンセル")
                    }
                }
            )
        }
    }
}

@Composable
fun FloatingActionButtons(
    modifier: Modifier = Modifier,
    onSave: () -> Unit,
    onNavigateToFileManager: () -> Unit,
    onNewFile: () -> Unit
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 新規作成ボタン
        FloatingActionButton(
            onClick = onNewFile,
            containerColor = Color(0xFF1A3A2A)
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "新規作成",
                tint = Color.White
            )
        }

        // ファイル一覧ボタン
        FloatingActionButton(
            onClick = onNavigateToFileManager,
            containerColor = Color(0xFF1A3A2A)
        ) {
            Icon(
                Icons.Default.List,
                contentDescription = "ファイル一覧",
                tint = Color.White
            )
        }

        // 保存ボタン
        FloatingActionButton(
            onClick = onSave,
            containerColor = Color(0xFF1A3A2A)
        ) {
            Icon(
                Icons.Default.Save,
                contentDescription = "保存",
                tint = Color.White
            )
        }
    }
}
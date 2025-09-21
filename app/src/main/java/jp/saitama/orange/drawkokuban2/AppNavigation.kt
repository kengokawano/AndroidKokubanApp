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
import androidx.compose.ui.res.stringResource
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
    var showNewFileDialog by remember { mutableStateOf(false) }
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
                    showNewFileDialog = true
                },
                showNewFileDialog = showNewFileDialog,
                onNewFileConfirm = {
                    editMode = EditMode.NEW
                    currentSlot = null
                    viewModel.createNewBitmap()
                    showNewFileDialog = false
                },
                onNewFileCancel = {
                    showNewFileDialog = false
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
    onNewFile: () -> Unit,
    showNewFileDialog: Boolean,
    onNewFileConfirm: () -> Unit,
    onNewFileCancel: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // メインの黒板画面
        ChalkboardScreenContent(
            viewModel = viewModel,
            onNavigateToFileManager = onNavigateToFileManager,
            onSave = onSave,
            onNewFile = onNewFile
        )

        // 一覧ボタン（右上）
        FloatingActionButton(
            onClick = onNavigateToFileManager,
            containerColor = Color(0xFF1A3A2A),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 72.dp, end = 16.dp)
        ) {
            Icon(
                Icons.Default.List,
                contentDescription = stringResource(R.string.action_file_list),
                tint = Color.White
            )
        }

        // 保存ボタン（一覧ボタンの下）
        FloatingActionButton(
            onClick = onSave,
            containerColor = Color(0xFF1A3A2A),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 128.dp, end = 16.dp)
        ) {
            Icon(
                Icons.Default.Save,
                contentDescription = stringResource(R.string.action_save),
                tint = Color.White
            )
        }

        // 新規作成ボタン（保存ボタンの下）
        FloatingActionButton(
            onClick = onNewFile,
            containerColor = Color(0xFF1A3A2A),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 184.dp, end = 16.dp)
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.action_new),
                tint = Color.White
            )
        }

        // 現在のスロット番号表示（編集モードの時のみ）
        if (editMode == EditMode.EDIT) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 72.dp, start = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1A3A2A).copy(alpha = 0.8f)
                )
            ) {
                Text(
                    text = stringResource(R.string.mode_edit_slot, currentSlot ?: 0),
                    color = Color.White,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        // 新規作成確認ダイアログ
        if (showNewFileDialog) {
            AlertDialog(
                onDismissRequest = onNewFileCancel,
                title = { Text(stringResource(R.string.dialog_new_file_title)) },
                text = { Text(stringResource(R.string.dialog_new_file_message)) },
                confirmButton = {
                    TextButton(onClick = onNewFileConfirm) {
                        Text(stringResource(R.string.dialog_ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onNewFileCancel) {
                        Text(stringResource(R.string.dialog_cancel))
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChalkboardScreenContent(
    viewModel: ChalkboardViewModel,
    onNavigateToFileManager: () -> Unit,
    onSave: () -> Unit,
    onNewFile: () -> Unit
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
            ),
            actions = {
                // 一覧ボタン（ヘッダ右上）
                IconButton(onClick = onNavigateToFileManager) {
                    Icon(
                        Icons.Default.List,
                        contentDescription = stringResource(R.string.action_file_list),
                        tint = Color.White
                    )
                }
            }
        )

        // キャンバス
        Box(
            modifier = Modifier
                .weight(1f)
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
                    contentDescription = stringResource(R.string.content_desc_chalkboard),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // ツール選択（画面下部に移動）
        ToolSelector(
            penColor = viewModel.state.penColor,
            isThick = viewModel.state.isThick,
            isEraser = viewModel.state.isEraser,
            onColorSelected = { viewModel.selectPenColor(it) },
            onThicknessToggled = { viewModel.toggleThickness() },
            onEraserSelected = { viewModel.selectEraser() },
            onClearAllRequested = { viewModel.showClearAllDialog() }
        )

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


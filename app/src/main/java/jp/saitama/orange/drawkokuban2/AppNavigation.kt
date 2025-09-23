package jp.saitama.orange.drawkokuban2

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
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

enum class EditMode {
    EDIT      // 編集モード（指定スロットに保存）
}

@Composable
fun AppNavigation() {
    var currentSlot by remember { mutableStateOf<Int?>(null) }
    var editMode by remember { mutableStateOf(EditMode.EDIT) }
    val viewModel: ChalkboardViewModel = viewModel()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    // 起動時の初期化
    LaunchedEffect(Unit) {
        // SharedPreferencesから前回開いたスロット番号を取得
        val prefs = context.getSharedPreferences("chalkboard_prefs", android.content.Context.MODE_PRIVATE)
        val lastSlot = prefs.getInt("last_slot", 1)

        currentSlot = lastSlot
        editMode = EditMode.EDIT

        // 前回のファイルが存在するかチェック
        val file = java.io.File(context.filesDir, "chalkboard_$lastSlot.png")
        if (file.exists()) {
            viewModel.loadBitmap(context, lastSlot)
        } else {
            // スロット1をチェック
            val slot1File = java.io.File(context.filesDir, "chalkboard_1.png")
            if (slot1File.exists()) {
                currentSlot = 1
                viewModel.loadBitmap(context, 1)
            } else {
                // 何もない場合はスロット1で新規作成
                currentSlot = 1
                viewModel.createNewBitmap(context)
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(320.dp)
            ) {
                FileManagerScreen(
                    onFileSelected = { slotNumber ->
                        currentSlot = slotNumber
                        val file = java.io.File(context.filesDir, "chalkboard_$slotNumber.png")
                        if (file.exists()) {
                            viewModel.loadBitmap(context, slotNumber)
                        } else {
                            viewModel.createNewBitmap(context)
                        }
                        // 選択したスロットを記録
                        val prefs = context.getSharedPreferences("chalkboard_prefs", android.content.Context.MODE_PRIVATE)
                        prefs.edit().putInt("last_slot", slotNumber).apply()
                        scope.launch {
                            drawerState.close()
                        }
                    },
                    onBackPressed = {
                        scope.launch {
                            drawerState.close()
                        }
                    }
                )
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 木目テクスチャ背景
            WoodTextureBackground()

            // メイン描画画面
            ChalkboardScreenWithControls(
                viewModel = viewModel,
                currentSlot = currentSlot,
                editMode = editMode,
                snackbarHostState = snackbarHostState,
                onNavigateToFileManager = {
                    scope.launch {
                        drawerState.open()
                    }
                },
                onSave = {
                    currentSlot?.let { slot ->
                        val result = viewModel.saveBitmap(context, slot)
                        if (result != null) {
                            // 保存成功時にSnackbarを表示
                            scope.launch {
                                val job = launch {
                                    snackbarHostState.showSnackbar(
                                        message = "スロット${slot}に保存しました",
                                        duration = SnackbarDuration.Indefinite
                                    )
                                }
                                delay(1000) // 1秒後に消す
                                snackbarHostState.currentSnackbarData?.dismiss()
                            }
                            // 保存時に最後に開いたスロットを記録
                            val prefs = context.getSharedPreferences("chalkboard_prefs", android.content.Context.MODE_PRIVATE)
                            prefs.edit().putInt("last_slot", slot).apply()
                        }
                    }
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
    snackbarHostState: SnackbarHostState,
    onNavigateToFileManager: () -> Unit,
    onSave: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // メインの黒板画面
        ChalkboardScreenContent(
            viewModel = viewModel,
            onNavigateToFileManager = onNavigateToFileManager,
            onSave = onSave,
            editMode = editMode,
            currentSlot = currentSlot
        )

        // Snackbar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.Center)
        )
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChalkboardScreenContent(
    viewModel: ChalkboardViewModel,
    onNavigateToFileManager: () -> Unit,
    onSave: () -> Unit,
    editMode: EditMode,
    currentSlot: Int?
) {
    val context = LocalContext.current
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // ツールバー
        TopAppBar(
            title = {
                if (editMode == EditMode.EDIT) {
                    currentSlot?.let { slot ->
                        Text(TimeTableUtils.getHeaderTimeTableName(slot), color = Color.White)
                    } ?: Text(stringResource(R.string.app_name), color = Color.White)
                } else {
                    Text(stringResource(R.string.app_name), color = Color.White)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Black
            ),
            actions = {
                // 保存ボタン
                IconButton(onClick = onSave) {
                    Icon(
                        Icons.Default.Save,
                        contentDescription = stringResource(R.string.action_save),
                        tint = Color.White
                    )
                }
                // 一覧ボタン（一番右）
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
                .background(Color(0xFF0B2E1A)) // キャンバス部分は黒板色（緑）
                .border(2.dp, Color.Gray, RoundedCornerShape(8.dp))
                .onGloballyPositioned { coordinates ->
                    canvasSize = Size(
                        coordinates.size.width.toFloat(),
                        coordinates.size.height.toFloat()
                    )
                    if (viewModel.state.bitmap == null && canvasSize.width > 0 && canvasSize.height > 0) {
                        viewModel.initializeBitmap(
                            canvasSize.width.toInt(),
                            canvasSize.height.toInt(),
                            context
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

        // ツール選択（画面下部）
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
                        onClick = { viewModel.clearAll(context) }
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
fun WoodTextureBackground() {
    Image(
        painter = painterResource(R.drawable.wood_texture),
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop
    )
}
package jp.saitama.orange.drawkokuban2

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.*

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
    var refreshTrigger by remember { mutableStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    // 設定の状態管理
    var showDateOverlay by remember {
        mutableStateOf(
            context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
                .getBoolean("show_date_overlay", true)
        )
    }

    // ペンサイズ設定
    val prefs = context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
    var thinPenSize by remember { mutableStateOf(prefs.getFloat("thin_pen_size", 6f)) }
    var thickPenSize by remember { mutableStateOf(prefs.getFloat("thick_pen_size", 18f)) }
    var eraserRadius by remember { mutableStateOf(prefs.getFloat("eraser_radius", 48f)) }
    var exportWithBackground by remember { mutableStateOf(prefs.getBoolean("export_with_background", true)) }

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
                modifier = Modifier.width(400.dp)
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
                    },
                    refreshTrigger = refreshTrigger
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
                                    val slotName = DateTimeSlotUtils.getSlotName(slot, System.currentTimeMillis())
                                    snackbarHostState.showSnackbar(
                                        message = "${slotName}に保存しました",
                                        duration = SnackbarDuration.Indefinite
                                    )
                                }
                                delay(1000) // 1秒後に消す
                                snackbarHostState.currentSnackbarData?.dismiss()
                            }
                            // 保存時に最後に開いたスロットを記録
                            val prefs = context.getSharedPreferences("chalkboard_prefs", android.content.Context.MODE_PRIVATE)
                            prefs.edit().putInt("last_slot", slot).apply()
                            // ファイルマネージャーの更新をトリガー
                            refreshTrigger++
                        }
                    }
                },
                onShowSettings = { showSettings = true },
                onShowAbout = { showAbout = true },
                onExport = {
                    val success = viewModel.exportToPng(context)
                    scope.launch {
                        val message = if (success) {
                            context.getString(R.string.export_success)
                        } else {
                            context.getString(R.string.export_failed)
                        }
                        snackbarHostState.showSnackbar(
                            message = message,
                            duration = SnackbarDuration.Short
                        )
                    }
                },
                showDateOverlay = showDateOverlay,
                onDateOverlayChanged = { newValue ->
                    showDateOverlay = newValue
                    // SharedPreferencesに保存
                    context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("show_date_overlay", newValue)
                        .apply()
                }
            )
        }

        // 設定ダイアログ
        if (showSettings) {
            SettingsDialog(
                showDateOverlay = showDateOverlay,
                onDateOverlayChanged = { newValue ->
                    showDateOverlay = newValue
                    // SharedPreferencesに保存
                    context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("show_date_overlay", newValue)
                        .apply()
                },
                thinPenSize = thinPenSize,
                thickPenSize = thickPenSize,
                onThinPenSizeChanged = { newSize ->
                    thinPenSize = newSize
                    prefs.edit().putFloat("thin_pen_size", newSize).apply()
                },
                onThickPenSizeChanged = { newSize ->
                    thickPenSize = newSize
                    prefs.edit().putFloat("thick_pen_size", newSize).apply()
                },
                eraserRadius = eraserRadius,
                onEraserRadiusChanged = { newRadius ->
                    eraserRadius = newRadius
                    prefs.edit().putFloat("eraser_radius", newRadius).apply()
                },
                exportWithBackground = exportWithBackground,
                onExportWithBackgroundChanged = { newValue ->
                    exportWithBackground = newValue
                    prefs.edit().putBoolean("export_with_background", newValue).apply()
                },
                onDismiss = { showSettings = false }
            )
        }

        // Aboutダイアログ
        if (showAbout) {
            AboutDialog(
                onDismiss = { showAbout = false },
                onGameStart = {
                    val intent = Intent(context, GameActivity::class.java)
                    context.startActivity(intent)
                    showAbout = false
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
    onSave: () -> Unit,
    onShowSettings: () -> Unit,
    onShowAbout: () -> Unit,
    onExport: () -> Unit,
    showDateOverlay: Boolean,
    onDateOverlayChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    // ペンサイズ設定（ローカル）
    val prefs = context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
    var thinPenSize by remember { mutableStateOf(prefs.getFloat("thin_pen_size", 6f)) }
    var thickPenSize by remember { mutableStateOf(prefs.getFloat("thick_pen_size", 18f)) }
    var eraserRadius by remember { mutableStateOf(prefs.getFloat("eraser_radius", 48f)) }
    var exportWithBackground by remember { mutableStateOf(prefs.getBoolean("export_with_background", true)) }

    Box(modifier = Modifier.fillMaxSize()) {
        // メインの黒板画面
        ChalkboardScreenContent(
            viewModel = viewModel,
            onNavigateToFileManager = onNavigateToFileManager,
            onSave = onSave,
            editMode = editMode,
            currentSlot = currentSlot,
            onShowSettings = { showSettings = true },
            onShowAbout = { showAbout = true },
            onExport = onExport,
            showDateOverlay = showDateOverlay
        )

        // Snackbar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.Center)
        )
    }

    // 設定ダイアログ
    if (showSettings) {
        SettingsDialog(
            showDateOverlay = showDateOverlay,
            onDateOverlayChanged = { newValue -> onDateOverlayChanged(newValue) },
            thinPenSize = thinPenSize,
            thickPenSize = thickPenSize,
            onThinPenSizeChanged = { newSize ->
                thinPenSize = newSize
                prefs.edit().putFloat("thin_pen_size", newSize).apply()
            },
            onThickPenSizeChanged = { newSize ->
                thickPenSize = newSize
                prefs.edit().putFloat("thick_pen_size", newSize).apply()
            },
            eraserRadius = eraserRadius,
            onEraserRadiusChanged = { newRadius ->
                eraserRadius = newRadius
                prefs.edit().putFloat("eraser_radius", newRadius).apply()
            },
            exportWithBackground = exportWithBackground,
            onExportWithBackgroundChanged = { newValue ->
                exportWithBackground = newValue
                prefs.edit().putBoolean("export_with_background", newValue).apply()
            },
            onDismiss = { showSettings = false }
        )
    }

    // Aboutダイアログ
    if (showAbout) {
        AboutDialog(
            onDismiss = { showAbout = false },
            onGameStart = {
                val intent = Intent(context, GameActivity::class.java)
                context.startActivity(intent)
                showAbout = false
            }
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
    currentSlot: Int?,
    onShowSettings: () -> Unit,
    onShowAbout: () -> Unit,
    onExport: () -> Unit,
    showDateOverlay: Boolean
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
                        // 現在のファイルの保存日時を取得
                        val file = java.io.File(context.filesDir, "chalkboard_$slot.png")
                        val headerText = if (file.exists()) {
                            DateTimeSlotUtils.getHeaderSlotName(slot, file.lastModified())
                        } else {
                            "新規作成"
                        }
                        Text(
                            headerText,
                            color = Color.White,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                        )
                    } ?: Text(
                        stringResource(R.string.app_name),
                        color = Color.White,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.graphicsLayer(scaleX = 0.7f)
                    )
                } else {
                    Text(
                        stringResource(R.string.app_name),
                        color = Color.White,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.graphicsLayer(scaleX = 0.7f)
                    )
                }
            },
            navigationIcon = {
                // 一覧ボタン（一番左）
                IconButton(onClick = onNavigateToFileManager) {
                    Icon(
                        Icons.Default.List,
                        contentDescription = stringResource(R.string.action_file_list),
                        tint = Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Black
            ),
            actions = {
                Row {
                    // Aboutボタン（右寄せ）
                    IconButton(
                        onClick = onShowAbout,
                        modifier = Modifier.offset(x = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "About",
                            tint = Color.White
                        )
                    }
                    // 設定ボタン（Aboutと詰める）
                    IconButton(
                        onClick = onShowSettings,
                        modifier = Modifier.offset(x = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White
                        )
                    }
                    // PNG Exportボタン
                    IconButton(
                        onClick = onExport,
                        modifier = Modifier.offset(x = 0.dp)
                    ) {
                        Icon(
                            Icons.Default.FileDownload,
                            contentDescription = "PNG Export",
                            tint = Color.White
                        )
                    }
                    // 大きめのスペーサー（保存との間）
                    Spacer(modifier = Modifier.width(16.dp))
                    // 保存ボタン
                    IconButton(onClick = onSave) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = stringResource(R.string.action_save),
                            tint = Color.White
                        )
                    }
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
                    val newSize = Size(
                        coordinates.size.width.toFloat(),
                        coordinates.size.height.toFloat()
                    )
                    val currentBitmap = viewModel.state.bitmap

                    // 新しいサイズが有効で、現在のBitmapがないか、サイズが異なる場合に初期化する
                    if (newSize.width > 0 && newSize.height > 0 &&
                        (currentBitmap == null ||
                                currentBitmap.width != newSize.width.toInt() ||
                                currentBitmap.height != newSize.height.toInt())) {

                        canvasSize = newSize
                        viewModel.initializeBitmap(
                            newSize.width.toInt(),
                            newSize.height.toInt(),
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
                            viewModel.continueDrawing(change.position, context)
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
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds // サイズに合わせてスケール
                )
            } ?: run {
                // Bitmapがない場合は空のスペースを表示
                Spacer(modifier = Modifier.fillMaxSize())
            }

            // 日付表示（右上オーバーレイ）
            if (showDateOverlay) {
                DateOverlay(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 16.dp, end = 8.dp)
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

@Composable
fun DateOverlay(modifier: Modifier = Modifier) {
    val today = Calendar.getInstance()
    val japaneseMonth = getJapaneseMonth(today.get(Calendar.MONTH) + 1)
    val day = today.get(Calendar.DAY_OF_MONTH)
    val dayOfWeek = getJapaneseDayOfWeek(today.get(Calendar.DAY_OF_WEEK))

    // 縦書き1行表示（括弧部分は横並び）
    val beforeParen = "${japaneseMonth}${getJapaneseNumber(day)}日"
    val parenPart = "（$dayOfWeek）"
    val afterParen = "日直　XX"

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 括弧前の文字を縦に表示
        beforeParen.forEach { char ->
            Text(
                text = char.toString(),
                color = Color.White,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }

        // 括弧部分を横並びで表示
        Text(
            text = parenPart,
            color = Color.White,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        // 括弧後の文字を縦に表示
        afterParen.forEach { char ->
            Text(
                text = char.toString(),
                color = Color.White,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun getJapaneseMonth(month: Int): String {
    val months = arrayOf("一月", "二月", "三月", "四月", "五月", "六月",
                        "七月", "八月", "九月", "十月", "十一月", "十二月")
    return if (month in 1..12) months[month - 1] else "？月"
}

private fun getJapaneseDayOfWeek(dayOfWeek: Int): String {
    return when (dayOfWeek) {
        Calendar.SUNDAY -> "日"
        Calendar.MONDAY -> "月"
        Calendar.TUESDAY -> "火"
        Calendar.WEDNESDAY -> "水"
        Calendar.THURSDAY -> "木"
        Calendar.FRIDAY -> "金"
        Calendar.SATURDAY -> "土"
        else -> "？"
    }
}

private fun getJapaneseNumber(number: Int): String {
    val ones = arrayOf("", "一", "二", "三", "四", "五", "六", "七", "八", "九")
    val tens = arrayOf("", "十", "二十", "三十")

    return when {
        number == 10 -> "十"
        number < 10 -> ones[number]
        number < 20 -> "十${ones[number % 10]}"
        number < 40 -> "${tens[number / 10]}${if (number % 10 != 0) ones[number % 10] else ""}"
        else -> number.toString()
    }
}

@Composable
fun SettingsDialog(
    showDateOverlay: Boolean,
    onDateOverlayChanged: (Boolean) -> Unit,
    thinPenSize: Float,
    thickPenSize: Float,
    onThinPenSizeChanged: (Float) -> Unit,
    onThickPenSizeChanged: (Float) -> Unit,
    eraserRadius: Float,
    onEraserRadiusChanged: (Float) -> Unit,
    exportWithBackground: Boolean,
    onExportWithBackgroundChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("設定")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "アプリケーション設定",
                    fontSize = 18.sp,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(16.dp))

                // ペン設定セクション
                Text(
                    "描画設定",
                    fontSize = 16.sp,
                    color = Color.DarkGray
                )
                Spacer(modifier = Modifier.height(8.dp))

                // 細いペンサイズ設定
                Text("細いペンサイズ: ${thinPenSize.toInt()}")
                Slider(
                    value = thinPenSize,
                    onValueChange = onThinPenSizeChanged,
                    valueRange = 2f..12f,
                    steps = 9,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 太いペンサイズ設定
                Text("太いペンサイズ: ${thickPenSize.toInt()}")
                Slider(
                    value = thickPenSize,
                    onValueChange = onThickPenSizeChanged,
                    valueRange = 12f..36f,
                    steps = 23,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 黒板消しサイズ設定
                Text("黒板消しの円の広さ: ${eraserRadius.toInt()}")
                Slider(
                    value = eraserRadius,
                    onValueChange = onEraserRadiusChanged,
                    valueRange = 24f..96f,
                    steps = 35,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Spacer(modifier = Modifier.height(16.dp))

                // 表示設定セクション
                Text(
                    "表示設定",
                    fontSize = 16.sp,
                    color = Color.DarkGray
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 日付表示切り替え
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("右上の日付表示")
                    Switch(
                        checked = showDateOverlay,
                        onCheckedChange = onDateOverlayChanged
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("• 木目背景の変更（予定）")
                Text("• テーマ変更（予定）")

                Spacer(modifier = Modifier.height(16.dp))

                // ファイル設定セクション
                Text(
                    "ファイル設定",
                    fontSize = 16.sp,
                    color = Color.DarkGray
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Export時の背景設定
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("PNG Export時の背景")
                    Switch(
                        checked = exportWithBackground,
                        onCheckedChange = onExportWithBackgroundChanged
                    )
                }
                Text(
                    text = if (exportWithBackground) "緑の背景色でエクスポート" else "透明な背景でエクスポート",
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text("• 自動保存機能（予定）")
                Text("• バックアップ機能（予定）")

            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる")
            }
        }
    )
}

@Composable
fun AboutDialog(
    onDismiss: () -> Unit,
    onGameStart: () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFF0B2E1A),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("黒板太一2について")
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // アプリ基本情報
                Text(
                    "黒板太一2",
                    fontSize = 20.sp,
                    color = Color(0xFF0B2E1A)
                )
                Text(
                    "Chalkboard Taichi 2",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    "デジタル黒板描画アプリケーション",
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(16.dp))

                // 隠しゲーム起動ボタン
                Button(
                    onClick = onGameStart,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0B2E1A)
                    )
                ) {
                    Text(
                        "黒板大将",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // バージョン情報
                Text(
                    "アプリ情報",
                    fontSize = 16.sp,
                    color = Color.DarkGray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("バージョン: 2.0.0")
                Text("ビルド日: 2024年9月24日")
                Text("対応OS: Android 8.0以上")

                Spacer(modifier = Modifier.height(16.dp))

                // 主な機能
                Text(
                    "主な機能",
                    fontSize = 16.sp,
                    color = Color.DarkGray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("✓ 白・赤チョークでの自然な描画")
                Text("✓ 太い・細いペン切り替え")
                Text("✓ 消しゴム・全消し機能")
                Text("✓ 30スロットファイル管理")
                Text("✓ 日付時間ベース命名")
                Text("✓ 多言語対応（日本語・英語）")
                Text("✓ 木目テクスチャ背景")

                Spacer(modifier = Modifier.height(16.dp))

                // 開発者情報
                Text(
                    "開発者情報",
                    fontSize = 16.sp,
                    color = Color.DarkGray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("開発者: Orange Saitama")
                Text("技術: Kotlin, Jetpack Compose")
                Text("© 2024 Orange Saitama")

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "このアプリは教育現場での利用を想定して開発されました。",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる")
            }
        }
    )
}
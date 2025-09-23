package jp.saitama.orange.drawkokuban2

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class SlotData(
    val slotNumber: Int,
    val file: File?,
    val lastModified: Long?,
    val thumbnail: android.graphics.Bitmap?
) {
    val isEmpty: Boolean get() = file == null || !file.exists()
}

// 時間割形式のスロット名称ユーティリティ
object TimeTableUtils {
    private val dayNames = arrayOf("月", "火", "水", "木", "金", "土")

    fun getTimeTableName(slotNumber: Int): String {
        if (slotNumber < 1 || slotNumber > 30) return "不明"

        val dayIndex = (slotNumber - 1) / 5  // 0-5 (月-土)
        val period = (slotNumber - 1) % 5 + 1  // 1-5時間目

        return "（${dayNames[dayIndex]}）${period}時間目"
    }

    fun getShortTimeTableName(slotNumber: Int): String {
        if (slotNumber < 1 || slotNumber > 30) return "?"

        val dayIndex = (slotNumber - 1) / 5
        val period = (slotNumber - 1) % 5 + 1

        return "${dayNames[dayIndex]}${period}"
    }

    fun getHeaderTimeTableName(slotNumber: Int): String {
        if (slotNumber < 1 || slotNumber > 30) return "不明"

        val dayIndex = (slotNumber - 1) / 5  // 0-5 (月-土)
        val period = (slotNumber - 1) % 5 + 1  // 1-5時間目

        return "${dayNames[dayIndex]}曜日　${period}時間目"
    }
}

@Composable
fun FileManagerScreen(
    onFileSelected: (Int) -> Unit,
    onBackPressed: () -> Unit,
    viewModel: ChalkboardViewModel = viewModel()
) {
    val context = LocalContext.current
    var slots by remember { mutableStateOf<List<SlotData>>(emptyList()) }
    var showDeleteDialog by remember { mutableStateOf<Int?>(null) }

    // 30個のスロットを初期化（月〜土の1〜5時間目）
    LaunchedEffect(Unit) {
        val slotList = mutableListOf<SlotData>()
        for (i in 1..30) {
            val file = File(context.filesDir, "chalkboard_$i.png")
            val thumbnail = if (file.exists()) {
                val bitmap = loadPng(file)
                bitmap?.let { makeThumbnail(it, 200, 150) }
            } else null

            slotList.add(
                SlotData(
                    slotNumber = i,
                    file = if (file.exists()) file else null,
                    lastModified = if (file.exists()) file.lastModified() else null,
                    thumbnail = thumbnail
                )
            )
        }
        slots = slotList
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B2E1A))
    ) {
        // トップバー
        @OptIn(ExperimentalMaterial3Api::class)
        TopAppBar(
            title = { Text(stringResource(R.string.file_manager_title), color = Color.White) },
            navigationIcon = {
                IconButton(onClick = onBackPressed) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xFF0B2E1A)
            ),
            actions = {}
        )

        // スロット一覧
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(slots) { slot ->
                SlotCard(
                    slot = slot,
                    onTap = { onFileSelected(slot.slotNumber) },
                    onLongPress = {
                        if (!slot.isEmpty) {
                            showDeleteDialog = slot.slotNumber
                        }
                    }
                )
            }
        }
    }

    // 削除確認ダイアログ
    showDeleteDialog?.let { slotNumber ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(stringResource(R.string.dialog_delete_title)) },
            text = { Text(stringResource(R.string.dialog_delete_message, TimeTableUtils.getTimeTableName(slotNumber))) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val file = File(context.filesDir, "chalkboard_$slotNumber.png")
                        file.delete()
                        // スロットリストを更新
                        slots = slots.map { slot ->
                            if (slot.slotNumber == slotNumber) {
                                slot.copy(
                                    file = null,
                                    lastModified = null,
                                    thumbnail = null
                                )
                            } else slot
                        }
                        showDeleteDialog = null
                    }
                ) {
                    Text(stringResource(R.string.dialog_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SlotCard(
    slot: SlotData,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (slot.isEmpty) Color(0xFF2A2A2A) else Color(0xFF1A3A2A)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (slot.isEmpty) {
                // 空のスロット
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        TimeTableUtils.getShortTimeTableName(slot.slotNumber),
                        color = Color.Gray,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        stringResource(R.string.file_empty_slot),
                        color = Color.Gray,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // データがあるスロット
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // サムネイル
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF0B2E1A))
                    ) {
                        slot.thumbnail?.let { thumbnail ->
                            Image(
                                bitmap = thumbnail.asImageBitmap(),
                                contentDescription = stringResource(R.string.content_desc_thumbnail),
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // スロット番号と日時
                    Column(
                        modifier = Modifier.padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            TimeTableUtils.getShortTimeTableName(slot.slotNumber),
                            color = Color.White,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                        slot.lastModified?.let {
                            Text(
                                SimpleDateFormat("MM/dd", Locale.getDefault())
                                    .format(Date(it)),
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
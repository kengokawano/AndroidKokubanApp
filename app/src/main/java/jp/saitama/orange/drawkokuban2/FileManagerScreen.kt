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
import androidx.compose.ui.layout.ContentScale
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

// 日付時間形式のスロット名称ユーティリティ
object DateTimeSlotUtils {

    fun getSlotName(slotNumber: Int, saveDate: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = saveDate

        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        return "${month}月${day}日${hour}時間目"
    }

    fun getShortSlotName(slotNumber: Int, saveDate: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = saveDate

        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        return "${month}/${day} ${hour}h"
    }

    fun getHeaderSlotName(slotNumber: Int, saveDate: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = saveDate

        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        return "${month}月${day}日　${hour}時間目"
    }
}

@Composable
fun FileManagerScreen(
    onFileSelected: (Int) -> Unit,
    onBackPressed: () -> Unit,
    viewModel: ChalkboardViewModel = viewModel(),
    refreshTrigger: Int = 0
) {
    val context = LocalContext.current
    var slots by remember { mutableStateOf<List<SlotData>>(emptyList()) }
    var showDeleteDialog by remember { mutableStateOf<Int?>(null) }

    // スロットリストを更新する関数
    fun updateSlots() {
        val slotList = mutableListOf<SlotData>()
        for (i in 1..30) {
            val file = File(context.filesDir, "chalkboard_$i.png")
            val thumbnail = if (file.exists()) {
                val bitmap = loadPng(file)
                bitmap?.let { makeThumbnail(it, 300, 225) }
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

    // 30個のスロットを初期化
    LaunchedEffect(Unit) {
        updateSlots()
    }

    // 保存後にファイルリストを更新
    LaunchedEffect(refreshTrigger) {
        updateSlots()
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
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
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
            text = {
                val slot = slots.find { it.slotNumber == slotNumber }
                val slotName = slot?.lastModified?.let {
                    DateTimeSlotUtils.getSlotName(slotNumber, it)
                } ?: "空き$slotNumber"
                Text(stringResource(R.string.dialog_delete_message, slotName))
            },
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
            .aspectRatio(1.2f)
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
                        "空き${slot.slotNumber}",
                        color = Color.Gray,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // データがあるスロット
                Box(modifier = Modifier.fillMaxSize()) {
                    // サムネイル（全画面）
                    slot.thumbnail?.let { thumbnail ->
                        Image(
                            bitmap = thumbnail.asImageBitmap(),
                            contentDescription = stringResource(R.string.content_desc_thumbnail),
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.FillBounds
                        )
                    } ?: Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0B2E1A))
                    )

                    // スロット名称（左上オーバーレイ）
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp),
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            slot.lastModified?.let {
                                DateTimeSlotUtils.getShortSlotName(slot.slotNumber, it)
                            } ?: "空き${slot.slotNumber}",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }

                    // 保存時間（右下オーバーレイ）
                    slot.lastModified?.let {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp),
                            color = Color.Black.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                SimpleDateFormat("HH:mm", Locale.getDefault())
                                    .format(Date(it)),
                                color = Color.Gray,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.model.TaskStatus
import com.example.model.TikTokVideoTask
import com.example.ui.theme.MyApplicationTheme
import com.example.utils.DownloadHelper
import com.example.utils.LinkParser
import com.example.viewmodel.TikTokViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    TikTokDownloaderScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TikTokDownloaderScreen(
    modifier: Modifier = Modifier,
    viewModel: TikTokViewModel = viewModel()
) {
    val context = LocalContext.current
    val inputText by viewModel.inputText.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val isResolving by viewModel.isResolving.collectAsState()
    val historyList by viewModel.historyList.collectAsState()

    // Active Tab state: 0 = Downloader (Queue), 1 = History
    var activeTab by remember { mutableStateOf(0) }

    // Init the Room database repository once the screen starts
    LaunchedEffect(Unit) {
        viewModel.initRepository(context)
    }

    // Detect the count of actual links from live text input
    val detectedLinksCount = remember(inputText) {
        LinkParser.extractLinks(inputText).size
    }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App Premium Header
        HeaderSection()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Raw paste input card
            InputCard(
                inputText = inputText,
                detectedCount = detectedLinksCount,
                onValueChange = { viewModel.onInputTextChanged(it) },
                onClean = { viewModel.cleanLinks() },
                onRefresh = { viewModel.refreshAll() },
                onAnalyze = { viewModel.analyzeAndFetch() },
                isResolving = isResolving
            )

            // Storage Folder Quick Banner
            StorageLocationCard()

            // Dynamic action banner for Bulk Download
            val successTasks = tasks.filter { it.status == TaskStatus.SUCCESS }
            if (activeTab == 0 && successTasks.isNotEmpty()) {
                BulkDownloadBanner(
                    successCount = successTasks.size,
                    totalCount = tasks.size,
                    onDownloadAll = { viewModel.downloadAllVideos(context) }
                )
            }

            // Tab Switcher Row using modern FilterChips
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    label = { 
                        Text(
                            text = "Trình tải về" + if (tasks.isNotEmpty()) " (${tasks.size})" else "",
                            fontWeight = FontWeight.Bold
                        ) 
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "Downloader Tab",
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                FilterChip(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    label = { 
                        Text(
                            text = "Lịch sử tải về" + if (historyList.isNotEmpty()) " (${historyList.size})" else "",
                            fontWeight = FontWeight.Bold
                        ) 
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "History Tab",
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }

            // Body Area based on selected Tab
            if (activeTab == 0) {
                TaskListSection(
                    tasks = tasks,
                    isResolving = isResolving,
                    onDownloadIcon = { task, isAudio ->
                        viewModel.downloadSingle(context, task, isAudio)
                    },
                    modifier = Modifier.weight(1f)
                )
            } else {
                HistoryListSection(
                    historyList = historyList,
                    onDownload = { id, downloadUrl, isAudio ->
                        // Build context model to download & append in Room
                        val matchedItem = historyList.find { it.id == id }
                        val task = TikTokVideoTask(
                            id = id,
                            originalUrl = matchedItem?.originalUrl ?: downloadUrl,
                            status = TaskStatus.SUCCESS,
                            title = matchedItem?.title,
                            coverUrl = matchedItem?.coverUrl,
                            videoPlayUrl = matchedItem?.videoPlayUrl,
                            musicUrl = matchedItem?.musicUrl,
                            authorUsername = matchedItem?.authorUsername
                        )
                        viewModel.downloadSingle(context, task, isAudio)
                    },
                    onDelete = { id ->
                        viewModel.deleteHistoryItem(id)
                    },
                    onClearAll = {
                        viewModel.clearAllHistory()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun HeaderSection() {
    val gradient = Brush.horizontalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary, // Neon Cyan
            MaterialTheme.colorScheme.secondary // Neon Red
        )
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 12.dp, start = 16.dp, end = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black)
                    .border(1.5.dp, gradient, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.jl_logo),
                    contentDescription = "JL Logo",
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            Column {
                Text(
                    text = "TikTok Bulk Downloader",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
                Text(
                    text = "Tải video hàng loạt không watermark & tách nhạc MP3",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                )
            }
        }
        Divider(
            modifier = Modifier.padding(top = 12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
            thickness = 1.dp
        )
    }
}

@Composable
fun InputCard(
    inputText: String,
    detectedCount: Int,
    onValueChange: (String) -> Unit,
    onClean: () -> Unit,
    onRefresh: () -> Unit,
    onAnalyze: () -> Unit,
    isResolving: Boolean
) {
    val context = LocalContext.current
    val clipboardManager = remember {
        context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = "Link Icon",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Nhập liên kết chia sẻ của bạn",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                if (detectedCount > 0) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text(
                            text = "Phát hiện: $detectedCount liên kết",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            // Textarea Editor
            OutlinedTextField(
                value = inputText,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                placeholder = {
                    Text(
                        text = "Dán các đoạn văn bản chia sẻ hoặc danh sách liên kết TikTok vào đây. Hệ thống sẽ tự lọc bỏ các ký tự thừa khác...",
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Default
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                    focusedContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            )

            // Row 1: Helper Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Clipboard paste Button
                Button(
                    onClick = {
                        val clip = clipboardManager.primaryClip
                        if (clip != null && clip.itemCount > 0) {
                            val pastedText = clip.getItemAt(0).text?.toString() ?: ""
                            if (pastedText.isNotBlank()) {
                                if (inputText.isBlank()) {
                                    onValueChange(pastedText)
                                } else {
                                    onValueChange(inputText + "\n" + pastedText)
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                        contentColor = MaterialTheme.colorScheme.secondary
                    ),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = "Paste Custom Icon",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Dán từ bộ nhớ",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Outlined Clean Button
                OutlinedButton(
                    onClick = onClean,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clean Icon",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Dọn liên kết",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            // Row 2: Main Commands
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Outlined Reset Button
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Icon",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Làm mới",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                // Primary Analyze Button
                Button(
                    onClick = onAnalyze,
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    enabled = inputText.isNotBlank() && !isResolving
                ) {
                    if (isResolving) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Analyze Icon",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Phân tích",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StorageLocationCard() {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = "Folder Icon",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Column {
                    Text(
                        text = "Vị trí lưu trữ video",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Thư mục Tải xuống > " + DownloadHelper.getStorageLocationDescription(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            TextButton(
                onClick = { DownloadHelper.openDownloadsFolder(context) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Launch,
                    contentDescription = "Open directory",
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Mở thư mục",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
fun BulkDownloadBanner(
    successCount: Int,
    totalCount: Int,
    onDownloadAll: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Parsed successful",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "Phân tích thành công!",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Tổng số $successCount / $totalCount liên kết khả dụng",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            Button(
                onClick = onDownloadAll,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DownloadForOffline,
                    contentDescription = "Bulk Download All Icon",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Tải toàn bộ",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
fun TaskListSection(
    tasks: List<TikTokVideoTask>,
    isResolving: Boolean,
    onDownloadIcon: (TikTokVideoTask, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Danh sách phân tích",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            if (tasks.isNotEmpty()) {
                Text(
                    text = "${tasks.size} liên kết",
                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                )
            }
        }

        if (tasks.isEmpty()) {
            EmptyListPlaceholder()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    TaskCardItem(
                        task = task,
                        onDownload = { isAudio -> onDownloadIcon(task, isAudio) }
                    )
                }
            }
        }
    }
}

@Composable
fun TaskCardItem(
    task: TikTokVideoTask,
    onDownload: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = 1.dp,
            color = when (task.status) {
                TaskStatus.RESOLVING -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                TaskStatus.SUCCESS -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                TaskStatus.FAILED -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                else -> Color.Transparent
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Video Preview Thumbnail / Image Loading
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (task.coverUrl != null) {
                        AsyncImage(
                            model = task.coverUrl,
                            contentDescription = "Thumbnail cover",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = when (task.status) {
                                TaskStatus.RESOLVING -> Icons.Default.CloudSync
                                TaskStatus.FAILED -> Icons.Default.ErrorOutline
                                else -> Icons.Default.Movie
                            },
                            contentDescription = "Status icon placeholder",
                            tint = when (task.status) {
                                TaskStatus.RESOLVING -> MaterialTheme.colorScheme.primary
                                TaskStatus.FAILED -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Text Description Panel
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = task.title ?: "Đang chờ liên kết để phân tích...",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    if (task.status == TaskStatus.SUCCESS && task.authorUsername != null) {
                        Text(
                            text = "@${task.authorUsername}" + (task.authorName?.let { " ($it)" } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = task.originalUrl,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Status pill indicator
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        when (task.status) {
                            TaskStatus.RESOLVING -> {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                )
                                Text(
                                    text = "Đang lấy link...",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                            TaskStatus.SUCCESS -> {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFF4CAF50), CircleShape)
                                )
                                Text(
                                    text = "Sẵn sàng",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(0xFF4CAF50),
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                            TaskStatus.FAILED -> {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(MaterialTheme.colorScheme.error, CircleShape)
                                )
                                Text(
                                    text = "Lỗi: " + (task.errorMessage ?: "Không tải được"),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TaskStatus.IDLE -> {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), CircleShape)
                                )
                                Text(
                                    text = "Chờ khởi chạy",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Expanded action buttons once successfully parsed
            if (task.status == TaskStatus.SUCCESS) {
                Spacer(modifier = Modifier.height(10.dp))
                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Video without watermark button
                    FilledTonalButton(
                        onClick = { onDownload(false) },
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = "No Watermark Video Icon",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Video (Sạch)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    // Music partition Button
                    if (task.musicUrl != null) {
                        FilledTonalButton(
                            onClick = { onDownload(true) },
                            modifier = Modifier.weight(0.9f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                                contentColor = MaterialTheme.colorScheme.secondary
                            ),
                            contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = "Audio extraction Icon",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Nhạc MP3",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyListPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .border(
                1.5.dp,
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.02f)
                    )
                ),
                RoundedCornerShape(16.dp)
            )
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DownloadForOffline,
                contentDescription = "Placeholder icon downloads",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                modifier = Modifier.size(44.dp)
            )
            Text(
                text = "Hộp tải trống",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Text(
                text = "Dán các chia sẻ TikTok phía trên và bấm 'Phân tích' để bắt đầu.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
            )
        }
    }
}

@Composable
fun HistoryListSection(
    historyList: List<com.example.data.HistoryEntity>,
    onDownload: (String, String, Boolean) -> Unit, // id, url, isAudio
    onDelete: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Lịch sử đã tải",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            if (historyList.isNotEmpty()) {
                TextButton(
                    onClick = onClearAll,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear all history",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Xoá tất cả",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        if (historyList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                        RoundedCornerShape(16.dp)
                    )
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.HistoryToggleOff,
                        contentDescription = "Empty history icon",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = "Chưa có lưu trữ nào",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "Vui lòng tải xuống một số video để xem lịch sử được lưu tự động.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(historyList, key = { it.id }) { item ->
                    HistoryCardItem(
                        item = item,
                        onDownload = { isAudio -> 
                            val downloadUrl = if (isAudio) item.musicUrl else item.videoPlayUrl
                            if (downloadUrl != null) {
                                onDownload(item.id, downloadUrl, isAudio)
                            }
                        },
                        onDelete = { onDelete(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryCardItem(
    item: com.example.data.HistoryEntity,
    onDownload: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cover Art Thumbnail
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.coverUrl != null) {
                        AsyncImage(
                            model = item.coverUrl,
                            contentDescription = "Thumbnail cover",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = "Default Icon",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Text details
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    if (item.authorUsername != null) {
                        Text(
                            text = "@${item.authorUsername}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Format Time: Time of download
                    val formattedTime = remember(item.timestamp) {
                        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
                        sdf.format(java.util.Date(item.timestamp))
                    }
                    Text(
                        text = "Đã tải: $formattedTime",
                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    )
                }

                // Single Delete Button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Delete item",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Video Play Download button
                if (item.videoPlayUrl != null) {
                    FilledTonalButton(
                        onClick = { onDownload(false) },
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = "Video Icon",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Tải lại Video",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                // Audio extraction download button
                if (item.musicUrl != null) {
                    FilledTonalButton(
                        onClick = { onDownload(true) },
                        modifier = Modifier.weight(0.9f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                            contentColor = MaterialTheme.colorScheme.secondary
                        ),
                        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Music Icon",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Tải lại MP3",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

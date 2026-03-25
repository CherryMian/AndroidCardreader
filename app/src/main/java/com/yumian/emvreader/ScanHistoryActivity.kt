package com.yumian.emvreader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yumian.emvreader.ui.theme.EMVReaderTheme

class ScanHistoryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            EMVReaderTheme {
                ScanHistoryScreen(
                    onBack = { finish() },
                    onClearHistory = {
                        HistoryManager.clearHistory(this)
                        // Trigger UI refresh somehow. A reload call or state update will be needed.
                        Toast.makeText(this, "记录已清除", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(android.content.Intent(context, ScanHistoryActivity::class.java))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanHistoryScreen(
    onBack: () -> Unit,
    onClearHistory: () -> Unit
) {
    val context = LocalContext.current
    // Mutable state for the list to allow refreshing
    var historyList by remember { mutableStateOf(HistoryManager.getHistory(context)) }

    // State for renaming
    var editingScan by remember { mutableStateOf<SavedScan?>(null) }

    if (editingScan != null) {
        RenameDialog(
            currentName = editingScan?.customName ?: "",
            onDismiss = { editingScan = null },
            onConfirm = { newName ->
                editingScan?.let { scan ->
                    HistoryManager.updateScanName(context, scan.timestamp, newName)
                    // Refresh list
                    historyList = HistoryManager.getHistory(context)
                }
                editingScan = null
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("扫描记录", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (historyList.isNotEmpty()) {
                        IconButton(onClick = {
                            onClearHistory()
                            historyList = emptyList()
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "清除记录")
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { innerPadding ->
        if (historyList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无扫描记录",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = innerPadding,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
            ) {
                item {
                     Spacer(modifier = Modifier.height(8.dp))
                }
                items(historyList) { scan ->
                    HistoryItemCard(
                        scan = scan,
                        onEditName = { editingScan = scan }
                    )
                }
                item {
                     Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun RenameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义名称") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun HistoryItemCard(
    scan: SavedScan,
    onEditName: () -> Unit
) {
    val context = LocalContext.current

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label 已复制", Toast.LENGTH_SHORT).show()
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                val displayName = if (!scan.customName.isNullOrBlank()) scan.customName else scan.type
                val displaySub = if (!scan.customName.isNullOrBlank()) scan.type else ""

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(
                            onClick = onEditName,
                            modifier = Modifier.size(24.dp).padding(start = 4.dp)
                        ) {
                           Icon(
                               imageVector = Icons.Filled.Edit,
                               contentDescription = "修改名称",
                               modifier = Modifier.size(16.dp),
                               tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                           )
                        }
                    }
                    if (displaySub.isNotEmpty()) {
                        Text(
                            text = displaySub,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = scan.formattedTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Pan
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = scan.pan,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { copyToClipboard("卡号", scan.pan) }
                )
                 IconButton(onClick = { copyToClipboard("卡号", scan.pan) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "复制卡号", modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.clickable { copyToClipboard("有效期", scan.expiry) }
                ) {
                    Text(
                        text = "有效期",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = scan.expiry,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "AID",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = scan.aid,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}


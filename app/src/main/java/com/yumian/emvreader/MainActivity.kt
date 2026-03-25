package com.yumian.emvreader

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.yumian.emvreader.ui.theme.EMVReaderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

// ... existing code ...

class MainActivity : ComponentActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private var uiState by mutableStateOf<CardUiState>(CardUiState.Idle)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                uiState = CardUiState.Error("需要开启NFC权限")
            }
        }

    private val logTag = "EmvReader"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        ensureNfcPermission()

        setContent {
            EMVReaderTheme {
                EmvReaderScreen(
                    state = uiState,
                    onCopy = { text -> copyText(text) },
                    onSave = { card, name ->
                        HistoryManager.saveScan(this, card, name)
                        Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show()
                    },
                    onHistory = {
                        ScanHistoryActivity.start(this)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        enableReaderMode()
    }

    override fun onPause() {
        super.onPause()
        disableReaderMode()
    }

    private fun ensureNfcPermission() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.NFC) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            permissionLauncher.launch(Manifest.permission.NFC)
        }
    }

    private fun enableReaderMode() {
        nfcAdapter?.enableReaderMode(
            this,
            { tag -> handleTag(tag) },
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null
        )
    }

    private fun disableReaderMode() {
        nfcAdapter?.disableReaderMode(this)
    }

    private fun handleTag(tag: Tag) {
        Log.d(logTag, "Tag detected: $tag")
        uiState = CardUiState.Reading
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching { readCard(tag) }
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = { info ->
                        Log.d(logTag, "Read success: $info")
                        uiState = CardUiState.Success(info)
                    },
                    onFailure = {
                        Log.e(logTag, "Read failed", it)
                        val errorMsg = if (it.message?.contains("Tag was lost", ignoreCase = true) == true) {
                            "请不要移动的太快，请至少保持3-4秒"
                        } else {
                            it.message ?: "读取失败"
                        }
                        uiState = CardUiState.Error(errorMsg)
                    }
                )
            }
        }
    }

    private fun copyText(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("text", text))
        Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
    }

    private fun readCard(tag: Tag): CardInfo {
        Log.d(logTag, "Starting readCard with TapCard library")
        val reader = io.github.tapcard.android.NFCCardReader(this)
        val intent = android.content.Intent()
        intent.putExtra(NfcAdapter.EXTRA_TAG, tag)

        // library call
        val emvCard = reader.readCardBlocking(intent)

        Log.d(logTag, "--- Raw EmvCard Data ---")
        Log.d(logTag, "AID: ${emvCard.aid}")
        Log.d(logTag, "Card Number: ${emvCard.cardNumber}")
        Log.d(logTag, "Expire Date: ${emvCard.expireDate}")
        Log.d(logTag, "Type: ${emvCard.type}")
        Log.d(logTag, "Application Label: ${emvCard.applicationLabel}")
        Log.d(logTag, "Holder: ${emvCard.holderFirstname} ${emvCard.holderLastname}")
        Log.d(logTag, "Left PIN Try: ${emvCard.leftPinTry}")
        Log.d(logTag, "ATR Description: ${emvCard.atrDescription}")
        Log.d(logTag, "NFC Locked: ${emvCard.isNfcLocked}")
        Log.d(logTag, "Transactions: ${emvCard.listTransactions?.size}")
        Log.d(logTag, "------------------------")

        val expiry = emvCard.expireDate?.let {
            java.text.SimpleDateFormat("yyyy/MM", Locale.US).format(it)
        } ?: "--/--"

        val aid = emvCard.aid ?: ""
        val standard = if (aid.startsWith("A000000333")) "PBOC" else "EMV"

        // improved type detection
        val cardType = if (aid == "A0000000108888") {
            "Mastercard China"
        } else {
            val label = emvCard.applicationLabel
            if (!label.isNullOrBlank()) {
                if (label.trim().equals("PBOC DEBIT", ignoreCase = true)) {
                    "UNIONPAY PBOC DEBIT"
                } else {
                    label
                }
            } else {
                emvCard.type?.toString() ?: "Unknown"
            }
        }

        // Try NativeEmvReader first as requested
        val nativeTransactions = try {
            val isoDep = android.nfc.tech.IsoDep.get(tag)
            if (isoDep != null) {
                 NativeEmvReader(isoDep).readTransactions()
            } else {
                 emptyList()
            }
        } catch (e: Exception) {
            Log.e(logTag, "NativeEmvReader failed", e)
            emptyList()
        }

        val transactions = nativeTransactions.ifEmpty {
            emvCard.listTransactions?.map { tx ->
                CardTransaction(
                    date = tx.date?.let {
                        java.text.SimpleDateFormat("yyyy/MM/dd", Locale.US).format(it)
                    } ?: "Unknown",
                    time = tx.time?.let {
                        java.text.SimpleDateFormat("HH:mm:ss", Locale.US).format(it)
                    } ?: "",
                    amount = String.format(Locale.US, "%.2f", tx.amount ?: 0f),
                    currency = tx.currency?.toString() ?: "",
                    type = tx.transactionType?.toString() ?: "Unknown"
                )
            } ?: emptyList()
        }

        return CardInfo(
            type = cardType,
            pan = emvCard.cardNumber?.chunked(4)?.joinToString(" ") ?: "Unknown",
            expiry = expiry,
            standard = standard,
            transactions = transactions,
            aid = aid,
            applicationLabel = emvCard.applicationLabel ?: "",
            cardHolder = "${emvCard.holderFirstname ?: ""} ${emvCard.holderLastname ?: ""}".trim(),
            leftPinTry = emvCard.leftPinTry.toString(),
            atrDescription = (emvCard.atrDescription ?: "") as String
        )
    }

}

sealed interface CardUiState {
    object Idle : CardUiState
    object Reading : CardUiState
    data class Success(val card: CardInfo) : CardUiState
    data class Error(val message: String) : CardUiState
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmvReaderScreen(
    state: CardUiState,
    onCopy: (String) -> Unit,
    onSave: (CardInfo, String?) -> Unit,
    onHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf<CardInfo?>(null) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    if (showMenu) {
        ModalBottomSheet(
            onDismissRequest = { showMenu = false },
            sheetState = sheetState
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                NavigationDrawerItem(
                    label = { Text("扫描记录", style = MaterialTheme.typography.titleMedium) },
                    selected = false,
                    onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            showMenu = false
                            onHistory()
                        }
                    },
                    icon = { Icon(Icons.Filled.History, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showSaveDialog != null) {
        val card = showSaveDialog!!
        var name by remember { mutableStateOf("") }
        AlertDialog(
             onDismissRequest = { showSaveDialog = null },
             title = { Text("保存卡片") },
             text = {
                 Column {
                     Text("您可以为这张卡片设置一个名称（可选）", style = MaterialTheme.typography.bodyMedium)
                     Spacer(modifier = Modifier.height(8.dp))
                     OutlinedTextField(
                         value = name,
                         onValueChange = { name = it },
                         label = { Text("名称") },
                         singleLine = true,
                         modifier = Modifier.fillMaxWidth()
                     )
                 }
             },
             confirmButton = {
                 TextButton(onClick = {
                     onSave(card, name.takeIf { it.isNotBlank() })
                     showSaveDialog = null
                 }) {
                     Text("保存")
                 }
             },
             dismissButton = {
                 TextButton(onClick = { showSaveDialog = null }) {
                     Text("取消")
                 }
             }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(),
                title = { Text(text = "EMV 读卡器", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.Menu, contentDescription = "菜单")
                    }
                }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = "请将金融IC卡靠近手机背面的NFC区域，系统会自动读取。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            when (state) {
                CardUiState.Idle -> IdleCard()
                CardUiState.Reading -> ReadingCard()
                is CardUiState.Error -> ErrorCard(state.message)
                is CardUiState.Success -> ResultCard(state.card, onCopy,
                    { card -> showSaveDialog = card })
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Powered By Yumian，made with ❤",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun IdleCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "等待刷卡", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "靠近即可自动读取，无需手动操作。", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ReadingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "正在读取…", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "读取失败", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(text = message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun ResultCard(
    card: CardInfo,
    onCopy: (String) -> Unit,
    onSave: (CardInfo) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "卡片信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)

                Text(text = "Standard", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                Text(
                    text = card.standard,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                if (card.type != "Unknown") {
                    Text(text = "Card Type", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                    Text(
                        text = card.type,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Text(text = "Card No.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                Text(
                    text = card.pan,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onCopy(card.pan) }
                )
                Text(text = "Good Thru", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                Text(
                    text = card.expiry,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.clickable { onCopy(card.expiry) }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.Start) {
                    TextButton(onClick = { onCopy(card.pan) }) {
                        Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "复制卡号")
                    }
                    TextButton(onClick = { onCopy(card.expiry) }) {
                        Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "复制有效期")
                    }
                    if (card.type != "Unknown" && card.pan != "Unknown" && card.expiry != "--/--") {
                        TextButton(onClick = { onSave(card) }) {
                            Icon(imageVector = Icons.Filled.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "保存到历史记录")
                        }
                    }
                }
                Text(text = "点按文字或按钮可复制。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
            }
        }

        // Hidden transaction viewer entry
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewIdle() {
    EMVReaderTheme { EmvReaderScreen(CardUiState.Idle, {}, { _, _ -> }, {}) }
}

@Preview(showBackground = true)
@Composable
private fun PreviewResult() {
    EMVReaderTheme {
        EmvReaderScreen(CardUiState.Success(CardInfo("Visa", "6214 8888 1234 5678", "2028/12", "EMV")), {},
            { _, _ -> }, {})
    }
}
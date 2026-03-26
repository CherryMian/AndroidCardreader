package com.yumian.emvreader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yumian.emvreader.ui.theme.EMVReaderTheme

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EMVReaderTheme {
                SettingsScreen(
                    onBack = { finish() }
                )
            }
        }
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, SettingsActivity::class.java)
            context.startActivity(intent)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val context = LocalContext.current

    val canAuthenticate = remember {
        val biometricManager = BiometricManager.from(context)
        biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    var isBiometricEnabled by remember {
        mutableStateOf(SettingsManager.isBiometricEnabled(context) && canAuthenticate)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(),
                title = {
                    Text(
                        "设置",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            ListItem(
                headlineContent = { Text("启用身份验证") },
                supportingContent = {
                    val text = if (canAuthenticate) {
                        "开启后，进入历史记录需要通过身份验证。关闭此选项可直接查看，但会降低安全性。"
                    } else {
                        "您的设备未设置锁屏密码或生物识别信息，无法启用此功能。"
                    }
                    Text(text)
                },
                trailingContent = {
                    Switch(
                        checked = isBiometricEnabled,
                        onCheckedChange = { checked ->
                            if (canAuthenticate) {
                                isBiometricEnabled = checked
                                SettingsManager.setBiometricEnabled(context, checked)
                            }
                        },
                        enabled = canAuthenticate
                    )
                },
                modifier = Modifier.clickable(enabled = canAuthenticate) {
                    val newValue = !isBiometricEnabled
                    isBiometricEnabled = newValue
                    SettingsManager.setBiometricEnabled(context, newValue)
                }
            )
            HorizontalDivider()
        }
    }
}

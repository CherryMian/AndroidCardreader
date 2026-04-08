package com.yumian.emvreader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.yumian.emvreader.ui.theme.EMVReaderTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RegisterActivity : FragmentActivity() {
    private val logTag = "Register"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EMVReaderTheme {
                RegisterScreen(
                    onBack = { finish() },
                    onRegister = { username ->
                        scope.launch {
                            Log.d(logTag, "Register init user=$username")
                            if (username.isBlank()) {
                                Toast.makeText(this@RegisterActivity, "请输入用户名", Toast.LENGTH_SHORT).show()
                                Log.d(logTag, "Register failed: empty username")
                                return@launch
                            }
                            try {
                                val challenge = PasskeyAuthClient.requestRegisterChallenge(this@RegisterActivity, username)
                                val credential = PasskeyAuthClient.performPasskeyRegister(this@RegisterActivity, challenge)
                                PasskeyAuthClient.finishRegister(this@RegisterActivity, username, credential)
                                Toast.makeText(this@RegisterActivity, "注册成功", Toast.LENGTH_SHORT).show()
                                Log.d(logTag, "Register success user=$username")
                                finish()
                            } catch (e: Exception) {
                                Log.e(logTag, "Register failed", e)
                                Toast.makeText(this@RegisterActivity, e.message ?: "注册失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
        }
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, RegisterActivity::class.java)
            context.startActivity(intent)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegisterScreen(
    onBack: () -> Unit,
    onRegister: (String) -> Unit
) {
    var username by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("注册账号") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "创建一个新账户", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("用户名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { onRegister(username.trim()) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(modifier = Modifier.height(0.dp))
                Text("注册")
            }
        }
    }
}

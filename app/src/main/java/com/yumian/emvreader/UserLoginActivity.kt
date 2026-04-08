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
import androidx.compose.material.icons.filled.Login
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

class UserLoginActivity : FragmentActivity() {
    private val logTag = "UserLogin"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EMVReaderTheme {
                UserLoginScreen(
                    onBack = { finish() },
                    onLogin = { username ->
                        scope.launch {
                            Log.d(logTag, "Login init for user=$username")
                            if (username.isBlank()) {
                                Toast.makeText(this@UserLoginActivity, "请输入用户名", Toast.LENGTH_SHORT).show()
                                Log.d(logTag, "Login failed: empty username")
                                return@launch
                            }
                            try {
                                val challenge = PasskeyAuthClient.requestLoginChallenge(this@UserLoginActivity, username)
                                val credential = PasskeyAuthClient.performPasskeyLogin(this@UserLoginActivity, challenge)
                                val token = PasskeyAuthClient.finishLogin(this@UserLoginActivity, username, credential)
                                Toast.makeText(this@UserLoginActivity, "登录成功", Toast.LENGTH_SHORT).show()
                                Log.d(logTag, "Login success for user=$username token=$token")
                                finish()
                            } catch (e: Exception) {
                                Log.e(logTag, "Login failed", e)
                                Toast.makeText(this@UserLoginActivity, e.message ?: "登录失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onRegister = {
                        Log.d(logTag, "Navigate to RegisterActivity")
                        RegisterActivity.start(this)
                    }
                )
            }
        }
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, UserLoginActivity::class.java)
            context.startActivity(intent)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserLoginScreen(
    onBack: () -> Unit,
    onLogin: (String) -> Unit,
    onRegister: () -> Unit
) {
    var username by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账户登录") },
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
            Text(text = "请输入用户名以继续", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("用户名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { onLogin(username.trim()) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Login, contentDescription = null)
                Spacer(modifier = Modifier.height(0.dp))
                Text("登录")
            }
            TextButton(onClick = onRegister) {
                Text("没有账号？立即注册")
            }
        }
    }
}

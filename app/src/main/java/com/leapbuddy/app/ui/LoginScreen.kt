package com.leapbuddy.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 登录页（占位设计）：手机号 + 短信验证码。
 * 后期可自由重设计，业务调用全部走 AppRuntime。
 */
@Composable
fun LoginScreen(
    runtime: AppRuntime,
    onLoginSuccess: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var phone by remember { mutableStateOf("") }
    var smsCode by remember { mutableStateOf("") }
    var countdown by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1000)
            countdown -= 1
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("零控", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text("零跑车控 · 自用客户端", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(40.dp))

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it.filter(Char::isDigit).take(11) },
            label = { Text("手机号") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = smsCode,
            onValueChange = { smsCode = it.filter(Char::isDigit).take(6) },
            label = { Text("短信验证码") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                scope.launch {
                    busy = true
                    message = null
                    try {
                        runtime.sendSms(phone)
                        countdown = 60
                        message = "验证码已发送"
                    } catch (e: Exception) {
                        message = e.message ?: "验证码发送失败"
                    }
                    busy = false
                }
            },
            enabled = !busy && phone.length == 11 && countdown == 0,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (countdown > 0) "重新发送 (${countdown}s)" else "获取验证码")
        }
        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                scope.launch {
                    busy = true
                    message = null
                    try {
                        runtime.login(phone, smsCode)
                        onLoginSuccess()
                    } catch (e: Exception) {
                        message = e.message ?: "登录失败"
                    }
                    busy = false
                }
            },
            enabled = !busy && phone.length == 11 && smsCode.length == 6,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("登录")
        }

        if (busy) {
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator()
        }
        message?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

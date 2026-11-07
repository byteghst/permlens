package dev.byteghst.permlens

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import rikka.shizuku.Shizuku

enum class ShizukuStatus { NOT_RUNNING, NEED_PERMISSION, READY }

class MainActivity : ComponentActivity() {

    private val status = mutableStateOf(ShizukuStatus.NOT_RUNNING)

    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener { refresh() }
    private val permissionResult =
        Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        refresh()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val current by status
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("PermLens", style = MaterialTheme.typography.headlineMedium)
                        when (current) {
                            ShizukuStatus.NOT_RUNNING -> {
                                Text("Shizuku is not running.")
                                Button(onClick = { openShizuku() }) { Text("Open Shizuku") }
                            }
                            ShizukuStatus.NEED_PERMISSION -> {
                                Text("Shizuku is running. Permission needed.")
                                Button(onClick = { Shizuku.requestPermission(0) }) {
                                    Text("Grant permission")
                                }
                            }
                            ShizukuStatus.READY -> Text("Connected to Shizuku.")
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        Shizuku.removeRequestPermissionResultListener(permissionResult)
        super.onDestroy()
    }

    private fun refresh() {
        status.value = when {
            !Shizuku.pingBinder() -> ShizukuStatus.NOT_RUNNING
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShizukuStatus.READY
            else -> ShizukuStatus.NEED_PERMISSION
        }
    }

    private fun openShizuku() {
        packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            ?.let { startActivity(it) }
    }
}

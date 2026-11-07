package dev.byteghst.permlens

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import rikka.shizuku.Shizuku

enum class ShizukuStatus { NOT_RUNNING, NEED_PERMISSION, READY }

class MainActivity : ComponentActivity() {

    private val status = mutableStateOf(ShizukuStatus.NOT_RUNNING)
    private val crash = mutableStateOf<String?>(null)

    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener { refresh() }
    private val permissionResult =
        Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        crash.value = CrashLog.read(this)

        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        refresh()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val crashText = crash.value
                    val current by status
                    if (crashText != null) {
                        CrashScreen(
                            text = crashText,
                            onShare = { share(crashText) },
                            onDismiss = {
                                CrashLog.clear(this@MainActivity)
                                crash.value = null
                            }
                        )
                    } else if (current == ShizukuStatus.READY) {
                        PermLensApp()
                    } else {
                        StatusScreen(
                            current,
                            onOpen = { openShizuku() },
                            onGrant = { Shizuku.requestPermission(0) }
                        )
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

    private fun share(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, "Share crash log"))
    }
}

@Composable
private fun StatusScreen(status: ShizukuStatus, onOpen: () -> Unit, onGrant: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("PermLens", style = MaterialTheme.typography.headlineMedium)
        if (status == ShizukuStatus.NOT_RUNNING) {
            Text("Shizuku is not running.")
            Button(onClick = onOpen) { Text("Open Shizuku") }
        } else {
            Text("Shizuku is running. Permission needed.")
            Button(onClick = onGrant) { Text("Grant permission") }
        }
    }
}

@Composable
private fun CrashScreen(text: String, onShare: () -> Unit, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("The app crashed last time", style = MaterialTheme.typography.titleLarge)
        Button(onClick = onShare) { Text("Share log") }
        OutlinedButton(onClick = onDismiss) { Text("Dismiss") }
        SelectionContainer(Modifier.verticalScroll(rememberScrollState())) {
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

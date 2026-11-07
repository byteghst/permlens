package dev.byteghst.permlens

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        crash.value = CrashLog.read(this)

        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        refresh()

        setContent {
            PermLensTheme {
                AuroraBackground {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Transparent,
                        contentColor = Pal.TextHi
                    ) {
                        val crashText = crash.value
                        val current by status
                        val screen = when {
                            crashText != null -> 0
                            current == ShizukuStatus.READY -> 2
                            else -> 1
                        }
                        AnimatedContent(
                            targetState = screen,
                            transitionSpec = { fadeIn(tween(450)) togetherWith fadeOut(tween(250)) },
                            label = "root"
                        ) { s ->
                            when (s) {
                                0 -> {
                                    val text = crash.value.orEmpty()
                                    CrashScreen(
                                        text = text,
                                        onShare = { share(text) },
                                        onDismiss = {
                                            CrashLog.clear(this@MainActivity)
                                            crash.value = null
                                        }
                                    )
                                }
                                2 -> PermLensApp()
                                else -> StatusScreen(
                                    current,
                                    onOpen = { openShizuku() },
                                    onGrant = { Shizuku.requestPermission(0) }
                                )
                            }
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

    private fun share(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, "Share crash log"))
    }
}

@Composable
private fun StepIndicator(c: Color, done: Boolean) {
    Box(
        Modifier.size(22.dp).background(c.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (done) {
            Text("\u2713", color = c, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        } else {
            Box(Modifier.size(8.dp).background(c, CircleShape))
        }
    }
}

@Composable
private fun StepRow(text: String, done: Boolean) {
    val c by animateColorAsState(if (done) Pal.Allow else Pal.TextLo, tween(500), label = "step")
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepIndicator(c, done)
        Spacer(Modifier.width(12.dp))
        Text(text, color = if (done) Pal.TextHi else Pal.TextLo, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StatusScreen(status: ShizukuStatus, onOpen: () -> Unit, onGrant: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LensLogo(Modifier.size(260.dp))
        GradientTitle("PermLens", 40.sp)
        Spacer(Modifier.height(4.dp))
        Text("See what every app can do", color = Pal.TextLo, fontSize = 15.sp)
        Spacer(Modifier.height(28.dp))
        Column(
            Modifier.fillMaxWidth().glass().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            StepRow("Shizuku is running", done = status != ShizukuStatus.NOT_RUNNING)
            StepRow("Permission granted", done = status == ShizukuStatus.READY)
        }
        Spacer(Modifier.height(24.dp))
        AnimatedContent(
            targetState = status == ShizukuStatus.NOT_RUNNING,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
            label = "cta"
        ) { notRunning ->
            if (notRunning) GradientButton("Open Shizuku", onOpen)
            else GradientButton("Grant permission", onGrant)
        }
    }
}

@Composable
private fun CrashScreen(text: String, onShare: () -> Unit, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("The app crashed last time", color = Pal.Deny, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GradientButton("Share log", onShare)
            GradientButton("Dismiss", onDismiss, filled = false)
        }
        Column(Modifier.fillMaxWidth().glass().padding(14.dp)) {
            SelectionContainer(Modifier.verticalScroll(rememberScrollState())) {
                Text(text, color = Pal.TextLo, fontSize = 12.sp)
            }
        }
    }
}

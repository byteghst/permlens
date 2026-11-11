package dev.byteghst.permlens

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        crash.value = CrashLog.read(this)

        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        refresh()

        setContent {
            PermLensTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
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
                        transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(200)) },
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
                            2 -> PermLensRoot()
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

// The onboarding and crash screens set the font families explicitly (Space Grotesk for headings,
// Montserrat for the rest) so they always match the app, whatever the theme passes down.

@Composable
private fun StepRow(text: String, done: Boolean) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (done) cs.primary else cs.surfaceContainerHighest, label = "stepBg")
    val fg by animateColorAsState(if (done) cs.onPrimary else cs.onSurfaceVariant, label = "stepFg")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(28.dp).background(bg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (done) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            } else {
                Box(Modifier.size(8.dp).background(fg, CircleShape))
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = BodyFont, fontWeight = FontWeight.SemiBold),
            color = if (done) cs.onSurface else cs.onSurfaceVariant
        )
    }
}

@Composable
private fun StatusScreen(status: ShizukuStatus, onOpen: () -> Unit, onGrant: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .statusBarsPadding().navigationBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LensLogo(Modifier.size(280.dp))
        Text(
            "PermLens",
            style = MaterialTheme.typography.displaySmall.copy(fontFamily = HeadingFont, fontWeight = FontWeight.Bold),
            color = cs.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "See what every app can do",
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = BodyFont, fontWeight = FontWeight.Normal),
            color = cs.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            color = cs.surfaceContainerLow
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                StepRow("Shizuku is running", done = status != ShizukuStatus.NOT_RUNNING)
                StepRow("Permission granted", done = status == ShizukuStatus.READY)
            }
        }
        Spacer(Modifier.height(24.dp))
        AnimatedContent(
            targetState = status == ShizukuStatus.NOT_RUNNING,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
            label = "cta"
        ) { notRunning ->
            if (notRunning) ExpressiveButton("Open Shizuku", onOpen, Modifier.fillMaxWidth())
            else ExpressiveButton("Grant permission", onGrant, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CrashScreen(text: String, onShare: () -> Unit, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "The app crashed last time",
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = HeadingFont, fontWeight = FontWeight.Medium),
            color = cs.error
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ExpressiveButton("Share log", onShare)
            ExpressiveButton("Dismiss", onDismiss, kind = BtnKind.Tonal)
        }
        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(28.dp),
            color = cs.surfaceContainerHigh
        ) {
            SelectionContainer(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = BodyFont),
                    color = cs.onSurfaceVariant
                )
            }
        }
    }
}

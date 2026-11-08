package dev.byteghst.permlens

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun relTime(ms: Long): String =
    DateUtils.getRelativeTimeSpanString(ms, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

@Composable
fun ModePickerDialog(title: String, current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                listOf(0, 1, 2, 4, 3).forEach { m ->
                    TextButton(onClick = { onPick(m) }) {
                        Text(if (m == current) "${modeLabel(m)} (current)" else modeLabel(m))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ---------------------------------------------------------------- Ops tab: who has X?

@Composable
fun GlobalOpsScreen(apps: List<AppItem>?) {
    var picked by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var query by remember { mutableStateOf("") }

    val p = picked
    if (p != null) {
        OpHoldersScreen(p, apps, onBack = { picked = null })
        return
    }

    val all = remember { runCatching { AppOpsRepo.opNames() }.getOrDefault(emptyList()) }
    val shown = remember(all, query) { all.filter { it.second.contains(query, true) } }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search operations") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(shown, key = { it.first }) { op ->
                Text(
                    op.second,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().clickable { picked = op }.padding(vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun OpHoldersScreen(op: Pair<Int, String>, apps: List<AppItem>?, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<OpEntryInfo>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var userOnly by remember { mutableStateOf(true) }
    var editing by remember { mutableStateOf<OpEntryInfo?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val labels = remember(apps) { apps?.associate { it.pkg to it.label } ?: emptyMap() }
    val system = remember(apps) { apps?.filter { it.system }?.map { it.pkg }?.toSet() ?: emptySet() }

    suspend fun load() {
        entries = withContext(Dispatchers.IO) { AppOpsRepo.getEntries(intArrayOf(op.first)) }
    }

    LaunchedEffect(op.first) {
        try {
            load()
        } catch (t: Throwable) {
            error = Log.getStackTraceString(t)
        }
    }

    editing?.let { e ->
        val name = labels[e.pkg] ?: e.pkg
        ModePickerDialog(
            title = name,
            current = e.mode,
            onPick = { m ->
                editing = null
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            AppOpsRepo.setMode(e.uid, e.pkg, e.code, m)
                            RuleStore.record(e.pkg, e.name, m)
                        }
                        load()
                        message = "$name: ${modeLabel(m)}"
                    } catch (t: Throwable) {
                        message = "Failed: " + (t.cause ?: t)
                    }
                }
            },
            onDismiss = { editing = null }
        )
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(modifier = Modifier.padding(top = 8.dp)) {
            TextButton(onClick = onBack) { Text("Back") }
        }
        Text(op.second, style = MaterialTheme.typography.titleLarge)
        Text(
            "Apps that used this op or have a custom mode. Tap one to change it.",
            style = MaterialTheme.typography.bodySmall
        )

        val err = error
        val list = entries
        when {
            err != null -> SelectionContainer(Modifier.verticalScroll(rememberScrollState())) {
                Text(err, style = MaterialTheme.typography.bodySmall)
            }
            list == null -> Text("Loading...", modifier = Modifier.padding(top = 12.dp))
            else -> {
                val shown = remember(list, userOnly, system) {
                    list.filter { !userOnly || it.pkg !in system }.sortedByDescending { it.lastAccess }
                }
                OutlinedButton(
                    onClick = { userOnly = !userOnly },
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Text(if (userOnly) "User apps (${shown.size}). Include system" else "All apps (${shown.size}). User only")
                }
                message?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 4.dp))
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(shown) { e ->
                        Column(Modifier.fillMaxWidth().clickable { editing = e }.padding(vertical = 8.dp)) {
                            Text(labels[e.pkg] ?: e.pkg, style = MaterialTheme.typography.bodyLarge)
                            val used = if (e.lastAccess > 0) relTime(e.lastAccess) else "never used"
                            Text("${modeLabel(e.mode)} - $used", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Activity tab: privacy dashboard

@Composable
fun ActivityScreen(apps: List<AppItem>?) {
    var entries by remember { mutableStateOf<List<OpEntryInfo>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var userOnly by remember { mutableStateOf(true) }
    var reload by remember { mutableStateOf(0) }

    val labels = remember(apps) { apps?.associate { it.pkg to it.label } ?: emptyMap() }
    val system = remember(apps) { apps?.filter { it.system }?.map { it.pkg }?.toSet() ?: emptySet() }

    LaunchedEffect(reload) {
        entries = null
        error = null
        try {
            entries = withContext(Dispatchers.IO) { AppOpsRepo.getEntries(null) }
        } catch (t: Throwable) {
            error = Log.getStackTraceString(t)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Text("Recent activity", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))

        val err = error
        val list = entries
        when {
            err != null -> SelectionContainer(Modifier.verticalScroll(rememberScrollState())) {
                Text(err, style = MaterialTheme.typography.bodySmall)
            }
            list == null -> Text("Loading...", modifier = Modifier.padding(top = 12.dp))
            else -> {
                val recent = remember(list, userOnly, system) {
                    list.filter { it.lastAccess > 0 && (!userOnly || it.pkg !in system) }
                        .sortedByDescending { it.lastAccess }
                }
                val now = System.currentTimeMillis()
                fun appsIn24h(vararg names: String): Int =
                    recent.filter { it.name in names && now - it.lastAccess < 86_400_000L }
                        .map { it.pkg }.distinct().size

                Text(
                    "Last 24h, apps using: camera ${appsIn24h("camera")}, " +
                        "microphone ${appsIn24h("record_audio")}, " +
                        "location ${appsIn24h("fine_location", "coarse_location")}, " +
                        "clipboard read ${appsIn24h("read_clipboard")}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { userOnly = !userOnly }) {
                        Text(if (userOnly) "User apps" else "All apps")
                    }
                    OutlinedButton(onClick = { reload++ }) { Text("Refresh") }
                }
                LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
                    items(recent.take(150)) { e ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text(labels[e.pkg] ?: e.pkg, style = MaterialTheme.typography.bodyLarge)
                            Text("${e.name} - ${relTime(e.lastAccess)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- More tab

@Composable
fun MoreScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var view by remember { mutableStateOf(0) } // 0 main, 1 rules and history
    var message by remember { mutableStateOf<String?>(null) }
    var ruleCount by remember { mutableStateOf(RuleStore.rules().size) }

    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) scope.launch {
            message = try {
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri)?.use {
                        it.write(RuleStore.exportJson().toByteArray())
                    }
                }
                "Backup saved"
            } catch (t: Throwable) {
                "Backup failed: " + (t.cause ?: t)
            }
        }
    }

    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) scope.launch {
            message = try {
                val n = withContext(Dispatchers.IO) {
                    val text = ctx.contentResolver.openInputStream(uri)!!.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    }
                    val count = RuleStore.importJson(text)
                    RuleStore.applyAll(ctx)
                    count
                }
                ruleCount = RuleStore.rules().size
                "Restored $n rules and applied them"
            } catch (t: Throwable) {
                "Restore failed: " + (t.cause ?: t)
            }
        }
    }

    if (view == 1) {
        BackHandler { view = 0 }
        val rules = remember { RuleStore.rules() }
        val history = remember { RuleStore.history() }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            item {
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    TextButton(onClick = { view = 0 }) { Text("Back") }
                    TextButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, history.joinToString("\n"))
                        }
                        ctx.startActivity(Intent.createChooser(send, "Share history"))
                    }) { Text("Share history") }
                }
                Text("Saved rules (${rules.size})", style = MaterialTheme.typography.titleMedium)
            }
            items(rules) { r ->
                Text(
                    "${r.pkg}\n${r.op}: ${modeLabel(r.mode)}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            item {
                Text(
                    "History",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            items(history) { line ->
                Text(line, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
        return
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("PermLens", style = MaterialTheme.typography.titleLarge)
        Text(
            "Saved rules: $ruleCount. Every mode you change is remembered and re-applied " +
                "automatically whenever Shizuku starts.",
            style = MaterialTheme.typography.bodyMedium
        )
        Button(onClick = {
            scope.launch {
                val (ok, fail) = withContext(Dispatchers.IO) { RuleStore.applyAll(ctx) }
                RuleStore.log("manual re-apply: $ok ok, $fail failed")
                message = "Re-applied: $ok ok, $fail failed"
            }
        }) { Text("Re-apply rules now") }
        OutlinedButton(onClick = { exporter.launch("permlens-rules.json") }) { Text("Backup rules to file") }
        OutlinedButton(onClick = { importer.launch(arrayOf("*/*")) }) { Text("Restore rules from file") }
        OutlinedButton(onClick = { view = 1 }) { Text("Rules and history") }
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Text("Version 0.4.0", style = MaterialTheme.typography.bodySmall)
    }
}

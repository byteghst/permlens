@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dev.byteghst.permlens

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.text.format.DateUtils
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun relTime(ms: Long): String =
    DateUtils.getRelativeTimeSpanString(ms, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

// ---------------------------------------------------------------------------
// Root: wraps the Apps screen and adds Ops / Activity / More tabs
// ---------------------------------------------------------------------------

private data class TabSpec(val label: String, val icon: ImageVector)

private val Tabs = listOf(
    TabSpec("Apps", AppIcons.Apps),
    TabSpec("Ops", AppIcons.Tune),
    TabSpec("Activity", AppIcons.History),
    TabSpec("More", AppIcons.MoreHoriz)
)

@Composable
fun PermLensRoot() {
    var tab by remember { mutableStateOf(0) }
    val cs = MaterialTheme.colorScheme
    Scaffold(
        containerColor = cs.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            ShortNavigationBar(containerColor = cs.surfaceContainer) {
                Tabs.forEachIndexed { i, t ->
                    ShortNavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) }
                    )
                }
            }
        }
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            // Apps stays composed underneath so its selection and scroll survive tab switches.
            PermLensApp()
            if (tab != 0) BackHandler { tab = 0 }
            Crossfade(targetState = tab, animationSpec = tween(220), label = "tabs") { t ->
                if (t != 0) {
                    Surface(
                        modifier = Modifier.fillMaxSize().pointerInput(Unit) {},
                        color = cs.surface
                    ) {
                        when (t) {
                            1 -> OpsTab()
                            2 -> ActivityTab()
                            else -> MoreTab()
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Shared helpers
// ---------------------------------------------------------------------------

private var catalogCache: Pair<Map<String, String>, Set<String>>? = null

/** Package -> label map plus the set of system packages. Loaded once, off the main thread. */
@Composable
private fun rememberCatalog(): Pair<Map<String, String>, Set<String>> {
    val ctx = LocalContext.current
    var data by remember { mutableStateOf(catalogCache ?: Pair(emptyMap(), emptySet())) }
    LaunchedEffect(Unit) {
        if (catalogCache == null) {
            val loaded = withContext(Dispatchers.Default) {
                val pm = ctx.packageManager
                val apps = pm.getInstalledApplications(0)
                Pair(
                    apps.associate { it.packageName to it.loadLabel(pm).toString() },
                    apps.filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 }
                        .map { it.packageName }.toSet()
                )
            }
            catalogCache = loaded
            data = loaded
        }
    }
    return data
}

// ---------------------------------------------------------------------------
// Ops tab: pick an op, see which apps use it
// ---------------------------------------------------------------------------

@Composable
private fun OpsTab() {
    val cs = MaterialTheme.colorScheme
    var picked by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var query by remember { mutableStateOf("") }
    val all = remember { runCatching { AppOpsRepo.opNames() }.getOrDefault(emptyList()) }
    val shown = remember(all, query) {
        all.filter { it.second.contains(query, true) || prettyOp(it.second).contains(query, true) }
            .sortedBy { it.second }
    }

    val p = picked
    if (p != null) {
        BackHandler { picked = null }
        OpHoldersScreen(p, onBack = { picked = null })
    } else {
        ScreenFrame(title = "Operations", subtitle = "Pick one to see which apps use it") {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item(key = "header") {
                    Column {
                        SearchField(query, { query = it }, "Search operations")
                        Spacer(Modifier.height(8.dp))
                    }
                }
                itemsIndexed(shown, key = { _, o -> o.first }) { i, op ->
                    PressSurface(
                        onClick = { picked = op },
                        modifier = Modifier.fillMaxWidth().entrance(i),
                        restCorner = 20.dp,
                        pressedCorner = 10.dp
                    ) {
                        Row(
                            Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    prettyOp(op.second),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = cs.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    op.second,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = cs.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OpHoldersScreen(op: Pair<Int, String>, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val toast = rememberToast()
    val (labels, system) = rememberCatalog()
    var entries by remember { mutableStateOf<List<OpEntryInfo>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var userOnly by remember { mutableStateOf(true) }
    var editing by remember { mutableStateOf<OpEntryInfo?>(null) }

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
        ModeSheet(
            title = name,
            subtitle = prettyOp(op.second),
            current = e.mode,
            onPick = { m ->
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { AppOpsRepo.setMode(e.uid, e.pkg, e.code, m) }
                        load()
                        toast.show("$name: ${modeLabel(m)}")
                    } catch (t: Throwable) {
                        toast.show("Failed: " + (t.cause ?: t))
                    }
                }
            },
            onDismiss = { editing = null }
        )
    }

    ScreenFrame(
        title = prettyOp(op.second),
        subtitle = op.second,
        onBack = onBack,
        overlay = {
            ToastPill(
                visible = toast.shown,
                text = toast.text,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
            )
        }
    ) {
        val err = error
        val list = entries
        when {
            err != null -> ErrorCard("Failed to read", err, Modifier.padding(horizontal = 16.dp))
            list == null -> SkeletonList(rows = 6, rowHeight = 64.dp)
            else -> {
                val userCount = remember(list, system) { list.count { it.pkg !in system } }
                val shown = remember(list, userOnly, system) {
                    list.filter { !userOnly || it.pkg !in system }.sortedByDescending { it.lastAccess }
                }
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item(key = "header") {
                        Column {
                            Text(
                                "Apps that used it or have a custom mode. Tap one to change it.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = cs.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp, bottom = 10.dp)
                            )
                            ConnectedGroup(
                                listOf("User apps ($userCount)", "All (${list.size})"),
                                if (userOnly) 0 else 1,
                                { userOnly = it == 0 }
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                    if (shown.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                "No apps to show",
                                style = MaterialTheme.typography.bodyLarge,
                                color = cs.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(top = 40.dp)
                            )
                        }
                    }
                    itemsIndexed(shown) { i, e ->
                        val name = labels[e.pkg] ?: e.pkg
                        PressSurface(
                            onClick = { editing = e },
                            modifier = Modifier.fillMaxWidth().entrance(i),
                            restCorner = 20.dp,
                            pressedCorner = 10.dp
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                AppIcon(e.pkg, name, 44.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = cs.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        if (e.lastAccess > 0) relTime(e.lastAccess) else "never used",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = cs.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                ModeChip(e.mode)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Activity tab: privacy dashboard + timeline
// ---------------------------------------------------------------------------

@Composable
private fun ActivityTab() {
    val cs = MaterialTheme.colorScheme
    val (labels, system) = rememberCatalog()
    var entries by remember { mutableStateOf<List<OpEntryInfo>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var userOnly by remember { mutableStateOf(true) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(reload) {
        entries = null
        error = null
        try {
            entries = withContext(Dispatchers.IO) { AppOpsRepo.getEntries(null) }
        } catch (t: Throwable) {
            error = Log.getStackTraceString(t)
        }
    }

    ScreenFrame(
        title = "Activity",
        subtitle = "What your apps touched recently",
        actions = {
            FilledTonalIconButton(onClick = { reload++ }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
            Spacer(Modifier.width(8.dp))
        }
    ) {
        val err = error
        val list = entries
        when {
            err != null -> ErrorCard("Failed to read", err, Modifier.padding(horizontal = 16.dp))
            list == null -> SkeletonList(rows = 7, rowHeight = 64.dp)
            else -> {
                val recent = remember(list, userOnly, system) {
                    list.filter { it.lastAccess > 0 && (!userOnly || it.pkg !in system) }
                        .sortedByDescending { it.lastAccess }
                }
                val now = System.currentTimeMillis()
                fun appsIn24h(vararg names: String): Int =
                    recent.filter { it.name in names && now - it.lastAccess < 86_400_000L }
                        .map { it.pkg }.distinct().size

                val fg = modeColors(4)
                val ig = modeColors(1)
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item(key = "tiles") {
                        Column {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                StatTile(
                                    appsIn24h("camera"), "Camera",
                                    cs.tertiaryContainer, cs.onTertiaryContainer,
                                    Modifier.weight(1f), groupShape(0, 4)
                                )
                                StatTile(
                                    appsIn24h("record_audio"), "Mic",
                                    ig.container, ig.onContainer,
                                    Modifier.weight(1f), groupShape(1, 4)
                                )
                                StatTile(
                                    appsIn24h("fine_location", "coarse_location"), "Location",
                                    fg.container, fg.onContainer,
                                    Modifier.weight(1f), groupShape(2, 4)
                                )
                                StatTile(
                                    appsIn24h("read_clipboard"), "Clipboard",
                                    cs.secondaryContainer, cs.onSecondaryContainer,
                                    Modifier.weight(1f), groupShape(3, 4)
                                )
                            }
                            Text(
                                "Apps using each in the last 24 hours",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 12.dp)
                            )
                            ConnectedGroup(
                                listOf("User apps", "All apps"),
                                if (userOnly) 0 else 1,
                                { userOnly = it == 0 }
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                    itemsIndexed(recent.take(150)) { i, e ->
                        val name = labels[e.pkg] ?: e.pkg
                        val live = now - e.lastAccess < 5 * 60_000L
                        Surface(
                            modifier = Modifier.fillMaxWidth().entrance(i),
                            shape = RoundedCornerShape(20.dp),
                            color = cs.surfaceContainerLow
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                AppIcon(e.pkg, name, 40.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = cs.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        prettyOp(e.name),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = cs.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                if (live) PulseDot(modeColors(0).accent, 6.dp)
                                Text(
                                    relTime(e.lastAccess),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = cs.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// More tab: rules, backup/restore, history
// ---------------------------------------------------------------------------

@Composable
private fun MoreTab() {
    var view by remember { mutableStateOf(0) } // 0 main, 1 rules and history
    if (view == 1) {
        BackHandler { view = 0 }
        RulesHistoryView(onBack = { view = 0 })
    } else {
        MoreMain(onDetails = { view = 1 })
    }
}

@Composable
private fun MoreMain(onDetails: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast = rememberToast()
    var ruleCount by remember { mutableStateOf(RuleStore.rules().size) }
    var confirmReset by remember { mutableStateOf(false) }

    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) scope.launch {
            val msg = try {
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri)?.use {
                        it.write(RuleStore.exportJson().toByteArray())
                    }
                }
                "Backup saved"
            } catch (t: Throwable) {
                "Backup failed: " + (t.cause ?: t)
            }
            toast.show(msg)
        }
    }

    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) scope.launch {
            val msg = try {
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
            toast.show(msg)
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            shape = RoundedCornerShape(32.dp),
            icon = { Icon(Icons.Filled.Warning, contentDescription = null) },
            title = { Text("Reset everything?") },
            text = {
                Text("Every mode saved by PermLens goes back to Default and the saved rules are cleared.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    scope.launch {
                        val n = withContext(Dispatchers.IO) {
                            var count = 0
                            val pm = ctx.packageManager
                            for (r in RuleStore.rules()) {
                                try {
                                    val uid = pm.getApplicationInfo(r.pkg, 0).uid
                                    val code = AppOpsRepo.codeOf(r.op) ?: continue
                                    AppOpsRepo.setMode(uid, r.pkg, code, 3, record = false)
                                    count++
                                } catch (t: Throwable) {
                                    // app removed or op unavailable: skip it
                                }
                            }
                            RuleStore.clearAll()
                            count
                        }
                        ruleCount = RuleStore.rules().size
                        toast.show("Reset $n ops to default")
                    }
                }) { Text("Reset", color = cs.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
            }
        )
    }

    ScreenFrame(
        title = "More",
        subtitle = "Rules, backup and history",
        overlay = {
            ToastPill(
                visible = toast.shown,
                text = toast.text,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
            )
        }
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(36.dp),
                color = cs.primaryContainer,
                contentColor = cs.onPrimaryContainer
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text("$ruleCount", style = MaterialTheme.typography.displaySmall)
                    Text("saved rules", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Every mode you change is remembered and re-applied automatically " +
                            "whenever Shizuku starts.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            ExpressiveButton(
                "Re-apply rules now",
                {
                    scope.launch {
                        val (ok, fail) = withContext(Dispatchers.IO) { RuleStore.applyAll(ctx) }
                        RuleStore.log("manual re-apply: $ok ok, $fail failed")
                        toast.show("Re-applied: $ok ok, $fail failed")
                    }
                },
                Modifier.fillMaxWidth(),
                icon = Icons.Filled.Refresh
            )
            ExpressiveButton(
                "Backup rules to file",
                { exporter.launch("permlens-rules.json") },
                Modifier.fillMaxWidth(),
                kind = BtnKind.Tonal
            )
            ExpressiveButton(
                "Restore rules from file",
                { importer.launch(arrayOf("*/*")) },
                Modifier.fillMaxWidth(),
                kind = BtnKind.Tonal
            )
            ExpressiveButton(
                "Rules and history",
                onDetails,
                Modifier.fillMaxWidth(),
                kind = BtnKind.Tonal,
                icon = Icons.Filled.Info
            )
            ExpressiveButton(
                "Reset everything I changed",
                { confirmReset = true },
                Modifier.fillMaxWidth(),
                kind = BtnKind.Danger,
                icon = Icons.Filled.Warning
            )
            Text(
                "PermLens 0.6.0",
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 24.dp)
            )
        }
    }
}

@Composable
private fun RulesHistoryView(onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val (labels, _) = rememberCatalog()
    val rules = remember { RuleStore.rules() }
    val history = remember { RuleStore.history() }

    ScreenFrame(
        title = "Rules",
        subtitle = "${rules.size} saved",
        onBack = onBack,
        actions = {
            FilledTonalIconButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, history.joinToString("\n"))
                }
                ctx.startActivity(Intent.createChooser(send, "Share history"))
            }) {
                Icon(Icons.Filled.Share, contentDescription = "Share history")
            }
            Spacer(Modifier.width(8.dp))
        }
    ) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(rules) { i, r ->
                Surface(
                    modifier = Modifier.fillMaxWidth().entrance(i),
                    shape = RoundedCornerShape(20.dp),
                    color = cs.surfaceContainerLow
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                labels[r.pkg] ?: r.pkg,
                                style = MaterialTheme.typography.titleMedium,
                                color = cs.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                prettyOp(r.op),
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        ModeChip(r.mode)
                    }
                }
            }
            item(key = "historyTitle") { SectionLabel("History") }
            items(history) { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

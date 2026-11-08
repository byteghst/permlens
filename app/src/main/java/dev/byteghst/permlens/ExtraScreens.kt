package dev.byteghst.permlens

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.text.format.DateUtils
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun relTime(ms: Long): String =
    DateUtils.getRelativeTimeSpanString(ms, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

// ---------------------------------------------------------------------------
// Root: wraps the existing Apps screen and adds Ops / Activity / More tabs
// ---------------------------------------------------------------------------

@Composable
fun PermLensRoot() {
    var tab by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            PermLensApp()
            if (tab != 0) {
                BackHandler { tab = 0 }
                AuroraBackground {
                    Box(Modifier.fillMaxSize().pointerInput(Unit) {}) {
                        when (tab) {
                            1 -> OpsTab()
                            2 -> ActivityTab()
                            else -> MoreTab()
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Segmented(listOf("Apps", "Ops", "Activity", "More"), tab, { tab = it })
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

private class ToastState {
    var text by mutableStateOf("")
    var tick by mutableStateOf(0)
    var shown by mutableStateOf(false)
    fun show(t: String) {
        text = t
        tick++
        shown = true
    }
}

@Composable
private fun rememberToast(): ToastState {
    val s = remember { ToastState() }
    LaunchedEffect(s.tick) {
        if (s.tick > 0) {
            delay(2600)
            s.shown = false
        }
    }
    return s
}

@Composable
private fun BackPill(onBack: () -> Unit) {
    Row(
        Modifier.bouncyClick(onBack).glass(RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("\u2039", color = Pal.Cyan, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(6.dp))
        Text("Back", color = Pal.TextHi, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PillButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.bouncyClick(onClick).glass(RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(text, color = Pal.TextHi, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}

@Composable
private fun SearchBox(query: String, onQuery: (String) -> Unit, hint: String) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        placeholder = { Text(hint, color = Pal.TextLo) },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        trailingIcon = {
            if (query.isNotEmpty()) {
                Text(
                    "\u2715",
                    color = Pal.TextLo,
                    modifier = Modifier.padding(end = 8.dp).clip(CircleShape)
                        .clickable { onQuery("") }.padding(10.dp)
                )
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Pal.Cyan,
            unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
            focusedContainerColor = Color.White.copy(alpha = 0.07f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
            cursorColor = Pal.Cyan,
            focusedTextColor = Pal.TextHi,
            unfocusedTextColor = Pal.TextHi
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SheetOption(mode: Int, current: Boolean, index: Int, onClick: () -> Unit) {
    val c = modeColor(mode)
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth()
            .entrance(index)
            .bouncyClick(onClick)
            .clip(shape)
            .background(if (current) c.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f))
            .border(1.dp, if (current) c.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.08f), shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(12.dp).background(c, CircleShape))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(modeLabel(mode), color = Pal.TextHi, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(modeHint(mode), color = Pal.TextLo, fontSize = 12.sp)
        }
        if (current) Text("\u2713", color = c, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSheet(
    title: String,
    subtitle: String,
    current: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Pal.Surface,
        contentColor = Pal.TextHi
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Pal.TextHi)
            Text(subtitle, fontSize = 12.sp, color = Pal.TextLo)
            Spacer(Modifier.height(4.dp))
            listOf(0, 1, 2, 4, 3).forEachIndexed { i, m ->
                SheetOption(m, m == current, i) {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                    onPick(m)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Ops tab: pick an op, see which apps use it
// ---------------------------------------------------------------------------

@Composable
private fun OpsTab() {
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
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(16.dp))
            GradientTitle("Operations")
            Text("Pick one to see which apps use it", color = Pal.TextLo, fontSize = 14.sp)
            Spacer(Modifier.height(14.dp))
            SearchBox(query, { query = it }, "Search operations")
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(shown, key = { _, o -> o.first }) { i, op ->
                    Row(
                        Modifier.fillMaxWidth()
                            .entrance(i)
                            .bouncyClick { picked = op }
                            .glass()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                prettyOp(op.second),
                                color = Pal.TextHi,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(op.second, color = Pal.TextLo, fontSize = 12.sp)
                        }
                        Text("\u203A", color = Pal.TextLo, fontSize = 24.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun OpHoldersScreen(op: Pair<Int, String>, onBack: () -> Unit) {
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

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))
            Row { BackPill(onBack) }
            Spacer(Modifier.height(14.dp))
            Text(
                prettyOp(op.second),
                color = Pal.TextHi,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Apps that used it or have a custom mode. Tap one to change it.",
                color = Pal.TextLo,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(12.dp))

            val err = error
            val list = entries
            when {
                err != null -> Column(Modifier.fillMaxWidth().glass().padding(16.dp)) {
                    Text("Failed to read", color = Pal.Deny, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    SelectionContainer(Modifier.verticalScroll(rememberScrollState())) {
                        Text(err, color = Pal.TextLo, fontSize = 12.sp)
                    }
                }
                list == null -> SkeletonList(rows = 6, rowHeight = 64.dp)
                else -> {
                    val userCount = remember(list, system) { list.count { it.pkg !in system } }
                    val shown = remember(list, userOnly, system) {
                        list.filter { !userOnly || it.pkg !in system }.sortedByDescending { it.lastAccess }
                    }
                    Segmented(
                        listOf("User apps ($userCount)", "All (${list.size})"),
                        if (userOnly) 0 else 1,
                        { userOnly = it == 0 }
                    )
                    Spacer(Modifier.height(4.dp))
                    if (shown.isEmpty()) {
                        Text(
                            "No apps to show",
                            color = Pal.TextLo,
                            modifier = Modifier.padding(top = 40.dp).fillMaxWidth()
                        )
                    }
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 90.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(shown) { i, e ->
                            val name = labels[e.pkg] ?: e.pkg
                            Row(
                                Modifier.fillMaxWidth()
                                    .entrance(i)
                                    .bouncyClick { editing = e }
                                    .glass()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppIcon(e.pkg, name, 42.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        name,
                                        color = Pal.TextHi,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        if (e.lastAccess > 0) relTime(e.lastAccess) else "never used",
                                        color = Pal.TextLo,
                                        fontSize = 12.sp
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
        ToastPill(
            visible = toast.shown,
            text = toast.text,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// Activity tab: privacy dashboard + timeline
// ---------------------------------------------------------------------------

@Composable
private fun ActivityTab() {
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

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                GradientTitle("Activity")
                Text("What your apps touched recently", color = Pal.TextLo, fontSize = 14.sp)
            }
            PillButton("Refresh") { reload++ }
        }
        Spacer(Modifier.height(14.dp))

        val err = error
        val list = entries
        when {
            err != null -> Column(Modifier.fillMaxWidth().glass().padding(16.dp)) {
                Text("Failed to read", color = Pal.Deny, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                SelectionContainer(Modifier.verticalScroll(rememberScrollState())) {
                    Text(err, color = Pal.TextLo, fontSize = 12.sp)
                }
            }
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

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(appsIn24h("camera"), "Camera", Pal.Pink, Modifier.weight(1f))
                    StatTile(appsIn24h("record_audio"), "Mic", Pal.Ignore, Modifier.weight(1f))
                    StatTile(appsIn24h("fine_location", "coarse_location"), "Location", Pal.Foreground, Modifier.weight(1f))
                    StatTile(appsIn24h("read_clipboard"), "Clipboard", Pal.Violet, Modifier.weight(1f))
                }
                Text(
                    "Apps using each in the last 24 hours",
                    color = Pal.TextLo,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)
                )
                Segmented(
                    listOf("User apps", "All apps"),
                    if (userOnly) 0 else 1,
                    { userOnly = it == 0 }
                )
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(recent.take(150)) { i, e ->
                        val name = labels[e.pkg] ?: e.pkg
                        val live = now - e.lastAccess < 5 * 60_000L
                        Row(
                            Modifier.fillMaxWidth().entrance(i).glass().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIcon(e.pkg, name, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    name,
                                    color = Pal.TextHi,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(prettyOp(e.name), color = Pal.TextLo, fontSize = 12.sp)
                            }
                            Spacer(Modifier.width(8.dp))
                            if (live) PulseDot(Pal.Allow, 6.dp)
                            Text(relTime(e.lastAccess), color = Pal.TextLo, fontSize = 12.sp)
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
            containerColor = Pal.Surface,
            titleContentColor = Pal.TextHi,
            textContentColor = Pal.TextLo,
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
                }) { Text("Reset", color = Pal.Deny) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel", color = Pal.TextHi) }
            }
        )
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            GradientTitle("More")
            Column(Modifier.fillMaxWidth().glass().padding(16.dp)) {
                Text("$ruleCount saved rules", color = Pal.TextHi, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Every mode you change is remembered and re-applied automatically " +
                        "whenever Shizuku starts.",
                    color = Pal.TextLo,
                    fontSize = 13.sp
                )
            }
            GradientButton("Re-apply rules now", {
                scope.launch {
                    val (ok, fail) = withContext(Dispatchers.IO) { RuleStore.applyAll(ctx) }
                    RuleStore.log("manual re-apply: $ok ok, $fail failed")
                    toast.show("Re-applied: $ok ok, $fail failed")
                }
            }, Modifier.fillMaxWidth())
            GradientButton("Backup rules to file", { exporter.launch("permlens-rules.json") },
                Modifier.fillMaxWidth(), filled = false)
            GradientButton("Restore rules from file", { importer.launch(arrayOf("*/*")) },
                Modifier.fillMaxWidth(), filled = false)
            GradientButton("Rules and history", onDetails, Modifier.fillMaxWidth(), filled = false)
            GradientButton("Reset everything I changed", { confirmReset = true },
                Modifier.fillMaxWidth(), filled = false)
            Text("PermLens 0.4.0", color = Pal.TextLo, fontSize = 12.sp)
            Spacer(Modifier.height(80.dp))
        }
        ToastPill(
            visible = toast.shown,
            text = toast.text,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
        )
    }
}

@Composable
private fun RulesHistoryView(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val (labels, _) = rememberCatalog()
    val rules = remember { RuleStore.rules() }
    val history = remember { RuleStore.history() }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BackPill(onBack)
                Spacer(Modifier.weight(1f))
                PillButton("Share history") {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, history.joinToString("\n"))
                    }
                    ctx.startActivity(Intent.createChooser(send, "Share history"))
                }
            }
            Spacer(Modifier.height(14.dp))
            GradientTitle("Rules")
            Text("${rules.size} saved", color = Pal.TextLo, fontSize = 14.sp)
        }
        itemsIndexed(rules) { i, r ->
            Row(
                Modifier.fillMaxWidth().entrance(i).glass().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        labels[r.pkg] ?: r.pkg,
                        color = Pal.TextHi,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(prettyOp(r.op), color = Pal.TextLo, fontSize = 12.sp)
                }
                Spacer(Modifier.width(8.dp))
                ModeChip(r.mode)
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            Text("History", color = Pal.TextHi, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        items(history) { line ->
            Text(line, color = Pal.TextLo, fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp))
        }
    }
}

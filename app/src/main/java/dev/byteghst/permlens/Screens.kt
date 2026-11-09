@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dev.byteghst.permlens

import android.content.pm.ApplicationInfo
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppItem(val pkg: String, val label: String, val uid: Int, val system: Boolean)

@Composable
fun PermLensApp() {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<AppItem>?>(null) }
    var selected by remember { mutableStateOf<AppItem?>(null) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(0) } // 0 user, 1 system, 2 all
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.Default) {
            val pm = ctx.packageManager
            pm.getInstalledApplications(0)
                .map {
                    AppItem(
                        it.packageName,
                        it.loadLabel(pm).toString(),
                        it.uid,
                        (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    )
                }
                .sortedBy { it.label.lowercase() }
        }
    }

    AnimatedContent(
        targetState = selected,
        transitionSpec = {
            if (targetState != null) {
                (slideInHorizontally(Motion.spatial<IntOffset>()) { it / 4 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(Motion.spatial<IntOffset>()) { -it / 6 } + fadeOut(tween(160)))
            } else {
                (slideInHorizontally(Motion.spatial<IntOffset>()) { -it / 6 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(Motion.spatial<IntOffset>()) { it / 4 } + fadeOut(tween(160)))
            }
        },
        label = "nav"
    ) { sel ->
        if (sel == null) {
            AppListScreen(
                apps = apps,
                query = query,
                onQuery = { query = it },
                filter = filter,
                onFilter = { filter = it },
                listState = listState,
                onPick = { selected = it }
            )
        } else {
            BackHandler { selected = null }
            OpsScreen(sel, onBack = { selected = null })
        }
    }
}

@Composable
fun AppListScreen(
    apps: List<AppItem>?,
    query: String,
    onQuery: (String) -> Unit,
    filter: Int,
    onFilter: (Int) -> Unit,
    listState: LazyListState,
    onPick: (AppItem) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val shown = remember(apps, query, filter) {
        apps?.filter {
            (filter == 2 || (filter == 1) == it.system) &&
                (it.label.contains(query, true) || it.pkg.contains(query, true))
        } ?: emptyList()
    }
    val count by animateIntAsState(shown.size, tween(500), label = "count")

    ScreenFrame(title = "Apps", subtitle = "Choose an app to inspect") {
        if (apps == null) {
            SkeletonList()
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item(key = "header") {
                    Column {
                        SearchField(query, onQuery, "Search apps or packages")
                        Spacer(Modifier.height(12.dp))
                        ConnectedGroup(listOf("User", "System", "All"), filter, onFilter)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "$count apps",
                            style = MaterialTheme.typography.labelLarge,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                        )
                    }
                }
                if (shown.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "No apps match",
                            style = MaterialTheme.typography.bodyLarge,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                        )
                    }
                } else {
                    itemsIndexed(shown, key = { _, a -> a.pkg }) { i, app ->
                        AppRow(app, i, onPick, Modifier.animateItem())
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: AppItem, index: Int, onPick: (AppItem) -> Unit, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    PressSurface(
        onClick = { onPick(app) },
        modifier = modifier.fillMaxWidth().entrance(index)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.pkg, app.label, 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    app.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    app.pkg,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (app.system) {
                Spacer(Modifier.width(8.dp))
                Tag("System")
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = cs.onSurfaceVariant
            )
        }
    }
}

private fun relativeUsed(lastAccess: Long): String =
    if (lastAccess > 0) {
        DateUtils.getRelativeTimeSpanString(
            lastAccess,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        ).toString()
    } else "never used"

// ---------------------------------------------------------------------------
// Presets: one-tap bundles, shown through the official expressive FAB menu.
// ---------------------------------------------------------------------------

private class Preset(val title: String, val icon: ImageVector, val ops: Set<String>?, val mode: Int)

private val PRESETS = listOf(
    Preset(
        "Lockdown", Icons.Filled.Lock,
        setOf(
            "camera", "record_audio", "coarse_location", "fine_location",
            "read_clipboard", "read_contacts", "read_call_log", "read_sms"
        ),
        1
    ),
    Preset("No overlays", AppIcons.Block, setOf("system_alert_window", "picture_in_picture"), 2),
    Preset("Quiet in background", AppIcons.Snooze, setOf("run_in_background", "run_any_in_background"), 1),
    Preset("Reset app", Icons.Filled.Refresh, null, 3)
)

/** The (op, newMode) changes a preset would make right now. Reset puts every changed op back to default. */
private fun changesFor(p: Preset, list: List<OpInfo>): List<Pair<OpInfo, Int>> {
    val names = p.ops
    return if (names == null) {
        list.filter { it.mode != it.defaultMode }.map { it to 3 }
    } else {
        list.filter { it.name in names && it.mode != p.mode }.map { it to p.mode }
    }
}

@Composable
private fun PresetsOverlay(
    countFor: (Preset) -> Int,
    onApply: (Preset) -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    BackHandler(expanded) { expanded = false }
    Box(modifier) {
        if (expanded) {
            Box(
                Modifier.fillMaxSize()
                    .background(cs.scrim.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { expanded = false }
            )
        }
        FloatingActionButtonMenu(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 8.dp),
            expanded = expanded,
            button = {
                ToggleFloatingActionButton(
                    checked = expanded,
                    onCheckedChange = { expanded = !expanded }
                ) {
                    val imageVector by remember {
                        derivedStateOf {
                            if (checkedProgress > 0.5f) Icons.Filled.Close else Icons.Filled.Add
                        }
                    }
                    Icon(
                        painter = rememberVectorPainter(imageVector),
                        contentDescription = "Presets",
                        modifier = Modifier.animateIcon({ checkedProgress })
                    )
                }
            }
        ) {
            PRESETS.forEach { p ->
                val n = countFor(p)
                FloatingActionButtonMenuItem(
                    onClick = {
                        expanded = false
                        onApply(p)
                    },
                    icon = { Icon(p.icon, contentDescription = null) },
                    text = { Text(if (n == 0) "${p.title}: nothing to do" else "${p.title} ($n)") }
                )
            }
        }
    }
}

@Composable
fun OpsScreen(app: AppItem, onBack: () -> Unit) {
    var ops by remember { mutableStateOf<List<OpInfo>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var activeOnly by remember { mutableStateOf(true) }
    var editing by remember { mutableStateOf<OpInfo?>(null) }
    var flash by remember { mutableStateOf<Int?>(null) }
    val toast = rememberToast()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(flash) {
        if (flash != null) {
            delay(900)
            flash = null
        }
    }
    LaunchedEffect(app.pkg) {
        try {
            ops = withContext(Dispatchers.IO) { AppOpsRepo.getOps(app.uid, app.pkg) }
        } catch (t: Throwable) {
            error = android.util.Log.getStackTraceString(t)
        }
    }

    fun applyPreset(p: Preset) {
        val current = ops ?: return
        val changes = changesFor(p, current)
        if (changes.isEmpty()) {
            toast.show("${p.title}: nothing to change")
            return
        }
        scope.launch {
            try {
                val fresh = withContext(Dispatchers.IO) {
                    changes.forEach { (op, m) -> AppOpsRepo.setMode(app.uid, app.pkg, op.code, m) }
                    AppOpsRepo.getOps(app.uid, app.pkg)
                }
                ops = fresh
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                toast.show("${p.title}: ${changes.size} changed")
            } catch (t: Throwable) {
                toast.show("Failed: " + (t.cause ?: t).toString())
            }
        }
    }

    val target = editing
    if (target != null) {
        ModeSheet(
            title = prettyOp(target.name),
            subtitle = target.name,
            current = target.mode,
            onPick = { m ->
                scope.launch {
                    try {
                        val fresh = withContext(Dispatchers.IO) {
                            AppOpsRepo.setMode(app.uid, app.pkg, target.code, m)
                            AppOpsRepo.getOps(app.uid, app.pkg)
                        }
                        ops = fresh
                        flash = target.code
                        val now = fresh.first { it.code == target.code }.mode
                        val applied = now == m || (m == 3 && now == target.defaultMode)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        toast.show(
                            if (applied) "${prettyOp(target.name)}: ${modeLabel(m)}"
                            else "${prettyOp(target.name)}: system kept ${modeLabel(now)}"
                        )
                    } catch (t: Throwable) {
                        toast.show("Failed: " + (t.cause ?: t).toString())
                    }
                }
            },
            onDismiss = { editing = null }
        )
    }

    ScreenFrame(
        title = app.label,
        subtitle = app.pkg,
        onBack = onBack,
        overlay = {
            if (ops != null && error == null) {
                PresetsOverlay(
                    countFor = { p -> ops?.let { changesFor(p, it).size } ?: 0 },
                    onApply = { applyPreset(it) },
                    modifier = Modifier.fillMaxSize()
                )
            }
            ToastPill(
                visible = toast.shown,
                text = toast.text,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)
            )
        }
    ) {
        val err = error
        val list = ops
        when {
            err != null -> ErrorCard("Failed to read ops", err, Modifier.padding(horizontal = 16.dp))
            list == null -> SkeletonList(rows = 7, rowHeight = 64.dp)
            else -> {
                val shown = remember(list, activeOnly) {
                    list.filter { !activeOnly || it.active }
                        .sortedWith(compareByDescending<OpInfo> { it.lastAccess }.thenBy { it.name })
                }
                val activeCount = remember(list) { list.count { it.active } }
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 112.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item(key = "hero") { OpsHero(app, list) }
                    item(key = "filter") {
                        Column {
                            Spacer(Modifier.height(6.dp))
                            ConnectedGroup(
                                listOf("Active ($activeCount)", "All (${list.size})"),
                                if (activeOnly) 0 else 1,
                                { activeOnly = it == 0 }
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                    itemsIndexed(shown, key = { _, o -> o.code }) { i, op ->
                        OpRow(op, i, flash == op.code, Modifier.animateItem()) { editing = op }
                    }
                }
            }
        }
    }
}

@Composable
private fun OpsHero(app: AppItem, list: List<OpInfo>) {
    val cs = MaterialTheme.colorScheme
    val allow = modeColors(0)
    Surface(
        modifier = Modifier.fillMaxWidth().entrance(0),
        shape = RoundedCornerShape(32.dp),
        color = cs.surfaceContainerLow
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app.pkg, app.label, 64.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (app.system) "System app" else "Installed app",
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onSurface
                    )
                    Text(
                        "${list.size} operations",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                StatTile(
                    list.count { it.active }, "Active",
                    cs.primaryContainer, cs.onPrimaryContainer,
                    Modifier.weight(1f), groupShape(0, 3)
                )
                StatTile(
                    list.count { it.lastAccess > 0 }, "Used",
                    allow.container, allow.onContainer,
                    Modifier.weight(1f), groupShape(1, 3)
                )
                StatTile(
                    list.count { it.mode == 1 || it.mode == 2 }, "Restricted",
                    cs.errorContainer, cs.onErrorContainer,
                    Modifier.weight(1f), groupShape(2, 3)
                )
            }
        }
    }
}

@Composable
private fun OpRow(op: OpInfo, index: Int, flashed: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val mc = modeColors(op.mode)
    val accent by animateColorAsState(mc.accent, Motion.effects(), label = "stripe")
    val container by animateColorAsState(
        if (flashed) mc.container else cs.surfaceContainerLow,
        Motion.effects(),
        label = "flash"
    )
    val live = op.lastAccess > 0 && System.currentTimeMillis() - op.lastAccess < 5 * 60_000L
    PressSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().entrance(index),
        color = container,
        restCorner = 20.dp,
        pressedCorner = 10.dp
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(6.dp).height(36.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    prettyOp(op.name),
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (live) {
                        PulseDot(modeColors(0).accent, 6.dp)
                        Spacer(Modifier.width(2.dp))
                    }
                    Text(
                        relativeUsed(op.lastAccess),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            ModeChip(op.mode)
        }
    }
}

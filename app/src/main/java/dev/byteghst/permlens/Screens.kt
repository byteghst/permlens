package dev.byteghst.permlens

import android.content.pm.ApplicationInfo
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
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
                (slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) { it / 4 } + fadeIn(tween(340))) togetherWith
                    (slideOutHorizontally(tween(340)) { -it / 6 } + fadeOut(tween(220)))
            } else {
                (slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) { -it / 6 } + fadeIn(tween(340))) togetherWith
                    (slideOutHorizontally(tween(340)) { it / 4 } + fadeOut(tween(220)))
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
    val shown = remember(apps, query, filter) {
        apps?.filter {
            (filter == 2 || (filter == 1) == it.system) &&
                (it.label.contains(query, true) || it.pkg.contains(query, true))
        } ?: emptyList()
    }
    val count by animateIntAsState(shown.size, tween(500), label = "count")
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        GradientTitle("PermLens")
        Text(
            if (apps == null) "Scanning apps..." else "$count apps",
            color = Pal.TextLo,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            placeholder = { Text("Search apps or packages", color = Pal.TextLo) },
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
        Spacer(Modifier.height(12.dp))
        Segmented(listOf("User", "System", "All"), filter, onFilter)
        Spacer(Modifier.height(8.dp))

        when {
            apps == null -> SkeletonList()
            shown.isEmpty() -> Box(Modifier.fillMaxSize().padding(top = 60.dp), contentAlignment = Alignment.TopCenter) {
                Text("No apps match", color = Pal.TextLo, fontSize = 16.sp)
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp + navBottom),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(shown, key = { _, a -> a.pkg }) { i, app ->
                    AppRow(app, i, onPick, Modifier.animateItem())
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: AppItem, index: Int, onPick: (AppItem) -> Unit, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth()
            .entrance(index)
            .bouncyClick { onPick(app) }
            .glass()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(app.pkg, app.label, 46.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                app.label,
                color = Pal.TextHi,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(app.pkg, color = Pal.TextLo, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (app.system) {
            Spacer(Modifier.width(8.dp))
            Tag("SYSTEM")
        }
        Text("\u203A", color = Pal.TextLo, fontSize = 24.sp, modifier = Modifier.padding(start = 8.dp))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpsScreen(app: AppItem, onBack: () -> Unit) {
    var ops by remember { mutableStateOf<List<OpInfo>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var activeOnly by remember { mutableStateOf(true) }
    var editing by remember { mutableStateOf<OpInfo?>(null) }
    var flash by remember { mutableStateOf<Int?>(null) }
    var toastText by remember { mutableStateOf("") }
    var toastTick by remember { mutableStateOf(0) }
    var toastShown by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    fun showToast(t: String) {
        toastText = t
        toastTick++
        toastShown = true
    }

    LaunchedEffect(toastTick) {
        if (toastTick > 0) {
            delay(2600)
            toastShown = false
        }
    }
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

    val target = editing
    if (target != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { editing = null },
            sheetState = sheetState,
            containerColor = Pal.Surface,
            contentColor = Pal.TextHi
        ) {
            Column(
                Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(prettyOp(target.name), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Pal.TextHi)
                Text(target.name, fontSize = 12.sp, color = Pal.TextLo)
                Spacer(Modifier.height(4.dp))
                listOf(0, 1, 2, 4, 3).forEachIndexed { i, m ->
                    ModeOption(m, current = m == target.mode, index = i) {
                        scope.launch { sheetState.hide() }.invokeOnCompletion { editing = null }
                        scope.launch {
                            try {
                                val fresh = withContext(Dispatchers.IO) {
                                    AppOpsRepo.setMode(app.uid, app.pkg, target.code, m)
                                    AppOpsRepo.getOps(app.uid, app.pkg)
                                }
                                ops = fresh
                                flash = target.code
                                val now = fresh.first { it.code == target.code }.mode
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showToast(
                                    if (now == m) "${prettyOp(target.name)}: ${modeLabel(m)}"
                                    else "${prettyOp(target.name)}: system kept ${modeLabel(now)}"
                                )
                            } catch (t: Throwable) {
                                showToast("Failed: " + (t.cause ?: t).toString())
                            }
                        }
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.bouncyClick(onBack).glass(RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("\u2039", color = Pal.Cyan, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Text("Back", color = Pal.TextHi, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(14.dp))

            val err = error
            val list = ops

            Column(Modifier.fillMaxWidth().entrance(0).glass().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app.pkg, app.label, 60.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            app.label,
                            color = Pal.TextHi,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(app.pkg, color = Pal.TextLo, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (list != null) {
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(list.count { it.active }, "Active", Pal.Cyan, Modifier.weight(1f))
                        StatTile(list.count { it.lastAccess > 0 }, "Used", Pal.Allow, Modifier.weight(1f))
                        StatTile(list.count { it.mode == 1 || it.mode == 2 }, "Restricted", Pal.Deny, Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            when {
                err != null -> {
                    Column(Modifier.fillMaxWidth().glass().padding(16.dp)) {
                        Text("Failed to read ops", color = Pal.Deny, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.height(8.dp))
                        SelectionContainer(Modifier.verticalScroll(rememberScrollState())) {
                            Text(err, color = Pal.TextLo, fontSize = 12.sp)
                        }
                    }
                }
                list == null -> SkeletonList(rows = 7, rowHeight = 64.dp)
                else -> {
                    val shown = remember(list, activeOnly) {
                        list.filter { !activeOnly || it.active }
                            .sortedWith(compareByDescending<OpInfo> { it.lastAccess }.thenBy { it.name })
                    }
                    val activeCount = remember(list) { list.count { it.active } }
                    Segmented(
                        listOf("Active ($activeCount)", "All (${list.size})"),
                        if (activeOnly) 0 else 1,
                        { activeOnly = it == 0 }
                    )
                    Spacer(Modifier.height(4.dp))
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 90.dp + navBottom),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(shown, key = { _, o -> o.code }) { i, op ->
                            OpRow(op, i, flash == op.code, Modifier.animateItem()) { editing = op }
                        }
                    }
                }
            }
        }

        ToastPill(
            visible = toastShown,
            text = toastText,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp)
        )
    }
}

@Composable
private fun OpRow(op: OpInfo, index: Int, flashed: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color by animateColorAsState(modeColor(op.mode), tween(400), label = "stripe")
    val hl by animateColorAsState(
        if (flashed) modeColor(op.mode).copy(alpha = 0.28f) else Color.Transparent,
        tween(500),
        label = "flash"
    )
    val live = op.lastAccess > 0 && System.currentTimeMillis() - op.lastAccess < 5 * 60_000L
    Row(
        modifier.fillMaxWidth()
            .entrance(index)
            .bouncyClick(onClick)
            .glass()
            .background(hl)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(4.dp).height(36.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                prettyOp(op.name),
                color = Pal.TextHi,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (live) {
                    PulseDot(Pal.Allow, 6.dp)
                    Spacer(Modifier.width(2.dp))
                }
                Text(relativeUsed(op.lastAccess), color = Pal.TextLo, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.width(8.dp))
        ModeChip(op.mode)
    }
}

@Composable
private fun ModeOption(mode: Int, current: Boolean, index: Int, onClick: () -> Unit) {
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

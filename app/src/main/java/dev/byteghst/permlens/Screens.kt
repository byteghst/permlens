package dev.byteghst.permlens

import android.content.pm.ApplicationInfo
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
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

data class AppItem(val pkg: String, val label: String, val uid: Int, val system: Boolean)

@Composable
fun PermLensApp() {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<AppItem>?>(null) }
    var selected by remember { mutableStateOf<AppItem?>(null) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(0) } // 0 user, 1 system, 2 all

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

    val sel = selected
    if (sel == null) {
        AppListScreen(
            apps = apps,
            query = query,
            onQuery = { query = it },
            filter = filter,
            onFilter = { filter = it },
            onPick = { selected = it }
        )
    } else {
        BackHandler { selected = null }
        OpsScreen(sel, onBack = { selected = null })
    }
}

@Composable
fun AppListScreen(
    apps: List<AppItem>?,
    query: String,
    onQuery: (String) -> Unit,
    filter: Int,
    onFilter: (Int) -> Unit,
    onPick: (AppItem) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            label = { Text("Search apps") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            listOf("User", "System", "All").forEachIndexed { i, t ->
                if (filter == i) Button(onClick = { onFilter(i) }) { Text(t) }
                else OutlinedButton(onClick = { onFilter(i) }) { Text(t) }
            }
        }

        if (apps == null) {
            Text("Loading apps...")
            return
        }

        val shown = remember(apps, query, filter) {
            apps.filter {
                (filter == 2 || (filter == 1) == it.system) &&
                    (it.label.contains(query, true) || it.pkg.contains(query, true))
            }
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(shown, key = { it.pkg }) { app ->
                Column(
                    Modifier.fillMaxWidth().clickable { onPick(app) }.padding(vertical = 10.dp)
                ) {
                    Text(app.label, style = MaterialTheme.typography.bodyLarge)
                    Text(app.pkg, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun modeLabel(mode: Int) = when (mode) {
    0 -> "Allow"
    1 -> "Ignore"
    2 -> "Deny"
    3 -> "Default"
    4 -> "Foreground"
    else -> "Mode $mode"
}

@Composable
fun OpsScreen(app: AppItem, onBack: () -> Unit) {
    var ops by remember { mutableStateOf<List<OpInfo>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var activeOnly by remember { mutableStateOf(true) }
    var editing by remember { mutableStateOf<OpInfo?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(app.pkg) {
        try {
            ops = withContext(Dispatchers.IO) { AppOpsRepo.getOps(app.uid, app.pkg) }
        } catch (t: Throwable) {
            error = android.util.Log.getStackTraceString(t)
        }
    }

    val target = editing
    if (target != null) {
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(target.name) },
            text = {
                Column {
                    listOf(0, 1, 2, 4, 3).forEach { m ->
                        TextButton(onClick = {
                            editing = null
                            scope.launch {
                                try {
                                    val fresh = withContext(Dispatchers.IO) {
                                        AppOpsRepo.setMode(app.uid, app.pkg, target.code, m)
                                        AppOpsRepo.getOps(app.uid, app.pkg)
                                    }
                                    ops = fresh
                                    val now = fresh.first { it.code == target.code }.mode
                                    message = if (now == m) {
                                        "${target.name}: ${modeLabel(m)}"
                                    } else {
                                        "${target.name}: system kept ${modeLabel(now)}"
                                    }
                                } catch (t: Throwable) {
                                    message = "Failed: " + (t.cause ?: t).toString()
                                }
                            }
                        }) {
                            Text(
                                if (m == target.mode) "${modeLabel(m)} (current)" else modeLabel(m)
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
        )
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(modifier = Modifier.padding(top = 8.dp)) {
            TextButton(onClick = onBack) { Text("Back") }
        }
        Text(app.label, style = MaterialTheme.typography.titleLarge)
        Text(app.pkg, style = MaterialTheme.typography.bodySmall)

        val err = error
        val list = ops
        when {
            err != null -> {
                Text("Failed to read ops:", modifier = Modifier.padding(top = 12.dp))
                SelectionContainer(Modifier.verticalScroll(rememberScrollState())) {
                    Text(err, style = MaterialTheme.typography.bodySmall)
                }
            }
            list == null -> Text("Loading...", modifier = Modifier.padding(top = 12.dp))
            else -> {
                val shown = remember(list, activeOnly) {
                    list.filter { !activeOnly || it.active }
                        .sortedWith(compareByDescending<OpInfo> { it.lastAccess }.thenBy { it.name })
                }
                OutlinedButton(
                    onClick = { activeOnly = !activeOnly },
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Text(
                        if (activeOnly) "Showing active (${shown.size}). Show all"
                        else "Showing all (${shown.size}). Active only"
                    )
                }
                message?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 4.dp))
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(shown, key = { it.code }) { op ->
                        Column(
                            Modifier.fillMaxWidth().clickable { editing = op }.padding(vertical = 8.dp)
                        ) {
                            Text(op.name, style = MaterialTheme.typography.bodyLarge)
                            val used = if (op.lastAccess > 0) {
                                DateUtils.getRelativeTimeSpanString(
                                    op.lastAccess,
                                    System.currentTimeMillis(),
                                    DateUtils.MINUTE_IN_MILLIS
                                ).toString()
                            } else "never used"
                            Text(
                                "${modeLabel(op.mode)} - $used",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

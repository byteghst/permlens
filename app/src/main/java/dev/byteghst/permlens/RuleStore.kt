package dev.byteghst.permlens

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Rule(val pkg: String, val op: String, val mode: Int)

fun modeLabel(mode: Int) = when (mode) {
    0 -> "Allow"
    1 -> "Ignore"
    2 -> "Deny"
    3 -> "Default"
    4 -> "Foreground"
    else -> "Mode $mode"
}

/** Persists the modes the user set (rules) plus a change history. Rules are re-applied automatically. */
object RuleStore {
    private var dir: File? = null

    fun init(ctx: Context) {
        dir = ctx.applicationContext.filesDir
    }

    private fun rulesFile() = File(dir!!, "rules.json")
    private fun historyFile() = File(dir!!, "history.txt")

    private fun parse(text: String): List<Rule> {
        val arr = JSONArray(text)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Rule(o.getString("pkg"), o.getString("op"), o.getInt("mode"))
        }
    }

    private fun toJson(list: List<Rule>): String {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("pkg", it.pkg).put("op", it.op).put("mode", it.mode)) }
        return arr.toString(2)
    }

    @Synchronized
    fun rules(): List<Rule> {
        val f = rulesFile()
        if (!f.exists()) return emptyList()
        return runCatching { parse(f.readText()) }.getOrDefault(emptyList())
    }

    @Synchronized
    private fun save(list: List<Rule>) {
        rulesFile().writeText(toJson(list))
    }

    /** Records a change. Setting a rule to Default (3) removes it. */
    @Synchronized
    fun record(pkg: String, op: String, mode: Int) {
        val list = rules().filterNot { it.pkg == pkg && it.op == op }.toMutableList()
        if (mode != 3) list += Rule(pkg, op, mode)
        save(list)
        log("$pkg  $op -> ${modeLabel(mode)}")
    }

    fun exportJson(): String = toJson(rules())

    /** Merges rules from a backup. Returns how many rules the backup held. */
    @Synchronized
    fun importJson(text: String): Int {
        val incoming = parse(text)
        val merged = rules()
            .filterNot { r -> incoming.any { it.pkg == r.pkg && it.op == r.op } }
            .toMutableList()
        merged.addAll(incoming.filter { it.mode != 3 })
        save(merged)
        log("restored ${incoming.size} rules from backup")
        return incoming.size
    }

    /** Re-applies every saved rule. Returns (applied, failed). Call off the main thread. */
    fun applyAll(ctx: Context): Pair<Int, Int> {
        val pm = ctx.packageManager
        var ok = 0
        var fail = 0
        for (r in rules()) {
            try {
                val uid = pm.getApplicationInfo(r.pkg, 0).uid
                val code = AppOpsRepo.codeOf(r.op) ?: continue
                AppOpsRepo.setMode(uid, r.pkg, code, r.mode)
                ok++
            } catch (t: Throwable) {
                fail++
            }
        }
        return Pair(ok, fail)
    }

    @Synchronized
    fun history(): List<String> {
        val f = historyFile()
        return if (f.exists()) f.readLines() else emptyList()
    }

    @Synchronized
    fun log(line: String) {
        runCatching {
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
            val lines = history().toMutableList()
            lines.add(0, "$stamp  $line")
            historyFile().writeText(lines.take(300).joinToString("\n"))
        }
    }
}

package dev.byteghst.permlens

import android.os.IBinder
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

data class OpInfo(
    val code: Int,
    val name: String,
    val mode: Int,
    val defaultMode: Int,
    val lastAccess: Long
) {
    val active: Boolean get() = lastAccess > 0 || mode != defaultMode
}

data class OpEntryInfo(
    val pkg: String,
    val uid: Int,
    val code: Int,
    val name: String,
    val mode: Int,
    val lastAccess: Long
)

object AppOpsRepo {

    init {
        HiddenApiBypass.addHiddenApiExemptions("L")
    }

    private val intType = Int::class.javaPrimitiveType!!

    private val aomClass: Class<*> by lazy { Class.forName("android.app.AppOpsManager") }
    private val serviceClass: Class<*> by lazy { Class.forName("com.android.internal.app.IAppOpsService") }
    private val packageOpsClass: Class<*> by lazy { Class.forName("android.app.AppOpsManager\$PackageOps") }
    private val opEntryClass: Class<*> by lazy { Class.forName("android.app.AppOpsManager\$OpEntry") }

    private val service: Any by lazy {
        val binder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService("appops"))
        Class.forName("com.android.internal.app.IAppOpsService\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, binder)!!
    }

    private val numOps: Int by lazy { aomClass.getField("_NUM_OP").getInt(null) }
    private val opToName by lazy { aomClass.getMethod("opToPublicName", intType) }
    private val opToInternalName by lazy { aomClass.getMethod("opToName", intType) }
    private val opToDefault by lazy { aomClass.getMethod("opToDefaultMode", intType) }

    private val getOpsMethod by lazy {
        serviceClass.getMethod("getOpsForPackage", intType, String::class.java, IntArray::class.java)
    }
    private val getPackagesForOpsMethod by lazy {
        serviceClass.getMethod("getPackagesForOps", IntArray::class.java)
    }
    private val setModeMethod by lazy {
        serviceClass.getMethod("setMode", intType, intType, String::class.java, intType)
    }

    private val pkgGetName by lazy { packageOpsClass.getMethod("getPackageName") }
    private val pkgGetUid by lazy { packageOpsClass.getMethod("getUid") }
    private val pkgGetOps by lazy { packageOpsClass.getMethod("getOps") }
    private val entryGetOp by lazy { opEntryClass.getMethod("getOp") }
    private val entryGetMode by lazy { opEntryClass.getMethod("getMode") }
    private val entryGetLast by lazy { opEntryClass.getMethod("getLastAccessTime", intType) }

    /** Public name, else the internal name (e.g. COARSE_LOCATION), else op_<code>. Never blank. */
    private fun nameFor(code: Int): String {
        val pub = runCatching { opToName.invoke(null, code) as? String }.getOrNull()
            ?.removePrefix("android:")
            ?.takeIf { it.isNotBlank() }
        if (pub != null) return pub
        val internal = runCatching { opToInternalName.invoke(null, code) as? String }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.lowercase()
        return internal ?: "op_$code"
    }

    private fun isRetired(name: String) = name.contains("deprecated", ignoreCase = true)

    private val defaults by lazy { IntArray(numOps) { (opToDefault.invoke(null, it) as? Int) ?: 3 } }
    private val names by lazy { Array(numOps) { nameFor(it) } }
    private val nameToCode by lazy {
        HashMap<String, Int>().also { m -> names.forEachIndexed { i, n -> m[n] = i } }
    }

    /** Real ops only (retired DEPRECATED_* placeholder slots are left out). */
    fun opNames(): List<Pair<Int, String>> =
        names.mapIndexedNotNull { i, n -> if (isRetired(n)) null else i to n }

    fun codeOf(name: String): Int? = nameToCode[name]

    private fun entriesOf(pops: Any): List<OpEntryInfo> {
        val pkg = pkgGetName.invoke(pops) as String
        val uid = pkgGetUid.invoke(pops) as Int
        val entries = pkgGetOps.invoke(pops) as? List<*> ?: return emptyList()
        val out = ArrayList<OpEntryInfo>(entries.size)
        for (e in entries) {
            if (e == null) continue
            val code = entryGetOp.invoke(e) as Int
            if (code < 0 || code >= numOps) continue
            val mode = entryGetMode.invoke(e) as Int
            val last = runCatching { entryGetLast.invoke(e, 31) as Long }.getOrDefault(0L)
            out.add(OpEntryInfo(pkg, uid, code, names[code], mode, last))
        }
        return out
    }

    /**
     * Returns every real op for the package; ops never touched get their default mode and
     * lastAccess 0. Retired placeholder slots (DEPRECATED_*) are left out.
     */
    fun getOps(uid: Int, pkg: String): List<OpInfo> {
        val present = HashMap<Int, OpEntryInfo>()
        val packages = getOpsMethod.invoke(service, uid, pkg, null) as? List<*>
        packages?.forEach { p ->
            if (p != null) entriesOf(p).forEach { present[it.code] = it }
        }
        return (0 until numOps).mapNotNull { code ->
            val name = names[code]
            if (isRetired(name)) return@mapNotNull null
            val p = present[code]
            OpInfo(code, name, p?.mode ?: defaults[code], defaults[code], p?.lastAccess ?: 0L)
        }
    }

    /** Entries across all packages for the given ops (null = every op). Only ops with a record are listed. */
    fun getEntries(codes: IntArray?): List<OpEntryInfo> {
        val packages = getPackagesForOpsMethod.invoke(service, *arrayOf<Any?>(codes)) as? List<*>
            ?: return emptyList()
        val out = ArrayList<OpEntryInfo>()
        packages.forEach { p -> if (p != null) out.addAll(entriesOf(p)) }
        return out.filterNot { isRetired(it.name) }
    }

    /** Sets the mode for one op. Modes: 0 allow, 1 ignore, 2 deny, 3 default, 4 foreground. */
    fun setMode(uid: Int, pkg: String, code: Int, mode: Int) {
        setModeMethod.invoke(service, code, uid, pkg, mode)
    }
}

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

object AppOpsRepo {

    init {
        HiddenApiBypass.addHiddenApiExemptions("L")
    }

    private val intType = Int::class.javaPrimitiveType!!

    private val aomClass: Class<*> by lazy { Class.forName("android.app.AppOpsManager") }
    private val serviceClass: Class<*> by lazy { Class.forName("com.android.internal.app.IAppOpsService") }

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
    private val setModeMethod by lazy {
        serviceClass.getMethod("setMode", intType, intType, String::class.java, intType)
    }

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

    /**
     * Returns every real op for the package; ops never touched get their default mode and
     * lastAccess 0. Retired placeholder slots (DEPRECATED_*) are left out.
     */
    fun getOps(uid: Int, pkg: String): List<OpInfo> {
        val present = HashMap<Int, Pair<Int, Long>>()
        val packages = getOpsMethod.invoke(service, uid, pkg, null) as? List<*>

        packages?.forEach { pops ->
            val entries = pops!!.javaClass.getMethod("getOps").invoke(pops) as? List<*>
                ?: return@forEach
            entries.forEach { e ->
                val c = e!!.javaClass
                val code = c.getMethod("getOp").invoke(e) as Int
                val mode = c.getMethod("getMode").invoke(e) as Int
                val last = runCatching {
                    c.getMethod("getLastAccessTime", intType).invoke(e, 31) as Long
                }.getOrDefault(0L)
                present[code] = Pair(mode, last)
            }
        }

        return (0 until numOps).mapNotNull { code ->
            val name = nameFor(code)
            if (name.contains("deprecated", ignoreCase = true)) return@mapNotNull null
            val p = present[code]
            val def = (opToDefault.invoke(null, code) as? Int) ?: 3
            OpInfo(code, name, p?.first ?: def, def, p?.second ?: 0L)
        }
    }

    /** Sets the mode for one op. Modes: 0 allow, 1 ignore, 2 deny, 3 default, 4 foreground. */
    fun setMode(uid: Int, pkg: String, code: Int, mode: Int) {
        setModeMethod.invoke(service, code, uid, pkg, mode)
    }
}

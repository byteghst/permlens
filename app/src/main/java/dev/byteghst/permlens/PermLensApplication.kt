package dev.byteghst.permlens

import android.app.Application
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

class PermLensApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        RuleStore.init(this)

        // When Shizuku (re)starts it wakes this process; re-apply saved rules without opening the UI.
        Shizuku.addBinderReceivedListenerSticky {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                Thread {
                    val (ok, fail) = RuleStore.applyAll(this)
                    if (ok + fail > 0) RuleStore.log("auto re-apply: $ok ok, $fail failed")
                }.start()
            }
        }
    }
}

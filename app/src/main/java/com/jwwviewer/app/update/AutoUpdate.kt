package com.jwwviewer.app.update

import android.content.Context
import android.widget.Toast
import com.jwwviewer.app.BuildConfig
import com.jwwviewer.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * On each cold start: check GitHub latest release; if newer APK exists, download and open installer.
 * Already-latest stays silent. Progress / errors use Toast.
 */
object AutoUpdate {
    private val started = AtomicBoolean(false)

    suspend fun runOncePerProcess(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext
        try {
            // Let first screen / PDF open settle before using network + disk heavily
            delay(2_500)
            val result = withContext(Dispatchers.IO) {
                UpdateChecker.check(BuildConfig.VERSION_NAME)
            }
            if (!result.newer) return
            val apkUrl = result.apkUrl
            if (apkUrl.isNullOrBlank()) {
                toast(app, app.getString(R.string.update_no_apk, result.tagName))
                return
            }
            toast(app, app.getString(R.string.update_found, result.tagName))
            val file = withContext(Dispatchers.IO) {
                UpdateChecker.downloadApk(app, apkUrl) { pct ->
                    if (pct == 0 || pct == 100 || pct % 25 == 0) {
                        toast(app, app.getString(R.string.update_downloading, pct))
                    }
                }
            }
            toast(app, app.getString(R.string.update_installing))
            withContext(Dispatchers.Main) {
                UpdateInstaller.installApk(
                    context = app,
                    file = file,
                    needPermMessage = app.getString(R.string.update_need_install_perm),
                )
            }
        } catch (_: Exception) {
            // Quiet on auto path — manual check in Settings still reports errors.
            started.set(false) // allow retry next launch / resume
        }
    }

    private fun toast(context: Context, msg: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
}

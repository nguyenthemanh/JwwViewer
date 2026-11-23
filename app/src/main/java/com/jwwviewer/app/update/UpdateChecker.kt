package com.jwwviewer.app.update

import android.content.Context
import com.jwwviewer.app.BuildConfig
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {
    data class Result(
        val tagName: String,
        val htmlUrl: String,
        val apkUrl: String?,
        val newer: Boolean,
    )

    fun check(
        currentVersionName: String,
        repo: String = BuildConfig.GITHUB_REPO,
    ): Result {
        val url = URL("https://api.github.com/repos/$repo/releases/latest")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "JwwViewer-Android")
        }
        try {
            val code = conn.responseCode
            if (code == 404) {
                return Result(
                    currentVersionName,
                    "https://github.com/$repo/releases",
                    apkUrl = null,
                    newer = false,
                )
            }
            if (code !in 200..299) error("HTTP $code")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tag = json.optString("tag_name", "").removePrefix("v").trim()
            val html = json.optString("html_url", "https://github.com/$repo/releases")
            val assets = json.optJSONArray("assets")
            var apkUrl: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    val name = a.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = a.optString("browser_download_url", "").ifBlank { null }
                        if (apkUrl != null) break
                    }
                }
            }
            val newer = compareVersions(tag, currentVersionName) > 0
            return Result(tag.ifBlank { currentVersionName }, html, apkUrl, newer)
        } finally {
            conn.disconnect()
        }
    }

    fun downloadApk(
        context: Context,
        apkUrl: String,
        onProgress: ((Int) -> Unit)? = null,
    ): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val out = File(dir, "JwwViewer-update.apk")
        if (out.exists()) out.delete()
        val conn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 120_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "JwwViewer-Android")
            setRequestProperty("Accept", "application/octet-stream")
        }
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong.coerceAtLeast(0L)
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var readTotal = 0L
                    var lastPct = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        readTotal += n
                        if (total > 0 && onProgress != null) {
                            val pct = ((readTotal * 100) / total).toInt().coerceIn(0, 100)
                            if (pct != lastPct) {
                                lastPct = pct
                                onProgress(pct)
                            }
                        }
                    }
                    output.flush()
                }
            }
            return out
        } finally {
            conn.disconnect()
        }
    }

    /** Positive if a > b. Supports dotted numeric versions like 1.0.1-beta. */
    fun compareVersions(a: String, b: String): Int {
        val pa = a.trim().removePrefix("v").split('.', '-', '_').mapNotNull { it.toIntOrNull() }
        val pb = b.trim().removePrefix("v").split('.', '-', '_').mapNotNull { it.toIntOrNull() }
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }
}

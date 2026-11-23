package com.jwwviewer.app.translate

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

object OnlineTranslator {
    private val cache = ConcurrentHashMap<String, String>()

    fun translateGoogle(text: String, target: TranslateTarget, timeoutMs: Int = 5_000): String =
        translateProtected(text, target, "google") { protected, tl ->
            val q = URLEncoder.encode(protected.take(450), StandardCharsets.UTF_8.name())
            val url = URL(
                "https://translate.googleapis.com/translate_a/single" +
                    "?client=gtx&sl=ja&tl=$tl&dt=t&q=$q",
            )
            val body = httpGet(url, timeoutMs)
            parseGtx(body)
        }

    fun translateMyMemory(text: String, target: TranslateTarget, timeoutMs: Int = 5_000): String =
        translateProtected(text, target, "mymemory") { protected, tl ->
            val pair = when (tl) {
                "vi" -> "ja|vi"
                "zh-CN" -> "ja|zh-CN"
                else -> "ja|en"
            }
            val q = URLEncoder.encode(protected.take(450), StandardCharsets.UTF_8.name())
            val url = URL("https://api.mymemory.translated.net/get?q=$q&langpair=$pair")
            val body = httpGet(url, timeoutMs)
            val json = JSONObject(body)
            val status = json.optInt("responseStatus", 0)
            val translated = json.optJSONObject("responseData")
                ?.optString("translatedText")
                ?.trim()
                .orEmpty()
            if (status != 200 || translated.isBlank()) {
                error(json.optString("responseDetails").ifBlank { "MyMemory failed" })
            }
            if (translated.contains("MYMEMORY WARNING", ignoreCase = true)) {
                error(translated)
            }
            translated
        }

    private inline fun translateProtected(
        text: String,
        target: TranslateTarget,
        engineKey: String,
        fetch: (protected: String, tl: String) -> String,
    ): String {
        val cleaned = normalizeCadText(text)
        if (cleaned.isBlank()) return cleaned
        CadGlossary.lookupExact(cleaned, target)?.let { return it }

        val tl = when (target) {
            TranslateTarget.VI -> "vi"
            TranslateTarget.EN -> "en"
            TranslateTarget.ZH -> "zh-CN"
        }
        val cacheKey = "$engineKey::$tl::$cleaned"
        cache[cacheKey]?.let { return it }

        val (protected, parts) = CadGlossary.protect(cleaned, target)
        if (parts.isNotEmpty() && !protected.contains(Regex("[\\u3040-\\u30ff\\u4e00-\\u9fff]"))) {
            val onlyGlossary = CadGlossary.restore(protected, parts)
            cache[cacheKey] = onlyGlossary
            return onlyGlossary
        }

        val raw = fetch(protected.ifBlank { cleaned }, tl)
        if (raw.isBlank()) error("Empty translation")
        val finalText = CadGlossary.restore(raw, parts).trim()
        cache[cacheKey] = finalText
        return finalText
    }

    private fun httpGet(url: URL, timeoutMs: Int): String {
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0")
            setRequestProperty("Accept", "*/*")
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }
                ?: error("Empty response")
            if (code !in 200..299) error("HTTP $code")
            return body
        } finally {
            conn.disconnect()
        }
    }

    private fun parseGtx(body: String): String {
        val root = JSONArray(body)
        val sentences = root.optJSONArray(0) ?: return ""
        val sb = StringBuilder()
        for (i in 0 until sentences.length()) {
            val row = sentences.optJSONArray(i) ?: continue
            sb.append(row.optString(0))
        }
        return sb.toString().trim()
    }
}

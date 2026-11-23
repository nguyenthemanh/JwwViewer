package com.jwwviewer.app.translate

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.jwwviewer.app.data.AppLanguage
import com.jwwviewer.app.data.TranslateEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

enum class TranslateTarget(val mlKitTag: String) {
    VI(TranslateLanguage.VIETNAMESE),
    EN(TranslateLanguage.ENGLISH),
    ZH(TranslateLanguage.CHINESE),
}

object JaTranslator {
    private val mlCache = ConcurrentHashMap<String, Translator>()

    fun targetFor(language: AppLanguage): TranslateTarget? {
        val tag = when (language) {
            AppLanguage.VI -> "vi"
            AppLanguage.EN -> "en"
            AppLanguage.ZH -> "zh"
            AppLanguage.JA -> return null
            AppLanguage.SYSTEM -> {
                val sys = Locale.getDefault().toLanguageTag().lowercase()
                when {
                    sys.startsWith("vi") -> "vi"
                    sys.startsWith("zh") -> "zh"
                    sys.startsWith("ja") -> return null
                    else -> "en"
                }
            }
        }
        return when (tag) {
            "vi" -> TranslateTarget.VI
            "zh" -> TranslateTarget.ZH
            else -> TranslateTarget.EN
        }
    }

    suspend fun translateJa(
        text: String,
        target: TranslateTarget,
        engine: TranslateEngine = TranslateEngine.GOOGLE,
    ): String = withContext(Dispatchers.IO) {
        CadGlossary.lookupExact(text, target)?.let { return@withContext it }
        when (engine) {
            TranslateEngine.GOOGLE -> OnlineTranslator.translateGoogle(text, target)
            TranslateEngine.MYMEMORY -> OnlineTranslator.translateMyMemory(text, target)
            TranslateEngine.ML_KIT -> translateMlKit(text, target)
            TranslateEngine.AUTO -> {
                val errors = mutableListOf<String>()
                runCatching { OnlineTranslator.translateGoogle(text, target, 4_000) }
                    .onSuccess { return@withContext it }
                    .onFailure { errors += "Google: ${it.message}" }
                runCatching { OnlineTranslator.translateMyMemory(text, target, 4_000) }
                    .onSuccess { return@withContext it }
                    .onFailure { errors += "MyMemory: ${it.message}" }
                runCatching { translateMlKit(text, target) }
                    .onSuccess { return@withContext it }
                    .onFailure { errors += "ML Kit: ${it.message}" }
                error(errors.joinToString(" | ").ifBlank { "All engines failed" })
            }
        }
    }

    private suspend fun translateMlKit(text: String, target: TranslateTarget): String {
        val cleaned = normalizeCadText(text)
        if (cleaned.isBlank()) return cleaned
        val (protected, parts) = CadGlossary.protect(cleaned, target)
        if (parts.isNotEmpty() && !protected.contains(Regex("[\\u3040-\\u30ff\\u4e00-\\u9fff]"))) {
            return CadGlossary.restore(protected, parts)
        }
        val translator = mlCache.getOrPut(target.mlKitTag) {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.JAPANESE)
                .setTargetLanguage(target.mlKitTag)
                .build()
            Translation.getClient(options)
        }
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
        val raw = translator.translate(protected.ifBlank { cleaned }).await()
        return CadGlossary.restore(raw, parts).trim()
    }
}

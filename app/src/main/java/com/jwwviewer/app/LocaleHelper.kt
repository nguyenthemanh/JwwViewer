package com.jwwviewer.app

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.os.LocaleListCompat
import com.jwwviewer.app.data.AppLanguage
import java.util.Locale

object LocaleHelper {
    fun tagFor(language: AppLanguage): String? = when (language) {
        AppLanguage.SYSTEM -> null
        AppLanguage.VI -> "vi"
        AppLanguage.EN -> "en"
        AppLanguage.JA -> "ja"
        AppLanguage.ZH -> "zh-CN"
    }

    fun localeFor(language: AppLanguage): Locale {
        val tag = tagFor(language)
        return if (tag == null) {
            Resources.getSystem().configuration.locales[0] ?: Locale.getDefault()
        } else {
            Locale.forLanguageTag(tag)
        }
    }

    /**
     * Official per-app language. Call only when the setting changes
     * (LaunchedEffect), never every frame — that caused UI flicker before.
     */
    fun applyAppCompatLocales(language: AppLanguage) {
        val tag = tagFor(language)
        val wanted = if (tag == null) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        val current = AppCompatDelegate.getApplicationLocales()
        if (current.toLanguageTags() != wanted.toLanguageTags()) {
            AppCompatDelegate.setApplicationLocales(wanted)
        }
    }

    fun wrap(base: Context, language: AppLanguage): Context {
        val locale = localeFor(language)
        val locales = android.os.LocaleList(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocales(locales)
        config.setLocale(locale)
        val localized = base.createConfigurationContext(config)
        return object : ContextWrapper(base) {
            override fun getResources(): Resources = localized.resources
            override fun getAssets(): AssetManager = localized.assets
        }
    }
}

/**
 * Keeps Compose [stringResource] on the selected language.
 * Also syncs AppCompat locales once so Activity resources / tools match.
 */
@Composable
fun ProvideAppLocale(language: AppLanguage, content: @Composable () -> Unit) {
    LaunchedEffect(language) {
        LocaleHelper.applyAppCompatLocales(language)
    }

    val base = LocalContext.current
    val localized = remember(language) { LocaleHelper.wrap(base, language) }
    val configuration = remember(language, localized) {
        Configuration(localized.resources.configuration)
    }
    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides configuration,
    ) {
        key(language.tag) {
            content()
        }
    }
}

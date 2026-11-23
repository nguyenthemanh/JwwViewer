package com.jwwviewer.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("jww_settings")

enum class AppLanguage(val tag: String) {
    SYSTEM("system"),
    VI("vi"),
    EN("en"),
    JA("ja"),
    ZH("zh"),
    ;

    companion object {
        fun fromTag(tag: String): AppLanguage =
            entries.firstOrNull { it.tag == tag } ?: SYSTEM
    }
}

/**
 * Device performance profile — tunes CAD/PDF rendering cost.
 * LOW: máy yếu / tiết kiệm pin
 * BALANCED: mặc định
 * HIGH: máy mạnh, chất lượng cao
 */
enum class PerformanceMode(val tag: String) {
    LOW("low"),
    BALANCED("balanced"),
    HIGH("high"),
    ;

    /** PDF page bitmap scale vs native page size */
    val pdfRasterScale: Float
        get() = when (this) {
            LOW -> 1f
            BALANCED -> 2f
            HIGH -> 3f
        }

    val drawTexts: Boolean
        get() = this != LOW

    val drawPoints: Boolean
        get() = this != LOW

    val drawSolids: Boolean
        get() = true

    val antiAlias: Boolean
        get() = this != LOW

    val arcStepsDivisor: Double
        get() = when (this) {
            LOW -> PI_COARSE
            BALANCED -> PI_MID
            HIGH -> PI_FINE
        }

    val maxTexts: Int
        get() = when (this) {
            LOW -> 0
            BALANCED -> 2_000
            HIGH -> 20_000
        }

    companion object {
        private val PI_COARSE = Math.PI / 12
        private val PI_MID = Math.PI / 24
        private val PI_FINE = Math.PI / 36

        fun fromTag(tag: String): PerformanceMode =
            entries.firstOrNull { it.tag == tag } ?: BALANCED
    }
}

data class AppSettings(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val performance: PerformanceMode = PerformanceMode.BALANCED,
    val translateEngine: TranslateEngine = TranslateEngine.GOOGLE,
    /** UI + CAD text multiplier (0.75 … 2.0) */
    val fontScale: Float = 1f,
    /** CAD line thickness multiplier (0.5 … 3.0) */
    val strokeScale: Float = 1f,
    /** Fit-to-screen padding in dp (24 … 120) */
    val fitPaddingDp: Float = 48f,
)

class AppSettingsStore(private val context: Context) {
    private val keyLang = stringPreferencesKey("language")
    private val keyPerf = stringPreferencesKey("performance")
    private val keyTranslate = stringPreferencesKey("translate_engine")
    private val keyFont = floatPreferencesKey("font_scale")
    private val keyStroke = floatPreferencesKey("stroke_scale")
    private val keyFit = floatPreferencesKey("fit_padding_dp")

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { p ->
        AppSettings(
            language = AppLanguage.fromTag(p[keyLang] ?: AppLanguage.SYSTEM.tag),
            performance = PerformanceMode.fromTag(p[keyPerf] ?: PerformanceMode.BALANCED.tag),
            translateEngine = TranslateEngine.fromTag(
                p[keyTranslate] ?: TranslateEngine.GOOGLE.tag,
            ),
            fontScale = (p[keyFont] ?: 1f).coerceIn(0.75f, 2f),
            strokeScale = (p[keyStroke] ?: 1f).coerceIn(0.5f, 3f),
            fitPaddingDp = (p[keyFit] ?: 48f).coerceIn(24f, 120f),
        )
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.settingsDataStore.edit { it[keyLang] = language.tag }
    }

    suspend fun setPerformance(mode: PerformanceMode) {
        context.settingsDataStore.edit { it[keyPerf] = mode.tag }
    }

    suspend fun setTranslateEngine(engine: TranslateEngine) {
        context.settingsDataStore.edit { it[keyTranslate] = engine.tag }
    }

    suspend fun setFontScale(value: Float) {
        context.settingsDataStore.edit { it[keyFont] = value.coerceIn(0.75f, 2f) }
    }

    suspend fun setStrokeScale(value: Float) {
        context.settingsDataStore.edit { it[keyStroke] = value.coerceIn(0.5f, 3f) }
    }

    suspend fun setFitPaddingDp(value: Float) {
        context.settingsDataStore.edit { it[keyFit] = value.coerceIn(24f, 120f) }
    }
}

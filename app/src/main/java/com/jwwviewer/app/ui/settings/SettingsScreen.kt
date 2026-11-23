package com.jwwviewer.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jwwviewer.app.R
import com.jwwviewer.app.data.AppLanguage
import com.jwwviewer.app.data.AppSettings
import com.jwwviewer.app.data.PerformanceMode
import com.jwwviewer.app.data.TranslateEngine
import com.jwwviewer.app.ui.adaptive.AdaptiveLayout
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    adaptive: AdaptiveLayout,
    onBack: () -> Unit,
    onLanguage: (AppLanguage) -> Unit,
    onPerformance: (PerformanceMode) -> Unit,
    onTranslateEngine: (TranslateEngine) -> Unit,
    onFontScale: (Float) -> Unit,
    onStrokeScale: (Float) -> Unit,
    onFitPadding: (Float) -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (adaptive.contentMaxWidth != null) {
                        Modifier.widthIn(max = adaptive.contentMaxWidth)
                    } else {
                        Modifier
                    },
                )
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = adaptive.horizontalPadding, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                    )
                }
                Text(
                    text = stringResource(R.string.settings),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }

            if (adaptive.useDualPane) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        LanguageBlock(settings, onLanguage)
                        Spacer(modifier = Modifier.height(24.dp))
                        TranslateEngineBlock(settings, onTranslateEngine)
                        Spacer(modifier = Modifier.height(24.dp))
                        PerformanceBlock(settings, onPerformance)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        DisplayBlock(settings, onFontScale, onStrokeScale, onFitPadding)
                    }
                }
            } else {
                LanguageBlock(settings, onLanguage)
                Spacer(modifier = Modifier.height(28.dp))
                TranslateEngineBlock(settings, onTranslateEngine)
                Spacer(modifier = Modifier.height(28.dp))
                PerformanceBlock(settings, onPerformance)
                Spacer(modifier = Modifier.height(28.dp))
                DisplayBlock(settings, onFontScale, onStrokeScale, onFitPadding)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageBlock(
    settings: AppSettings,
    onLanguage: (AppLanguage) -> Unit,
) {
    Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleLarge)
    Spacer(modifier = Modifier.height(8.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LangChip(R.string.lang_system, settings.language == AppLanguage.SYSTEM) {
            onLanguage(AppLanguage.SYSTEM)
        }
        LangChip(R.string.lang_vi, settings.language == AppLanguage.VI) {
            onLanguage(AppLanguage.VI)
        }
        LangChip(R.string.lang_en, settings.language == AppLanguage.EN) {
            onLanguage(AppLanguage.EN)
        }
        LangChip(R.string.lang_ja, settings.language == AppLanguage.JA) {
            onLanguage(AppLanguage.JA)
        }
        LangChip(R.string.lang_zh, settings.language == AppLanguage.ZH) {
            onLanguage(AppLanguage.ZH)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TranslateEngineBlock(
    settings: AppSettings,
    onTranslateEngine: (TranslateEngine) -> Unit,
) {
    Text(stringResource(R.string.settings_translate), style = MaterialTheme.typography.titleLarge)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.settings_translate_hint),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LangChip(R.string.translate_engine_google, settings.translateEngine == TranslateEngine.GOOGLE) {
            onTranslateEngine(TranslateEngine.GOOGLE)
        }
        LangChip(R.string.translate_engine_mymemory, settings.translateEngine == TranslateEngine.MYMEMORY) {
            onTranslateEngine(TranslateEngine.MYMEMORY)
        }
        LangChip(R.string.translate_engine_mlkit, settings.translateEngine == TranslateEngine.ML_KIT) {
            onTranslateEngine(TranslateEngine.ML_KIT)
        }
        LangChip(R.string.translate_engine_auto, settings.translateEngine == TranslateEngine.AUTO) {
            onTranslateEngine(TranslateEngine.AUTO)
        }
    }
}

@Composable
private fun PerformanceBlock(
    settings: AppSettings,
    onPerformance: (PerformanceMode) -> Unit,
) {
    Text(stringResource(R.string.settings_performance), style = MaterialTheme.typography.titleLarge)
    Spacer(modifier = Modifier.height(8.dp))
    PerfOption(
        title = stringResource(R.string.perf_low),
        desc = stringResource(R.string.perf_low_desc),
        selected = settings.performance == PerformanceMode.LOW,
        onClick = { onPerformance(PerformanceMode.LOW) },
    )
    PerfOption(
        title = stringResource(R.string.perf_balanced),
        desc = stringResource(R.string.perf_balanced_desc),
        selected = settings.performance == PerformanceMode.BALANCED,
        onClick = { onPerformance(PerformanceMode.BALANCED) },
    )
    PerfOption(
        title = stringResource(R.string.perf_high),
        desc = stringResource(R.string.perf_high_desc),
        selected = settings.performance == PerformanceMode.HIGH,
        onClick = { onPerformance(PerformanceMode.HIGH) },
    )
}

@Composable
private fun DisplayBlock(
    settings: AppSettings,
    onFontScale: (Float) -> Unit,
    onStrokeScale: (Float) -> Unit,
    onFitPadding: (Float) -> Unit,
) {
    Text(stringResource(R.string.settings_display), style = MaterialTheme.typography.titleLarge)
    Spacer(modifier = Modifier.height(12.dp))
    SettingSlider(
        label = stringResource(R.string.settings_font_scale),
        value = settings.fontScale,
        valueRange = 0.75f..2f,
        valueLabel = String.format("%.2fx", settings.fontScale),
        onValueChange = onFontScale,
    )
    Text(
        text = stringResource(R.string.settings_preview),
        style = MaterialTheme.typography.bodyLarge,
        fontSize = (16 * settings.fontScale).sp,
        modifier = Modifier.padding(bottom = 16.dp),
    )
    SettingSlider(
        label = stringResource(R.string.settings_stroke_scale),
        value = settings.strokeScale,
        valueRange = 0.5f..3f,
        valueLabel = String.format("%.2fx", settings.strokeScale),
        onValueChange = onStrokeScale,
    )
    SettingSlider(
        label = stringResource(R.string.settings_fit_padding),
        value = settings.fitPaddingDp,
        valueRange = 24f..120f,
        valueLabel = "${settings.fitPaddingDp.roundToInt()} dp",
        onValueChange = onFitPadding,
    )
}

@Composable
private fun PerfOption(
    title: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    desc,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    )
}

@Composable
private fun LangChip(labelRes: Int, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(stringResource(labelRes)) },
    )
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(valueLabel, style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
        )
    }
}

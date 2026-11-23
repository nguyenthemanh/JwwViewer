package com.jwwviewer.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.jwwviewer.app.BuildConfig
import com.jwwviewer.app.R
import com.jwwviewer.app.data.AppLanguage
import com.jwwviewer.app.data.AppSettings
import com.jwwviewer.app.data.PerformanceMode
import com.jwwviewer.app.data.TranslateEngine
import com.jwwviewer.app.ui.adaptive.AdaptiveLayout
import com.jwwviewer.app.update.UpdateChecker
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                        Spacer(modifier = Modifier.height(24.dp))
                        AboutBlock()
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
                Spacer(modifier = Modifier.height(28.dp))
                AboutBlock()
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
private fun AboutBlock() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val checking = stringResource(R.string.update_checking)
    val latestFmt = stringResource(R.string.update_latest)
    val foundFmt = stringResource(R.string.update_found)
    val downloadingFmt = stringResource(R.string.update_downloading)
    val installing = stringResource(R.string.update_installing)
    val noApkFmt = stringResource(R.string.update_no_apk)
    val failFmt = stringResource(R.string.update_fail)
    val needPerm = stringResource(R.string.update_need_install_perm)

    Text(stringResource(R.string.section_about), style = MaterialTheme.typography.titleLarge)
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
        style = MaterialTheme.typography.bodyLarge,
    )
    Text(
        text = stringResource(R.string.github_repo_label, BuildConfig.GITHUB_REPO),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
    )

    Text(stringResource(R.string.section_author), style = MaterialTheme.typography.titleMedium)
    Text(
        text = BuildConfig.AUTHOR_NAME,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(top = 4.dp)
            .clickable {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.AUTHOR_URL)),
                    )
                }
            },
    )
    Text(
        text = stringResource(R.string.author_role),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = stringResource(R.string.author_tap_hint),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    TextButton(
        onClick = {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.AUTHOR_URL)),
                )
            }
        },
    ) {
        Text(stringResource(R.string.btn_author_facebook))
    }

    Spacer(modifier = Modifier.height(8.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Button(
            onClick = {
                if (busy) return@Button
                busy = true
                status = checking
                scope.launch {
                    try {
                        val result = withContext(Dispatchers.IO) {
                            UpdateChecker.check(BuildConfig.VERSION_NAME)
                        }
                        if (!result.newer) {
                            status = latestFmt.format(result.tagName)
                            busy = false
                            return@launch
                        }
                        val apkUrl = result.apkUrl
                        if (apkUrl.isNullOrBlank()) {
                            status = noApkFmt.format(result.tagName)
                            busy = false
                            return@launch
                        }
                        status = foundFmt.format(result.tagName)
                        val file = withContext(Dispatchers.IO) {
                            UpdateChecker.downloadApk(context, apkUrl) { pct ->
                                scope.launch(Dispatchers.Main.immediate) {
                                    status = downloadingFmt.format(pct)
                                }
                            }
                        }
                        status = installing
                        installApk(
                            context = context,
                            file = file,
                            needPermMessage = needPerm,
                        )
                    } catch (e: Exception) {
                        val msg = failFmt.format(e.message ?: e.javaClass.simpleName)
                        status = msg
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.btn_check_update))
        }
        OutlinedButton(
            onClick = {
                runCatching {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/${BuildConfig.GITHUB_REPO}"),
                        ),
                    )
                }
            },
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.btn_open_github))
        }
    }
    status?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
    }
}

private fun installApk(
    context: android.content.Context,
    file: java.io.File,
    needPermMessage: String,
) {
    if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
        Toast.makeText(context, needPermMessage, Toast.LENGTH_LONG).show()
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        return
    }
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
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

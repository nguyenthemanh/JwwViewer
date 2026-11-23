package com.jwwviewer.app.ui.adaptive

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun DualPaneScaffold(
    adaptive: AdaptiveLayout,
    list: @Composable () -> Unit,
    detail: @Composable () -> Unit,
) {
    when {
        adaptive.useTabletopSplit -> {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(0.38f)
                        .fillMaxWidth(),
                ) { list() }
                HorizontalDivider()
                Column(
                    modifier = Modifier
                        .weight(0.62f)
                        .fillMaxWidth(),
                ) { detail() }
            }
        }
        adaptive.useDualPane -> {
            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(adaptive.paneListWeight)
                        .fillMaxHeight(),
                ) { list() }
                VerticalDivider()
                Column(
                    modifier = Modifier
                        .weight(adaptive.paneDetailWeight)
                        .fillMaxHeight(),
                ) { detail() }
            }
        }
        else -> detail()
    }
}

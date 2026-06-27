package com.jetsetter.pro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jetsetter.pro.ui.theme.JetTheme

/**
 * Wraps a feature screen (one reached from More → Features) with a themed back-arrow bar.
 * The bar consumes the status-bar inset; the wrapped screen renders its own large title below,
 * so there is no title duplication. System back still works too.
 */
@Composable
fun FeatureScaffold(onBack: () -> Unit, content: @Composable () -> Unit) {
    val colors = JetTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.textPrimary,
                )
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}

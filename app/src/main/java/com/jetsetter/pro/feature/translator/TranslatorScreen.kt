package com.jetsetter.pro.feature.translator

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jetsetter.pro.ui.components.AccentTag
import com.jetsetter.pro.ui.components.JetCard
import com.jetsetter.pro.ui.components.PremiumTextField
import com.jetsetter.pro.ui.theme.JetSetterTheme
import com.jetsetter.pro.ui.theme.JetTheme

/**
 * Stateful entry point: owns the [TranslatorViewModel], collects its [TranslatorUiState]
 * lifecycle-aware, and forwards events. The only Android-UI concern it handles directly is the
 * clipboard write (copy result); everything else is delegated. Holds no logic — see [TranslatorContent].
 */
@Composable
fun TranslatorScreen(viewModel: TranslatorViewModel = hiltViewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    TranslatorContent(
        state = state,
        onInputChange = viewModel::onInputChange,
        onClearInput = viewModel::clearInput,
        onSwapLanguages = viewModel::swapLanguages,
        onTranslate = viewModel::translate,
        onUsePhrase = viewModel::usePhrase,
        onUseRecent = viewModel::useRecent,
        onClearRecents = viewModel::clearRecents,
        onCopyResult = {
            state.result?.let { result ->
                clipboard.setText(AnnotatedString(result.translatedText))
                viewModel.markResultCopied()
            }
        },
    )
}

/**
 * Stateless content: a pure function of [state] + event lambdas, so it's trivially previewable
 * and testable.
 */
@Composable
private fun TranslatorContent(
    state: TranslatorUiState,
    onInputChange: (String) -> Unit,
    onClearInput: () -> Unit,
    onSwapLanguages: () -> Unit,
    onTranslate: () -> Unit,
    onUsePhrase: (TranslatorPhrase) -> Unit,
    onUseRecent: (TranslatorRecentEntry) -> Unit,
    onClearRecents: () -> Unit,
    onCopyResult: () -> Unit,
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing

    // One-time entrance: a quick fade + slight slide-in on first composition.
    // Animates the content layer only (background stays stable) and settles to rest, so scroll is unaffected.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val entranceAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "translatorEntranceAlpha",
    )
    val slideFromPx = with(LocalDensity.current) { spacing.medium.toPx() }
    val entranceOffsetY by animateFloatAsState(
        targetValue = if (entered) 0f else slideFromPx,
        animationSpec = tween(durationMillis = 250),
        label = "translatorEntranceOffsetY",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .graphicsLayer {
                alpha = entranceAlpha
                translationY = entranceOffsetY
            }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.medium)
            .padding(top = spacing.large, bottom = spacing.xlarge),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Column {
            Text("Translator", style = JetTheme.typography.displayTitle, color = colors.textPrimary)
            Spacer(Modifier.height(2.dp))
            Text(
                "Offline travel phrasebook · ${state.phrases.size} phrases",
                style = JetTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }

        LanguagePairCard(
            from = state.fromLanguage,
            to = state.toLanguage,
            onSwap = onSwapLanguages,
        )

        InputCard(
            inputText = state.inputText,
            characterCount = state.characterCount,
            isOverLimit = state.isOverLimit,
            canTranslate = state.canTranslate,
            isTranslating = state.isTranslating,
            onInputChange = onInputChange,
            onClearInput = onClearInput,
            onTranslate = onTranslate,
        )

        state.result?.let { result ->
            ResultCard(
                result = result,
                toLanguage = state.toLanguage,
                recentlyCopied = state.recentlyCopied,
                onCopyResult = onCopyResult,
            )
        }

        if (state.recents.isNotEmpty()) {
            SectionHeader(
                title = "RECENT",
                trailing = {
                    Text(
                        "Clear",
                        style = JetTheme.typography.label,
                        color = colors.accent,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .clickable(onClick = onClearRecents)
                            .semantics { role = Role.Button }
                            .padding(horizontal = spacing.small, vertical = 2.dp),
                    )
                },
            )
            state.recents.forEach { entry ->
                RecentCard(entry = entry, onClick = { onUseRecent(entry) })
            }
        }

        SectionHeader(title = "COMMON TRAVEL PHRASES")
        if (state.phrases.isEmpty()) {
            PhrasebookEmptyState()
        } else {
            state.phrases.forEach { phrase ->
                PhraseCard(phrase = phrase, onClick = { onUsePhrase(phrase) })
            }
        }
    }
}

/** Polished empty state for the phrasebook list (icon + message) when no phrases are available. */
@Composable
private fun PhrasebookEmptyState() {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing

    JetCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = spacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            Icon(
                Icons.Outlined.Language,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(32.dp),
            )
            Text("No phrases yet", style = typography.cardTitle, color = colors.textPrimary)
            Text(
                "Your offline travel phrasebook will appear here.",
                style = typography.caption,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, trailing: (@Composable () -> Unit)? = null) {
    val colors = JetTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = JetTheme.spacing.xsmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = JetTheme.typography.caption, color = colors.textSecondary)
        trailing?.invoke()
    }
}

@Composable
private fun LanguagePairCard(
    from: TranslatorLanguage,
    to: TranslatorLanguage,
    onSwap: () -> Unit,
) {
    val colors = JetTheme.colors
    val haptics = LocalHapticFeedback.current
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            LanguagePill(language = from, label = "FROM", modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .padding(horizontal = JetTheme.spacing.small)
                    .minimumInteractiveComponentSize()
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.15f))
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSwap()
                    }
                    .semantics { role = Role.Button },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.SwapHoriz,
                    contentDescription = "Swap languages",
                    tint = colors.accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            LanguagePill(language = to, label = "TO", modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun LanguagePill(language: TranslatorLanguage, label: String, modifier: Modifier = Modifier) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = typography.caption, color = colors.textSecondary)
        Spacer(Modifier.height(JetTheme.spacing.xsmall))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(language.flag, style = typography.cardTitle)
            Text(language.name, style = typography.cardTitle, color = colors.textPrimary)
        }
    }
}

@Composable
private fun InputCard(
    inputText: String,
    characterCount: Int,
    isOverLimit: Boolean,
    canTranslate: Boolean,
    isTranslating: Boolean,
    onInputChange: (String) -> Unit,
    onClearInput: () -> Unit,
    onTranslate: () -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing
    val haptics = LocalHapticFeedback.current

    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("ENTER TEXT", style = typography.caption, color = colors.textSecondary)
            if (inputText.isNotEmpty()) {
                Icon(
                    Icons.Filled.Clear,
                    contentDescription = "Clear text",
                    tint = colors.textSecondary,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(18.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onClearInput)
                        .semantics { role = Role.Button },
                )
            }
        }
        Spacer(Modifier.height(spacing.small))
        PremiumTextField(
            value = inputText,
            onValueChange = onInputChange,
            placeholder = "Type a phrase to translate…",
            singleLine = false,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        )
        Spacer(Modifier.height(spacing.xsmall))
        Text(
            text = if (isOverLimit) {
                "Too long — keep it under ${TranslatorUiState.MAX_INPUT_LENGTH} characters"
            } else {
                "$characterCount / ${TranslatorUiState.MAX_INPUT_LENGTH}"
            },
            style = typography.caption,
            color = if (isOverLimit) colors.danger else colors.textSecondary,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(spacing.small))
        Button(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onTranslate()
            },
            enabled = canTranslate,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = Color.White,
                disabledContainerColor = colors.surfaceElevated,
                disabledContentColor = colors.textSecondary,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isTranslating) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(spacing.small))
                Text("Translating…", style = typography.label)
            } else {
                Icon(Icons.Filled.Translate, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(spacing.small))
                Text("Translate", style = typography.label)
            }
        }
    }
}

@Composable
private fun ResultCard(
    result: TranslatorResult,
    toLanguage: TranslatorLanguage,
    recentlyCopied: Boolean,
    onCopyResult: () -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing

    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                AccentTag(text = "${toLanguage.flag} ${toLanguage.name}", icon = Icons.Outlined.Language)
                if (result.matched) {
                    StatusChip(text = "Exact match", color = colors.success, icon = Icons.Outlined.CheckCircle)
                } else {
                    StatusChip(text = "Demo", color = colors.warning, icon = Icons.Outlined.WifiOff)
                }
            }
            CopyPill(copied = recentlyCopied, onClick = onCopyResult)
        }
        Spacer(Modifier.height(spacing.small))
        Text(result.translatedText, style = typography.cardTitle, color = colors.textPrimary)
        if (result.pronunciation.isNotBlank()) {
            Spacer(Modifier.height(spacing.xsmall))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    Icons.Outlined.RecordVoiceOver,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    result.pronunciation,
                    style = typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    color = colors.textSecondary,
                )
            }
        }
        Spacer(Modifier.height(spacing.small))
        Text(result.note, style = typography.caption, color = colors.textSecondary)
    }
}

/** Copy-to-clipboard affordance for the active translation; flips to a confirmation when tapped. */
@Composable
private fun CopyPill(copied: Boolean, onClick: () -> Unit) {
    val colors = JetTheme.colors
    val haptics = LocalHapticFeedback.current
    val tint = if (copied) colors.success else colors.accent
    Row(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.15f))
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .semantics { role = Role.Button }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = if (copied) Icons.Filled.Check else Icons.Outlined.ContentCopy,
            contentDescription = if (copied) "Copied" else "Copy translation",
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
        Text(if (copied) "Copied" else "Copy", style = JetTheme.typography.label, color = tint)
    }
}

/** Small semantic status capsule (success / warning) — sibling of [AccentTag] for non-accent hues. */
@Composable
private fun StatusChip(text: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        Text(text, style = JetTheme.typography.label, color = color)
    }
}

@Composable
private fun RecentCard(entry: TranslatorRecentEntry, onClick: () -> Unit) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing
    val haptics = LocalHapticFeedback.current
    val from = TranslatorLanguage.fromCode(entry.fromCode)
    val to = TranslatorLanguage.fromCode(entry.toCode)

    JetCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics(mergeDescendants = true) { role = Role.Button },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            Icon(
                Icons.Outlined.History,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.sourceText,
                    style = typography.bodyMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    entry.translatedText,
                    style = typography.cardTitle,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AccentTag(text = "${from.flag} → ${to.flag}")
        }
    }
}

@Composable
private fun PhraseCard(phrase: TranslatorPhrase, onClick: () -> Unit) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing
    val haptics = LocalHapticFeedback.current

    JetCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics(mergeDescendants = true) { role = Role.Button },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(phrase.source, style = typography.bodyMedium, color = colors.textPrimary)
                Spacer(Modifier.height(spacing.xsmall))
                Text(phrase.target, style = typography.cardTitle, color = colors.textPrimary)
                Spacer(Modifier.height(2.dp))
                Text(
                    phrase.pronunciation,
                    style = typography.caption.copy(fontStyle = FontStyle.Italic),
                    color = colors.textSecondary,
                )
            }
            Spacer(Modifier.size(spacing.small))
            AccentTag(text = phrase.category)
        }
    }
}

@Preview(showBackground = true, name = "Translator – with result")
@Composable
private fun TranslatorContentPreview() {
    JetSetterTheme {
        TranslatorContent(
            state = TranslatorUiState(
                inputText = "Thank you",
                result = TranslatorResult(
                    sourceText = "Thank you",
                    translatedText = "ありがとうございます",
                    pronunciation = "Arigatō gozaimasu",
                    note = "Matched a saved English → Japanese travel phrase.",
                    matched = true,
                ),
                recents = listOf(
                    TranslatorRecentEntry(
                        sourceText = "Where is the station?",
                        translatedText = "駅はどこですか？",
                        pronunciation = "Eki wa doko desu ka?",
                        fromCode = "EN",
                        toCode = "JA",
                        matched = true,
                    ),
                ),
                phrases = listOf(
                    TranslatorPhrase(
                        id = "greet-hello",
                        category = "Greetings",
                        source = "Hello",
                        target = "こんにちは",
                        pronunciation = "Konnichiwa",
                    ),
                    TranslatorPhrase(
                        id = "directions-station",
                        category = "Directions",
                        source = "Where is the station?",
                        target = "駅はどこですか？",
                        pronunciation = "Eki wa doko desu ka?",
                    ),
                ),
            ),
            onInputChange = {},
            onClearInput = {},
            onSwapLanguages = {},
            onTranslate = {},
            onUsePhrase = {},
            onUseRecent = {},
            onClearRecents = {},
            onCopyResult = {},
        )
    }
}

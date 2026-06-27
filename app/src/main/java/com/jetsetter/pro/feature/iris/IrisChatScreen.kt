package com.jetsetter.pro.feature.iris

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jetsetter.pro.ui.components.AccentTag
import com.jetsetter.pro.ui.components.PremiumTextField
import com.jetsetter.pro.ui.theme.JetSetterTheme
import com.jetsetter.pro.ui.theme.JetTheme

/**
 * Stateful entry point: owns the [IrisChatViewModel], collects its [IrisUiState] lifecycle-aware,
 * and forwards events. Holds no logic — see [IrisChatContent].
 */
@Composable
fun IrisChatScreen(viewModel: IrisChatViewModel = hiltViewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    IrisChatContent(state = state, onSend = viewModel::send)
}

/**
 * Stateless content: a pure function of [state] + a single send lambda, so it's trivially
 * previewable. Local [input] text is ephemeral UI-only state and stays here.
 */
@Composable
private fun IrisChatContent(
    state: IrisUiState,
    onSend: (String) -> Unit,
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to the newest message (or the typing bubble) whenever the list grows.
    LaunchedEffect(state.messages.size, state.isThinking) {
        val count = state.messages.size + if (state.isThinking) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    // One-time entrance: fade + subtle slide-up of the content on first composition.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val entranceProgress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "entrance",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .imePadding()
            .graphicsLayer {
                alpha = entranceProgress
                translationY = (1f - entranceProgress) * 16.dp.toPx()
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.medium, vertical = spacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = colors.accent)
            Text("IRIS", style = JetTheme.typography.pageTitle, color = colors.textPrimary)
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (state.messages.isEmpty() && !state.isThinking) {
                EmptyState(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(spacing.small),
                ) {
                    itemsIndexed(
                        items = state.messages,
                        key = { index, _ -> index },
                    ) { _, message ->
                        MessageBubble(message, modifier = Modifier.animateItem())
                    }
                    if (state.isThinking) {
                        item(key = "typing") { TypingIndicator(modifier = Modifier.animateItem()) }
                    }
                }
            }
        }

        SuggestionRow(
            suggestions = state.suggestions,
            enabled = !state.isThinking,
            onSuggestion = onSend,
        )

        InputBar(
            value = input,
            onValueChange = { input = it },
            onSend = {
                onSend(input)
                input = ""
            },
            enabled = !state.isThinking,
        )
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, modifier: Modifier = Modifier) {
    val colors = JetTheme.colors
    val isUser = message.fromUser
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Text(
            text = message.text,
            style = JetTheme.typography.bodyMedium,
            color = if (isUser) Color.White else colors.textPrimary,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isUser) colors.accent else colors.surface)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

/** Three bouncing dots in a bubble, shown while IRIS composes a reply. */
@Composable
private fun TypingIndicator(modifier: Modifier = Modifier) {
    val colors = JetTheme.colors
    val transition = rememberInfiniteTransition(label = "typing")
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surface)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) { index ->
                val translateY by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = -6f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                        initialStartOffset = StartOffset(index * 130),
                    ),
                    label = "dot$index",
                )
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .graphicsLayer { translationY = translateY.dp.toPx() }
                        .clip(CircleShape)
                        .background(colors.textSecondary),
                )
            }
        }
    }
}

/** Friendly placeholder shown before any conversation exists. */
@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    Column(
        modifier = modifier.padding(spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        Icon(
            Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(40.dp),
        )
        Text(
            "Ask IRIS anything",
            style = JetTheme.typography.cardTitle,
            color = colors.textPrimary,
        )
        Text(
            "Flights, itinerary, packing, or expenses — I'm here to help.",
            style = JetTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun SuggestionRow(
    suggestions: List<String>,
    enabled: Boolean,
    onSuggestion: (String) -> Unit,
) {
    if (suggestions.isEmpty()) return
    val spacing = JetTheme.spacing
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = spacing.medium, vertical = spacing.small),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        suggestions.forEach { suggestion ->
            AccentTag(
                text = suggestion,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clickable(enabled = enabled, role = Role.Button) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSuggestion(suggestion)
                    },
            )
        }
    }
}

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
) {
    val colors = JetTheme.colors
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PremiumTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = "Ask IRIS…",
            modifier = Modifier.weight(1f),
        )
        FilledIconButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onSend()
            },
            enabled = enabled && value.isNotBlank(),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = colors.accent,
                contentColor = Color.White,
            ),
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
        }
    }
}

@Preview(showBackground = true, name = "IRIS – chat")
@Composable
private fun IrisChatContentPreview() {
    JetSetterTheme {
        IrisChatContent(
            state = IrisUiState(
                messages = listOf(
                    ChatMessage(
                        "Hi, I'm IRIS — your travel concierge. Ask me about your flights, itinerary, packing, or expenses.",
                        fromUser = false,
                    ),
                    ChatMessage("What's my gate?", fromUser = true),
                    ChatMessage("You're at gate C22 for DL 1423 LAS → ATL, on time.", fromUser = false),
                ),
                isThinking = true,
            ),
            onSend = {},
        )
    }
}

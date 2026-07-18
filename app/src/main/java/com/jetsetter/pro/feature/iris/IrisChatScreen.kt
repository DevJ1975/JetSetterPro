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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FlightLand
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import com.jetsetter.pro.core.ai.IrisPendingAction
import com.jetsetter.pro.core.voice.VoiceLoopState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jetsetter.pro.core.voice.VoiceInput
import com.jetsetter.pro.ui.components.AccentTag
import com.jetsetter.pro.ui.components.JetCard
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
    IrisChatContent(
        state = state,
        onSend = viewModel::send,
        onSetTtsEnabled = viewModel::setTtsEnabled,
        onSetHandsFree = viewModel::setHandsFree,
        onConfirmPending = viewModel::confirmPendingAction,
        onCancelPending = viewModel::cancelPendingAction,
    )
}

/**
 * Stateless content: a pure function of [state] + a single send lambda, so it's trivially
 * previewable. Local [input] text is ephemeral UI-only state and stays here.
 */
@Composable
private fun IrisChatContent(
    state: IrisUiState,
    onSend: (String) -> Unit,
    onSetTtsEnabled: (Boolean) -> Unit = {},
    onSetHandsFree: (Boolean) -> Unit = {},
    onConfirmPending: () -> Unit = {},
    onCancelPending: () -> Unit = {},
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // NOTE: all speech (TTS + hands-free loop) is owned by the ViewModel's VoiceLoopController
    // (R9) — this screen only renders state and forwards toggles.

    // Hands-free needs RECORD_AUDIO; BLUETOOTH_CONNECT (API 31+) is requested alongside but
    // optional — the loop degrades to speaker routing when it's denied.
    val handsFreePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val micGranted = grants[Manifest.permission.RECORD_AUDIO]
            ?: (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED)
        if (micGranted) {
            onSetHandsFree(true)
        } else {
            Toast.makeText(context, "Microphone permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    fun enableHandsFree() {
        val missing = buildList {
            val micGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
            if (!micGranted) add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val btGranted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.BLUETOOTH_CONNECT,
                ) == PackageManager.PERMISSION_GRANTED
                if (!btGranted) add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        if (missing.isEmpty()) onSetHandsFree(true)
        else handsFreePermissionLauncher.launch(missing.toTypedArray())
    }

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
            Spacer(Modifier.weight(1f))
            // Small phase chip while the hands-free loop is active (spec §1.8).
            if (state.handsFree) {
                val phaseLabel = when (state.voiceState) {
                    VoiceLoopState.LISTENING -> "Listening…"
                    VoiceLoopState.THINKING -> "Thinking…"
                    VoiceLoopState.SPEAKING -> "Speaking…"
                    VoiceLoopState.IDLE -> null
                }
                phaseLabel?.let { AccentTag(text = it) }
            }
            IconButton(onClick = {
                if (state.handsFree) onSetHandsFree(false) else enableHandsFree()
            }) {
                Icon(
                    imageVector = Icons.Filled.HeadsetMic,
                    contentDescription = if (state.handsFree) {
                        "Stop hands-free conversation"
                    } else {
                        "Start hands-free conversation"
                    },
                    tint = if (state.handsFree) colors.accent else colors.textSecondary,
                )
            }
            IconButton(onClick = { onSetTtsEnabled(!state.ttsEnabled) }) {
                Icon(
                    imageVector = if (state.ttsEnabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                    contentDescription = if (state.ttsEnabled) "Mute IRIS" else "Speak replies aloud",
                    tint = if (state.ttsEnabled) colors.accent else colors.textSecondary,
                )
            }
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

        state.pendingAction?.let { pending ->
            PendingActionCard(
                action = pending,
                onConfirm = onConfirmPending,
                onCancel = onCancelPending,
            )
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
            // Ghost the live partial hypothesis into the (empty) field while the loop listens.
            ghostText = state.partialTranscript
                ?.takeIf { state.handsFree && state.voiceState == VoiceLoopState.LISTENING },
            // The loop owns the mic while hands-free — hide push-to-talk contention.
            pushToTalkEnabled = !state.handsFree,
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

/**
 * Confirm-before-commit card (spec §1.4): shows the one staged action's icon + summary with
 * Cancel/Confirm, swapping the buttons for a spinner while the commit runs.
 */
@Composable
private fun PendingActionCard(
    action: PendingActionUi,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    val haptics = LocalHapticFeedback.current
    JetCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.medium, vertical = spacing.xsmall),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            Icon(action.kind.icon(), contentDescription = null, tint = colors.accent)
            Column(modifier = Modifier.weight(1f)) {
                Text("CONFIRM ACTION", style = JetTheme.typography.label, color = colors.textSecondary)
                Text(action.summary, style = JetTheme.typography.bodyMedium, color = colors.textPrimary)
            }
        }
        Spacer(Modifier.height(spacing.small))
        if (action.isCommitting) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = colors.accent,
                    strokeWidth = 2.5.dp,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCancel()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.surfaceElevated,
                        contentColor = colors.textPrimary,
                    ),
                ) {
                    Text("Cancel", style = JetTheme.typography.cardTitle)
                }
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onConfirm()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = Color.White,
                    ),
                ) {
                    Text("Confirm", style = JetTheme.typography.cardTitle)
                }
            }
        }
    }
}

/** Icon per staged-action kind, keyed off the shared iOS-parity enum. */
private fun IrisPendingAction.Kind.icon(): ImageVector = when (this) {
    IrisPendingAction.Kind.LOG_EXPENSE -> Icons.Filled.AttachMoney
    IrisPendingAction.Kind.ADD_TRIP -> Icons.Filled.FlightTakeoff
    IrisPendingAction.Kind.CHECK_IN -> Icons.Filled.HowToReg
    IrisPendingAction.Kind.TRACK_FLIGHT -> Icons.Filled.FlightLand
    IrisPendingAction.Kind.GENERATE_PACKING_LIST -> Icons.Filled.Luggage
    IrisPendingAction.Kind.SUBMIT_EXPENSES -> Icons.Filled.Upload
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
    ghostText: String? = null,
    pushToTalkEnabled: Boolean = true,
) {
    val colors = JetTheme.colors
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current

    // Push-to-talk: a single recognizer per InputBar, released when the bar leaves composition.
    val voiceInput = remember { VoiceInput(context) }
    var listening by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose { voiceInput.destroy() }
    }

    fun startListening() {
        if (!voiceInput.isAvailable) {
            Toast.makeText(context, "Voice input isn't available on this device", Toast.LENGTH_SHORT).show()
            return
        }
        listening = true
        voiceInput.start(
            onResult = { phrase ->
                listening = false
                // Feed the recognized phrase into the same field the keyboard fills.
                onValueChange(phrase)
            },
            onError = {
                listening = false
                Toast.makeText(context, "Didn't catch that — try again", Toast.LENGTH_SHORT).show()
            },
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startListening()
        } else {
            Toast.makeText(context, "Microphone permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PremiumTextField(
            value = value,
            onValueChange = onValueChange,
            // Ghosted partial transcript while the hands-free loop listens (spec §1.8).
            placeholder = ghostText ?: "Ask IRIS…",
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                if (listening) {
                    listening = false
                    voiceInput.stop()
                    return@IconButton
                }
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    startListening()
                } else {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            enabled = enabled && pushToTalkEnabled,
        ) {
            Icon(
                Icons.Filled.Mic,
                contentDescription = if (listening) "Stop listening" else "Voice input",
                tint = if (listening) colors.accent else colors.textSecondary,
            )
        }
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

@Preview(showBackground = true, name = "IRIS – pending action")
@Composable
private fun IrisPendingActionPreview() {
    JetSetterTheme {
        IrisChatContent(
            state = IrisUiState(
                messages = listOf(
                    ChatMessage("Log a $40 dinner at Nobu", fromUser = true),
                    ChatMessage("I've prepared that expense — confirm below and I'll log it.", fromUser = false),
                ),
                pendingAction = PendingActionUi(
                    id = "preview",
                    kind = IrisPendingAction.Kind.LOG_EXPENSE,
                    summary = "Log expense: USD 40.00 at Nobu (food)",
                ),
            ),
            onSend = {},
        )
    }
}

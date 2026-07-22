package com.focusgate.launcher.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.focusgate.launcher.R
import com.focusgate.launcher.agent.AgentState
import com.focusgate.launcher.apps.InstalledApp
import com.focusgate.launcher.model.ChatMessage
import com.focusgate.launcher.schedule.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onRequestDefaultHome: () -> Unit,
    onEnableBlocking: () -> Unit,
) {
    val setupStep by viewModel.setupStep.collectAsState()
    if (setupStep != ChatViewModel.SetupStep.NONE) {
        SetupFlow(viewModel, setupStep)
        return
    }

    val messages by viewModel.messages.collectAsState()
    val agentState by viewModel.agentState.collectAsState()
    val thinking by viewModel.thinking.collectAsState()
    val isDefaultHome by viewModel.isDefaultHome.collectAsState()
    val blockingEnabled by viewModel.blockingEnabled.collectAsState()
    val coins by viewModel.coins.collectAsState()

    val listState = rememberLazyListState()
    // Keep the newest bubble (or the typing indicator) in view.
    LaunchedEffect(messages.size, thinking) {
        val lastIndex = messages.lastIndex + if (thinking) 1 else 0
        if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Header(agentState, coins)
            if (!isDefaultHome) DefaultHomeBanner(onRequestDefaultHome)
            if (!blockingEnabled) EnableBlockingBanner(onEnableBlocking)
            DownloadBanner(agentState)

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(message)
                }
                if (thinking) {
                    item(key = "typing") { TypingIndicator() }
                }
            }

            InputBar(
                enabled = agentState is AgentState.Ready || agentState is AgentState.Unavailable,
                busy = thinking,
                onSend = viewModel::send,
            )
        }

            // The dragon perches on the LEFT edge, right on top of the input bar
            // (and rides above the keyboard when it's open). Decorative +
            // non-interactive, so taps pass straight through to the text field.
            HoverMascot(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(start = 8.dp, bottom = 66.dp),
            )
        }
    }
}

@Composable
private fun SetupFlow(viewModel: ChatViewModel, step: ChatViewModel.SetupStep) {
    when (step) {
        ChatViewModel.SetupStep.INTRO -> IntroScreen(onNext = viewModel::dismissIntro)
        ChatViewModel.SetupStep.CATEGORIZE -> {
            // Reading + auto-categorizing every app touches PackageManager, so do
            // it off the main thread and show a placeholder until it's ready.
            val apps by produceState<List<Pair<InstalledApp, Category>>?>(initialValue = null) {
                value = withContext(Dispatchers.IO) { viewModel.appsForCategorize() }
            }
            apps?.let { CategorizeScreen(it, viewModel::saveCategories) }
                ?: LoadingScreen("Reading your apps…")
        }
        ChatViewModel.SetupStep.NONE -> Unit
    }
}

@Composable
private fun LoadingScreen(text: String) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Header(state: AgentState, coins: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Wordmark logo (self-coloured), with a tiny AI-status dot beside it.
        Image(
            painter = painterResource(R.drawable.logo_fokused),
            contentDescription = "FoKused",
            modifier = Modifier
                .height(30.dp)
                .width(120.dp),
        )
        Spacer(Modifier.width(8.dp))
        val dotColor = when (state) {
            is AgentState.Ready -> Color(0xFF81C995)
            is AgentState.Unavailable -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.outline
        }
        Box(
            Modifier
                .size(8.dp)
                .background(dotColor, CircleShape)
        )
        Spacer(Modifier.weight(1f))
        CoinPill(coins)
    }
}

@Composable
private fun CoinPill(coins: Int) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(50),
        border = BorderStroke(2.dp, Color.Black),
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.coin),
                contentDescription = "coins",
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "$coins",
                fontFamily = PixelFont,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun DefaultHomeBanner(onRequestDefaultHome: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "FoKused isn't your home screen yet",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Make it the default home app so every app goes through the gate.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = onRequestDefaultHome) { Text("Set as home app") }
        }
    }
}

@Composable
private fun EnableBlockingBanner(onEnableBlocking: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Strict blocking is off",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Turn on FoKused accessibility so apps your schedule blocks can't be " +
                    "opened another way — you'll be sent back to the gate.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = onEnableBlocking) { Text("Enable strict blocking") }
        }
    }
}

@Composable
private fun DownloadBanner(state: AgentState) {
    if (state !is AgentState.Downloading) return
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        val text = if (state.totalBytes > 0) {
            val pct = (state.downloadedBytes * 100 / state.totalBytes).toInt()
            "Downloading Gemini Nano… $pct%"
        } else {
            "Downloading Gemini Nano…"
        }
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        if (state.totalBytes > 0) {
            LinearProgressIndicator(
                progress = { state.downloadedBytes.toFloat() / state.totalBytes },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

// Agent bubbles: 8-bit game dialogue — mascot-yellow fill, deep-green pixel-font
// text, thick black near-square border. User bubbles stay a normal neutral chat
// bubble; error bubbles keep the error palette.
private val PixelYellow = Color(0xFFFFD21E)
private val PixelGreen = Color(0xFF1B5E20)

@Composable
private fun MessageBubble(message: ChatMessage) {
    val fromUser = message.fromUser
    val pixelStyle = !fromUser && !message.isError
    val shape = if (pixelStyle) {
        RoundedCornerShape(3.dp)
    } else {
        RoundedCornerShape(
            topStart = 24.dp,
            topEnd = 24.dp,
            bottomStart = if (fromUser) 24.dp else 6.dp,
            bottomEnd = if (fromUser) 6.dp else 24.dp,
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = when {
                message.isError -> MaterialTheme.colorScheme.errorContainer
                pixelStyle -> PixelYellow
                else -> MaterialTheme.colorScheme.surfaceVariant   // user: neutral
            },
            contentColor = when {
                message.isError -> MaterialTheme.colorScheme.onErrorContainer
                pixelStyle -> PixelGreen
                else -> MaterialTheme.colorScheme.onSurface
            },
            shape = shape,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .then(if (pixelStyle) Modifier.border(3.dp, Color.Black, shape) else Modifier),
        ) {
            Text(
                message.text,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = if (pixelStyle) PixelFont else null,
            )
        }
    }
}

@Composable
private fun TypingIndicator() {
    val shape = RoundedCornerShape(3.dp)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            color = PixelYellow,
            shape = shape,
            modifier = Modifier.border(3.dp, Color.Black, shape),
        ) {
            Text(
                "thinking…",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                fontFamily = PixelFont,
                color = PixelGreen,
            )
        }
    }
}

@Composable
private fun InputBar(
    enabled: Boolean,
    busy: Boolean,
    onSend: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val submit = {
        if (text.isNotBlank()) {
            onSend(text)
            text = ""
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            enabled = enabled,
            placeholder = { Text("App, why, and for how long…") },
            shape = RoundedCornerShape(24.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { submit() }),
            maxLines = 4,
        )
        Spacer(Modifier.width(8.dp))
        val canSend = enabled && !busy && text.isNotBlank()
        // Pixel-art coin send button: the asset is self-coloured (yellow coin,
        // green arrow), so no tint — just dim it when sending isn't possible.
        IconButton(
            onClick = submit,
            enabled = canSend,
            modifier = Modifier.size(56.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_send),
                contentDescription = "Send",
                alpha = if (canSend) 1f else 0.35f,
                modifier = Modifier.size(48.dp),
            )
        }
    }
}

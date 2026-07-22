package com.focusgate.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.focusgate.launcher.R

// Plain-ASCII, game-style copy (no em-dashes / emoji / other AI-tell characters).
private const val WELCOME =
    "Dragon King Fo flies by your side, guarding your time and cheering you on, " +
        "until you reach your destiny and your full potential.\n\n" +
        "Keep your screen dark to earn coins. Spend them wisely to open apps. " +
        "Rise, focus, and level up."

private val FAQ = listOf(
    "How do I open an app?" to
        "Ask Dragon Fo in the chat. Tell him the app, why you need it, and for how long.",
    "What are coins for?" to
        "Coins are your focus energy. Opening any app costs coins. No coins, no scrolling.",
    "How do I earn coins?" to
        "Keep your screen off. Every full minute away from your phone earns you 1 coin.",
    "Why do some apps cost more?" to
        "Fun apps like social, video and games cost 10 coins a minute. Useful ones cost 1. " +
        "Choose your time well.",
    "What is strict mode?" to
        "Turn it on and the only door into any app is Dragon Fo. Try to sneak in another way " +
        "and Fo drags you back to the den.",
)

/** First-launch welcome + FAQ. NEXT marks it seen and drops into the setup/chat. */
@Composable
fun IntroScreen(onNext: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(20.dp))
            Image(
                painter = painterResource(R.drawable.mascot_icon),
                contentDescription = "FoKused",
                modifier = Modifier.size(168.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Your journey to full focus begins now.",
                fontFamily = PixelFont,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                WELCOME,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(26.dp))
            Text(
                "HOW IT WORKS",
                fontFamily = PixelFont,
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
            )
            FAQ.forEach { (q, a) ->
                Spacer(Modifier.height(14.dp))
                Text(
                    q,
                    fontFamily = PixelFont,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    a,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(28.dp))
            val shape = RoundedCornerShape(6.dp)
            Button(
                onClick = onNext,
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .border(3.dp, Color.Black, shape),
            ) {
                Text("NEXT", fontFamily = PixelFont, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

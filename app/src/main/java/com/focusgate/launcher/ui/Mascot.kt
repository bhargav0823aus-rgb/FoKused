package com.focusgate.launcher.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.focusgate.launcher.R
import kotlinx.coroutines.delay

// Wing frames, ordered so cycling them reads as a flap (up → spread → down → spread).
private val flapFrames = intArrayOf(
    R.drawable.mascot_fly3,
    R.drawable.mascot_fly1,
    R.drawable.mascot_fly2,
    R.drawable.mascot_fly4,
)

/**
 * The FoKused dragon hovering above the chat box: it flaps its wings (frame swap)
 * and gently bobs up and down. Purely decorative — it has no pointer handling, so
 * taps pass straight through to the input bar beneath it.
 */
@Composable
fun HoverMascot(modifier: Modifier = Modifier, size: Dp = 92.dp) {
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(220L)
            frame = (frame + 1) % flapFrames.size
        }
    }

    val transition = rememberInfiniteTransition(label = "hover")
    val bobDp by transition.animateFloat(
        initialValue = 0f,
        targetValue = -14f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bob",
    )

    Image(
        painter = painterResource(flapFrames[frame]),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .size(size)
            .offset { IntOffset(0, bobDp.dp.roundToPx()) },
    )
}

/** A small static mascot for light branding (e.g. next to the header title). */
@Composable
fun MascotBadge(modifier: Modifier = Modifier, size: Dp = 34.dp) {
    Image(
        painter = painterResource(R.drawable.mascot_fly1),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size),
    )
}

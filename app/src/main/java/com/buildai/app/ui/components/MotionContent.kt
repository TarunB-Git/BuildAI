package com.buildai.app.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier

const val ScreenTransitionMillis = 180

/** Fixed-size content fades; each destination retains its own fields and scroll position.
 * Compose's animation clock respects Android's animator duration scale, including zero.
 */
@Composable
fun MotionContent(key: String, modifier: Modifier = Modifier, content: @Composable (String) -> Unit) {
    val states = rememberSaveableStateHolder()
    Crossfade(targetState = key, modifier = modifier,
        animationSpec = tween(ScreenTransitionMillis, easing = FastOutSlowInEasing), label = "Screen content") { target ->
        states.SaveableStateProvider(target) { content(target) }
    }
}

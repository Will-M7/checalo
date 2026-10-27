package com.example.ecolacteoshuata.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

@Composable
fun StaggeredListItem(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var visible by remember(index) { mutableStateOf(false) }
    LaunchedEffect(index) {
        delay(index * 45L)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { 22 }, animationSpec = tween(350)) + fadeIn(tween(350)),
        modifier = modifier,
    ) {
        content()
    }
}

@Composable
fun ScreenEntrance(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { 14 }, animationSpec = tween(350)) + fadeIn(tween(350)),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        content()
    }
}

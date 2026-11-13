package com.example.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Unified gesture modifier for the block drag handle (`⠿`).
 * - Vertical drag swaps the block up or down among paragraphs and other elements.
 * - Double tap (for graphic elements) immediately opens the configuration menu without
 *   interfering with LazyColumn scrolling, text fields, or native media views.
 */
@Composable
fun Modifier.graphicDragHandleGestures(
    blockKey: String,
    enableDoubleTap: Boolean,
    onDoubleTap: () -> Unit,
    onSingleTap: () -> Unit = {},
    onDragStart: () -> Unit,
    onDragStepDown: () -> Boolean,
    onDragStepUp: () -> Boolean
): Modifier {
    val haptic = LocalHapticFeedback.current
    val currentEnableDoubleTap by rememberUpdatedState(enableDoubleTap)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val currentOnSingleTap by rememberUpdatedState(onSingleTap)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDragStepDown by rememberUpdatedState(onDragStepDown)
    val currentOnDragStepUp by rememberUpdatedState(onDragStepUp)

    return this
        .pointerInput(blockKey) {
            detectTapGestures(
                onTap = {
                    currentOnSingleTap()
                },
                onDoubleTap = {
                    if (currentEnableDoubleTap) {
                        try {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        } catch (_: Exception) {
                            // Ignore haptic failure on emulator/container
                        }
                        currentOnDoubleTap()
                    }
                }
            )
        }
        .pointerInput(blockKey) {
            var totalDragY = 0f
            detectDragGestures(
                onDragStart = {
                    totalDragY = 0f
                    currentOnDragStart()
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    totalDragY += dragAmount.y
                    val threshold = 60f
                    if (totalDragY > threshold) {
                        if (currentOnDragStepDown()) {
                            totalDragY = 0f
                        }
                    } else if (totalDragY < -threshold) {
                        if (currentOnDragStepUp()) {
                            totalDragY = 0f
                        }
                    }
                }
            )
        }
}

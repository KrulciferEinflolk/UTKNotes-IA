package com.example.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Custom Modifier for Graphic Elements in the note editor.
 * Requires holding down for more than 2 seconds (2000ms) to open the edit menu.
 * Prevents UI freezing by cancelling cleanly on drag/scroll and decoupling gesture release
 * from modal window creation, avoiding SurfaceSyncGroup timeouts.
 */
@Composable
fun Modifier.graphicElement2SecondHold(
    onOpenSettings: () -> Unit,
    onClick: (() -> Unit)? = null
): Modifier {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val currentOnOpenSettings by rememberUpdatedState(onOpenSettings)
    val currentOnClick by rememberUpdatedState(onClick)

    return this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var heldFor2Seconds = false
            var tapTriggered = false

            try {
                withTimeout(2000L) {
                    var upOrCancel: PointerInputChange? = null
                    while (upOrCancel == null) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || change.isConsumed) {
                            // Cancelled by scroll or consumed by container
                            return@withTimeout
                        }
                        if (!change.pressed) {
                            // Finger lifted before 2 seconds
                            upOrCancel = change
                        } else {
                            val diff = change.position - down.position
                            if (diff.getDistance() > viewConfiguration.touchSlop) {
                                // User dragged or scrolled
                                return@withTimeout
                            }
                        }
                    }
                    tapTriggered = true
                }
            } catch (e: TimeoutCancellationException) {
                // 2000ms passed with finger stationary
                heldFor2Seconds = true
            }

            if (heldFor2Seconds) {
                down.consume()
                try {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                } catch (e: Exception) {
                    // Ignore haptic failure on emulator/container
                }
                // Decouple window creation from pointer event loop to prevent SurfaceSyncGroup timeout
                scope.launch {
                    currentOnOpenSettings()
                }
            } else if (tapTriggered) {
                currentOnClick?.invoke()
            }
        }
    }
}

package com.example.ui.creationlab

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.ui.AetherViewModel

@Composable
fun CreationLabHostScreen(
    viewModel: AetherViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasSeenOnboarding by remember {
        mutableStateOf(CreationLabPrefs.hasSeenOnboarding(context))
    }
    var forceShowOnboarding by remember { mutableStateOf(false) }

    // Manejo de botón atrás del sistema
    BackHandler {
        if (forceShowOnboarding) {
            forceShowOnboarding = false
        } else {
            onBack()
        }
    }

    if (!hasSeenOnboarding || forceShowOnboarding) {
        CreationLabOnboardingScreen(
            onFinish = {
                CreationLabPrefs.setOnboardingSeen(context, true)
                hasSeenOnboarding = true
                forceShowOnboarding = false
            },
            modifier = modifier
        )
    } else {
        CreationLabScreen(
            viewModel = viewModel,
            onBack = onBack,
            onReopenTutorial = {
                forceShowOnboarding = true
            },
            modifier = modifier
        )
    }
}

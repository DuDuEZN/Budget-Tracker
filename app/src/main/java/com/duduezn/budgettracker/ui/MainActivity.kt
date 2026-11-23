package com.duduezn.budgettracker.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duduezn.budgettracker.BudgetTrackerApp
import com.duduezn.budgettracker.security.AppLockManager
import com.duduezn.budgettracker.ui.lock.PinSetupScreen
import com.duduezn.budgettracker.ui.lock.PinUnlockScreen
import com.duduezn.budgettracker.ui.onboarding.OnboardingScreen
import com.duduezn.budgettracker.ui.theme.BudgetTrackerTheme

class MainActivity : FragmentActivity() {

    private lateinit var lockManager: AppLockManager
    private lateinit var mainViewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as BudgetTrackerApp).container
        lockManager = AppLockManager(applicationContext)
        mainViewModel = ViewModelProvider(this)[MainViewModel::class.java]
        mainViewModel.initialize(initialStage(lockManager))

        setContent {
            BudgetTrackerTheme {
                val stage by mainViewModel.stage.collectAsStateWithLifecycle()

                when (stage ?: initialStage(lockManager)) {
                    GateStage.ONBOARDING -> OnboardingScreen(
                        onComplete = {
                            lockManager.setOnboardingComplete()
                            mainViewModel.setStage(
                                if (lockManager.isPinSet()) {
                                    GateStage.UNLOCK
                                } else {
                                    GateStage.PIN_SETUP
                                }
                            )
                        }
                    )

                    GateStage.PIN_SETUP -> PinSetupScreen(
                        lockManager = lockManager,
                        onDone = { mainViewModel.setStage(GateStage.ROOT) }
                    )

                    GateStage.UNLOCK -> PinUnlockScreen(
                        lockManager = lockManager,
                        onUnlocked = { mainViewModel.setStage(GateStage.ROOT) }
                    )

                    GateStage.ROOT -> {
                        AutoLockEffect(onLock = { mainViewModel.setStage(GateStage.UNLOCK) })
                        BudgetTrackerRoot(
                            container = container,
                            lockManager = lockManager,
                            onLockNow = { mainViewModel.setStage(GateStage.UNLOCK) }
                        )
                    }
                }
            }
        }
    }

    private fun initialStage(lockManager: AppLockManager): GateStage = when {
        !lockManager.isOnboardingComplete() -> GateStage.ONBOARDING
        !lockManager.isPinSet() -> GateStage.PIN_SETUP
        else -> GateStage.UNLOCK
    }
}

/**
 * Verrouille automatiquement l'application après un passage en arrière-plan.
 * Un délai évite de redemander le code lors d'une simple rotation ou d'un aller-retour rapide.
 */
@Composable
private fun AutoLockEffect(onLock: () -> Unit) {
    var stoppedAt by remember { mutableStateOf(0L) }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        stoppedAt = System.currentTimeMillis()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        val elapsed = System.currentTimeMillis() - stoppedAt
        if (stoppedAt > 0L && elapsed > BACKGROUND_LOCK_DELAY_MS) {
            stoppedAt = 0L
            onLock()
        }
    }
}

private const val BACKGROUND_LOCK_DELAY_MS = 60_000L

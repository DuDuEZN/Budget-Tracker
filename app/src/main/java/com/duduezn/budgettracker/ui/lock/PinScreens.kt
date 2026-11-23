package com.duduezn.budgettracker.ui.lock

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.duduezn.budgettracker.security.AppLockManager

/** Écran de création du code PIN, affiché au premier lancement après l'onboarding. */
@Composable
fun PinSetupScreen(
    lockManager: AppLockManager,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    var firstPin by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val confirming = firstPin != null

    fun submit() {
        if (!lockManager.isValidPin(pin)) {
            error = "Choisissez 4 à 6 chiffres."
            return
        }
        if (!confirming) {
            firstPin = pin
            pin = ""
            error = null
        } else if (firstPin == pin) {
            if (lockManager.setPin(pin)) {
                onDone()
            } else {
                error = "Impossible d'enregistrer le code."
            }
        } else {
            firstPin = null
            pin = ""
            error = "Les deux codes ne correspondent pas."
        }
    }

    PinScaffold(
        modifier = modifier,
        title = if (confirming) "Confirmez votre code" else "Créez votre code PIN",
        subtitle = if (confirming) {
            "Saisissez à nouveau le code pour le confirmer."
        } else {
            "Il sera demandé à chaque démarrage de l'application. 4 à 6 chiffres."
        },
        pin = pin,
        dotCount = 6,
        error = error
    ) {
        PinKeypad(
            onDigit = { digit ->
                if (pin.length < 6) {
                    pin += digit
                    error = null
                }
            },
            onBackspace = {
                if (pin.isNotEmpty()) pin = pin.dropLast(1)
            }
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { submit() },
            enabled = pin.length in 4..6,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (confirming) "Confirmer le code" else "Continuer")
        }
        if (confirming) {
            TextButton(
                onClick = {
                    firstPin = null
                    pin = ""
                    error = null
                }
            ) {
                Text("Recommencer")
            }
        }
    }
}

/** Écran de déverrouillage affiché avant l'accès aux données. */
@Composable
fun PinUnlockScreen(
    lockManager: AppLockManager,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val requiredLength = remember { lockManager.pinLength() }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val biometricAvailable = lockManager.isBiometricEnabled() &&
        activity != null &&
        AppLockManager.isBiometricSupported(context)

    LaunchedEffect(pin) {
        if (pin.length == requiredLength) {
            if (lockManager.verifyPin(pin)) {
                onUnlocked()
            } else {
                error = "Code incorrect. Réessayez."
                pin = ""
            }
        }
    }

    PinScaffold(
        modifier = modifier,
        title = "Application verrouillée",
        subtitle = "Saisissez votre code PIN pour accéder à vos finances.",
        pin = pin,
        dotCount = requiredLength,
        error = error
    ) {
        PinKeypad(
            onDigit = { digit ->
                if (pin.length < requiredLength) {
                    pin += digit
                    error = null
                }
            },
            onBackspace = {
                if (pin.isNotEmpty()) pin = pin.dropLast(1)
            }
        )

        if (biometricAvailable) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = {
                    activity?.let { unlockedActivity ->
                        showBiometricPrompt(
                            activity = unlockedActivity,
                            onSuccess = onUnlocked,
                            onError = { message ->
                                if (message.isNotBlank()) error = message
                            }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Lock, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Déverrouiller par biométrie")
            }
        }
    }
}

@Composable
private fun PinScaffold(
    title: String,
    subtitle: String,
    pin: String,
    dotCount: Int,
    error: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(38.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        PinDots(filled = pin.length, total = dotCount)
        Spacer(Modifier.height(10.dp))
        Text(
            text = error.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.height(20.dp)
        )
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun PinDots(filled: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .size(if (index < filled) 16.dp else 12.dp)
                    .clip(CircleShape)
                    .background(
                        if (index < filled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
            )
        }
    }
}

@Composable
private fun PinKeypad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫")
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { label ->
                    Box(modifier = Modifier.weight(1f)) {
                        when (label) {
                            "" -> Spacer(Modifier.height(60.dp))
                            "⌫" -> OutlinedButton(
                                onClick = onBackspace,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp)
                            ) {
                                Text("⌫", style = MaterialTheme.typography.titleLarge)
                            }

                            else -> Button(
                                onClick = { onDigit(label) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp)
                            ) {
                                Text(label, style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun showBiometricPrompt(
    activity: FragmentActivity,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(
                result: BiometricPrompt.AuthenticationResult
            ) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError(errString.toString())
            }
        }
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Déverrouiller Budget Tracker")
        .setSubtitle("Utilisez votre empreinte digitale")
        .setNegativeButtonText("Utiliser le code PIN")
        .setConfirmationRequired(false)
        .build()
    prompt.authenticate(info)
}

private fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

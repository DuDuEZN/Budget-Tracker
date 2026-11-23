package com.duduezn.budgettracker.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.duduezn.budgettracker.BudgetTrackerApp
import com.duduezn.budgettracker.domain.model.IncomingMessage
import com.duduezn.budgettracker.domain.model.MessageSource
import com.duduezn.budgettracker.security.AppLockManager
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    lockManager: AppLockManager,
    onLockNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val biometricSupported = remember { AppLockManager.isBiometricSupported(context) }
    var biometricEnabled by remember { mutableStateOf(lockManager.isBiometricEnabled()) }
    var showChangePin by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Paramètres",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        item { SectionHeader("Permissions") }

        item {
            PermissionRow(
                title = "SMS",
                granted = hasSmsPermission(context),
                actionLabel = "Ouvrir les réglages",
                onAction = { openAppSettings(context) }
            )
        }
        item {
            PermissionRow(
                title = "Accès aux notifications",
                granted = NotificationManagerCompat.getEnabledListenerPackages(context)
                    .contains(context.packageName),
                actionLabel = "Activer",
                onAction = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            )
        }
        item {
            PermissionRow(
                title = "Notifications",
                granted = NotificationManagerCompat.from(context).areNotificationsEnabled(),
                actionLabel = "Ouvrir les réglages",
                onAction = { openAppSettings(context) }
            )
        }
        item {
            PermissionRow(
                title = "Optimisation batterie",
                granted = isIgnoringBatteryOptimizations(context),
                actionLabel = "Désactiver",
                onAction = {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            )
        }

        item { SectionHeader("Sécurité") }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Code PIN", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Demandé à chaque démarrage de l'application.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { showChangePin = true }) {
                            Text("Modifier")
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Verrouillage biométrique", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (biometricSupported) {
                                    "Déverrouillez avec votre empreinte en plus du code PIN."
                                } else {
                                    "Non disponible sur cet appareil (Android 9+ requis)."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = biometricEnabled,
                            enabled = biometricSupported,
                            onCheckedChange = { value ->
                                biometricEnabled = value
                                lockManager.setBiometricEnabled(value)
                            }
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onLockNow) {
                            Text("Verrouiller maintenant")
                        }
                    }
                }
            }
        }

        item { SectionHeader("Mode test") }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Injecte 5 faux SMS (MTN, Moov, Celtiis, BIIC) pour tester " +
                            "les notifications et la mise à jour du budget.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        scope.launch {
                            val app = context.applicationContext as BudgetTrackerApp
                            DEMO_MESSAGES.forEach { (sender, body) ->
                                app.container.ingestMessage(
                                    IncomingMessage(
                                        body = body,
                                        sender = sender,
                                        timestamp = System.currentTimeMillis(),
                                        source = MessageSource.SMS
                                    )
                                )
                            }
                        }
                    }) {
                        Text("Lancer le mode test")
                    }
                }
            }
        }

        item { SectionHeader("Budgets") }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Les plafonds mensuels se gèrent dans l'onglet Catégories.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }

    if (showChangePin) {
        ChangePinDialog(
            lockManager = lockManager,
            onDismiss = { showChangePin = false }
        )
    }
}

@Composable
private fun ChangePinDialog(
    lockManager: AppLockManager,
    onDismiss: () -> Unit
) {
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val canSubmit = currentPin.isNotEmpty() &&
        lockManager.isValidPin(newPin) &&
        newPin == confirmPin

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifier le code PIN") },
        text = {
            Column {
                OutlinedTextField(
                    value = currentPin,
                    onValueChange = { value ->
                        currentPin = value.filter { it.isDigit() }.take(6)
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Code actuel") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { value ->
                        newPin = value.filter { it.isDigit() }.take(6)
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nouveau code (4 à 6 chiffres)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { value ->
                        confirmPin = value.filter { it.isDigit() }.take(6)
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Confirmer le nouveau code") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSubmit,
                onClick = {
                    if (!lockManager.verifyPin(currentPin)) {
                        error = "Le code actuel est incorrect."
                    } else if (newPin != confirmPin) {
                        error = "Les deux nouveaux codes ne correspondent pas."
                    } else if (lockManager.setPin(newPin)) {
                        onDismiss()
                    } else {
                        error = "Impossible d'enregistrer le nouveau code."
                    }
                }
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun PermissionRow(
    title: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (granted) {
                Color(0xFFE8F5E9)
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (granted) "Activé" else "Désactivé",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (granted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onErrorContainer
                )
            }
            if (!granted) {
                TextButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

private fun hasSmsPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    context.startActivity(intent)
}

private val DEMO_MESSAGES = listOf(
    "MTN" to "Transfert 10000F de VITAL KADOUKPE EZIN\nVITAL KADOUKPE EZIN 2026-11-21 16:30:04\nRef:BAccount:BJ66BJ18501100***********\n9,BFI:BJB011,BName:VITAL KADOUKPE\nDIEUDONNEEZIN Solde: *****F ID:13429733003.",
    "MTN" to "Paiement 5000F a MFS DIRECT SBEE\n2026-11-21 16:33:09 Frais:50F Solde: **** F\nID:13429757965 Ref :-. Nouvelle App MoMo\nhttps://onelink.to/va7cnt",
    "MTN" to "Transfert 4000F a HOUEFA GINA MAHUSSE\nABOUTA(2290152380480) 2026-11-21 20:28:50\nFrais: 50F Solde:912F Ref: Gina ID: 13432144364.Nouvelle App MoMo\nhttps://onelink.to/va7cnt",
    "Moov" to "Retrait de 10 000 FCFA effectue. Frais: 100 FCFA.\nNouveau solde: 5 000 FCFA.",
    "Celtiis" to "Celtiis Money: vous avez effectue un paiement de 1 000 FCFA chez BOUTIQUE ALPHA."
)

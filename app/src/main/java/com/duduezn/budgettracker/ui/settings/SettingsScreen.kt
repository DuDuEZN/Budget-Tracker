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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.duduezn.budgettracker.BudgetTrackerApp
import com.duduezn.budgettracker.R
import com.duduezn.budgettracker.domain.model.IncomingMessage
import com.duduezn.budgettracker.domain.model.MessageSource
import com.duduezn.budgettracker.ui.util.hexToColor
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var biometricEnabled by remember {
        mutableStateOf(
            context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getBoolean("biometric_lock", false)
        )
    }

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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Verrouillage biométrique", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Protège l'application au démarrage.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = biometricEnabled,
                        onCheckedChange = { value ->
                            biometricEnabled = value
                            context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                                .edit()
                                .putBoolean("biometric_lock", value)
                                .apply()
                        }
                    )
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

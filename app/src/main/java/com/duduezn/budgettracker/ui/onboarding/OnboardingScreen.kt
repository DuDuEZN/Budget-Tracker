package com.duduezn.budgettracker.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val title: String,
    val description: String,
    val icon: ImageVector
)

private val pages = listOf(
    OnboardingPage(
        title = "Bienvenue dans Budget Tracker",
        description = "Suivez vos dépenses Mobile Money (MTN MoMo, Moov Money, Celtiis, BIIC) " +
            "sans jamais vous connecter à internet. Tout reste sur votre téléphone.",
        icon = Icons.Default.CheckCircle
    ),
    OnboardingPage(
        title = "Lisez vos SMS Mobile Money",
        description = "L'application analyse uniquement les SMS de transactions pour enregistrer " +
            "automatiquement les montants. Les publicités, codes OTP et messages personnels sont ignorés.",
        icon = Icons.Default.Email
    ),
    OnboardingPage(
        title = "Détectez les notifications",
        description = "En plus des SMS, l'accès aux notifications permet de capter les paiements " +
            "Mobile Money affichés par vos applications. Aucune notification n'est envoyée à l'extérieur.",
        icon = Icons.Default.Notifications
    ),
    OnboardingPage(
        title = "Gardez l'application active",
        description = "Désactivez l'optimisation de batterie pour que la détection continue même " +
            "quand le téléphone est en veille. Vous pouvez revenir sur ce réglage plus tard.",
        icon = Icons.Default.Settings
    ),
    OnboardingPage(
        title = "Vos données restent privées",
        description = "Aucun compte, aucun cloud, aucune permission INTERNET. Vos transactions et " +
            "votre code PIN restent stockés localement. Vous pouvez commencer maintenant.",
        icon = Icons.Default.Lock
    )
)

/**
 * Assistant de premier lancement : explique chaque permission avec une illustration.
 * L'utilisateur peut passer l'assistant ; les permissions restent modifiables dans Paramètres.
 */
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val isLastPage = pagerState.currentPage == pages.lastIndex

    var smsGranted by remember { mutableStateOf(hasSmsPermission(context)) }
    var notificationsGranted by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    var listenerEnabled by remember {
        mutableStateOf(hasNotificationListenerAccess(context))
    }
    var batteryOptimized by remember {
        mutableStateOf(isIgnoringBatteryOptimizations(context))
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        smsGranted = hasSmsPermission(context)
        notificationsGranted = NotificationManagerCompat.from(context).areNotificationsEnabled()
        listenerEnabled = hasNotificationListenerAccess(context)
        batteryOptimized = isIgnoringBatteryOptimizations(context)
    }

    val smsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        smsGranted = result.values.all { it } || hasSmsPermission(context)
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsGranted = granted
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onComplete) {
                Text("Passer")
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { index ->
            val page = pages[index]
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                OnboardingIllustration(page.icon)
                Spacer(Modifier.height(28.dp))
                Text(
                    text = page.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = page.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))

                when (index) {
                    1 -> PermissionAction(
                        granted = smsGranted,
                        grantedLabel = "SMS déjà autorisés",
                        actionLabel = "Autoriser la lecture des SMS",
                        onAction = {
                            smsLauncher.launch(
                                arrayOf(
                                    Manifest.permission.RECEIVE_SMS,
                                    Manifest.permission.READ_SMS
                                )
                            )
                        }
                    )

                    2 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PermissionAction(
                            granted = listenerEnabled,
                            grantedLabel = "Accès aux notifications activé",
                            actionLabel = "Ouvrir l'accès aux notifications",
                            onAction = {
                                context.startActivity(
                                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                )
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                        PermissionAction(
                            granted = notificationsGranted,
                            grantedLabel = "Alertes autorisées",
                            actionLabel = "Autoriser les alertes",
                            onAction = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationLauncher.launch(
                                        Manifest.permission.POST_NOTIFICATIONS
                                    )
                                } else {
                                    notificationsGranted = true
                                }
                            }
                        )
                    }

                    3 -> PermissionAction(
                        granted = batteryOptimized,
                        grantedLabel = "Optimisation batterie désactivée",
                        actionLabel = "Désactiver l'optimisation",
                        onAction = {
                            val intent = Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                            ).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(pages.size) { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (index == pagerState.currentPage) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == pagerState.currentPage) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                    }
                },
                enabled = pagerState.currentPage > 0
            ) {
                Text("Précédent")
            }

            Button(
                onClick = {
                    if (isLastPage) {
                        onComplete()
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                }
            ) {
                Text(if (isLastPage) "Commencer" else "Suivant")
            }
        }
    }
}

@Composable
private fun OnboardingIllustration(icon: ImageVector) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier.size(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(CircleShape)
                .background(primary.copy(alpha = 0.08f))
        )
        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(CircleShape)
                .background(primary.copy(alpha = 0.14f))
        )
        Box(
            modifier = Modifier
                .size(104.dp)
                .clip(CircleShape)
                .background(primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(52.dp)
            )
        }
    }
}

@Composable
private fun PermissionAction(
    granted: Boolean,
    grantedLabel: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    if (granted) {
        Text(
            text = "✓ $grantedLabel",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    } else {
        OutlinedButton(onClick = onAction) {
            Text(actionLabel)
        }
    }
}

private fun hasSmsPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) ==
        PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED

private fun hasNotificationListenerAccess(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

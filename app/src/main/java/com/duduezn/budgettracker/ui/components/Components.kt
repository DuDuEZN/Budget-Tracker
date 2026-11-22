package com.duduezn.budgettracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.duduezn.budgettracker.domain.model.Transaction
import com.duduezn.budgettracker.domain.model.TransactionStatus
import com.duduezn.budgettracker.domain.model.TransactionType
import com.duduezn.budgettracker.ui.model.CategoryProgress
import com.duduezn.budgettracker.ui.theme.IncomeGreen
import com.duduezn.budgettracker.ui.theme.PendingAmber
import com.duduezn.budgettracker.ui.util.formatDate
import com.duduezn.budgettracker.ui.util.formatSignedXof
import com.duduezn.budgettracker.ui.util.formatXof
import com.duduezn.budgettracker.ui.util.hexToColor

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
fun CategoryBudgetRow(progress: CategoryProgress, modifier: Modifier = Modifier) {
    val category = progress.category
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(hexToColor(category.colorHex))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (category.monthlyBudgetLimit > 0.0) {
                    "${formatXof(progress.spent)} / ${formatXof(category.monthlyBudgetLimit)}"
                } else {
                    formatXof(progress.spent)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (category.monthlyBudgetLimit > 0.0) {
            Spacer(Modifier.height(5.dp))
            val overBudget = progress.ratio > 1f
            LinearProgressIndicator(
                progress = { progress.ratio.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = when {
                    overBudget -> MaterialTheme.colorScheme.error
                    progress.ratio >= 0.8f -> Color(0xFFEF6C00)
                    else -> hexToColor(category.colorHex)
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
fun TransactionCard(
    transaction: Transaction,
    categoryName: String?,
    modifier: Modifier = Modifier,
    onConfirmReason: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (transaction.type == TransactionType.DEPOT) {
                                IncomeGreen
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (transaction.type == TransactionType.DEPOT) "+" else "−",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (transaction.type == TransactionType.DEPOT) {
                            Color.White
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        }
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transaction.counterparty
                            ?: "${transaction.operator.label} • ${transaction.type.label}",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${transaction.type.label} • ${formatDate(transaction.timestamp)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatSignedXof(transaction),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (transaction.type == TransactionType.DEPOT) {
                            IncomeGreen
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    StatusBadge(transaction.status)
                }
            }

            val detail = listOfNotNull(
                categoryName,
                transaction.finalReason?.takeIf { it != categoryName }
            ).joinToString(" • ")

            if (detail.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (transaction.status == TransactionStatus.PENDING_REASON && onConfirmReason != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onConfirmReason) {
                        Text("Préciser le motif")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: TransactionStatus) {
    val (container, content) = when (status) {
        TransactionStatus.CONFIRMED -> MaterialTheme.colorScheme.primaryContainer to
            MaterialTheme.colorScheme.onPrimaryContainer
        TransactionStatus.PENDING_REASON -> PendingAmber.copy(alpha = 0.15f) to PendingAmber
    }
    Surface(
        color = container,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.padding(top = 3.dp)
    ) {
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

package com.duduezn.budgettracker.ui.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duduezn.budgettracker.domain.analysis.DetectedSubscription
import com.duduezn.budgettracker.ui.components.SectionTitle
import com.duduezn.budgettracker.ui.util.formatXof
import com.duduezn.budgettracker.ui.util.hexToColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AnalysisScreen(
    viewModel: AnalysisViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Analyse",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnalysisScale.entries.forEach { scale ->
                    FilterChip(
                        selected = state.scale == scale,
                        onClick = { viewModel.setScale(scale) },
                        label = { Text(scale.label) }
                    )
                }
            }
        }

        item {
            CategorySelector(
                state = state,
                onSelected = { viewModel.setCategory(it) }
            )
        }

        item {
            val selectedCategory = state.categories.firstOrNull { it.id == state.selectedCategoryId }
            val chartColor = selectedCategory?.let { hexToColor(it.colorHex) }
                ?: MaterialTheme.colorScheme.primary

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = state.selectedCategoryName ?: "Toutes les dépenses",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Évolution sur ${state.buckets.size} " +
                            if (state.scale == AnalysisScale.MONTH) "mois" else "années",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.scale == AnalysisScale.MONTH && state.monthStartDay != 1) {
                        Text(
                            text = "Mois budgétaire : du ${state.monthStartDay} au " +
                                "${state.monthStartDay - 1} du mois suivant",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    SpendingLineChart(
                        values = state.series,
                        labels = state.labels,
                        color = chartColor
                    )
                }
            }
        }

        item { ComparisonCard(state) }

        item { SectionTitle("Comparaison par catégorie") }

        if (state.categoryComparisons.isEmpty()) {
            item {
                Text(
                    text = "Aucune dépense confirmée sur les périodes affichées.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(state.categoryComparisons, key = { "comparison-${it.category.id}" }) { comparison ->
                CategoryComparisonRow(comparison)
            }
        }

        item { SectionTitle("Abonnements détectés") }

        if (state.subscriptions.isEmpty()) {
            item {
                Text(
                    text = "Aucun abonnement mensuel détecté pour l'instant. " +
                        "L'analyse devient plus fiable après 3 paiements au même marchand.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(state.subscriptions, key = { "subscription-${it.normalizedCounterparty}" }) { subscription ->
                SubscriptionCard(
                    subscription = subscription,
                    categoryName = subscription.categoryId?.let { id ->
                        state.categories.firstOrNull { it.id == id }?.name
                    }
                )
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun CategorySelector(
    state: AnalysisUiState,
    onSelected: (Long?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(
                text = state.selectedCategoryName ?: "Toutes les catégories",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Toutes les catégories") },
                onClick = {
                    onSelected(null)
                    expanded = false
                }
            )
            state.categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name) },
                    onClick = {
                        onSelected(category.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ComparisonCard(state: AnalysisUiState) {
    val currentLabel = state.buckets.lastOrNull()?.label.orEmpty()
    val previousLabel = state.buckets.dropLast(1).lastOrNull()?.label.orEmpty()
    val delta = if (state.previousTotal > 0.0) {
        (state.currentTotal - state.previousTotal) / state.previousTotal * 100.0
    } else {
        null
    }
    val deltaColor = when {
        delta == null -> MaterialTheme.colorScheme.onSurfaceVariant
        delta > 0.0 -> MaterialTheme.colorScheme.error
        delta < 0.0 -> Color(0xFF2E7D32)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Comparaison $currentLabel / $previousLabel",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = currentLabel.ifBlank { "Période actuelle" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                    Text(
                        text = formatXof(state.currentTotal),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = previousLabel.ifBlank { "Période précédente" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                    Text(
                        text = formatXof(state.previousTotal),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = when {
                    delta == null && state.currentTotal > 0.0 -> "Nouvelle dépense sur cette période"
                    delta == null -> "Aucune dépense sur ces périodes"
                    delta > 0.0 -> "En hausse de ${delta.toInt()} %"
                    delta < 0.0 -> "En baisse de ${(-delta).toInt()} %"
                    else -> "Stable"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = deltaColor
            )
        }
    }
}

@Composable
private fun CategoryComparisonRow(comparison: CategoryComparison) {
    val delta = comparison.deltaPercent
    val deltaColor = when {
        delta == null -> MaterialTheme.colorScheme.onSurfaceVariant
        delta > 0.0 -> MaterialTheme.colorScheme.error
        delta < 0.0 -> Color(0xFF2E7D32)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(hexToColor(comparison.category.colorHex))
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = comparison.category.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "précédent : ${formatXof(comparison.previous)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatXof(comparison.current),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when {
                        delta == null && comparison.current > 0.0 -> "nouveau"
                        delta == null -> "—"
                        delta > 0.0 -> "+${delta.toInt()} %"
                        delta < 0.0 -> "${delta.toInt()} %"
                        else -> "stable"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = deltaColor
                )
            }
        }
    }
}

@Composable
private fun SubscriptionCard(
    subscription: DetectedSubscription,
    categoryName: String?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "↻",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = subscription.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = categoryName ?: "Sans catégorie",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = formatXof(subscription.averageAmount),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Tous les ${subscription.intervalDays} jours • " +
                    "${subscription.occurrences} paiements • " +
                    if (subscription.active) "actif" else "en pause",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Dernier : ${shortDate(subscription.lastPayment)} • " +
                    "Prochain : ${shortDate(subscription.nextExpected)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SpendingLineChart(
    values: List<Double>,
    labels: List<String>,
    color: Color,
    modifier: Modifier = Modifier
) {
    if (values.isEmpty()) {
        Text(
            text = "Pas encore de données à afficher.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val maxValue = values.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Maximum : ${formatXof(maxValue)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            val horizontalPadding = 8.dp.toPx()
            val verticalPadding = 10.dp.toPx()
            val left = horizontalPadding
            val right = size.width - horizontalPadding
            val top = verticalPadding
            val bottom = size.height - verticalPadding
            val chartWidth = right - left
            val chartHeight = bottom - top

            repeat(4) { index ->
                val y = top + chartHeight * index / 3f
                drawLine(
                    color = gridColor,
                    start = Offset(left, y),
                    end = Offset(right, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            val stepX = if (values.size > 1) chartWidth / (values.size - 1) else 0f
            val points = values.mapIndexed { index, value ->
                val ratio = (value / maxValue).toFloat().coerceIn(0f, 1f)
                Offset(
                    x = left + stepX * index,
                    y = bottom - ratio * chartHeight
                )
            }

            val areaPath = Path().apply {
                moveTo(points.first().x, bottom)
                points.forEach { lineTo(it.x, it.y) }
                lineTo(points.last().x, bottom)
                close()
            }
            drawPath(areaPath, color = color.copy(alpha = 0.12f))

            val linePath = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(
                path = linePath,
                color = color,
                style = Stroke(
                    width = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            points.forEach { point ->
                drawCircle(color = color, radius = 3.5.dp.toPx(), center = point)
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                val show = labels.size <= 6 || index % 2 == 0 || index == labels.lastIndex
                Text(
                    text = if (show) label else "",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

private fun shortDate(timestamp: Long): String =
    SimpleDateFormat("dd MMM yyyy", Locale.FRENCH).format(Date(timestamp))

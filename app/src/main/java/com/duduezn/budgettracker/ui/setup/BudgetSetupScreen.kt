package com.duduezn.budgettracker.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duduezn.budgettracker.data.local.BudgetSettings
import com.duduezn.budgettracker.domain.util.MonthRange
import com.duduezn.budgettracker.ui.util.formatXof

/**
 * Assistant de premier lancement : définit le revenu mensuel et le jour de début
 * du mois budgétaire. Le salaire n'arrive pas toujours le 1er : l'utilisateur
 * choisit le jour qui correspond à son cycle.
 */
@Composable
fun BudgetSetupScreen(
    budgetSettings: BudgetSettings,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val budget by budgetSettings.state.collectAsStateWithLifecycle()

    var incomeText by remember(budget.monthlyIncome) {
        mutableStateOf(if (budget.monthlyIncome > 0.0) budget.monthlyIncome.toLong().toString() else "")
    }
    var startDayText by remember(budget.monthStartDay) {
        mutableStateOf(budget.monthStartDay.toString())
    }

    val income = incomeText.filter { it.isDigit() }.toDoubleOrNull()
    val startDay = startDayText.toIntOrNull()
    val validDay = startDay != null && startDay in MonthRange.MIN_START_DAY..MonthRange.MAX_START_DAY

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Configurons ton budget",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Quelques informations pour que l'application suive ton mois à partir " +
                "de ton propre cycle, pas forcément du 1er du mois.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Revenu mensuel", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Le montant que tu prévois de percevoir ce mois (salaire, revenus).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = incomeText,
                    onValueChange = { value ->
                        incomeText = value.filter { it.isDigit() }.take(12)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Montant (FCFA)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = {
                        if (income != null) Text(formatXof(income))
                    }
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Début du mois budgétaire", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Souvent le jour de ton salaire (entre 1 et 28). Le cycle ira de ce " +
                        "jour au jour précédent du mois suivant.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = startDayText,
                    onValueChange = { value ->
                        startDayText = value.filter { it.isDigit() }.take(2)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Jour du mois") },
                    singleLine = true,
                    isError = !validDay,
                    supportingText = {
                        if (validDay) {
                            Text(MonthRange.label(startDay = startDay!!))
                        } else {
                            Text("Entre 1 et 28.")
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }

        if (income != null && income > 0.0 && validDay) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Aperçu", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Cycle : ${MonthRange.label(startDay = startDay!!)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Revenu : ${formatXof(income)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                if (income != null) budgetSettings.setMonthlyIncome(income)
                if (validDay) budgetSettings.setMonthStartDay(startDay!!)
                budgetSettings.setBudgetSetupComplete()
                onComplete()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = validDay
        ) {
            Text("Continuer")
        }

        TextButton(
            onClick = {
                budgetSettings.setBudgetSetupComplete()
                onComplete()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Passer pour l'instant")
        }
    }
}

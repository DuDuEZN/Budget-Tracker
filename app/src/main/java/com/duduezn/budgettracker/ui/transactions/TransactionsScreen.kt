package com.duduezn.budgettracker.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duduezn.budgettracker.domain.model.Category
import com.duduezn.budgettracker.domain.model.Transaction
import com.duduezn.budgettracker.domain.model.TransactionType
import com.duduezn.budgettracker.ui.components.TransactionCard
import com.duduezn.budgettracker.ui.util.formatXof
import com.duduezn.budgettracker.ui.util.hexToColor

@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }
    var showManualDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Transactions",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${state.pendingCount} en attente de motif",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { showManualDialog = true }) {
                    Text("Ajouter cash")
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TransactionFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.filter == filter,
                        onClick = { viewModel.setFilter(filter) },
                        label = { Text(filter.label) }
                    )
                }
                TransactionType.entries.forEach { type ->
                    FilterChip(
                        selected = state.typeFilter == type,
                        onClick = {
                            viewModel.setTypeFilter(if (state.typeFilter == type) null else type)
                        },
                        label = { Text(type.label) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            CategoryFilterRow(
                categories = state.categories,
                selectedCategoryId = state.categoryFilter,
                onSelected = { viewModel.setCategoryFilter(it) }
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.transactions.isEmpty()) {
                item {
                    Text(
                        text = "Aucune transaction dans ce filtre.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }

            items(state.transactions, key = { it.id }) { transaction ->
                TransactionCard(
                    transaction = transaction,
                    categoryName = transaction.detectedCategory
                        ?.let { state.categoriesById[it]?.name },
                    onConfirmReason = { selectedTransaction = transaction }
                )
            }
        }
    }

    selectedTransaction?.let { transaction ->
        ConfirmReasonDialog(
            transaction = transaction,
            categories = state.categories,
            onDismiss = { selectedTransaction = null },
            onConfirm = { categoryId, reason, rememberMerchant ->
                viewModel.confirmReason(transaction.id, categoryId, reason, rememberMerchant)
                selectedTransaction = null
            }
        )
    }

    if (showManualDialog) {
        ManualTransactionDialog(
            categories = state.categories,
            onDismiss = { showManualDialog = false },
            onConfirm = { type, amount, counterparty, categoryId, reason, remember ->
                viewModel.addManualTransaction(type, amount, counterparty, categoryId, reason, remember)
                showManualDialog = false
            }
        )
    }
}

@Composable
private fun CategoryFilterRow(
    categories: List<Category>,
    selectedCategoryId: Long?,
    onSelected: (Long?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            val label = selectedCategoryId?.let { id ->
                categories.firstOrNull { it.id == id }?.name
            } ?: "Toutes catégories"
            Text(label)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Toutes catégories") },
                onClick = {
                    onSelected(null)
                    expanded = false
                }
            )
            categories.forEach { category ->
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
private fun ConfirmReasonDialog(
    transaction: Transaction,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (categoryId: Long, reason: String, rememberMerchant: Boolean) -> Unit
) {
    var selectedCategoryId by remember(transaction.id) {
        mutableStateOf(transaction.detectedCategory)
    }
    var reason by remember(transaction.id) {
        mutableStateOf(transaction.rawReason.orEmpty())
    }
    var rememberMerchant by remember(transaction.id) { mutableStateOf(true) }
    val selectedCategory = categories.firstOrNull { it.id == selectedCategoryId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Préciser le motif") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "${transaction.type.label} de ${formatXof(transaction.amount)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (!transaction.counterparty.isNullOrBlank()) {
                    Text(
                        text = transaction.counterparty,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Catégorie",
                    style = MaterialTheme.typography.labelLarge
                )

                categories.forEach { category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedCategoryId = category.id }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedCategoryId == category.id,
                            onClick = { selectedCategoryId = category.id }
                        )
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(hexToColor(category.colorHex))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = category.name,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Motif (optionnel)") },
                    placeholder = { Text("Ex. Déjeuner, taxi, facture…") },
                    singleLine = true
                )

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = rememberMerchant,
                        onCheckedChange = { rememberMerchant = it }
                    )
                    Text(
                        text = "Mémoriser pour ce contact/marchand",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedCategoryId != null,
                onClick = {
                    val categoryId = selectedCategoryId ?: return@TextButton
                    val finalReason = reason.trim().ifBlank {
                        selectedCategory?.name ?: "Motif confirmé"
                    }
                    onConfirm(categoryId, finalReason, rememberMerchant)
                }
            ) {
                Text("Confirmer")
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
private fun ManualTransactionDialog(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (
        type: TransactionType,
        amount: Double,
        counterparty: String?,
        categoryId: Long?,
        reason: String?,
        rememberMerchant: Boolean
    ) -> Unit
) {
    var type by remember { mutableStateOf(TransactionType.PAIEMENT) }
    var amountText by remember { mutableStateOf("") }
    var counterparty by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var rememberMerchant by remember { mutableStateOf(true) }

    val amount = amountText.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter une dépense cash") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TransactionType.entries.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(t.label) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Montant (FCFA)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = counterparty,
                    onValueChange = { counterparty = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Marchand / contact (optionnel)") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Text("Catégorie", style = MaterialTheme.typography.labelLarge)
                categories.forEach { category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedCategoryId = if (selectedCategoryId == category.id) null else category.id
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedCategoryId == category.id,
                            onClick = {
                                selectedCategoryId = if (selectedCategoryId == category.id) null else category.id
                            }
                        )
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(hexToColor(category.colorHex))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(category.name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Motif (optionnel)") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = rememberMerchant,
                        onCheckedChange = { rememberMerchant = it }
                    )
                    Text("Mémoriser pour ce contact/marchand", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = amount != null && amount!! > 0.0,
                onClick = {
                    onConfirm(
                        type,
                        amount!!,
                        counterparty,
                        selectedCategoryId,
                        reason,
                        rememberMerchant
                    )
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

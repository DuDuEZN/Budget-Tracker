package com.duduezn.budgettracker.data.mapper

import com.duduezn.budgettracker.data.local.DefaultCategory
import com.duduezn.budgettracker.data.local.entity.CategoryEntity
import com.duduezn.budgettracker.data.local.entity.MerchantRuleEntity
import com.duduezn.budgettracker.data.local.entity.TransactionEntity
import com.duduezn.budgettracker.domain.model.Category
import com.duduezn.budgettracker.domain.model.MerchantRule
import com.duduezn.budgettracker.domain.model.Transaction

fun TransactionEntity.toDomain(): Transaction = Transaction(
    id = id,
    rawMessage = rawMessage,
    operator = operator,
    type = type,
    amount = amount,
    currency = currency,
    counterparty = counterparty,
    timestamp = timestamp,
    rawReason = rawReason,
    detectedCategory = detectedCategory,
    finalReason = finalReason,
    status = status
)

fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    monthlyBudgetLimit = monthlyBudgetLimit,
    colorHex = colorHex,
    iconName = iconName
)

fun MerchantRuleEntity.toDomain(): MerchantRule = MerchantRule(
    id = id,
    counterpartyIdentifier = counterpartyIdentifier,
    defaultReason = defaultReason,
    categoryId = categoryId,
    usageCount = usageCount
)

fun DefaultCategory.toEntity(): CategoryEntity = CategoryEntity(
    name = name,
    monthlyBudgetLimit = monthlyBudgetLimit,
    colorHex = colorHex,
    iconName = iconName
)

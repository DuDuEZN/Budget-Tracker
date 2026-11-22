package com.duduezn.budgettracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Règle d'automatisation : un identifiant marchand (numéro ou nom normalisé)
 * est associé à un motif et une catégorie par défaut.
 */
@Entity(
    tableName = "merchant_rules",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["counterpartyIdentifier"], unique = true),
        Index(value = ["categoryId"])
    ]
)
data class MerchantRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val counterpartyIdentifier: String,
    val defaultReason: String,
    val categoryId: Long,
    val usageCount: Int = 0
)

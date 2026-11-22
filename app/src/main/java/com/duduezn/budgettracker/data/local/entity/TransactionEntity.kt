package com.duduezn.budgettracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.duduezn.budgettracker.domain.model.Operator
import com.duduezn.budgettracker.domain.model.TransactionStatus
import com.duduezn.budgettracker.domain.model.TransactionType

/**
 * Transaction issue d'un SMS ou d'une notification Mobile Money.
 *
 * `detectedCategory` référence `categories.id` (nullable : motif non encore identifié).
 * `finalReason` contient le motif retenu (règle marchand, texte du message ou saisie utilisateur).
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["detectedCategory"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["status"]),
        Index(value = ["detectedCategory"]),
        Index(value = ["rawMessage", "timestamp"], unique = true)
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val rawMessage: String,
    val operator: Operator,
    val type: TransactionType,
    val amount: Double,
    val currency: String = "XOF",
    val counterparty: String? = null,
    val timestamp: Long,
    val rawReason: String? = null,
    val detectedCategory: Long? = null,
    val finalReason: String? = null,
    val status: TransactionStatus = TransactionStatus.PENDING_REASON
)

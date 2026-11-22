package com.duduezn.budgettracker.data.local

import androidx.room.TypeConverter
import com.duduezn.budgettracker.domain.model.Operator
import com.duduezn.budgettracker.domain.model.TransactionStatus
import com.duduezn.budgettracker.domain.model.TransactionType

/** Conversion des enums en texte lisible dans SQLite. */
class Converters {

    @TypeConverter
    fun fromOperator(value: Operator): String = value.name

    @TypeConverter
    fun toOperator(value: String): Operator =
        runCatching { Operator.valueOf(value) }.getOrDefault(Operator.UNKNOWN)

    @TypeConverter
    fun fromTransactionType(value: TransactionType): String = value.name

    @TypeConverter
    fun toTransactionType(value: String): TransactionType =
        runCatching { TransactionType.valueOf(value) }.getOrDefault(TransactionType.PAIEMENT)

    @TypeConverter
    fun fromTransactionStatus(value: TransactionStatus): String = value.name

    @TypeConverter
    fun toTransactionStatus(value: String): TransactionStatus =
        runCatching { TransactionStatus.valueOf(value) }
            .getOrDefault(TransactionStatus.PENDING_REASON)
}

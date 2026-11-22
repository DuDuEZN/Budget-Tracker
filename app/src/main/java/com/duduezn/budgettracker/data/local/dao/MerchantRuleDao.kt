package com.duduezn.budgettracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.duduezn.budgettracker.data.local.entity.MerchantRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MerchantRuleDao {

    @Query("SELECT * FROM merchant_rules ORDER BY usageCount DESC")
    fun observeAll(): Flow<List<MerchantRuleEntity>>

    @Query("SELECT * FROM merchant_rules WHERE counterpartyIdentifier = :counterparty LIMIT 1")
    suspend fun findByCounterparty(counterparty: String): MerchantRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: MerchantRuleEntity): Long

    @Query(
        """
        UPDATE merchant_rules
        SET defaultReason = :reason,
            categoryId = :categoryId,
            usageCount = usageCount + 1
        WHERE counterpartyIdentifier = :counterparty
        """
    )
    suspend fun updateUsage(counterparty: String, reason: String, categoryId: Long)
}

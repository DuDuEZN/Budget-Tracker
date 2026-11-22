package com.duduezn.budgettracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.duduezn.budgettracker.data.local.dao.CategoryDao
import com.duduezn.budgettracker.data.local.dao.MerchantRuleDao
import com.duduezn.budgettracker.data.local.dao.TransactionDao
import com.duduezn.budgettracker.data.local.entity.CategoryEntity
import com.duduezn.budgettracker.data.local.entity.MerchantRuleEntity
import com.duduezn.budgettracker.data.local.entity.TransactionEntity

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        MerchantRuleEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class BudgetDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao

    abstract fun categoryDao(): CategoryDao

    abstract fun merchantRuleDao(): MerchantRuleDao

    companion object {
        private const val DATABASE_NAME = "budget_tracker.db"

        @Volatile
        private var instance: BudgetDatabase? = null

        fun getInstance(context: Context): BudgetDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): BudgetDatabase =
            Room.databaseBuilder(context, BudgetDatabase::class.java, DATABASE_NAME)
                .addCallback(SeedCallback)
                .build()

        /**
         * Pré-remplit les catégories par défaut à la création de la base.
         * `INSERT OR IGNORE` rend l'opération idempotente.
         */
        private val SeedCallback = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                DefaultCategories.ALL.forEach { category ->
                    db.execSQL(
                        """
                        INSERT OR IGNORE INTO categories
                            (name, monthlyBudgetLimit, colorHex, iconName)
                        VALUES (?, ?, ?, ?)
                        """.trimIndent(),
                        arrayOf<Any?>(
                            category.name,
                            category.monthlyBudgetLimit,
                            category.colorHex,
                            category.iconName
                        )
                    )
                }
            }
        }
    }
}

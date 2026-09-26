package com.pesaflow.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.TypeConverters
import com.pesaflow.app.data.models.*


@Database(
    entities = [Transaction::class, PendingTransaction::class, Budget::class, SavingsGoal::class, UniversityProfile::class, Bill::class, Debt::class, MealItem::class, ChamaGroup::class, Belonging::class, KitchenStock::class, UserRhythm::class],
    version = 13,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun pendingTransactionDao(): PendingTransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun universityProfileDao(): UniversityProfileDao
    abstract fun billDao(): BillDao
    abstract fun debtDao(): DebtDao
    abstract fun mealDao(): MealDao
    abstract fun chamaDao(): ChamaDao
    abstract fun belongingDao(): BelongingDao
    abstract fun kitchenStockDao(): KitchenStockDao
    abstract fun userRhythmDao(): UserRhythmDao


    companion object {
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE kitchen_stock ADD COLUMN expiryTimestamp INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE kitchen_stock ADD COLUMN eatByDays INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE debts ADD COLUMN direction TEXT NOT NULL DEFAULT 'THEY_OWE'")
            }
        }
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN isSample INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE transactions ADD COLUMN batchId TEXT")
            }
        }
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS user_rhythms (id TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL, category TEXT NOT NULL, confidence REAL NOT NULL, hint TEXT NOT NULL, dayOfMonth INTEGER NOT NULL, amount REAL NOT NULL, sourceCode TEXT NOT NULL, confirmed INTEGER NOT NULL, dismissed INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null


        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pesaflow_secure_db"
                ).addMigrations(MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
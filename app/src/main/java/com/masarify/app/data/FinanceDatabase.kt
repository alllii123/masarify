package com.masarify.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val currentBalance: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "transactions",
    indices = [Index("accountId"), Index("timestamp"), Index(value = ["messageHash"], unique = true)]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val amount: Long,
    val accountId: Long,
    val toAccountId: Long? = null,
    val category: String,
    val merchant: String = "",
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val source: String = "MANUAL",
    val messageHash: String? = null
)

@Entity(tableName = "sms_drafts", indices = [Index(value = ["messageHash"], unique = true)])
data class SmsDraftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val amount: Long,
    val merchant: String,
    val sender: String,
    val accountHint: String = "",
    val category: String,
    val timestamp: Long,
    val messageHash: String
)

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY id")
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY id")
    suspend fun accountsOnce(): List<AccountEntity>

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int

    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Query("UPDATE accounts SET currentBalance = :balance WHERE id = :accountId")
    suspend fun setBalance(accountId: Long, balance: Long)

    @Query("UPDATE accounts SET currentBalance = currentBalance + :delta WHERE id = :accountId")
    suspend fun adjustBalance(accountId: Long, delta: Long)
}

@Dao
interface FinanceDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC, id DESC")
    fun observeTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC, id DESC")
    suspend fun transactionsOnce(): List<TransactionEntity>

    @Insert
    suspend fun insertTransactionRow(transaction: TransactionEntity): Long

    @Delete
    suspend fun deleteTransactionRow(transaction: TransactionEntity)

    @Query("DELETE FROM transactions")
    suspend fun clearTransactions()

    @Transaction
    suspend fun addTransaction(transaction: TransactionEntity): Long {
        when (transaction.type) {
            "EXPENSE" -> adjustAccount(transaction.accountId, -transaction.amount)
            "INCOME" -> adjustAccount(transaction.accountId, transaction.amount)
            "TRANSFER" -> {
                adjustAccount(transaction.accountId, -transaction.amount)
                transaction.toAccountId?.let { adjustAccount(it, transaction.amount) }
            }
        }
        return insertTransactionRow(transaction)
    }

    @Transaction
    suspend fun deleteAndReverse(transaction: TransactionEntity) {
        when (transaction.type) {
            "EXPENSE" -> adjustAccount(transaction.accountId, transaction.amount)
            "INCOME" -> adjustAccount(transaction.accountId, -transaction.amount)
            "TRANSFER" -> {
                adjustAccount(transaction.accountId, transaction.amount)
                transaction.toAccountId?.let { adjustAccount(it, -transaction.amount) }
            }
        }
        deleteTransactionRow(transaction)
    }

    @Query("UPDATE accounts SET currentBalance = currentBalance + :delta WHERE id = :accountId")
    suspend fun adjustAccount(accountId: Long, delta: Long)
}

@Dao
interface SmsDraftDao {
    @Query("SELECT * FROM sms_drafts ORDER BY timestamp DESC, id DESC")
    fun observeDrafts(): Flow<List<SmsDraftEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(draft: SmsDraftEntity): Long

    @Delete
    suspend fun delete(draft: SmsDraftEntity)

    @Query("DELETE FROM sms_drafts")
    suspend fun clear()
}

@Database(
    entities = [AccountEntity::class, TransactionEntity::class, SmsDraftEntity::class],
    version = 1,
    exportSchema = true
)
abstract class MasarifyDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun financeDao(): FinanceDao
    abstract fun smsDraftDao(): SmsDraftDao

    companion object {
        @Volatile private var instance: MasarifyDatabase? = null
        fun get(context: Context): MasarifyDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                MasarifyDatabase::class.java,
                "masarify.db"
            ).build().also { instance = it }
        }
    }
}

class FinanceRepository(private val db: MasarifyDatabase) {
    val accounts = db.accountDao().observeAccounts()
    val transactions = db.financeDao().observeTransactions()
    val smsDrafts = db.smsDraftDao().observeDrafts()

    suspend fun ensureSetup() {
        if (db.accountDao().count() == 0) {
            db.accountDao().insert(AccountEntity(name = "الحساب الرئيسي", currentBalance = 0))
        }
    }

    suspend fun addAccount(name: String, openingBalance: Long) =
        db.accountDao().insert(AccountEntity(name = name.trim(), currentBalance = openingBalance))

    suspend fun setBalance(accountId: Long, balance: Long) = db.accountDao().setBalance(accountId, balance)

    suspend fun addTransaction(
        type: String,
        amount: Long,
        accountId: Long,
        toAccountId: Long?,
        category: String,
        merchant: String,
        note: String,
        source: String = "MANUAL",
        messageHash: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ) = db.financeDao().addTransaction(
        TransactionEntity(
            type = type,
            amount = amount,
            accountId = accountId,
            toAccountId = toAccountId,
            category = category,
            merchant = merchant.trim(),
            note = note.trim(),
            source = source,
            messageHash = messageHash,
            timestamp = timestamp
        )
    )

    suspend fun deleteTransaction(transaction: TransactionEntity) = db.financeDao().deleteAndReverse(transaction)

    suspend fun confirmDraft(draft: SmsDraftEntity, accountId: Long) {
        addTransaction(
            type = draft.type,
            amount = draft.amount,
            accountId = accountId,
            toAccountId = null,
            category = draft.category,
            merchant = draft.merchant,
            note = "من رسالة بنكية",
            source = "SMS",
            messageHash = draft.messageHash,
            timestamp = draft.timestamp
        )
        db.smsDraftDao().delete(draft)
    }

    suspend fun dismissDraft(draft: SmsDraftEntity) = db.smsDraftDao().delete(draft)
}

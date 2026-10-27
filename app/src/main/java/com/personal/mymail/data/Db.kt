package com.personal.mymail.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY id")
    fun observeAll(): Flow<List<Account>>

    @Query("SELECT * FROM accounts ORDER BY id")
    suspend fun getAll(): List<Account>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun get(id: Long): Account?

    @Insert
    suspend fun insert(account: Account): Long

    @Update
    suspend fun update(account: Account)

    @Delete
    suspend fun delete(account: Account)
}

@Dao
interface MessageDao {
    @Query(
        "SELECT id, accountId, fromName, fromAddress, subject, date, snippet, isRead " +
            "FROM messages ORDER BY date DESC LIMIT 500"
    )
    fun observeAll(): Flow<List<MessageRow>>

    @Query(
        "SELECT id, accountId, fromName, fromAddress, subject, date, snippet, isRead " +
            "FROM messages WHERE accountId = :accountId ORDER BY date DESC LIMIT 500"
    )
    fun observeForAccount(accountId: Long): Flow<List<MessageRow>>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun get(id: Long): MailMessage?

    @Query("SELECT uid FROM messages WHERE accountId = :accountId")
    suspend fun knownUids(accountId: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(messages: List<MailMessage>)

    @Query("UPDATE messages SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: Long)

    @Query("DELETE FROM messages WHERE accountId = :accountId")
    suspend fun deleteForAccount(accountId: Long)
}

@Database(entities = [Account::class, MailMessage::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accounts(): AccountDao
    abstract fun messages(): MessageDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mymail.db"
                ).build().also { instance = it }
            }
    }
}

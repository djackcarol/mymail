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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

private const val ROW_COLS =
    "id, accountId, fromName, fromAddress, subject, date, snippet, isRead, hasAttachments"

private const val FOLDER_ORDER =
    "ORDER BY CASE role WHEN 'INBOX' THEN 0 WHEN 'SENT' THEN 1 WHEN 'DRAFTS' THEN 2 " +
        "WHEN 'ARCHIVE' THEN 3 WHEN 'JUNK' THEN 4 WHEN 'TRASH' THEN 5 ELSE 6 END, " +
        "displayName COLLATE NOCASE"

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
interface FolderDao {
    @Query("SELECT * FROM folders WHERE accountId = :accountId AND syncEnabled = 1 $FOLDER_ORDER")
    fun observeEnabled(accountId: Long): Flow<List<MailFolder>>

    @Query("SELECT * FROM folders WHERE accountId = :accountId $FOLDER_ORDER")
    fun observeAll(accountId: Long): Flow<List<MailFolder>>

    @Query("SELECT * FROM folders WHERE accountId = :accountId")
    suspend fun getAll(accountId: Long): List<MailFolder>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(folders: List<MailFolder>)

    @Query("UPDATE folders SET syncEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE folders SET lastSync = :time WHERE id = :id")
    suspend fun setLastSync(id: Long, time: Long)

    @Query("UPDATE folders SET uidValidity = :validity WHERE id = :id")
    suspend fun setUidValidity(id: Long, validity: Long)

    @Query("DELETE FROM folders WHERE accountId = :accountId")
    suspend fun deleteForAccount(accountId: Long)
}

@Dao
interface MessageDao {
    @Query("SELECT $ROW_COLS FROM messages WHERE folder = 'INBOX' ORDER BY date DESC LIMIT 500")
    fun observeInboxes(): Flow<List<MessageRow>>

    @Query(
        "SELECT $ROW_COLS FROM messages WHERE accountId = :accountId AND folder = :folder " +
            "ORDER BY date DESC LIMIT 500"
    )
    fun observeFolder(accountId: Long, folder: String): Flow<List<MessageRow>>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun get(id: Long): MailMessage?

    @Query("SELECT uid FROM messages WHERE accountId = :accountId AND folder = :folder")
    suspend fun knownUids(accountId: Long, folder: String): List<String>

    /** Returns the new row id for each message, or -1 for ones that were already stored. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(messages: List<MailMessage>): List<Long>

    /** Marks a message read here and remembers that the server still has to be told. */
    @Query("UPDATE messages SET isRead = 1, readPending = 1 WHERE id = :id AND isRead = 0")
    suspend fun markRead(id: Long)

    @Query(
        "SELECT id, uid, isRead, readPending FROM messages " +
            "WHERE accountId = :accountId AND folder = :folder ORDER BY date DESC LIMIT 500"
    )
    suspend fun syncState(accountId: Long, folder: String): List<LocalState>

    @Query("UPDATE messages SET isRead = :read WHERE id IN (:ids)")
    suspend fun setRead(ids: List<Long>, read: Boolean)

    @Query("UPDATE messages SET readPending = 0 WHERE id IN (:ids)")
    suspend fun clearPending(ids: List<Long>)

    @Query("SELECT DISTINCT folder FROM messages WHERE accountId = :accountId AND readPending = 1")
    suspend fun pendingFolders(accountId: Long): List<String>

    @Query("DELETE FROM messages WHERE accountId = :accountId AND folder = :folder")
    suspend fun deleteFolder(accountId: Long, folder: String)

    @Query("UPDATE messages SET htmlBody = :html, htmlChecked = 1 WHERE id = :id")
    suspend fun setHtml(id: Long, html: String)

    @Query("SELECT * FROM messages WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<MailMessage>

    @Query("DELETE FROM messages WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM messages WHERE accountId = :accountId")
    suspend fun deleteForAccount(accountId: Long)
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE messageId = :messageId ORDER BY partIndex")
    suspend fun forMessage(messageId: Long): List<AttachmentInfo>

    @Insert
    suspend fun insertAll(attachments: List<AttachmentInfo>)

    @Query("DELETE FROM attachments WHERE messageId IN (:ids)")
    suspend fun deleteForMessages(ids: List<Long>)

    @Query(
        "DELETE FROM attachments WHERE messageId IN " +
            "(SELECT id FROM messages WHERE accountId = :accountId AND folder = :folder)"
    )
    suspend fun deleteForFolder(accountId: Long, folder: String)

    @Query(
        "DELETE FROM attachments WHERE messageId IN " +
            "(SELECT id FROM messages WHERE accountId = :accountId)"
    )
    suspend fun deleteForAccount(accountId: Long)
}

/**
 * Version 1 -> 2: adds folders, attachments, and extra message columns.
 * Existing accounts and downloaded messages are kept.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE messages ADD COLUMN folder TEXT NOT NULL DEFAULT 'INBOX'")
        db.execSQL("ALTER TABLE messages ADD COLUMN toAddresses TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE messages ADD COLUMN ccAddresses TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE messages ADD COLUMN messageIdHeader TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE messages ADD COLUMN hasAttachments INTEGER NOT NULL DEFAULT 0")
        db.execSQL("DROP INDEX IF EXISTS index_messages_accountId_uid")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_messages_accountId_folder_uid " +
                "ON messages (accountId, folder, uid)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS folders (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "accountId INTEGER NOT NULL, " +
                "fullName TEXT NOT NULL, " +
                "displayName TEXT NOT NULL, " +
                "role TEXT NOT NULL, " +
                "syncEnabled INTEGER NOT NULL, " +
                "lastSync INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_folders_accountId_fullName " +
                "ON folders (accountId, fullName)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS attachments (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "messageId INTEGER NOT NULL, " +
                "partIndex INTEGER NOT NULL, " +
                "name TEXT NOT NULL, " +
                "mimeType TEXT NOT NULL, " +
                "size INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_attachments_messageId ON attachments (messageId)"
        )
    }
}

/**
 * Version 2 -> 3: adds the bookkeeping needed to sync read and deleted status with the server.
 * Inbox messages that are already read here are flagged so the server is told about them,
 * instead of the server's "unread" overwriting them.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE messages ADD COLUMN readPending INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE folders ADD COLUMN uidValidity INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE messages SET readPending = 1 WHERE isRead = 1 AND folder = 'INBOX'")
    }
}

/**
 * Version 3 -> 4: messages can keep their formatted (HTML) version.
 * Older messages get it from the server the first time they are opened.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE messages ADD COLUMN htmlBody TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE messages ADD COLUMN htmlChecked INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(
    entities = [Account::class, MailFolder::class, MailMessage::class, AttachmentInfo::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accounts(): AccountDao
    abstract fun folders(): FolderDao
    abstract fun messages(): MessageDao
    abstract fun attachments(): AttachmentDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mymail.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
            }
    }
}

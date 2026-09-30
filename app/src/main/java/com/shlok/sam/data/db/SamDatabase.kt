package com.shlok.sam.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val displayName: String = "there",
    val preferredLanguage: String = "auto",
    val voicePreference: String = "ANDROID_DEFAULT",
    val responseStyle: String = "BALANCED",
    val notificationsEnabled: Boolean = true,
    val speakNotifications: Boolean = false,
    val wakeWordEnabled: Boolean = true,
    val engineEnabled: Boolean = false,
    val overlayEnabled: Boolean = false,
    val memoryEnabled: Boolean = true,
    val aiProvider: String = "local",
    val defaultModel: String = "",
    val systemMode: String = "NORMAL"
)

@Entity(tableName = "orb_settings")
data class OrbSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val orbType: String = "CLASSIC",
    val sizePreset: String = "MEDIUM",
    val sizeSlider: Float = 0.55f,
    val aura: String = "CYAN",
    val customAuraArgb: Long = 0xFF2EE6FF,
    val auraBorder: Boolean = true,
    val voiceVisualizer: Boolean = true,
    val particles: Boolean = true,
    val glow: Boolean = true,
    val rotatingRings: Boolean = true,
    val haptic: Boolean = true
)

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val approved: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: String,
    val text: String,
    val taskId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val rawCommand: String,
    val state: String,
    val result: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "task_steps")
data class TaskStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val index: Int,
    val label: String,
    val state: String,
    val detail: String? = null
)

@Entity(tableName = "command_patterns")
data class CommandPatternEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val language: String,
    val pattern: String,
    val intent: String,
    val example: String
)

@Entity(tableName = "knowledge_documents")
data class KnowledgeDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val mime: String,
    val uri: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "knowledge_chunks")
data class KnowledgeChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val chunkIndex: Int,
    val content: String
)

@Entity(tableName = "ai_providers")
data class AiProviderEntity(
    @PrimaryKey val id: String,
    val enabled: Boolean = false,
    val model: String = "",
    val baseUrl: String = "",
    val lastStatus: String = "Not connected",
    val lastOk: Boolean = false
)

@Entity(tableName = "app_info")
data class AppInfoEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    val aliases: String
)

@Entity(tableName = "notification_records")
data class NotificationRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postedAt: Long
)

@Entity(tableName = "dev_logs")
data class DevLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val level: String,
    val tag: String,
    val message: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun observe(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun get(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: UserProfileEntity)
}

@Dao
interface OrbSettingsDao {
    @Query("SELECT * FROM orb_settings WHERE id = 1")
    fun observe(): Flow<OrbSettingsEntity?>

    @Query("SELECT * FROM orb_settings WHERE id = 1")
    suspend fun get(): OrbSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OrbSettingsEntity)
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE content LIKE '%' || :q || '%' OR title LIKE '%' || :q || '%' ORDER BY updatedAt DESC")
    suspend fun search(q: String): List<MemoryEntity>

    @Insert
    suspend fun insert(entity: MemoryEntity): Long

    @Update
    suspend fun update(entity: MemoryEntity)

    @Delete
    suspend fun delete(entity: MemoryEntity)

    @Query("DELETE FROM memories")
    suspend fun clear()
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observe(): Flow<List<ConversationEntity>>

    @Insert
    suspend fun insert(entity: ConversationEntity): Long

    @Update
    suspend fun update(entity: ConversationEntity)

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latest(): ConversationEntity?
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :cid ORDER BY createdAt ASC")
    fun observe(cid: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :cid ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recent(cid: Long, limit: Int): List<MessageEntity>

    @Insert
    suspend fun insert(entity: MessageEntity): Long
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY updatedAt DESC LIMIT 50")
    fun observe(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): TaskEntity?

    @Insert
    suspend fun insert(entity: TaskEntity): Long

    @Update
    suspend fun update(entity: TaskEntity)
}

@Dao
interface TaskStepDao {
    @Query("SELECT * FROM task_steps WHERE taskId = :taskId ORDER BY `index` ASC")
    fun observe(taskId: Long): Flow<List<TaskStepEntity>>

    @Query("SELECT * FROM task_steps WHERE taskId = :taskId ORDER BY `index` ASC")
    suspend fun forTask(taskId: Long): List<TaskStepEntity>

    @Insert
    suspend fun insertAll(steps: List<TaskStepEntity>)

    @Update
    suspend fun update(entity: TaskStepEntity)
}

@Dao
interface CommandPatternDao {
    @Query("SELECT * FROM command_patterns")
    suspend fun all(): List<CommandPatternEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CommandPatternEntity>)

    @Query("SELECT COUNT(*) FROM command_patterns")
    suspend fun count(): Int
}

@Dao
interface KnowledgeDao {
    @Query("SELECT * FROM knowledge_documents ORDER BY createdAt DESC")
    fun observeDocs(): Flow<List<KnowledgeDocumentEntity>>

    @Insert
    suspend fun insertDoc(entity: KnowledgeDocumentEntity): Long

    @Insert
    suspend fun insertChunks(chunks: List<KnowledgeChunkEntity>)

    @Query("SELECT * FROM knowledge_chunks WHERE content LIKE '%' || :q || '%' LIMIT 12")
    suspend fun likeChunks(q: String): List<KnowledgeChunkEntity>

    @Query("DELETE FROM knowledge_documents WHERE id = :id")
    suspend fun deleteDoc(id: Long)

    @Query("DELETE FROM knowledge_chunks WHERE documentId = :id")
    suspend fun deleteChunks(id: Long)
}

@Dao
interface AiProviderDao {
    @Query("SELECT * FROM ai_providers")
    fun observe(): Flow<List<AiProviderEntity>>

    @Query("SELECT * FROM ai_providers")
    suspend fun all(): List<AiProviderEntity>

    @Query("SELECT * FROM ai_providers WHERE id = :id")
    suspend fun get(id: String): AiProviderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AiProviderEntity)
}

@Dao
interface AppInfoDao {
    @Query("SELECT * FROM app_info")
    suspend fun all(): List<AppInfoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<AppInfoEntity>)

    @Query("SELECT COUNT(*) FROM app_info")
    suspend fun count(): Int
}

@Dao
interface NotificationRecordDao {
    @Query("SELECT * FROM notification_records ORDER BY postedAt DESC LIMIT 80")
    fun observe(): Flow<List<NotificationRecordEntity>>

    @Query("SELECT * FROM notification_records WHERE packageName LIKE '%' || :pkg || '%' OR appLabel LIKE '%' || :pkg || '%' ORDER BY postedAt DESC LIMIT 40")
    suspend fun forApp(pkg: String): List<NotificationRecordEntity>

    @Query("SELECT * FROM notification_records ORDER BY postedAt DESC LIMIT 40")
    suspend fun recent(): List<NotificationRecordEntity>

    @Insert
    suspend fun insert(entity: NotificationRecordEntity)
}

@Dao
interface DevLogDao {
    @Query("SELECT * FROM dev_logs ORDER BY createdAt DESC LIMIT 200")
    fun observe(): Flow<List<DevLogEntity>>

    @Insert
    suspend fun insert(entity: DevLogEntity)

    @Query("DELETE FROM dev_logs")
    suspend fun clear()
}

@Database(
    entities = [
        UserProfileEntity::class, OrbSettingsEntity::class, MemoryEntity::class,
        ConversationEntity::class, MessageEntity::class, TaskEntity::class, TaskStepEntity::class,
        CommandPatternEntity::class, KnowledgeDocumentEntity::class,         KnowledgeChunkEntity::class, AiProviderEntity::class, AppInfoEntity::class,
        NotificationRecordEntity::class, DevLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SamDatabase : RoomDatabase() {
    abstract fun userProfile(): UserProfileDao
    abstract fun orb(): OrbSettingsDao
    abstract fun memory(): MemoryDao
    abstract fun conversations(): ConversationDao
    abstract fun messages(): MessageDao
    abstract fun tasks(): TaskDao
    abstract fun steps(): TaskStepDao
    abstract fun patterns(): CommandPatternDao
    abstract fun knowledge(): KnowledgeDao
    abstract fun providers(): AiProviderDao
    abstract fun apps(): AppInfoDao
    abstract fun notifications(): NotificationRecordDao
    abstract fun logs(): DevLogDao
}

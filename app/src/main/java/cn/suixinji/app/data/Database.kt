package cn.suixinji.app.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "records", indices = [Index("kind"), Index("deletedAt")])
data class RecordEntity(@PrimaryKey val id: String, val kind: String, val payload: String, val deletedAt: Long? = null) {
    fun stored() = StoredRow(id, kind, payload, deletedAt)
}
@Dao interface RecordDao {
    @Query("SELECT * FROM records") fun observe(): Flow<List<RecordEntity>>
    @Query("SELECT * FROM records") suspend fun all(): List<RecordEntity>
    @Query("SELECT * FROM records WHERE id = :id") suspend fun get(id: String): RecordEntity?
    @Upsert suspend fun put(row: RecordEntity)
    @Upsert suspend fun putAll(rows: List<RecordEntity>)
    @Query("UPDATE records SET deletedAt = :timestamp WHERE id = :id") suspend fun trash(id: String, timestamp: Long?)
    @Query("DELETE FROM records WHERE id = :id") suspend fun remove(id: String)
    @Query("DELETE FROM records") suspend fun clear()
}
@Database(entities = [RecordEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun records(): RecordDao
    companion object {
        fun create(context: Context): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java,
            java.io.File(context.noBackupFilesDir, "suixinji.db").absolutePath).build()
    }
}

class AppData(val rows: List<RecordEntity> = emptyList()) {
    inline fun <reified T> list(kind: String): List<T> = rows.filter { it.kind == kind && it.deletedAt == null }.mapNotNull { runCatching { codec.decodeFromString<T>(it.payload) }.getOrNull() }
    val events by lazy { list<Event>("event") }
    val diaries by lazy { list<Diary>("diary") }
    val habits by lazy { list<Habit>("habit") }
    val logs by lazy { list<HabitLog>("habitlog") }
    val terms by lazy { list<Term>("term") }
    val courses by lazy { list<Course>("course") }
    val occurrences by lazy { list<Occurrence>("occurrence") }
    val sessions by lazy { list<FocusSession>("focus") }
    val timer by lazy { list<ActiveTimer>("timer").firstOrNull() }
    val settings by lazy { list<Settings>("settings").firstOrNull() ?: Settings() }
    val trashed get() = rows.filter { it.deletedAt != null && it.kind in setOf("event", "diary", "habit", "term", "course", "focus") }
}

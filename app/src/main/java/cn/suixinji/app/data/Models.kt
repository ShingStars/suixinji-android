package cn.suixinji.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

val codec = Json { ignoreUnknownKeys = true; encodeDefaults = true }
fun newId(): String = UUID.randomUUID().toString()
fun today(): String = LocalDate.now().toString()

@Serializable enum class Repeat { NONE, DAILY, WEEKLY, MONTHLY, WEEKDAYS }
@Serializable data class Event(
    val id: String = newId(), val title: String = "", val note: String = "", val date: String = today(),
    val start: String = "09:00", val end: String = "10:00", val allDay: Boolean = false,
    val category: String = "生活", val color: Int = 0, val repeat: Repeat = Repeat.NONE,
    val weekdays: List<Int> = emptyList(), val repeatUntil: String? = null, val reminder: Int = 10
)
@Serializable data class Diary(
    val id: String = newId(), val date: String = today(), val title: String = "", val body: String = "",
    val mood: String = "平静", val tags: String = "", val images: List<String> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
)
@Serializable enum class HabitFrequency { DAILY, WEEKDAYS, WEEKLY }
@Serializable data class Habit(
    val id: String = newId(), val title: String = "", val numeric: Boolean = false, val target: Double = 1.0,
    val unit: String = "次", val frequency: HabitFrequency = HabitFrequency.DAILY,
    val weekdays: List<Int> = listOf(1,2,3,4,5), val weeklyTarget: Int = 3,
    val reminderTime: String? = null, val createdDate: String = today(), val color: Int = 0
)
@Serializable data class HabitLog(val id: String = newId(), val habitId: String, val date: String = today(), val value: Double = 1.0, val backfill: Boolean = false, val recordedAt: Long = System.currentTimeMillis())
@Serializable data class LessonTime(val start: String, val end: String)
@Serializable data class Term(
    val id: String = newId(), val title: String = "新学期", val startDate: String = today(), val weeks: Int = 20,
    val slots: List<LessonTime> = listOf(LessonTime("08:00","08:45"), LessonTime("08:55","09:40"), LessonTime("10:00","10:45"), LessonTime("10:55","11:40"), LessonTime("14:00","14:45"), LessonTime("14:55","15:40"), LessonTime("16:00","16:45"), LessonTime("16:55","17:40"))
)
@Serializable data class Course(
    val id: String = newId(), val termId: String, val title: String = "", val teacher: String = "", val room: String = "",
    val weekday: Int = 1, val fromSlot: Int = 1, val toSlot: Int = 2,
    val weeks: List<Int> = (1..20).toList(), val reminder: Int = 10, val color: Int = 1
)
@Serializable data class Occurrence(
    val id: String, val ownerId: String, val date: String, val kind: String = "event",
    val cancelled: Boolean = false, val replacement: String? = null, val completed: Boolean = false,
    val snapshot: String? = null, val movedDate: String? = null
)
@Serializable data class FocusSession(
    val id: String = newId(), val eventId: String? = null, val title: String = "自由专注", val date: String = today(),
    val startedAt: Long = System.currentTimeMillis(), val seconds: Int = 0, val status: String = "完整", val mode: String = "专注"
)
@Serializable data class ActiveTimer(
    val id: String = newId(), val eventId: String? = null, val title: String = "自由专注", val date: String = today(),
    val mode: String = "专注", val duration: Int = 1500, val remaining: Int = 1500, val running: Boolean = false,
    val baseElapsed: Long = 0, val bootCount: Int = 0, val startedAt: Long = System.currentTimeMillis()
)
@Serializable data class Settings(
    val theme: String = "system", val focusMinutes: Int = 25, val shortMinutes: Int = 5, val longMinutes: Int = 15,
    val rounds: Int = 4, val eventNotifications: Boolean = true, val courseNotifications: Boolean = true,
    val habitNotifications: Boolean = true, val focusNotifications: Boolean = true
)
@Serializable data class StoredRow(val id: String, val kind: String, val payload: String, val deletedAt: Long? = null)
@Serializable data class Snapshot(val formatVersion: Int = 1, val createdAt: Long = System.currentTimeMillis(), val rows: List<StoredRow>)

data class AgendaItem(val id: String, val ownerId: String, val date: String, val title: String, val subtitle: String,
    val start: String?, val end: String?, val color: Int, val kind: String, val complete: Boolean = false,
    val reminder: Int = -1, val event: Event? = null, val course: Course? = null)

fun validateClock(value: String): Boolean = runCatching { LocalTime.parse(value) }.isSuccess

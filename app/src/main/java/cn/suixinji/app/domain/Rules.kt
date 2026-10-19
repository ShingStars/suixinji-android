package cn.suixinji.app.domain

import cn.suixinji.app.data.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

object Rules {
    fun eventOccurs(event: Event, date: LocalDate): Boolean {
        val base = LocalDate.parse(event.date)
        if (date < base || event.repeatUntil?.let { date > LocalDate.parse(it) } == true) return false
        return when (event.repeat) {
            Repeat.NONE -> date == base
            Repeat.DAILY -> true
            Repeat.WEEKLY -> date.dayOfWeek == base.dayOfWeek
            Repeat.MONTHLY -> date.dayOfMonth == base.dayOfMonth
            Repeat.WEEKDAYS -> date.dayOfWeek.value in event.weekdays
        }
    }
    fun termWeek(term: Term, date: LocalDate): Int {
        val monday = LocalDate.parse(term.startDate).with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        return (ChronoUnit.DAYS.between(monday, date) / 7).toInt() + 1
    }
    fun courseOccurs(course: Course, term: Term, date: LocalDate): Boolean {
        if (date < LocalDate.parse(term.startDate)) return false
        val week = termWeek(term, date)
        return week in 1..term.weeks && week in course.weeks && date.dayOfWeek.value == course.weekday
    }
    fun agenda(data: AppData, date: LocalDate): List<AgendaItem> {
        val ds = date.toString()
        val overrides = data.occurrences.associateBy { it.id }
        val result = mutableListOf<AgendaItem>()
        data.events.forEach { base ->
            val regularId="event:${base.id}:$ds"
            val candidates=listOf(regularId to overrides[regularId]) + data.occurrences.filter {it.ownerId==base.id && it.movedDate==ds && it.date!=ds}.map {it.id to it}
            candidates.forEach candidate@{ (id,ex)->
                if (ex?.cancelled == true || (ex?.movedDate!=null && ex.movedDate!=ds)) return@candidate
                if (!eventOccurs(base, date) && ex?.replacement == null && ex?.snapshot == null) return@candidate
                val event = (if (ex?.completed == true) ex.snapshot else ex?.replacement)?.let { runCatching { codec.decodeFromString<Event>(it) }.getOrNull() } ?: base
                result += AgendaItem(id, base.id, ds, event.title, event.category, if(event.allDay) null else event.start,
                    if(event.allDay) null else event.end, event.color, "event", ex?.completed == true, event.reminder, event)
            }
        }
        data.courses.forEach { base ->
            val term = data.terms.find { it.id == base.termId } ?: return@forEach
            val regularId="course:${base.id}:$ds"
            val candidates=listOf(regularId to overrides[regularId]) + data.occurrences.filter {it.ownerId==base.id && it.movedDate==ds && it.date!=ds}.map {it.id to it}
            candidates.forEach candidate@{ (id,ex)->
                if(ex?.movedDate!=null && ex.movedDate!=ds) return@candidate
                if (ex?.cancelled == true || (!courseOccurs(base, term, date) && ex?.replacement == null)) return@candidate
                val c = ex?.replacement?.let { runCatching { codec.decodeFromString<Course>(it) }.getOrNull() } ?: base
                val start = term.slots.getOrNull(c.fromSlot-1)?.start ?: return@candidate
                val end = term.slots.getOrNull(c.toSlot-1)?.end ?: return@candidate
                result += AgendaItem(id, base.id, ds, c.title, listOf(c.room,c.teacher).filter { it.isNotBlank() }.joinToString(" · "), start,end,c.color,"course",reminder=c.reminder,course=c)
            }
        }
        return result.sortedWith(compareBy<AgendaItem> { it.start ?: "00:00" }.thenBy { it.title })
    }
    fun habitDue(habit: Habit, date: LocalDate): Boolean = date >= LocalDate.parse(habit.createdDate) && when(habit.frequency) {
        HabitFrequency.WEEKDAYS -> date.dayOfWeek.value in habit.weekdays
        else -> true
    }
    fun value(habit: Habit, date: LocalDate, logs: List<HabitLog>) = logs.filter { it.habitId == habit.id && it.date == date.toString() }.sumOf { it.value }
    fun done(habit: Habit, date: LocalDate, logs: List<HabitLog>) = value(habit,date,logs) + 0.000001 >= if(habit.numeric) habit.target else 1.0
    fun monday(date: LocalDate) = date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    fun weekCount(habit: Habit, date: LocalDate, logs: List<HabitLog>): Int = (0..6).count { done(habit,monday(date).plusDays(it.toLong()),logs) }
    fun periodDone(habit: Habit, date: LocalDate, logs: List<HabitLog>) = if(habit.frequency == HabitFrequency.WEEKLY) weekCount(habit,date,logs) >= habit.weeklyTarget else done(habit,date,logs)
    fun streak(habit: Habit, logs: List<HabitLog>, now: LocalDate = LocalDate.now()): Int {
        val created = LocalDate.parse(habit.createdDate)
        var cursor = if(habit.frequency == HabitFrequency.WEEKLY) monday(now) else now
        var count = 0
        var first = true
        while(cursor >= if(habit.frequency == HabitFrequency.WEEKLY) monday(created) else created) {
            if(habit.frequency == HabitFrequency.WEEKLY) {
                if(weekCount(habit,cursor,logs) >= habit.weeklyTarget) count++ else if(!first) break
                cursor = cursor.minusWeeks(1)
            } else {
                if(habitDue(habit,cursor)) {
                    if(done(habit,cursor,logs)) count++ else if(cursor!=now) break
                    first = false
                }
                cursor = cursor.minusDays(1)
                continue
            }
            first = false
        }
        return count
    }
    fun conflicts(items: List<AgendaItem>, start: String, end: String, excludeOwner: String): Boolean = items.any {
        it.ownerId != excludeOwner && it.start != null && it.end != null && it.start < end && start < it.end
    }
    fun remaining(timer: ActiveTimer, elapsed: Long): Int = if(timer.running) (timer.remaining - ((elapsed - timer.baseElapsed).coerceAtLeast(0)/1000).toInt()).coerceAtLeast(0) else timer.remaining
}

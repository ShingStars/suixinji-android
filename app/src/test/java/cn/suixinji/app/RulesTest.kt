package cn.suixinji.app

import cn.suixinji.app.data.*
import cn.suixinji.app.domain.Rules
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class RulesTest {
    private fun date(s:String)=LocalDate.parse(s)
    private inline fun <reified T> row(id:String,kind:String,value:T)=RecordEntity(id,kind,codec.encodeToString(value))
    @Test fun monthlyMissingDayIsSkippedAndLeapDayWorks() {
        val e=Event(date="2024-01-31",repeat=Repeat.MONTHLY)
        assertFalse(Rules.eventOccurs(e,date("2024-02-29")))
        assertTrue(Rules.eventOccurs(e,date("2024-03-31")))
        assertTrue(Rules.eventOccurs(Event(date="2024-02-29",repeat=Repeat.MONTHLY),date("2024-03-29")))
    }
    @Test fun weekdayAndEndDateAreRespected() {
        val e=Event(date="2026-10-19",repeat=Repeat.WEEKDAYS,weekdays=listOf(1,3),repeatUntil="2026-10-26")
        assertFalse(Rules.eventOccurs(e,date("2026-10-18")));assertTrue(Rules.eventOccurs(e,date("2026-10-21")));assertFalse(Rules.eventOccurs(e,date("2026-10-28")))
    }
    @Test fun movedOccurrenceDoesNotHideNormalOccurrenceOnTargetDay() {
        val e=Event(title="每天读书",date="2026-10-19",repeat=Repeat.DAILY)
        val ex=Occurrence("event:${e.id}:2026-10-19",e.id,"2026-10-19",replacement=codec.encodeToString(e.copy(date="2026-10-20",title="补上的读书")),movedDate="2026-10-20")
        val d=AppData(listOf(row(e.id,"event",e),row(ex.id,"occurrence",ex)))
        assertEquals(0,Rules.agenda(d,date("2026-10-19")).size);assertEquals(2,Rules.agenda(d,date("2026-10-20")).size)
    }
    @Test fun completedHistorySurvivesSeriesEdits() {
        val old=Event(title="旧安排",date="2026-10-19",repeat=Repeat.WEEKLY)
        val ex=Occurrence("event:${old.id}:2026-10-19",old.id,"2026-10-19",completed=true,snapshot=codec.encodeToString(old))
        val current=old.copy(title="新安排",date="2026-10-20")
        val d=AppData(listOf(row(current.id,"event",current),row(ex.id,"occurrence",ex)))
        assertEquals("旧安排",Rules.agenda(d,date("2026-10-19")).single().title)
        assertTrue(Rules.agenda(d,date("2026-10-19")).single().complete)
    }
    @Test fun teachingWeekUsesMondayAndExplicitWeeks() {
        val t=Term(startDate="2026-09-02",weeks=20)
        val c=Course(termId=t.id,weekday=1,weeks=listOf(1,3))
        assertEquals(1,Rules.termWeek(t,date("2026-09-06")));assertEquals(2,Rules.termWeek(t,date("2026-09-07")))
        assertFalse(Rules.courseOccurs(c,t,date("2026-08-31")));assertFalse(Rules.courseOccurs(c,t,date("2026-09-07")));assertTrue(Rules.courseOccurs(c,t,date("2026-09-14")))
    }
    @Test fun weeklyHabitCountsDistinctCompletedDates() {
        val h=Habit(numeric=true,target=10.0,frequency=HabitFrequency.WEEKLY,weeklyTarget=2,createdDate="2026-10-01")
        val logs=listOf(HabitLog(habitId=h.id,date="2026-10-19",value=6.0),HabitLog(habitId=h.id,date="2026-10-19",value=5.0),HabitLog(habitId=h.id,date="2026-10-20",value=9.0))
        assertEquals(1,Rules.weekCount(h,date("2026-10-20"),logs));assertFalse(Rules.periodDone(h,date("2026-10-20"),logs))
    }
    @Test fun restDayDoesNotSkipAMissedScheduledDay() {
        val h=Habit(frequency=HabitFrequency.WEEKDAYS,weekdays=listOf(1,3,5),createdDate="2026-10-12")
        val logs=listOf(HabitLog(habitId=h.id,date="2026-10-14"),HabitLog(habitId=h.id,date="2026-10-12"))
        assertEquals(0,Rules.streak(h,logs,date("2026-10-18")))
    }
    @Test fun backfillRestoresAStreak() {
        val h=Habit(createdDate="2026-10-17")
        val logs=listOf(HabitLog(habitId=h.id,date="2026-10-17"),HabitLog(habitId=h.id,date="2026-10-19"))
        assertEquals(1,Rules.streak(h,logs,date("2026-10-19")))
        assertEquals(3,Rules.streak(h,logs+HabitLog(habitId=h.id,date="2026-10-18",backfill=true),date("2026-10-19")))
    }
    @Test fun countdownUsesElapsedTimeAndPauseFreezesIt() {
        val t=ActiveTimer(duration=1500,remaining=100,running=true,baseElapsed=10000)
        assertEquals(70,Rules.remaining(t,40000));assertEquals(0,Rules.remaining(t,200000));assertEquals(100,Rules.remaining(t.copy(running=false),40000))
    }
}

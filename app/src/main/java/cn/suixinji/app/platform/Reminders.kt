package cn.suixinji.app.platform

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cn.suixinji.app.MainActivity
import cn.suixinji.app.R
import cn.suixinji.app.SuixinjiApplication
import cn.suixinji.app.data.*
import cn.suixinji.app.domain.Rules
import kotlinx.coroutines.launch
import java.time.*

class Reminders(private val context: Context, private val repo: Repository) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val nm = context.getSystemService(NotificationManager::class.java)
    init {
        nm.createNotificationChannel(NotificationChannel("arrangements","日程与课程",NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel("habits","习惯打卡",NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel("focus","番茄钟结束",NotificationManager.IMPORTANCE_HIGH).apply { enableVibration(true) })
        nm.createNotificationChannel(NotificationChannel("timer","正在专注",NotificationManager.IMPORTANCE_LOW).apply { setSound(null,null) })
    }
    fun notificationsAllowed() = (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) && NotificationManagerCompat.from(context).areNotificationsEnabled()
    fun exactAllowed() = Build.VERSION.SDK_INT<31 || alarms.canScheduleExactAlarms()
    private fun pending(key: String, title: String = "", body: String = "", kind: String = "event", owner: String = "", date: String = "") = PendingIntent.getBroadcast(context,key.hashCode(),Intent(context,ReminderReceiver::class.java).apply {
        action="cn.suixinji.REMINDER.$key"; putExtra("title",title); putExtra("body",body); putExtra("kind",kind); putExtra("owner",owner); putExtra("date",date)
    },PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun arrange(key: String, at: Long, title: String, body: String, kind: String, owner: String, date: String) {
        val pi = pending(key,title,body,kind,owner,date)
        try {
            if(exactAllowed()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi)
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi)
        } catch(_: SecurityException) { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi) }
    }
    @Synchronized fun reschedule(data: AppData) {
        val old = repo.device.getStringSet("alarm_keys",emptySet()).orEmpty().toSet()
        old.forEach { alarms.cancel(pending(it)) }
        val keys = mutableSetOf<String>()
        val now = System.currentTimeMillis()
        val owners = mutableSetOf<String>()
        val today=LocalDate.now()
        val dates=(0..370).map {today.plusDays(it.toLong())}.toMutableSet()
        data.events.map{LocalDate.parse(it.date)}.filter{it>today.plusDays(370)}.forEach{base->(0..7).forEach{dates+=base.plusDays(it.toLong())}}
        data.terms.forEach{term->val base=LocalDate.parse(term.startDate);(0 until term.weeks*7).forEach{val d=base.plusDays(it.toLong());if(d>today.plusDays(370))dates+=d}}
        data.occurrences.mapNotNull{it.movedDate}.map{LocalDate.parse(it)}.filter{it>today.plusDays(370)}.forEach{dates+=it}
        for(date in dates.sorted()) {
            Rules.agenda(data,date).forEach { a ->
                val enabled = if(a.kind=="course") data.settings.courseNotifications else data.settings.eventNotifications
                if(!enabled || a.complete || a.reminder<0 || a.ownerId in owners) return@forEach
                val time = if(a.start==null) LocalTime.of(9,0) else LocalTime.parse(a.start)
                val at = date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - if(a.start==null) 0L else a.reminder*60000L
                if(at>now) {
                    val key = "${a.kind}:${a.ownerId}"
                    arrange(key,at,a.title,if(a.start==null) "今天 · 全天" else "${a.start} ${a.subtitle}",a.kind,a.ownerId,a.date)
                    keys += key; owners += a.ownerId
                }
            }
        }
        data.habits.filter { data.settings.habitNotifications && it.reminderTime != null }.forEach { h ->
            for(day in 0..8) {
                val date = LocalDate.now().plusDays(day.toLong())
                val at = date.atTime(LocalTime.parse(h.reminderTime)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                if(at>now && Rules.habitDue(h,date) && !Rules.periodDone(h,date,data.logs)) {
                    val key="habit:${h.id}"
                    arrange(key,at,h.title,"给今天留下一点进步","habit",h.id,date.toString()); keys+=key; break
                }
            }
        }
        repo.device.edit().putStringSet("alarm_keys",keys).apply()
        val t=data.timer
        if(t!=null && t.running && t.bootCount==repo.bootCount()) {
            val at = t.baseElapsed + t.remaining*1000L
            val pi=pending("timer",kind="timer")
            try { if(exactAllowed()) alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,at,pi) else alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,at,pi) }
            catch(_:SecurityException) { alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,at,pi) }
            if(data.settings.focusNotifications) showTimer(t) else nm.cancel(1001)
        } else cancelTimer()
    }
    private fun contentIntent(kind:String, owner:String="", date:String="") = PendingIntent.getActivity(context,(kind+owner).hashCode(),Intent(context,MainActivity::class.java).apply {
        flags=Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra("destination",kind); putExtra("owner",owner); putExtra("date",date)
    },PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun show(title:String, body:String, kind:String, owner:String="", date:String="") {
        if(!notificationsAllowed()) return
        val locked=repo.device.getBoolean("app_lock",false)
        val notification=NotificationCompat.Builder(context,if(kind=="habit") "habits" else if(kind=="timer") "focus" else "arrangements")
            .setSmallIcon(R.drawable.ic_launcher).setContentTitle(if(locked) "随心记提醒" else title)
            .setContentText(if(locked) "打开应用查看" else body).setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setContentIntent(contentIntent(kind,owner,date)).build()
        try { nm.notify((kind+owner+date).hashCode(),notification) } catch(_:SecurityException) { }
    }
    private fun showTimer(t: ActiveTimer) {
        if(!notificationsAllowed()) return
        val ms = Rules.remaining(t,SystemClock.elapsedRealtime())*1000L
        val locked=repo.device.getBoolean("app_lock",false)
        val n=NotificationCompat.Builder(context,"timer").setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(if(locked) "随心记 · 计时中" else "${t.mode} · ${t.title}")
            .setContentText("打开查看计时").setOngoing(true).setSilent(true).setUsesChronometer(true)
            .setChronometerCountDown(true).setWhen(System.currentTimeMillis()+ms)
            .setContentIntent(contentIntent("timer")).build()
        try { nm.notify(1001,n) } catch(_:SecurityException) { }
    }
    fun cancelTimer() { alarms.cancel(pending("timer",kind="timer")); nm.cancel(1001) }
    fun clearDelivered() {nm.cancelAll()}
    fun timerFinished(mode: String,enabled:Boolean) { if(enabled) show("$mode 完成",if(mode=="专注") "辛苦了，休息一下吧" else "准备好再开始下一轮","timer") }
}

class ReminderReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        val pendingResult=goAsync()
        val app=context.applicationContext as SuixinjiApplication
        app.appScope.launch {
            try {
                val repo=app.repository
                val kind=intent.getStringExtra("kind") ?: "event"
                if(kind=="timer") {
                    val data=repo.snapshotData(); val t=data.timer
                    if(t!=null && t.bootCount==repo.bootCount() && repo.finishTimer()) repo.reminders.timerFinished(t.mode,data.settings.focusNotifications)
                } else {
                    val data=repo.snapshotData(); val owner=intent.getStringExtra("owner").orEmpty(); val ds=intent.getStringExtra("date").orEmpty()
                    val valid=if(kind=="habit") data.habits.find { it.id==owner }?.let { !Rules.periodDone(it,LocalDate.parse(ds),data.logs) && data.settings.habitNotifications } == true
                    else Rules.agenda(data,LocalDate.parse(ds)).any { it.ownerId==owner && !it.complete } && if(kind=="course") data.settings.courseNotifications else data.settings.eventNotifications
                    if(valid) repo.reminders.show(intent.getStringExtra("title").orEmpty(),intent.getStringExtra("body").orEmpty(),kind,owner,ds)
                }
                repo.reminders.reschedule(repo.snapshotData())
            } finally { pendingResult.finish() }
        }
    }
}
class RescheduleReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        if(intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_TIME_CHANGED,Intent.ACTION_TIMEZONE_CHANGED,Intent.ACTION_MY_PACKAGE_REPLACED,AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) return
        val result=goAsync(); val app=context.applicationContext as SuixinjiApplication
        app.appScope.launch { try { app.repository.reconcileTimer(); app.repository.reminders.reschedule(app.repository.snapshotData()) } finally { result.finish() } }
    }
}

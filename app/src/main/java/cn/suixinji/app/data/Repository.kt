package cn.suixinji.app.data

import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.provider.Settings as AndroidSettings
import androidx.room.withTransaction
import cn.suixinji.app.domain.Rules
import cn.suixinji.app.platform.Reminders
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import java.io.File
import java.time.LocalDate

class Repository(val context: Context, val scope: CoroutineScope) {
    val db = AppDatabase.create(context)
    val dao = db.records()
    val state = dao.observe().map(::AppData).stateIn(scope, SharingStarted.Eagerly, AppData())
    val mutation = Mutex()
    val attachments = File(context.noBackupFilesDir, "images").apply { mkdirs() }
    val device = context.getSharedPreferences("device", Context.MODE_PRIVATE)
    val reminders = Reminders(context, this)
    val ready = MutableStateFlow(false)
    init {
        scope.launch { cn.suixinji.app.platform.Backup(context,this@Repository).recoverJournal(); purgeExpired(); reconcileTimer(); ready.value=true }
        scope.launch { ready.first { it }; dao.observe().debounce(300).collect { reminders.reschedule(AppData(it)) } }
    }
    suspend inline fun <reified T> put(id: String, kind: String, value: T) {
        dao.put(RecordEntity(id,kind,codec.encodeToString(value)))
    }
    suspend fun snapshotData() = AppData(dao.all())
    suspend fun saveEvent(e: Event) { put(e.id,"event",e) }
    suspend fun saveDiary(d: Diary) = mutation.withLock { put(d.id,"diary",d.copy(updatedAt=System.currentTimeMillis())) }
    suspend fun saveHabit(h: Habit) { put(h.id,"habit",h) }
    suspend fun saveTerm(t: Term) { put(t.id,"term",t) }
    suspend fun saveCourse(c: Course) { put(c.id,"course",c) }
    suspend fun saveSettings(s: Settings) { put("settings","settings",s) }
    suspend fun saveProfile(nickname:String,signature:String,avatar:String?) = mutation.withLock {
        require(nickname.codePointCount(0,nickname.length)<=10 && signature.codePointCount(0,signature.length)<=30)
        saveSettings(snapshotData().settings.copy(nickname=nickname.trim(),signature=signature.trim(),avatar=avatar))
    }
    suspend fun importCourses(termId:String,courses:List<Course>,replace:Boolean):Int = mutation.withLock {
        val data=snapshotData(); val term=data.terms.find {it.id==termId} ?: error("学期已删除，请重新选择")
        require(courses.isNotEmpty() && courses.size<=2000)
        require(courses.all {it.termId==termId && it.title.isNotBlank() && it.weekday in 1..7 && it.fromSlot>=1 && it.toSlot in it.fromSlot..term.slots.size && it.weeks.isNotEmpty() && it.weeks.all {w->w in 1..term.weeks}}) {"导入课程超出学期节次或周数，请先调整学期设置"}
        val existing=data.courses.filter {it.termId==termId}; val keys=(if(replace) emptyList() else existing).map {cn.suixinji.app.domain.CourseImport.key(it)}.toMutableSet()
        val additions=courses.filter { keys.add(cn.suixinji.app.domain.CourseImport.key(it)) }
        db.withTransaction {
            if(replace) {val timestamp=System.currentTimeMillis();val rows=dao.all();existing.forEach { c-> children(c.id,rows).forEach {id->dao.trash(id,timestamp)} }}
            additions.forEach { saveCourse(it.copy(id=newId())) }
        }
        additions.size
    }
    suspend fun toggleComplete(item: AgendaItem) {
        if(item.kind != "event") return
        val current = snapshotData().occurrences.find { it.id == item.id }
        val event = item.event ?: return
        val complete = !(current?.completed ?: false)
        put(item.id,"occurrence",(current ?: Occurrence(item.id,item.ownerId,item.date)).copy(completed=complete,snapshot=if(complete) codec.encodeToString(event) else null))
    }
    suspend fun eventException(item: AgendaItem, event: Event? = null, cancel: Boolean = false) {
        val current = snapshotData().occurrences.find { it.id == item.id } ?: Occurrence(item.id,item.ownerId,item.date,item.kind)
        put(item.id,"occurrence",current.copy(cancelled=cancel,replacement=event?.let { codec.encodeToString(it) } ?: current.replacement,
            movedDate=event?.date?.takeIf { it!=current.date }, snapshot=if(current.completed && event!=null) codec.encodeToString(event) else current.snapshot))
    }
    suspend fun courseException(item: AgendaItem, course: Course? = null, cancel: Boolean = false, movedDate: String? = null) {
        val current = snapshotData().occurrences.find { it.id == item.id } ?: Occurrence(item.id,item.ownerId,item.date,"course")
        put(item.id,"occurrence",current.copy(cancelled=cancel,replacement=course?.let { codec.encodeToString(it) } ?: current.replacement,movedDate=movedDate?.takeIf { it!=current.date }))
    }
    suspend fun resetException(id:String) {
        val old=snapshotData().occurrences.find {it.id==id} ?: return
        if(old.completed) put(id,"occurrence",old.copy(cancelled=false,replacement=null,movedDate=null)) else dao.remove(id)
    }
    suspend fun logHabit(habit: Habit, date: LocalDate, value: Double) {
        require(value > 0 && value.isFinite()) { "请输入大于零的数值" }
        require(date <= LocalDate.now()) { "不能提前打卡" }
        require(date >= LocalDate.parse(habit.createdDate)) { "不能早于习惯创建日期" }
        val log = HabitLog(habitId=habit.id,date=date.toString(),value=value,backfill=date != LocalDate.now())
        put(log.id,"habitlog",log)
    }
    suspend fun undoHabit(habit: Habit, date: LocalDate) { snapshotData().logs.filter { it.habitId == habit.id && it.date == date.toString() }.forEach { dao.remove(it.id) } }
    suspend fun removeLog(id: String) { dao.remove(id) }

    private fun children(id: String, rows: List<RecordEntity>): Set<String> {
        val data = AppData(rows.map { it.copy(deletedAt=null) })
        val ids = mutableSetOf(id)
        data.courses.filter { it.termId == id }.forEach { ids += it.id }
        data.logs.filter { it.habitId in ids }.forEach { ids += it.id }
        data.occurrences.filter { it.ownerId in ids }.forEach { ids += it.id }
        return ids
    }
    suspend fun trash(id: String) = mutation.withLock {
        val rows = dao.all(); val timestamp = System.currentTimeMillis()
        db.withTransaction { children(id,rows).forEach { child -> if(rows.find { it.id == child }?.deletedAt == null) dao.trash(child,timestamp) } }
    }
    suspend fun restore(id: String) = mutation.withLock {
        val rows = dao.all()
        val record=rows.find {it.id==id} ?: return@withLock
        val parent=if(record.kind=="course") runCatching {codec.decodeFromString<Course>(record.payload).termId}.getOrNull() else null
        val root=rows.find {it.id==parent && it.deletedAt!=null} ?: record
        val timestamp=root.deletedAt ?: return@withLock
        db.withTransaction { children(root.id,rows).forEach { child -> if(rows.find { it.id == child }?.deletedAt == timestamp) dao.trash(child,null) };if(record.deletedAt!=null)dao.trash(record.id,null) }
    }
    suspend fun permanentlyDelete(id: String) = mutation.withLock {
        val rows = dao.all()
        db.withTransaction { children(id,rows).forEach { dao.remove(it) } }
        cleanupImages(graceMillis=0)
    }
    suspend fun purgeExpired() {
        val cutoff = System.currentTimeMillis()-30L*86400000
        dao.all().filter { it.deletedAt != null && it.deletedAt < cutoff }.forEach { dao.remove(it.id) }
        cleanupImages()
    }
    suspend fun cleanupImages(graceMillis:Long=86400000) {
        val data=AppData(dao.all().map { it.copy(deletedAt=null) })
        val used = (data.diaries.flatMap { it.images }+listOfNotNull(data.settings.avatar)).toSet()
        // Keep newly imported autosave attachments until they are at least a day old.
        attachments.listFiles()?.filter { it.name !in used && System.currentTimeMillis()-it.lastModified()>=graceMillis }?.forEach { it.delete() }
    }
    suspend fun importImage(uri: Uri): String = withContext(Dispatchers.IO) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds=true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it,null,options) }
        require(options.outWidth>0 && options.outHeight>0) { "无法读取这张图片" }
        var sample = 1
        while(options.outWidth/sample>2400 || options.outHeight/sample>2400) sample *= 2
        val bitmap = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply { inSampleSize=sample }) } ?: error("图片格式不支持")
        val name = "${newId()}.jpg"
        File(attachments,name).outputStream().use { require(bitmap.compress(Bitmap.CompressFormat.JPEG,90,it)) }
        bitmap.recycle()
        name
    }
    fun bootCount() = AndroidSettings.Global.getInt(context.contentResolver,AndroidSettings.Global.BOOT_COUNT,0)
    suspend fun startTimer(mode: String, minutes: Int, event: Event?) = mutation.withLock {
        require(minutes in 1..240)
        if(snapshotData().timer != null) return@withLock
        val t = ActiveTimer(eventId=event?.id,title=event?.title ?: if(mode=="专注") "自由专注" else mode,
            mode=mode,duration=minutes*60,remaining=minutes*60,running=true,baseElapsed=SystemClock.elapsedRealtime(),bootCount=bootCount())
        put("active-timer","timer",t)
    }
    suspend fun pauseTimer() = mutation.withLock {
        val t = snapshotData().timer ?: return@withLock
        if(t.running) put("active-timer","timer",t.copy(remaining=Rules.remaining(t,SystemClock.elapsedRealtime()),running=false))
        else put("active-timer","timer",t.copy(running=true,baseElapsed=SystemClock.elapsedRealtime(),bootCount=bootCount()))
    }
    suspend fun finishTimer(early: Boolean = false, interrupted: Boolean = false): Boolean = mutation.withLock {
        val t = snapshotData().timer ?: return@withLock false
        val remaining = if(interrupted) t.remaining else Rules.remaining(t,SystemClock.elapsedRealtime())
        if(!early && !interrupted && remaining>0) return@withLock false
        val seconds = (t.duration-remaining).coerceIn(0,t.duration)
        val session = FocusSession(id=t.id,eventId=t.eventId,title=t.title,date=t.date,startedAt=t.startedAt,seconds=seconds,
            status=if(interrupted) "中断" else if(early) "提前结束" else "完整",mode=t.mode)
        db.withTransaction { put(session.id,"focus",session); dao.remove("active-timer") }
        reminders.cancelTimer()
        true
    }
    suspend fun reconcileTimer() {
        val data=snapshotData()
        val t = data.timer ?: return
        if(t.bootCount != bootCount()) finishTimer(interrupted=true)
        else if(t.running && Rules.remaining(t,SystemClock.elapsedRealtime())==0) {
            if(finishTimer()) reminders.timerFinished(t.mode,data.settings.focusNotifications)
        }
    }
}

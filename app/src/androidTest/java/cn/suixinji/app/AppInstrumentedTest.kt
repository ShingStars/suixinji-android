package cn.suixinji.app

import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.suixinji.app.data.*
import cn.suixinji.app.domain.Rules
import cn.suixinji.app.platform.Backup
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.serialization.encodeToString

@RunWith(AndroidJUnit4::class)
class AppInstrumentedTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val app get()=compose.activity.application as SuixinjiApplication
    private val repo get()=app.repository
    @Before fun prepare()=runBlocking {withTimeout(20000){repo.ready.first{it}};repo.device.edit().putBoolean("asked_notifications",true).putBoolean("app_lock",false).commit();repo.dao.clear()}
    @After fun clean()=runBlocking {repo.dao.clear()}
    private fun waitData(predicate:(AppData)->Boolean){compose.waitUntil(15000){predicate(repo.state.value)}}
    @Test fun navigationAndRealRecordCreation() {
        compose.onNodeWithText("添日程").performClick()
        compose.onNodeWithTag("这件事的名字").performTextInput("验收安排")
        compose.onNodeWithText("保存").performClick()
        waitData{it.events.any{e->e.title=="验收安排"}}
        compose.onNodeWithText("验收安排").assertExists()
        compose.onNodeWithText("写日记").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.waitUntil(10000){compose.onAllNodesWithTag("标题（可留空）").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("标题（可留空）").performTextInput("验收日记")
        compose.onNodeWithTag("diary-body").performTextInput("离线记录也会自动保存。")
        compose.onNodeWithText("完成").performClick()
        waitData{it.diaries.any{d->d.body.contains("离线记录")}}
        compose.onNodeWithTag("nav-2").performClick()
        compose.onNodeWithText("验收日记").assertExists()
        compose.onNodeWithText("习惯打卡").performClick()
        compose.onNodeWithContentDescription("新建习惯").performClick()
        compose.onNodeWithTag("习惯名称").performTextInput("每日阅读")
        compose.onNodeWithText("保存").performClick()
        waitData{it.habits.isNotEmpty()}
        compose.onNodeWithText("每日阅读").performClick()
        compose.onNodeWithText("记录打卡").performScrollTo().performClick()
        waitData{it.logs.isNotEmpty()}
        compose.onAllNodesWithContentDescription("关闭").onLast().performClick()
        compose.onNodeWithTag("nav-3").performClick()
        compose.onNodeWithText("开始专注").performScrollTo().performSemanticsAction(SemanticsActions.OnClick){it()}
        waitData{it.timer?.running==true}
        compose.onNodeWithText("暂停",substring=false).performScrollTo().performSemanticsAction(SemanticsActions.OnClick){it()}
        waitData{it.timer?.running==false}
        compose.onNodeWithText("继续").assertExists()
    }
    @Test fun localDatabaseRecycleAndBackupRoundTrip()=runBlocking {
        val e=Event(title="数据验收",date="2026-10-19",repeat=Repeat.DAILY)
        repo.saveEvent(e)
        val h=Habit(title="喝水",numeric=true,target=1500.0,createdDate=LocalDate.now().minusDays(1).toString())
        repo.saveHabit(h);repo.logHabit(h,LocalDate.now(),1500.0)
        repo.trash(h.id)
        assertTrue(repo.snapshotData().habits.isEmpty());assertTrue(repo.snapshotData().logs.isEmpty())
        repo.restore(h.id);assertEquals(1,repo.snapshotData().logs.size)
        val backup=Backup(app,repo);val file=File(app.cacheDir,"instrumented.sxj")
        backup.export(Uri.fromFile(file),"测试密码1234".toCharArray())
        val before=repo.dao.all()
        var failed=false
        try{backup.restore(Uri.fromFile(file),"错误密码1234".toCharArray())}catch(_:Exception){failed=true}
        assertTrue(failed);assertEquals(before,repo.dao.all())
        repo.saveEvent(e.copy(title="修改后的标题"))
        backup.restore(Uri.fromFile(file),"测试密码1234".toCharArray())
        assertEquals("数据验收",repo.snapshotData().events.single().title)
        assertTrue(backup.recovery.exists())
        assertEquals(1,repo.snapshotData().logs.size)
    }
    @Test fun teachingScheduleAndSingleException()=runBlocking {
        val term=Term(title="验收学期",startDate="2026-09-01")
        val course=Course(termId=term.id,title="数学",weekday=1,weeks=listOf(8))
        repo.saveTerm(term);repo.saveCourse(course)
        val monday=LocalDate.of(2026,10,19)
        val item=Rules.agenda(repo.snapshotData(),monday).single()
        repo.courseException(item,cancel=true)
        assertTrue(Rules.agenda(repo.snapshotData(),monday).isEmpty())
        repo.resetException(item.id)
        assertEquals("数学",Rules.agenda(repo.snapshotData(),monday).single().title)
    }
    @Test fun createTermAndCourseThroughForms() {
        compose.onNodeWithTag("nav-4").performClick()
        compose.onNodeWithText("学期管理").performScrollTo().performClick()
        compose.onNodeWithText("新建学期").performClick()
        compose.onNodeWithTag("学期名称").performTextClearance()
        compose.onNodeWithTag("学期名称").performTextInput("界面验收学期")
        compose.onNodeWithText("保存").performClick()
        waitData{it.terms.isNotEmpty()}
        compose.onAllNodesWithContentDescription("关闭").onLast().performClick()
        compose.onNodeWithTag("nav-1").performClick()
        compose.onNodeWithContentDescription("课程表").performClick()
        compose.onNodeWithContentDescription("添加课程").performClick()
        compose.waitUntil(10000){compose.onAllNodesWithTag("课程名称").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("课程名称").performTextInput("界面数学课")
        compose.onNodeWithText("保存").performClick()
        waitData{it.courses.any{c->c.title=="界面数学课"}}
        compose.onAllNodesWithText("界面数学课").onLast().assertIsDisplayed()
    }
    @Test fun picturesSurviveSourceDeletionAndEncryptedRestore()=runBlocking {
        val source=File(app.cacheDir,"source.png")
        val bitmap=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.GREEN)}
        source.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        val name=repo.importImage(Uri.fromFile(source));source.delete()
        assertTrue(File(repo.attachments,name).isFile)
        val diary=Diary(title="图片验收",body="原图删除后附件仍在",images=listOf(name))
        repo.saveDiary(diary)
        val backup=Backup(app,repo);val file=File(app.cacheDir,"pictures.sxj")
        backup.export(Uri.fromFile(file),"图片备份密码123".toCharArray())
        repo.trash(diary.id)
        backup.restore(Uri.fromFile(file),"图片备份密码123".toCharArray())
        assertEquals(name,repo.snapshotData().diaries.single().images.single())
        assertNotNull(android.graphics.BitmapFactory.decodeFile(File(repo.attachments,name).path))
        repo.trash(diary.id);repo.permanentlyDelete(diary.id)
        assertFalse(File(repo.attachments,name).exists())
    }
    @Test fun timerCompletionPauseAndRebootInterruptionAreDistinct()=runBlocking {
        repo.startTimer("专注",1,null)
        repo.pauseTimer()
        assertFalse(repo.snapshotData().timer!!.running)
        repo.pauseTimer()
        val timer=repo.snapshotData().timer!!
        repo.put("active-timer","timer",timer.copy(remaining=1))
        withTimeout(15000){while(repo.snapshotData().timer!=null){delay(200)}}
        assertEquals(1,repo.snapshotData().sessions.count{it.status=="完整"})
        repo.startTimer("专注",1,null)
        val next=repo.snapshotData().timer!!
        repo.put("active-timer","timer",next.copy(bootCount=repo.bootCount()-1,remaining=45))
        repo.reconcileTimer()
        val interrupted=repo.snapshotData().sessions.single{it.status=="中断"}
        assertEquals(15,interrupted.seconds)
        assertNull(repo.snapshotData().timer)
    }
    @Test fun restoreJournalRollsBackAnInterruptedReplacement()=runBlocking {
        val before=Event(title="恢复前的原始记录")
        repo.saveEvent(before)
        val original=Snapshot(rows=repo.dao.all().map{it.stored()})
        val marker=File(app.noBackupFilesDir,"restore-journal.json")
        marker.writeText(codec.encodeToString(original))
        repo.dao.clear();repo.saveEvent(Event(title="不完整的替换"))
        Backup(app,repo).recoverJournal()
        assertEquals(before.id,repo.snapshotData().events.single().id)
        assertFalse(marker.exists())
    }
    @Test fun clearingAnExistingDiaryDoesNotRestoreOldText() {
        val diary=Diary(title="清空验收",body="这段内容将被清空")
        runBlocking{repo.saveDiary(diary)}
        waitData{it.diaries.any{d->d.id==diary.id}}
        compose.onNodeWithTag("nav-2").performClick()
        compose.onNodeWithText("清空验收").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.onNodeWithTag("标题（可留空）").performTextClearance()
        compose.onNodeWithTag("diary-body").performTextClearance()
        compose.onNodeWithText("完成").performSemanticsAction(SemanticsActions.OnClick){it()}
        waitData{it.diaries.any{d->d.id==diary.id && d.title.isEmpty() && d.body.isEmpty()}}
    }
    @Test fun lockedNotificationsDoNotExposePersonalContent() {
        if(android.os.Build.VERSION.SDK_INT>=33) InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(app.packageName,android.Manifest.permission.POST_NOTIFICATIONS)
        repo.device.edit().putBoolean("app_lock",true).commit()
        try {
            repo.reminders.show("私人日程标题","私人日程备注","event","privacy-test",today())
            val manager=app.getSystemService(android.app.NotificationManager::class.java)
            compose.waitUntil(10000){manager.activeNotifications.any{it.id==("event"+"privacy-test"+today()).hashCode()}}
            val notifications=manager.activeNotifications
            val n=notifications.first{it.id==("event"+"privacy-test"+today()).hashCode()}.notification
            assertEquals("随心记提醒",n.extras.getString(android.app.Notification.EXTRA_TITLE))
            assertEquals("打开应用查看",n.extras.getString(android.app.Notification.EXTRA_TEXT))
        } finally {repo.device.edit().putBoolean("app_lock",false).commit();repo.reminders.clearDelivered()}
    }
}

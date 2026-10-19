package cn.suixinji.app

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.suixinji.app.data.*
import cn.suixinji.app.platform.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class UpdateInstrumentedTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val app get()=compose.activity.application as SuixinjiApplication
    private val repo get()=app.repository
    @Before fun prepare()=runBlocking {withTimeout(20000){repo.ready.first{it}};repo.device.edit().putBoolean("asked_notifications",true).putBoolean("app_lock",false).commit();repo.dao.clear()}
    @After fun clean()=runBlocking {repo.dao.clear()}
    @Test fun swipeOrderAndBoundaries(){
        compose.onNodeWithTag("main-pages").performTouchInput {swipeRight()}
        compose.onNodeWithText("添日程").assertExists()
        compose.onNodeWithTag("main-pages").performTouchInput {swipeLeft()}
        compose.onNodeWithContentDescription("课程表").assertExists()
        compose.onNodeWithTag("main-pages").performTouchInput {swipeLeft()}
        compose.onNodeWithText("习惯打卡").assertExists()
        compose.onNodeWithTag("main-pages").performTouchInput {swipeLeft()}
        compose.onNodeWithText("开始专注").assertExists()
        compose.onNodeWithTag("main-pages").performTouchInput {swipeLeft()}
        compose.onNodeWithText("我的随心记").assertExists()
        compose.onNodeWithTag("main-pages").performTouchInput {swipeLeft()}
        compose.onNodeWithText("我的随心记").assertExists()
        compose.onNodeWithTag("main-pages").performTouchInput {swipeRight()}
        compose.onNodeWithText("开始专注").assertExists()
    }
    @Test fun editProfileAndRestoreAvatar()=runBlocking {
        val bitmap=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);val name="${newId()}.jpg"
        File(repo.attachments,name).outputStream().use {bitmap.compress(Bitmap.CompressFormat.JPEG,90,it)};bitmap.recycle()
        repo.saveProfile("新昵称","认真生活",name)
        compose.waitUntil(10000){repo.state.value.settings.nickname=="新昵称"}
        compose.onNodeWithTag("nav-4").performClick()
        compose.onNodeWithContentDescription("编辑个人资料").performClick()
        compose.onNodeWithTag("昵称（最多10字）").performTextReplacement("测试者")
        compose.onNodeWithTag("个性签名（最多30字）").performTextReplacement("保留每一天")
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(10000){repo.state.value.settings.nickname=="测试者"}
        val backup=Backup(app,repo);val file=File(app.cacheDir,"profile.sxj")
        backup.export(Uri.fromFile(file),"profile-test123".toCharArray())
        repo.saveProfile("已改","",null);repo.cleanupImages(graceMillis=0)
        assertFalse(File(repo.attachments,name).exists())
        backup.restore(Uri.fromFile(file),"profile-test123".toCharArray())
        assertEquals(name,repo.snapshotData().settings.avatar);assertEquals("测试者",repo.snapshotData().settings.nickname);assertTrue(File(repo.attachments,name).isFile)
    }
    @Test fun courseBatchReplaceRestoreAndImage()=runBlocking {
        val term=Term(startDate=LocalDate.now().toString());val old=Course(termId=term.id,title="原课程",weeks=listOf(1))
        repo.saveTerm(term);repo.saveCourse(old)
        val incoming=old.copy(id=newId(),title="新课程")
        assertEquals(1,repo.importCourses(term.id,listOf(incoming,incoming),false));assertEquals(0,repo.importCourses(term.id,listOf(incoming),false))
        assertEquals(1,repo.importCourses(term.id,listOf(incoming),true));assertEquals(1,repo.snapshotData().courses.size)
        assertTrue(repo.snapshotData().trashed.any {it.id==old.id});repo.restore(old.id);assertEquals(2,repo.snapshotData().courses.size)
        val image=WeekTable.render(app,repo.snapshotData(),term,1,false)
        val options=android.graphics.BitmapFactory.Options().apply {inJustDecodeBounds=true};android.graphics.BitmapFactory.decodeFile(image.path,options)
        assertEquals(1384,options.outWidth);assertEquals(230+144*term.slots.size+65,options.outHeight)
        WeekTable.save(app,image)
    }
    @Test fun sevenDaysVisibleAndPreviewOpens()=runBlocking {
        val term=Term(startDate=LocalDate.now().toString());repo.saveTerm(term)
        compose.waitUntil(10000){repo.state.value.terms.isNotEmpty()}
        compose.onNodeWithTag("nav-1").performClick()
        compose.onNodeWithContentDescription("课程表").performClick()
        listOf("周一","周二","周三","周四","周五","周六","周日").forEach {compose.onNodeWithText(it).assertIsDisplayed()}
        compose.onNodeWithContentDescription("导入课程").performClick()
        compose.onNodeWithText("选择 CSV / 苏大课表 HTML").assertExists()
        compose.onAllNodesWithContentDescription("关闭").onLast().performClick()
        compose.onNodeWithContentDescription("分享课表").performClick()
        compose.waitUntil(15000){compose.onAllNodesWithText("分享 PNG 图片").fetchSemanticsNodes().isNotEmpty()}
        compose.waitUntil(15000){compose.onAllNodesWithContentDescription("完整课表预览",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()};compose.onNodeWithContentDescription("完整课表预览",useUnmergedTree=true).assertExists();Unit
    }
    @Test fun updateScreenAndSignatureMismatch(){
        compose.onNodeWithTag("nav-4").performClick()
        compose.onNodeWithText("检查更新").performScrollTo().performClick()
        compose.onNodeWithText("当前版本 1.1.0").assertExists()
        compose.onNodeWithText("自动检查正式版").assertExists()
        // A current-version package must never be accepted as an upgrade.
        val own=File(compose.activity.applicationInfo.sourceDir)
        assertThrows(IllegalArgumentException::class.java){app.updates.verifyApk(own)}
    }
}

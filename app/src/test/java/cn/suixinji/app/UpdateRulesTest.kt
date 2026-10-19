package cn.suixinji.app

import cn.suixinji.app.data.*
import cn.suixinji.app.domain.CourseImport
import cn.suixinji.app.platform.ReleaseRules
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import java.io.File

class UpdateRulesTest {
    private val term=Term(weeks=20,slots=(1..12).map {LessonTime("08:00","08:45")})
    @Test fun sampleContainsSevenSchedulesWithoutDuplicateTable(){
        val html=javaClass.getResource("/suda-anonymous.html")!!.readText()
        val p=CourseImport.html(html,term)
        assertEquals(emptyList<String>(),p.errors);assertEquals(7,p.courses.size);assertEquals(6,p.courses.map{it.title}.distinct().size)
        val c=p.courses.single {it.title=="示例课程3"};assertEquals(listOf(20),c.weeks);assertEquals(3,c.toSlot);assertEquals("",c.room)
        assertEquals((2..17).toList(),p.courses.single {it.title=="示例课程1"}.weeks)
    }
    @Test fun localPrivateSampleStructure(){
        val path=System.getenv("SXJ_PRIVATE_TIMETABLE");assumeTrue(path!=null && File(path).isFile)
        val result=CourseImport.html(CourseImport.decode(File(path!!).readBytes()),term)
        assertEquals(0,result.errors.size);assertEquals(7,result.courses.size);assertEquals(6,result.courses.map {it.title}.distinct().size)
        assertTrue(result.courses.any {it.weeks==listOf(20) && it.fromSlot==3 && it.toSlot==3 && it.room.isBlank()})
    }
    @Test fun noncontinuousPeriodsDoNotFillGaps(){
        val html="<table id='Table1'><tr><td>测试课<br>周一第1,3,5节{第1-16周(单)}<br>教师<br>教室</td></tr></table>"
        val p=CourseImport.html(html,term);assertTrue(p.errors.isEmpty());assertEquals(listOf(1,3,5),p.courses.map{it.fromSlot});assertTrue(p.courses.all {it.fromSlot==it.toSlot && it.weeks.all {w->w%2==1}})
    }
    @Test fun csvQuotesAndDuplicateRows(){
        val raw=CourseImport.headers.joinToString(",")+"\r\n\"课程,名称\",1,1,2,教师,教室,\"1-4,8-12(双)\"\r\n\"课程,名称\",1,1,2,教师,教室,\"1-4,8-12(双)\""
        val p=CourseImport.csv(raw,term);assertTrue(p.errors.isEmpty());assertEquals(1,p.courses.size);assertEquals(listOf(1,2,3,4,8,10,12),p.courses.single().weeks)
    }
    @Test fun importErrorsPreventSilentLoss(){
        val raw=CourseImport.headers.joinToString(",")+"\n错误课程,1,13,14,教师,教室,1-20\n有效课程,2,1,2,教师,教室,1-20"
        val p=CourseImport.csv(raw,term);assertEquals(1,p.errors.size);assertEquals(1,p.courses.size)
        assertTrue(CourseImport.html("<iframe src='content_xs.html'></iframe>",term).errors.isNotEmpty())
        assertThrows(IllegalArgumentException::class.java){CourseImport.csvRows("\"未闭合")}
    }
    @Test fun weekRangesAreBoundedAndExact(){
        assertEquals(listOf(2,4,6),CourseImport.weeks("第2-6周(双)"));assertEquals(listOf(1,3,5,8),CourseImport.weeks("1-5(单),8"))
        for(raw in listOf("0-5","61","5-1","1-999999","abc"))assertThrows(Exception::class.java){CourseImport.weeks(raw)}
    }
    @Test fun oldSettingsRemainReadable(){val s=codec.decodeFromString<Settings>("{\"theme\":\"dark\"}");assertEquals("dark",s.theme);assertNull(s.avatar);assertEquals("生活的记录者",s.nickname);assertTrue(s.autoCheckUpdates)}
    @Test fun versionsAndTrustedDownloadRules(){
        assertTrue(ReleaseRules.newer("v1.10.0","1.9.9"));assertFalse(ReleaseRules.newer("v1.1.0","1.1.0"));assertFalse(ReleaseRules.newer("v9.0.0-beta","1.1.0"))
        assertTrue(ReleaseRules.trusted("https://release-assets.githubusercontent.com/file"));assertFalse(ReleaseRules.trusted("http://github.com/a"));assertFalse(ReleaseRules.trusted("https://github.com.evil.example/a"));assertFalse(ReleaseRules.trusted("https://user@github.com/a"))
        assertNull(ReleaseRules.digest("invalid"));assertEquals("a".repeat(64),ReleaseRules.digest("sha256:"+"a".repeat(64)))
    }
}

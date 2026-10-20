package cn.suixinji.app

import cn.suixinji.app.data.*
import cn.suixinji.app.domain.CourseImport
import org.junit.Assert.*
import org.junit.Test

class MultiSchoolImportTest {
    private val term=Term(weeks=20,slots=List(12){LessonTime("08:00","08:45")})
    @Test fun newZhengfangUsesExactWeeksAndMultipleCourses(){
        val html="""<table id="table1"><tr><td id="2-1">
            <div><span class="title">线性代数</span><p><i title="教师"></i>教师甲</p><p><i title="上课地点"></i>A101</p><p><i title="节/周"></i>(1-2节)1-16周(单)</p></div>
            <div><span class="title">实验</span><p><i title="周/节"></i>(3节)2,4,8周</p></div>
            </td></tr></table>"""
        val p=CourseImport.html("<table><tr><td>$html</td></tr></table>",term)
        assertEquals(emptyList<String>(),p.errors);assertEquals(2,p.courses.size)
        assertEquals("教师甲",p.courses[0].teacher);assertEquals("A101",p.courses[0].room)
        assertEquals((1..16 step 2).toList(),p.courses[0].weeks);assertEquals(2,p.courses[0].weekday)
        assertEquals(listOf(2,4,8),p.courses[1].weeks);assertEquals("",p.courses[1].teacher)
    }
    @Test fun qzReadsExplicitPeriodsAndSeparatedCourses(){
        val head=listOf("节次","星期一","星期二","星期三","星期四","星期五","星期六","星期日").joinToString(""){"<th>$it</th>"}
        val html="""<table id="kbtable"><tr>$head</tr><tr><th>1-2</th><td><div class="kbcontent">
            数学<br><font title="老师">老师甲</font><br><font title="周次(节次)">1-16(单)(周)[01-02]节</font><br><font title="教室">B102</font><br>-----<br>
            实验<br><font title="周次(节次)">2-8(周)[03-04]节</font>
            </div></td></tr></table>"""
        val p=CourseImport.html("<table><tr><td>$html</td></tr></table>",term)
        assertEquals(emptyList<String>(),p.errors);assertEquals(2,p.courses.size)
        assertEquals(1,p.courses[0].weekday);assertEquals("B102",p.courses[0].room);assertEquals(3,p.courses[1].fromSlot)
    }
    @Test fun urpMergedCellsAndNonContinuousPeriods(){
        val html="""<table><tr><th>课程名</th><th>教师</th><th>周次</th><th>星期</th><th>节次</th><th>教室</th></tr>
            <tr><td rowspan="2">物理</td><td rowspan="2">甲</td><td>1-8</td><td>一</td><td>1,3</td><td>A1</td></tr>
            <tr><td>9-16(双)</td><td>周日</td><td>5-6</td><td>A2</td></tr></table>"""
        val p=CourseImport.html("<table><tr><td>$html</td></tr></table>",term)
        assertEquals(emptyList<String>(),p.errors);assertEquals(3,p.courses.size)
        assertTrue(p.courses.all {it.title=="物理" && it.teacher=="甲"})
        assertEquals(listOf(10,12,14,16),p.courses.last().weeks);assertEquals(7,p.courses.last().weekday)
    }
    @Test fun invalidNewZhengfangDoesNotInventTime(){
        val p=CourseImport.html("""<table><tr><td id="1-1"><div><span class="title">课程</span><p title="节/周">未知</p></div></td></tr></table>""",term)
        assertTrue(p.errors.isNotEmpty());assertTrue(p.courses.isEmpty())
    }
    @Test fun emptyAndOuterTablesReportErrors(){
        assertTrue(CourseImport.html("<table id='Table1'><tr><td>空</td></tr></table>",term).errors.isNotEmpty())
        assertTrue(CourseImport.html("<table><tr><td>星期一 星期日</td></tr></table>",term).errors.isNotEmpty())
    }
}

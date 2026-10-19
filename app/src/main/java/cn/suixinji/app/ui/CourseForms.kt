@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package cn.suixinji.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.suixinji.app.data.*
import cn.suixinji.app.domain.Rules
import java.time.LocalDate

@Composable fun TermEditor(vm:AppViewModel,data:AppData,term:Term,onClose:()->Unit) {
    var title by remember { mutableStateOf(term.title) }; var start by remember { mutableStateOf(term.startDate) }
    var weeks by remember { mutableStateOf(term.weeks.toString()) }
    var times by remember { mutableStateOf(term.slots.joinToString("\n") { "${it.start}-${it.end}" }) }
    var error by remember { mutableStateOf<String?>(null) }
    FormDialog("学期设置",onClose,onSave={
        val count=weeks.toIntOrNull()
        val slots=times.lines().filter { it.isNotBlank() }.mapNotNull { line -> val p=line.trim().split("-"); if(p.size==2 && validateClock(p[0])&&validateClock(p[1])&&p[0]<p[1]) LessonTime(p[0],p[1]) else null }
        error=when { title.isBlank()->"请输入学期名称"; count==null || count !in 1..60 ->"总周数需在 1–60 之间"; slots.size!=times.lines().count { it.isNotBlank() } || slots.isEmpty() || slots.size>20 ->"每行填写一节，如 08:00-08:45，最多 20 节"; slots.zipWithNext().any { (a,b)->a.end>b.start }->"各节次时间应依次排列且不重叠"; data.courses.any { it.termId==term.id && (it.toSlot>slots.size || it.weeks.any { w->w>count!! }) }->"现有课程超出新的周数或节次，请先调整课程"; else->null }
        if(error==null) { vm.run("学期已保存") { vm.repo.saveTerm(term.copy(title=title,startDate=start,weeks=count!!,slots=slots)) }; onClose() }
    }) {
        Field("学期名称",title,{title=it})
        DateField("开学日期",start) { start=it }
        Text("开学日期所在的周为第 1 周，周一为每周起点。开学日期之前不排课。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Field("总周数（1–60）",weeks,{weeks=it})
        Field("每日节次时间（每行一节）",times,{times=it},multiline=true)
        ErrorText(error)
    }
}

@Composable fun CourseEditor(vm:AppViewModel,data:AppData,course:Course,onClose:()->Unit,instance:AgendaItem?=null) {
    var c by remember(course.id) { mutableStateOf(course) }; var instanceDate by remember { mutableStateOf(instance?.date ?: today()) }
    val term=data.terms.find { it.id==c.termId }
    var error by remember { mutableStateOf<String?>(null) }; var conflict by remember { mutableStateOf(false) }
    fun save() { vm.run("课程已保存") { if(instance==null) vm.repo.saveCourse(c) else vm.repo.courseException(instance,c,movedDate=instanceDate) }; onClose() }
    FormDialog(if(instance!=null) "调整这一次课程" else "课程安排",onClose,onSave={
        error=when { c.title.isBlank()->"请填写课程名称"; term==null ->"请先创建学期"; c.fromSlot>c.toSlot || c.toSlot>term.slots.size ->"结束节次不能早于开始，且不能超出学期设置"; instance==null && c.weeks.isEmpty()->"至少选择一个教学周"; else->null }
        if(error==null && term!=null) {
            val dates=if(instance!=null) listOf(LocalDate.parse(instanceDate)) else (0 until term.weeks*7).map { LocalDate.parse(term.startDate).plusDays(it.toLong()) }.filter { Rules.courseOccurs(c,term,it) }
            val overlaps=dates.any { Rules.conflicts(Rules.agenda(data,it),term.slots[c.fromSlot-1].start,term.slots[c.toSlot-1].end,c.id) }
            if(overlaps) conflict=true else save()
        }
    }) {
        Field("课程名称",c.title,{c=c.copy(title=it.take(150))})
        if(instance==null) {
            Text("所属学期",style=MaterialTheme.typography.labelLarge)
            Choices(data.terms.map { it.title },data.terms.indexOfFirst { it.id==c.termId }) { i -> val t=data.terms[i]; c=c.copy(termId=t.id,weeks=(1..t.weeks).toList(),fromSlot=1,toSlot=minOf(2,t.slots.size)) }
            Choices(weekdayNames.map { "周$it" },c.weekday-1) { c=c.copy(weekday=it+1) }
        } else DateField("这次上课日期",instanceDate) { instanceDate=it }
        term?.let { t ->
            Text("开始节次",style=MaterialTheme.typography.labelLarge)
            Choices(t.slots.indices.map { "第${it+1}节" },c.fromSlot-1) { c=c.copy(fromSlot=it+1,toSlot=maxOf(c.toSlot,it+1)) }
            Text("结束节次",style=MaterialTheme.typography.labelLarge)
            Choices(t.slots.indices.map { "第${it+1}节" },c.toSlot-1) { c=c.copy(toSlot=it+1) }
            if(instance==null) {
                Text("教学周",style=MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick={c=c.copy(weeks=(1..t.weeks).toList())}){Text("全周")}
                    TextButton(onClick={c=c.copy(weeks=(1..t.weeks).filter { it%2==1 })}){Text("单周")}
                    TextButton(onClick={c=c.copy(weeks=(1..t.weeks).filter { it%2==0 })}){Text("双周")}
                    TextButton(onClick={c=c.copy(weeks=emptyList())}){Text("清空")}
                }
                FlowRow(horizontalArrangement=Arrangement.spacedBy(7.dp)) { (1..t.weeks).forEach { w -> FilterChip(selected=w in c.weeks,onClick={c=c.copy(weeks=if(w in c.weeks) c.weeks-w else (c.weeks+w).sorted())},label={Text("$w")}) } }
            }
        }
        Field("教室",c.room,{c=c.copy(room=it)})
        Field("教师",c.teacher,{c=c.copy(teacher=it)})
        ColorPicker(c.color) { c=c.copy(color=it) }
        ReminderChoice(c.reminder) { c=c.copy(reminder=it) }
        ErrorText(error)
    }
    if(conflict) ConfirmDialog("上课时间有重叠","这门课与已有安排重叠，仍要保存吗？",{conflict=false},{conflict=false;save()},"仍然保存")
}

@Composable fun TermManager(vm:AppViewModel,data:AppData,onClose:()->Unit,onEdit:(Term)->Unit) {
    var deleting by remember { mutableStateOf<Term?>(null) }
    FormDialog("学期管理",onClose) {
        Button(onClick={onEdit(Term())},modifier=Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Add,null); Text("新建学期") }
        if(data.terms.isEmpty()) EmptyState(Icons.Rounded.School,"还没有学期","设置开学日期和节次时间后，就能开始排课。")
        data.terms.sortedByDescending { it.startDate }.forEach { t -> PaperCard {
            Text(t.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            Text("${t.startDate} 起 · ${t.weeks} 周 · 每天 ${t.slots.size} 节",style=MaterialTheme.typography.bodySmall)
            Row { TextButton(onClick={onEdit(t)}) { Text("编辑") }; TextButton(onClick={deleting=t}) { Text("删除",color=MaterialTheme.colorScheme.error) } }
        } }
    }
    deleting?.let { t -> ConfirmDialog("删除这个学期？","该学期及所属课程将一起进入回收站，保留 30 天。",{deleting=null},{vm.run("已移入回收站"){vm.repo.trash(t.id)};deleting=null}) }
}

@Composable fun Timetable(vm:AppViewModel,data:AppData,onClose:()->Unit,onCreate:(Term)->Unit,onItem:(AgendaItem)->Unit,onTerms:()->Unit) {
    var selected by remember { mutableStateOf(data.terms.maxByOrNull { it.startDate }?.id) }
    val term=data.terms.find { it.id==selected } ?: data.terms.firstOrNull()
    var week by remember(term?.id) { mutableIntStateOf(term?.let { Rules.termWeek(it,LocalDate.now()).coerceIn(1,it.weeks) } ?: 1) }
    FormDialog("课程表",onClose) {
        if(term==null) EmptyState(Icons.Rounded.School,"先设置一个学期","课程按教学周排列，不自动跟随法定调休。","管理学期",onTerms)
        else {
            Choices(data.terms.map { it.title },data.terms.indexOf(term)) { selected=data.terms[it].id }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                IconButton(onClick={week=(week-1).coerceAtLeast(1)}) { Icon(Icons.Rounded.ChevronLeft,"上一教学周") }
                Column(horizontalAlignment=Alignment.CenterHorizontally) { Text("第 $week 周",fontWeight=FontWeight.SemiBold); Text("共 ${term.weeks} 周",style=MaterialTheme.typography.bodySmall) }
                IconButton(onClick={week=(week+1).coerceAtMost(term.weeks)}) { Icon(Icons.Rounded.ChevronRight,"下一教学周") }
            }
            val monday=Rules.monday(LocalDate.parse(term.startDate)).plusWeeks((week-1).toLong())
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                Column(Modifier.width(56.dp)) {
                    Spacer(Modifier.height(56.dp))
                    term.slots.forEachIndexed { i,t -> Column(Modifier.height(72.dp),verticalArrangement=Arrangement.Center) { Text("${i+1}",fontWeight=FontWeight.SemiBold); Text(t.start,style=MaterialTheme.typography.labelSmall); Text(t.end,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant) } }
                }
                for(day in 0..6) {
                    val date=monday.plusDays(day.toLong())
                    val items=Rules.agenda(data,date).filter { it.kind=="course" && it.course?.termId==term.id }.sortedBy { it.course!!.fromSlot }
                    val lanes=mutableListOf<Int>(); val placed=items.map { item ->
                        val course=item.course!!; var lane=lanes.indexOfFirst { it<course.fromSlot }
                        if(lane==-1) { lane=lanes.size; lanes+=course.toSlot } else lanes[lane]=course.toSlot
                        item to lane
                    }
                    val width=108.dp*maxOf(1,lanes.size)
                    Column(Modifier.width(width).padding(horizontal=3.dp)) {
                        Column(Modifier.fillMaxWidth().height(56.dp),horizontalAlignment=Alignment.CenterHorizontally) { Text("周${weekdayNames[day]}",fontWeight=FontWeight.SemiBold); Text("${date.monthValue}/${date.dayOfMonth}",style=MaterialTheme.typography.bodySmall) }
                        Box(Modifier.height(72.dp*term.slots.size).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.3f),RoundedCornerShape(12.dp))) {
                            placed.forEach { (item,lane) -> val c=item.course!!
                                Surface(Modifier.offset(x=108.dp*lane,y=72.dp*(c.fromSlot-1)).width(102.dp).height(72.dp*(c.toSlot-c.fromSlot+1)-5.dp).clickable { onItem(item) },shape=RoundedCornerShape(12.dp),color=accents[c.color%accents.size].copy(alpha=.17f)) {
                                    Column(Modifier.padding(9.dp)) { Text(c.title,style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.SemiBold,maxLines=3); Text(c.room,style=MaterialTheme.typography.labelSmall,maxLines=2); Text("${item.start}",style=MaterialTheme.typography.labelSmall) }
                                }
                            }
                        }
                    }
                }
            }
            Button(onClick={onCreate(term)},modifier=Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Add,null); Text("添加课程") }
            TextButton(onClick=onTerms) { Text("管理学期与节次") }
            Text("法定假期仅作提示；临时停课或调课请点课程后修改这一次。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

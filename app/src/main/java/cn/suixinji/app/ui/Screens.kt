@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package cn.suixinji.app.ui

import android.os.SystemClock
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.suixinji.app.data.*
import cn.suixinji.app.domain.Rules
import cn.suixinji.app.platform.Holidays
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.YearMonth

@Composable fun TodayScreen(vm:AppViewModel,data:AppData,holidays:Holidays,onNewEvent:()->Unit,onDiary:()->Unit,onHabit:()->Unit,onItem:(AgendaItem)->Unit,onFocus:(Event?)->Unit,onTimetable:()->Unit,onHabitDetail:(Habit)->Unit) {
    val date=LocalDate.now(); val agenda=remember(data,date) { Rules.agenda(data,date) }
    val due=data.habits.filter { Rules.habitDue(it,date) && !Rules.periodDone(it,date,data.logs) }
    val lunar=holidays.label(date)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        PageTitle("随心记 · 给生活一点留白","今天，慢慢来") { IconButton(onClick=onTimetable) { Icon(Icons.Rounded.School,"打开课程表",tint=MaterialTheme.colorScheme.primary) } }
        Surface(shape=RoundedCornerShape(26.dp),color=MaterialTheme.colorScheme.primaryContainer) {
            Row(Modifier.fillMaxWidth().padding(22.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${date.year} 年 ${date.monthValue} 月",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                    Row(verticalAlignment=Alignment.Bottom) { Text("${date.dayOfMonth}",style=MaterialTheme.typography.displayLarge,fontWeight=FontWeight.Light); Spacer(Modifier.width(12.dp)); Text("星期${weekdayNames[date.dayOfWeek.value-1]}",Modifier.padding(bottom=10.dp),style=MaterialTheme.typography.titleMedium) }
                    Text("农历 ${lunar.lunar} ${lunar.festival}",style=MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment=Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.WbSunny,null,Modifier.size(42.dp),tint=MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(10.dp)); Text("留心每个日常",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                }
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            QuickAction(Icons.Rounded.AddTask,"添日程",Modifier.weight(1f),onNewEvent)
            QuickAction(Icons.Rounded.EditNote,"写日记",Modifier.weight(1f),onDiary)
            QuickAction(Icons.Rounded.CheckCircleOutline,"去打卡",Modifier.weight(1f),onHabit)
        }
        val minutes=data.sessions.filter { it.mode=="专注" && it.date==date.toString() }.sumOf { it.seconds }/60
        PaperCard {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Rounded.HourglassTop,null,tint=MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(horizontal=12.dp)) { Text("给自己一段专注时间",style=MaterialTheme.typography.titleSmall); Text("今天已专注 $minutes 分钟",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                FilledTonalIconButton(onClick={onFocus(null)}) { Icon(Icons.Rounded.PlayArrow,"开始专注") }
            }
        }
        SectionTitle("今天的安排","${agenda.count { it.kind=="event" && it.complete }} 件已完成 · ${agenda.count { it.kind=="course" }} 节课程") { TextButton(onClick=onNewEvent) { Text("添加") } }
        if(agenda.isEmpty()) PaperCard { EmptyState(Icons.Rounded.EventAvailable,"今天还很宽敞","记下一件想做的事，让生活更从容。","安排一下",onNewEvent) }
        else agenda.forEach { AgendaCard(it,{onItem(it)},{vm.run { vm.repo.toggleComplete(it) }},{onFocus(it.event)}) }
        SectionTitle("待打卡","小小坚持，也值得被记住")
        if(due.isEmpty()) PaperCard { Text(if(data.habits.isEmpty()) "还没有习惯，挑一件小事开始吧。" else "今天的目标都照顾好了。",color=MaterialTheme.colorScheme.onSurfaceVariant); TextButton(onClick=onHabit) { Text(if(data.habits.isEmpty()) "创建习惯" else "查看全部习惯") } }
        else due.forEach { HabitCard(it,data,{onHabitDetail(it)}) }
        Spacer(Modifier.height(20.dp))
    }
}
@Composable private fun QuickAction(icon:androidx.compose.ui.graphics.vector.ImageVector,label:String,modifier:Modifier,onClick:()->Unit) {
    Surface(modifier.clickable(onClick=onClick),shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) { Column(Modifier.padding(vertical=16.dp),horizontalAlignment=Alignment.CenterHorizontally) { Icon(icon,null,tint=MaterialTheme.colorScheme.primary); Spacer(Modifier.height(7.dp)); Text(label,style=MaterialTheme.typography.labelLarge) } }
}
@Composable fun AgendaCard(item:AgendaItem,onClick:()->Unit,onComplete:()->Unit,onFocus:()->Unit) {
    val c=accents[item.color%accents.size]
    Surface(Modifier.fillMaxWidth().clickable(onClick=onClick),shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant.copy(alpha=.65f))) {
        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(42.dp).background(c,RoundedCornerShape(3.dp)))
            Column(Modifier.width(64.dp).padding(start=12.dp)) { Text(item.start ?: "全天",style=MaterialTheme.typography.labelLarge,color=c); item.end?.let { Text(it,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant) } }
            Column(Modifier.weight(1f).padding(start=7.dp)) { Text(item.title,style=MaterialTheme.typography.titleSmall,maxLines=2,overflow=TextOverflow.Ellipsis,textDecoration=if(item.complete) TextDecoration.LineThrough else null,color=if(item.complete) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface); Text((if(item.kind=="course") "课程 · " else "")+item.subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis) }
            if(item.kind=="event") Checkbox(checked=item.complete,onCheckedChange={onComplete()}) else Icon(Icons.Rounded.School,null,Modifier.padding(10.dp).size(22.dp),tint=c)
        }
    }
}

@Composable fun CalendarScreen(vm:AppViewModel,data:AppData,holidays:Holidays,selected:LocalDate,onSelect:(LocalDate)->Unit,onNewEvent:()->Unit,onItem:(AgendaItem)->Unit,onDiary:(Diary)->Unit,onHabit:(Habit)->Unit,onTimetable:()->Unit) {
    var month by remember(selected) { mutableStateOf(YearMonth.from(selected)) }; var weekView by remember { mutableStateOf(false) }; var query by remember { mutableStateOf("") }
    val revision by holidays.revision.collectAsState()
    val dates=if(weekView) (0..6).map { Rules.monday(selected).plusDays(it.toLong()) } else {
        val first=Rules.monday(month.atDay(1)); val count=if(first.plusDays(34).isBefore(month.atEndOfMonth())) 42 else 35
        (0 until count).map { first.plusDays(it.toLong()) }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        PageTitle("有序的日子，也有自由","日历") { IconButton(onClick=onTimetable) { Icon(Icons.Rounded.School,"课程表") }; IconButton(onClick=onNewEvent) { Icon(Icons.Rounded.Add,"新建日程") } }
        Choices(listOf("月视图","周视图"),if(weekView)1 else 0) { weekView=it==1 }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("${month.year}年 ${month.monthValue}月",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
            TextButton(onClick={onSelect(LocalDate.now());month=YearMonth.now()}) { Text("今天") }
            IconButton(onClick={if(weekView) onSelect(selected.minusWeeks(1)) else {month=month.minusMonths(1);onSelect(month.atDay(1))}}) { Icon(Icons.Rounded.ChevronLeft,"上一个月或周") }
            IconButton(onClick={if(weekView) onSelect(selected.plusWeeks(1)) else {month=month.plusMonths(1);onSelect(month.atDay(1))}}) { Icon(Icons.Rounded.ChevronRight,"下一个月或周") }
        }
        PaperCard {
            Row(Modifier.fillMaxWidth()) { weekdayNames.forEach { Text(it,Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.Center,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) } }
            Spacer(Modifier.height(8.dp))
            dates.chunked(7).forEach { row -> Row(Modifier.fillMaxWidth()) { row.forEach { date ->
                val label=remember(date,revision) { holidays.label(date) }; val agenda=remember(data,date) { Rules.agenda(data,date) }
                val diary=data.diaries.any { it.date==date.toString() }; val habit=data.logs.any { it.date==date.toString() }
                val active=date==selected
                Column(Modifier.weight(1f).height(66.dp).clip(RoundedCornerShape(13.dp)).background(if(active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent).clickable { onSelect(date) }.padding(vertical=5.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Row(verticalAlignment=Alignment.CenterVertically) { Text("${date.dayOfMonth}",style=MaterialTheme.typography.titleSmall,fontWeight=if(active) FontWeight.Bold else FontWeight.Normal,color=if(YearMonth.from(date)!=month && !weekView) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.45f) else MaterialTheme.colorScheme.onSurface)
                        label.offDay?.let { Text(if(it) "休" else "班",style=MaterialTheme.typography.labelSmall,color=if(it) Color(0xFFAD7474) else MaterialTheme.colorScheme.primary) }
                    }
                    Text(label.festival.ifBlank { label.lunar },style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Clip)
                    Row(Modifier.padding(top=4.dp),horizontalArrangement=Arrangement.spacedBy(3.dp)) { if(agenda.any { it.kind=="event" }) Dot(accents[0]); if(agenda.any { it.kind=="course" }) Dot(accents[2]); if(diary) Dot(accents[3]); if(habit) Dot(accents[1]) }
                }
            } } }
        }
        if(!holidays.available(selected.year)) Text("${selected.year} 年调休数据暂未获取",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),placeholder={Text("搜索日程或课程")},leadingIcon={Icon(Icons.Rounded.Search,null)},singleLine=true,shape=RoundedCornerShape(15.dp))
        SectionTitle(if(query.isBlank()) humanDate(selected) else "搜索结果")
        val items=if(query.isBlank()) Rules.agenda(data,selected) else {
            val events=data.events.filter { (it.title+it.note+it.category).contains(query,true) }.map { e -> AgendaItem("event:${e.id}:${e.date}",e.id,e.date,e.title,e.category,if(e.allDay)null else e.start,if(e.allDay)null else e.end,e.color,"event",event=e) }
            val courses=data.courses.filter { (it.title+it.room+it.teacher).contains(query,true) }.map { c -> AgendaItem("course:${c.id}:$selected",c.id,selected.toString(),c.title,c.room,null,null,c.color,"course",course=c) }
            events+courses
        }
        if(items.isEmpty() && query.isNotBlank()) EmptyState(Icons.Rounded.SearchOff,"没有找到相关安排","试试更短的关键词。")
        items.forEach { item -> AgendaCard(item,{onItem(item)},{vm.run { vm.repo.toggleComplete(item) }},{}) }
        if(query.isBlank()) {
            val diaries=data.diaries.filter { it.date==selected.toString() }; val habits=data.habits.filter { h->data.logs.any { it.habitId==h.id && it.date==selected.toString() } }
            diaries.forEach { d -> PaperCard { MenuRow(Icons.Rounded.EditNote,d.title.ifBlank { "这一天的日记" },d.body.take(60)) {onDiary(d)} } }
            habits.forEach { h -> PaperCard { MenuRow(Icons.Rounded.CheckCircleOutline,h.title,"${compactNumber(Rules.value(h,selected,data.logs))} ${h.unit}") {onHabit(h)} } }
            if(items.isEmpty() && diaries.isEmpty() && habits.isEmpty()) EmptyState(Icons.Rounded.CalendarToday,"这一天还没有记录","安排、日记和打卡，都会在这里相遇。","添加日程",onNewEvent)
        }
        Spacer(Modifier.height(20.dp))
    }
}
@Composable private fun Dot(color:Color) { Box(Modifier.size(4.dp).background(color,CircleShape)) }

@Composable fun RecordsScreen(vm:AppViewModel,data:AppData,tab:Int,onTab:(Int)->Unit,onNewDiary:()->Unit,onDiary:(Diary)->Unit,onNewHabit:()->Unit,onHabit:(Habit)->Unit,onDelete:(String,String)->Unit) {
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        PageTitle("收集日常的小小回响","记录") { IconButton(onClick=if(tab==0)onNewDiary else onNewHabit) {Icon(Icons.Rounded.Add,if(tab==0)"新建日记" else "新建习惯")} }
        Choices(listOf("日记","习惯打卡"),tab,onTab)
        OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),placeholder={Text(if(tab==0) "搜索标题、正文、心情或标签" else "搜索习惯")},leadingIcon={Icon(Icons.Rounded.Search,null)},singleLine=true,shape=RoundedCornerShape(15.dp))
        if(tab==0) {
            val diaries=data.diaries.filter { (it.title+it.body+it.tags+it.mood).contains(query,true) }.sortedWith(compareByDescending<Diary>{it.date}.thenByDescending{it.updatedAt})
            if(diaries.isEmpty()) EmptyState(Icons.Rounded.MenuBook,if(query.isEmpty()) "给今天留一页" else "没有找到日记",if(query.isEmpty()) "几句话、一张图，都值得记录。" else "试试日期以外的关键词。",if(query.isEmpty()) "写第一篇日记" else null,onNewDiary)
            diaries.forEach { d -> Surface(Modifier.fillMaxWidth().clickable {onDiary(d)},shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(17.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) { Text("${d.date} · ${d.mood}",Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.secondary); IconButton(onClick={onDelete(d.id,"这篇日记")},Modifier.size(30.dp)) { Icon(Icons.Rounded.MoreHoriz,"删除日记") } }
                    if(d.title.isNotBlank()) Text(d.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) { if(d.body.isNotBlank()) Text(d.body,Modifier.weight(1f),maxLines=4,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant); d.images.firstOrNull()?.let { LocalImage(java.io.File(vm.repo.attachments,it),Modifier.size(76.dp)) } }
                    if(d.tags.isNotBlank()) Text(d.tags.split(",","，").filter {it.isNotBlank()}.joinToString("  "){"#${it.trim()}"},style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                }
            } }
        } else {
            val habits=data.habits.filter { it.title.contains(query,true) }
            if(habits.isEmpty()) EmptyState(Icons.Rounded.Spa,"慢慢养成一个好习惯","从一件容易做到的小事开始。","创建习惯",onNewHabit)
            habits.forEach { h -> HabitCard(h,data,{onHabit(h)},{onDelete(h.id,"这个习惯及其打卡记录")}) }
        }
        Spacer(Modifier.height(20.dp))
    }
}
@Composable fun HabitCard(h:Habit,data:AppData,onClick:()->Unit,onDelete:(()->Unit)?=null) {
    val date=LocalDate.now(); val done=Rules.done(h,date,data.logs); val due=Rules.habitDue(h,date)
    Surface(Modifier.fillMaxWidth().clickable(onClick=onClick),shape=RoundedCornerShape(19.dp),color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(15.dp),verticalAlignment=Alignment.CenterVertically) {
            Surface(shape=RoundedCornerShape(14.dp),color=accents[h.color%accents.size].copy(alpha=.16f)) { Icon(if(done) Icons.Rounded.Check else Icons.Rounded.Spa,null,Modifier.padding(12.dp),tint=accents[h.color%accents.size]) }
            Column(Modifier.weight(1f).padding(horizontal=13.dp)) {
                Text(h.title,style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold)
                Text(if(!due) "今日休息" else if(h.numeric) "${compactNumber(Rules.value(h,date,data.logs))} / ${compactNumber(h.target)} ${h.unit}" else if(done) "今天已完成" else "今天还未打卡",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(h.frequency==HabitFrequency.WEEKLY) Text("本周 ${Rules.weekCount(h,date,data.logs)}/${h.weeklyTarget} 天",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
            }
            if(onDelete!=null) IconButton(onClick=onDelete) { Icon(Icons.Rounded.MoreHoriz,"删除习惯") } else Icon(Icons.Rounded.ChevronRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable fun FocusScreen(vm:AppViewModel,data:AppData,onSettings:()->Unit,onDelete:(String,String)->Unit,initialEvent:Event?) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) { while(true) { now=SystemClock.elapsedRealtime(); delay(250) } }
    val completed=data.sessions.count { it.mode=="专注" && it.status=="完整" }
    val last=data.sessions.maxByOrNull { it.startedAt }
    var mode by remember(last?.id) { mutableStateOf(if(last?.mode=="专注" && last.status=="完整") if(completed%data.settings.rounds==0) "长休息" else "短休息" else "专注") }
    var eventId by remember(initialEvent?.id) { mutableStateOf(initialEvent?.id) }
    var taskPicker by remember { mutableStateOf(false) }; var stopping by remember { mutableStateOf(false) }
    val timer=data.timer
    val minutes=when(mode) { "短休息"->data.settings.shortMinutes; "长休息"->data.settings.longMinutes; else->data.settings.focusMinutes }
    val remaining=timer?.let { Rules.remaining(it,now) } ?: minutes*60
    val color=MaterialTheme.colorScheme.primary; val track=MaterialTheme.colorScheme.primaryContainer
    val selected=data.events.find { it.id==eventId }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=22.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(15.dp)) {
        PageTitle("一次只做一件事","专注此刻") { IconButton(onClick=onSettings,enabled=timer==null) { Icon(Icons.Rounded.Tune,"番茄钟设置") } }
        if(timer==null) Choices(listOf("专注","短休息","长休息"),listOf("专注","短休息","长休息").indexOf(mode)) {mode=listOf("专注","短休息","长休息")[it]}
        else Text(timer.mode,style=MaterialTheme.typography.titleMedium,color=color)
        Box(Modifier.padding(vertical=12.dp).size(252.dp),contentAlignment=Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) { val stroke=Stroke(width=7.dp.toPx(),cap=StrokeCap.Round); val inset=8.dp.toPx(); val size=Size(size.width-2*inset,size.height-2*inset); drawArc(track,-90f,360f,false,Offset(inset,inset),size,style=stroke); drawArc(color,-90f,360f*(remaining.toFloat()/(timer?.duration ?: minutes*60)).coerceIn(0f,1f),false,Offset(inset,inset),size,style=stroke) }
            Column(horizontalAlignment=Alignment.CenterHorizontally) { Text(secondsLabel(remaining),style=MaterialTheme.typography.displayLarge,fontWeight=FontWeight.Light); Spacer(Modifier.height(8.dp)); Text(if(timer?.running==true) "让注意力，留在这里" else if(timer!=null) "暂停一下，也没关系" else "准备好了，就开始",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        OutlinedButton(onClick={taskPicker=true},enabled=timer==null,shape=RoundedCornerShape(14.dp)) { Icon(Icons.Rounded.Link,null,Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(timer?.title ?: selected?.title ?: "自由专注 · 可关联日程",maxLines=1,overflow=TextOverflow.Ellipsis) }
        if(timer==null) Button(onClick={vm.run { vm.repo.startTimer(mode,minutes,selected) }},modifier=Modifier.fillMaxWidth().height(54.dp),shape=RoundedCornerShape(18.dp)) { Icon(Icons.Rounded.PlayArrow,null); Spacer(Modifier.width(8.dp)); Text("开始${mode}") }
        else Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) { Button(onClick={vm.run {vm.repo.pauseTimer()}},Modifier.weight(1f).height(52.dp)) { Icon(if(timer.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,null); Text(if(timer.running) "暂停" else "继续") }; OutlinedButton(onClick={stopping=true},Modifier.weight(1f).height(52.dp)) {Text("提前结束")} }
        if(!vm.repo.reminders.notificationsAllowed() || !vm.repo.reminders.exactAllowed()) Text(if(!vm.repo.reminders.notificationsAllowed()) "通知未开启，计时结束时可能无法提醒。" else "准时提醒权限未开启，锁屏提醒可能延迟。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.secondary)
        val focus=data.sessions.filter {it.mode=="专注"}; val today=LocalDate.now(); val todaySeconds=focus.filter { it.date==today.toString() }.sumOf {it.seconds}; val weekSeconds=focus.filter { LocalDate.parse(it.date)>=Rules.monday(today) && LocalDate.parse(it.date)<=today }.sumOf {it.seconds}
        PaperCard {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceAround) { FocusStat("今日专注","${todaySeconds/60} 分钟"); FocusStat("本周专注","${weekSeconds/60} 分钟"); FocusStat("完整轮次","$completed") }
            Spacer(Modifier.height(18.dp))
            val days=(6 downTo 0).map {today.minusDays(it.toLong())}; val amounts=days.map {d->focus.filter {it.date==d.toString()}.sumOf {it.seconds}/60}; val max=maxOf(1,amounts.maxOrNull()?:1)
            Row(Modifier.fillMaxWidth().height(100.dp),horizontalArrangement=Arrangement.SpaceAround,verticalAlignment=Alignment.Bottom) { days.forEachIndexed { i,d -> Column(horizontalAlignment=Alignment.CenterHorizontally) { Text("${amounts[i]}",style=MaterialTheme.typography.labelSmall); Box(Modifier.padding(vertical=5.dp).width(17.dp).height((amounts[i].toFloat()/max*55+4).dp).background(if(d==today) color else track,RoundedCornerShape(5.dp))); Text("${d.dayOfMonth}",style=MaterialTheme.typography.labelSmall) } } }
        }
        SectionTitle("最近的专注","提前结束和中断不会计为完整一轮")
        if(data.sessions.isEmpty()) Text("第一段专注时间，留给你喜欢的事。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        data.sessions.sortedByDescending {it.startedAt}.take(30).forEach { s -> PaperCard { Row(verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(s.title,style=MaterialTheme.typography.titleSmall); Text("${s.date} · ${s.mode} · ${s.status} · ${s.seconds/60}分${s.seconds%60}秒",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }; IconButton(onClick={onDelete(s.id,"这段专注记录")}) {Icon(Icons.Rounded.DeleteOutline,"删除专注记录")} } } }
        Spacer(Modifier.height(20.dp))
    }
    if(taskPicker) FormDialog("关联一个日程",{taskPicker=false}) { TextButton(onClick={eventId=null;taskPicker=false}) {Text("自由专注，不关联日程")}; data.events.forEach { e -> MenuRow(Icons.Rounded.Event,e.title,"${e.date} · ${e.category}") {eventId=e.id;taskPicker=false} } }
    if(stopping) ConfirmDialog("结束这一轮？","保留已专注的实际时长，本轮不计为完整完成。",{stopping=false},{stopping=false;vm.run {vm.repo.finishTimer(early=true)}},"结束并记录")
}
@Composable private fun FocusStat(title:String,value:String) { Column(horizontalAlignment=Alignment.CenterHorizontally) { Text(value,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold); Text(title,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant) } }

@Composable fun FocusSettings(vm:AppViewModel,settings:Settings,onClose:()->Unit) {
    var f by remember {mutableStateOf(settings.focusMinutes.toString())}; var s by remember {mutableStateOf(settings.shortMinutes.toString())}; var l by remember {mutableStateOf(settings.longMinutes.toString())}; var r by remember {mutableStateOf(settings.rounds.toString())}; var error by remember {mutableStateOf<String?>(null)}
    FormDialog("专注节奏",onClose,onSave={
        val values=listOf(f,s,l).map {it.toIntOrNull()}; val rounds=r.toIntOrNull()
        if(values.any {it==null || it !in 1..240} || rounds==null || rounds !in 1..12) error="时长需为 1–240 分钟，轮次需为 1–12"
        else {vm.run("专注设置已保存") {vm.repo.saveSettings(settings.copy(focusMinutes=values[0]!!,shortMinutes=values[1]!!,longMinutes=values[2]!!,rounds=rounds))};onClose()}
    }) { Field("专注分钟",f,{f=it});Field("短休息分钟",s,{s=it});Field("长休息分钟",l,{l=it});Field("每几轮进入长休息",r,{r=it}); Text("每轮结束后由你手动开始下一轮。",style=MaterialTheme.typography.bodySmall);ErrorText(error) }
}

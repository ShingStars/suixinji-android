@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package cn.suixinji.app.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.suixinji.app.data.*
import cn.suixinji.app.domain.Rules
import kotlinx.coroutines.*
import java.io.File
import java.time.LocalDate

@Composable fun ReminderChoice(minutes:Int,onChange:(Int)->Unit) {
    Text("提醒",style=MaterialTheme.typography.labelLarge)
    val values=listOf(-1,0,5,10,15,30,60,1440)
    Choices(listOf("关闭","准时","提前5分","提前10分","提前15分","提前30分","提前1小时","提前1天"),values.indexOf(minutes).coerceAtLeast(0)) { onChange(values[it]) }
}

@Composable fun EventEditor(vm:AppViewModel,data:AppData,event:Event,onClose:()->Unit,instance:AgendaItem?=null) {
    var e by remember(event.id,instance?.id) { mutableStateOf(event) }
    var error by remember { mutableStateOf<String?>(null) }
    var conflict by remember { mutableStateOf(false) }
    fun save() { vm.run("日程已保存") { if(instance!=null) vm.repo.eventException(instance,e) else vm.repo.saveEvent(e) }; onClose() }
    FormDialog(if(instance!=null) "编辑这一次日程" else if(data.events.any { it.id==event.id }) "编辑整个日程" else "新建日程",onClose,onSave={
        error=when { e.title.isBlank()->"请填写标题"; !e.allDay && e.end<=e.start -> "结束时间需晚于开始时间"; e.repeat==Repeat.WEEKDAYS && e.weekdays.isEmpty()->"请选择重复的星期"; e.repeatUntil!=null && e.repeatUntil!!<e.date->"结束重复日期不能早于开始日期"; else->null }
        if(error==null) { if(!e.allDay && Rules.conflicts(Rules.agenda(data,LocalDate.parse(e.date)),e.start,e.end,e.id)) conflict=true else save() }
    }) {
        Field("这件事的名字",e.title,{e=e.copy(title=it.take(200))})
        DateField("日期",e.date) { e=e.copy(date=it) }
        ToggleRow("全天 / 无具体时间",checked=e.allDay,onChange={e=e.copy(allDay=it)})
        if(!e.allDay) Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) { TimeField("开始",e.start,{e=e.copy(start=it)},Modifier.weight(1f)); TimeField("结束",e.end,{e=e.copy(end=it)},Modifier.weight(1f)) }
        Field("分类",e.category,{e=e.copy(category=it.take(30))})
        ColorPicker(e.color) { e=e.copy(color=it) }
        if(instance==null) {
            Text("重复",style=MaterialTheme.typography.labelLarge)
            Choices(listOf("不重复","每天","每周","每月","自选星期"),e.repeat.ordinal) { e=e.copy(repeat=Repeat.entries[it]) }
            if(e.repeat==Repeat.WEEKDAYS) WeekdayPicker(e.weekdays) { e=e.copy(weekdays=it) }
            if(e.repeat!=Repeat.NONE) {
                ToggleRow("设置重复结束日期",checked=e.repeatUntil!=null,onChange={e=e.copy(repeatUntil=if(it) LocalDate.parse(e.date).plusMonths(3).toString() else null)})
                e.repeatUntil?.let { DateField("重复至",it) { value->e=e.copy(repeatUntil=value) } }
                if(e.repeat==Repeat.MONTHLY) Text("没有对应日期的月份将跳过。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        ReminderChoice(e.reminder) { e=e.copy(reminder=it) }
        if(e.allDay && e.reminder>=0) Text("全天事项在当天 09:00 提醒。",style=MaterialTheme.typography.bodySmall)
        Field("备注",e.note,{e=e.copy(note=it)},multiline=true)
        ErrorText(error)
    }
    if(conflict) ConfirmDialog("时间有重叠","这个时间段已有其他安排，仍要保存吗？",{conflict=false},{conflict=false;save()},"仍然保存")
}

@Composable fun DiaryEditor(vm:AppViewModel,diary:Diary,onClose:()->Unit) {
    var d by remember(diary.id) { mutableStateOf(diary) }
    var initialized by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    val scope=rememberCoroutineScope()
    var saved by remember { mutableStateOf("随写随存") }
    var hasSaved by remember(diary.id) {mutableStateOf(vm.repo.state.value.diaries.any {it.id==diary.id})}
    fun persist(value:Diary) { if(hasSaved || value.title.isNotBlank() || value.body.isNotBlank() || value.images.isNotEmpty()) {hasSaved=true;vm.run { vm.repo.saveDiary(value) }} }
    LaunchedEffect(d) {
        if(!initialized) { initialized=true; return@LaunchedEffect }
        saved="正在保存…"; delay(400)
        if(hasSaved || d.title.isNotBlank() || d.body.isNotBlank() || d.images.isNotEmpty()) {
            runCatching { withContext(Dispatchers.IO) { vm.repo.saveDiary(d) } }.onSuccess {hasSaved=true;saved="已自动保存" }.onFailure { saved="保存失败，请重试" }
        } else saved="空白日记暂不保存"
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if(uris.isNotEmpty()) {
            importing=true
            scope.launch { try {
                val images=uris.map { vm.repo.importImage(it) }
                d=d.copy(images=d.images+images); persist(d)
            } catch(e:Exception) { vm.message(e.message ?: "图片导入失败") } finally { importing=false } }
        }
    }
    val close={ if(!importing) { persist(d); onClose() } }
    FormDialog("写下这一刻",close,onSave=close,saveLabel="完成",enabled=!importing) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(if(importing) "正在导入图片…" else saved,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary); Text("仅保存在本机",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        DateField("记录日期",d.date) { d=d.copy(date=it) }
        Field("标题（可留空）",d.title,{d=d.copy(title=it.take(200))})
        Text("此刻心情",style=MaterialTheme.typography.labelLarge)
        val moods=listOf("开心","平静","充实","疲惫","低落","焦虑")
        Choices(moods,moods.indexOf(d.mood)) { d=d.copy(mood=moods[it]) }
        OutlinedTextField(d.body,{d=d.copy(body=it)},Modifier.fillMaxWidth().heightIn(min=220.dp).testTag("diary-body"),placeholder={Text("今天发生了什么？\n慢慢写，留一点时间给自己。")},shape=RoundedCornerShape(16.dp))
        Field("标签（用逗号分隔）",d.tags,{d=d.copy(tags=it)})
        FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            d.images.forEach { name ->
                Box {
                    LocalImage(File(vm.repo.attachments,name),Modifier.size(100.dp))
                    IconButton(onClick={d=d.copy(images=d.images-name)},modifier=Modifier.align(Alignment.TopEnd).size(30.dp).background(MaterialTheme.colorScheme.surface.copy(alpha=.9f),RoundedCornerShape(10.dp))) { Icon(Icons.Rounded.Close,"移除图片",Modifier.size(18.dp)) }
                }
            }
        }
        OutlinedButton(onClick={picker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},enabled=!importing) { Icon(Icons.Rounded.AddPhotoAlternate,null); Spacer(Modifier.width(8.dp)); Text("添加图片") }
    }
}
@Composable fun LocalImage(file:File,modifier:Modifier=Modifier,description:String="日记图片",scale:ContentScale=ContentScale.Crop,sample:Int=4) {
    val bitmap by produceState<android.graphics.Bitmap?>(null,file.path,sample) { value=withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply { inSampleSize=sample }) }.getOrNull() } }
    Surface(modifier,shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surfaceVariant) { bitmap?.let { Image(it.asImageBitmap(),description,Modifier.fillMaxSize(),contentScale=scale) } }
}

@Composable fun HabitEditor(vm:AppViewModel,habit:Habit,onClose:()->Unit) {
    var h by remember(habit.id) { mutableStateOf(habit) }; var target by remember { mutableStateOf(compactNumber(h.target)) }; var error by remember { mutableStateOf<String?>(null) }
    FormDialog(if(habit.title.isEmpty()) "养成一个小习惯" else "编辑习惯",onClose,onSave={
        val number=target.toDoubleOrNull()
        error=when { h.title.isBlank()->"请填写习惯名称"; h.numeric && (number==null || !number.isFinite() || number<=0)->"目标必须是大于零的数值"; h.frequency==HabitFrequency.WEEKDAYS && h.weekdays.isEmpty()->"请选择打卡日"; else->null }
        if(error==null) { vm.run("习惯已保存") { vm.repo.saveHabit(h.copy(target=if(h.numeric) number!! else 1.0)) }; onClose() }
    }) {
        Field("习惯名称",h.title,{h=h.copy(title=it.take(100))})
        Choices(listOf("完成一次","累计数值"),if(h.numeric) 1 else 0) { h=h.copy(numeric=it==1) }
        if(h.numeric) { Field("每日目标",target,{target=it}); Field("单位（分钟、毫升、页…）",h.unit,{h=h.copy(unit=it.take(15))}) }
        Text("打卡频率",style=MaterialTheme.typography.labelLarge)
        Choices(listOf("每天","指定星期","每周若干次"),h.frequency.ordinal) { h=h.copy(frequency=HabitFrequency.entries[it]) }
        if(h.frequency==HabitFrequency.WEEKDAYS) WeekdayPicker(h.weekdays) { h=h.copy(weekdays=it) }
        if(h.frequency==HabitFrequency.WEEKLY) Choices((1..7).map { "${it}天" },h.weeklyTarget-1) { h=h.copy(weeklyTarget=it+1) }
        DateField("开始日期",h.createdDate) { h=h.copy(createdDate=it) }
        ColorPicker(h.color) { h=h.copy(color=it) }
        ToggleRow("打卡提醒",checked=h.reminderTime!=null,onChange={h=h.copy(reminderTime=if(it) "21:00" else null)})
        h.reminderTime?.let { TimeField("每天",it,{v->h=h.copy(reminderTime=v)}) }
        Text("补卡会标记为补录并重算统计；每周目标按不同日期计次。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        ErrorText(error)
    }
}

@Composable fun HabitDetail(vm:AppViewModel,data:AppData,habit:Habit,onClose:()->Unit,onEdit:()->Unit) {
    var date by remember { mutableStateOf(LocalDate.now()) }; var value by remember { mutableStateOf("") }; var error by remember { mutableStateOf<String?>(null) }
    val logs=data.logs.filter { it.habitId==habit.id }
    val todayDone=Rules.done(habit,date,logs)
    FormDialog(habit.title,onClose) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Column { Text("${Rules.streak(habit,logs)}",style=MaterialTheme.typography.displaySmall,color=MaterialTheme.colorScheme.primary); Text(if(habit.frequency==HabitFrequency.WEEKLY) "连续达标周" else "连续完成日",style=MaterialTheme.typography.bodySmall) }
            TextButton(onClick=onEdit) { Icon(Icons.Rounded.Edit,null,Modifier.size(18.dp)); Text(" 编辑习惯") }
        }
        if(habit.frequency==HabitFrequency.WEEKLY) Text("本周 ${Rules.weekCount(habit,LocalDate.now(),logs)} / ${habit.weeklyTarget} 天")
        Text("${LocalDate.now().monthValue}月记录",fontWeight=FontWeight.SemiBold)
        val start=LocalDate.now().withDayOfMonth(1)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(5.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            for(day in 1..start.lengthOfMonth()) {
                val dayDate=start.withDayOfMonth(day); val done=Rules.done(habit,dayDate,logs)
                Box(Modifier.size(32.dp).background(if(done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,RoundedCornerShape(9.dp)).clickable { date=dayDate },contentAlignment=Alignment.Center) { Text("$day",color=if(done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,style=MaterialTheme.typography.bodySmall) }
            }
        }
        DateField("打卡 / 补卡日期",date.toString()) { date=LocalDate.parse(it) }
        Text(if(habit.numeric) "当前 ${compactNumber(Rules.value(habit,date,logs))} / ${compactNumber(habit.target)} ${habit.unit}" else if(todayDone) "这一天已完成" else "这一天尚未完成")
        if(habit.numeric) Field("本次增加（${habit.unit}）",value,{value=it})
        Button(onClick={
            val number=if(habit.numeric) value.toDoubleOrNull() else 1.0
            error=when { date>LocalDate.now()->"不能提前打卡"; date<LocalDate.parse(habit.createdDate)->"不能早于开始日期"; number==null || number<=0 || !number.isFinite()->"请输入有效数值"; else->null }
            if(error==null) vm.run("记录已保存") { vm.repo.logHabit(habit,date,number!!) }
        },enabled=habit.numeric || !todayDone,modifier=Modifier.fillMaxWidth()) { Text(if(date==LocalDate.now()) "记录打卡" else "补录这一天") }
        ErrorText(error)
        SectionTitle("记录明细","点击右侧删除可修正误记")
        if(logs.isEmpty()) Text("从今天开始，慢慢积累。",color=MaterialTheme.colorScheme.onSurfaceVariant)
        logs.sortedByDescending { it.recordedAt }.take(100).forEach { log ->
            Row(verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(log.date+if(log.backfill) " · 补录" else ""); Text("+ ${compactNumber(log.value)} ${if(habit.numeric) habit.unit else "次"}",style=MaterialTheme.typography.bodySmall) }; IconButton(onClick={vm.run { vm.repo.removeLog(log.id) }}) { Icon(Icons.Rounded.DeleteOutline,"删除这条打卡") } }
        }
    }
}

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package cn.suixinji.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cn.suixinji.app.data.*
import cn.suixinji.app.domain.*
import cn.suixinji.app.platform.WeekTable
import kotlinx.coroutines.*
import java.io.File
import java.time.LocalDate

@Composable fun FullPage(title:String,onClose:()->Unit,actions:@Composable RowScope.()->Unit={},content:@Composable (PaddingValues)->Unit) {
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            Scaffold(topBar={TopAppBar(title={Text(title)},navigationIcon={IconButton(onClick=onClose){Icon(Icons.Rounded.Close,"关闭")}},actions=actions)},content=content)
        }
    }
}
@Composable fun Timetable(vm:AppViewModel,data:AppData,onClose:()->Unit,onCreate:(Term)->Unit,onItem:(AgendaItem)->Unit,onTerms:()->Unit) {
    var selected by rememberSaveable {mutableStateOf(vm.tableTerm ?: data.terms.filter {LocalDate.now()>=LocalDate.parse(it.startDate) && Rules.termWeek(it,LocalDate.now())<=it.weeks}.maxByOrNull {it.startDate}?.id ?: data.terms.maxByOrNull {it.startDate}?.id)}
    val term=data.terms.find {it.id==selected} ?: data.terms.firstOrNull()
    var week by rememberSaveable(term?.id) {mutableIntStateOf(vm.tableWeeks[term?.id] ?: term?.let {Rules.termWeek(it,LocalDate.now()).coerceIn(1,it.weeks)} ?: 1)}
    var importing by remember {mutableStateOf(false)};var sharing by remember {mutableStateOf(false)}
    SideEffect {vm.tableTerm=term?.id;term?.let {vm.tableWeeks[it.id]=week}}
    FullPage("课程表",onClose,actions={
        if(term!=null){IconButton(onClick={importing=true}){Icon(Icons.Rounded.FileDownload,"导入课程")};IconButton(onClick={sharing=true}){Icon(Icons.Rounded.Share,"分享课表")};IconButton(onClick={onCreate(term)}){Icon(Icons.Rounded.Add,"添加课程")}}
        IconButton(onClick=onTerms){Icon(Icons.Rounded.Settings,"管理学期与节次")}
    }) {padding->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal=5.dp)) {
            if(term==null)EmptyState(Icons.Rounded.School,"先设置一个学期","设置开学日期、教学周和节次时间后即可导入或添加课程。","管理学期",onTerms)
            else {
                Box(Modifier.padding(horizontal=8.dp)){Choices(data.terms.map {it.title},data.terms.indexOf(term)){selected=data.terms[it].id}}
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
                    IconButton(onClick={week=(week-1).coerceAtLeast(1)},enabled=week>1){Icon(Icons.Rounded.ChevronLeft,"上一教学周")}
                    Column(horizontalAlignment=Alignment.CenterHorizontally){Text("第 $week 周",fontWeight=FontWeight.SemiBold);Text("${term.slots.size} 节 / 天 · 共 ${term.weeks} 周",style=MaterialTheme.typography.labelSmall)}
                    IconButton(onClick={week=(week+1).coerceAtMost(term.weeks)},enabled=week<term.weeks){Icon(Icons.Rounded.ChevronRight,"下一教学周")}
                }
                val monday=Rules.monday(LocalDate.parse(term.startDate)).plusWeeks((week-1).toLong())
                Row(Modifier.fillMaxWidth()){
                    Spacer(Modifier.width(36.dp))
                    repeat(7){day->val date=monday.plusDays(day.toLong());Column(Modifier.weight(1f).padding(vertical=9.dp),horizontalAlignment=Alignment.CenterHorizontally){Text("周${weekdayNames[day]}",style=MaterialTheme.typography.labelMedium);Text("${date.monthValue}/${date.dayOfMonth}",style=MaterialTheme.typography.labelSmall)}}
                }
                Row(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("week-grid")){
                    Column(Modifier.width(36.dp)){term.slots.forEachIndexed {i,t->Column(Modifier.height(82.dp),verticalArrangement=Arrangement.Center){Text("${i+1}",fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.labelMedium);Text(t.start,fontSize=9.sp);Text(t.end,fontSize=9.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}
                    repeat(7){day->val date=monday.plusDays(day.toLong());val placed=WeekTable.items(data,term,date)
                        BoxWithConstraints(Modifier.weight(1f).height(82.dp*term.slots.size).padding(horizontal=1.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.35f),RoundedCornerShape(6.dp))){
                            val dayWidth=maxWidth
                            term.slots.indices.forEach {i->HorizontalDivider(Modifier.offset(y=82.dp*i),color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.4f))}
                            placed.forEach {p->val c=p.item.course!!;val width=dayWidth/p.lanes
                                Surface(Modifier.offset(x=width*p.lane,y=82.dp*(c.fromSlot-1)).width(width).height(82.dp*(c.toSlot-c.fromSlot+1)-3.dp).clickable {onItem(p.item)},color=accents[Math.floorMod(c.color,accents.size)].copy(alpha=.2f),shape=RoundedCornerShape(6.dp)){
                                    Column(Modifier.padding(3.dp)){Text(c.title,fontSize=11.sp,lineHeight=15.sp,fontWeight=FontWeight.SemiBold,maxLines=if(c.fromSlot==c.toSlot)3 else 5,overflow=TextOverflow.Ellipsis);if(c.room.isNotBlank())Text(c.room,fontSize=9.sp,lineHeight=12.sp,maxLines=2,overflow=TextOverflow.Ellipsis)}
                                }
                            }
                        }
                    }
                }
                Text("点击课程查看详情；课程不自动随法定调休变动",Modifier.fillMaxWidth().padding(vertical=7.dp),style=MaterialTheme.typography.labelSmall,textAlign=TextAlign.Center,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if(importing && term!=null)CourseImportDialog(vm,data,term,{importing=false})
    if(sharing && term!=null)TimetableShare(vm,data,term,week,{sharing=false})
}

@Composable fun CourseImportDialog(vm:AppViewModel,data:AppData,term:Term,onClose:()->Unit) {
    var preview by remember {mutableStateOf<CoursePreview?>(null)};var error by remember {mutableStateOf<String?>(null)}
    var busy by remember {mutableStateOf(false)};var replace by remember {mutableStateOf(false)};var confirm by remember {mutableStateOf(false)};var web by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope();val context=LocalContext.current
    fun parse(text:String,isHtml:Boolean){preview=runCatching {if(isHtml)CourseImport.html(text,term)else CourseImport.csv(text,term)}.onFailure {error=it.message}.getOrNull()}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null){busy=true;error=null;preview=null;scope.launch {try {val text=withContext(Dispatchers.IO){context.contentResolver.openInputStream(uri).use {input->requireNotNull(input);val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(16384);while(true){val n=input.read(buffer);if(n<0)break;require(out.size()+n<=2*1024*1024){"文件超过 2 MB"};out.write(buffer,0,n)};CourseImport.decode(out.toByteArray())}};parse(text,text.contains(Regex("(?i)<(?:html|table|!doctype|iframe)")))}catch(e:Exception){error=e.message}finally{busy=false}}}}
    val template=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")){uri->if(uri!=null)vm.run("模板已保存"){context.contentResolver.openOutputStream(uri,"wt").use {out->requireNotNull(out);out.write(CourseImport.template.toByteArray())}}}
    fun commit(){busy=true;scope.launch {try {val count=withContext(Dispatchers.IO){vm.repo.importCourses(term.id,preview!!.courses,replace)};vm.message("已导入 $count 条课程，其余完全重复项已跳过");onClose()}catch(e:Exception){error=e.message}finally{busy=false}}}
    FormDialog("导入课程",{if(!busy)onClose()}) {
        Text("目标学期：${term.title}",fontWeight=FontWeight.SemiBold)
        Text("${term.startDate} 开学 · ${term.weeks} 周 · ${term.slots.size} 节 / 天。导入不改变这些设置，请先确认与学校一致。",style=MaterialTheme.typography.bodySmall)
        Button(onClick={picker.launch(arrayOf("*/*"))},Modifier.fillMaxWidth(),enabled=!busy){Text("选择 CSV / 苏大课表 HTML")}
        OutlinedButton(onClick={web=true},Modifier.fillMaxWidth(),enabled=!busy){Text("苏州大学教务导入")}
        TextButton(onClick={template.launch("随心记课程导入模板.csv")},enabled=!busy){Text("保存 CSV 模板")}
        Text("Excel/WPS 请另存为 CSV；支持 UTF-8、GB18030。周数示例：1-16、1-16(单)、1-4,8-12。HTML 需选实际课表页面，不能只选门户外壳。",style=MaterialTheme.typography.bodySmall)
        preview?.let {p->
            SectionTitle("识别到 ${p.courses.size} 条课程")
            p.notes.forEach {Text(it,style=MaterialTheme.typography.bodySmall)}
            p.errors.forEach {ErrorText(it)}
            val existing=data.courses.filter {it.termId==term.id}.map(CourseImport::key).toSet();val duplicate=p.courses.count {CourseImport.key(it) in existing}
            Text("已有完全重复项 $duplicate 条（追加时跳过）",style=MaterialTheme.typography.bodySmall)
            val conflicts=p.courses.indices.any {i->val c=p.courses[i];(p.courses.take(i)+data.courses.filter {it.termId==term.id && !replace}).any {other->c.weekday==other.weekday && c.fromSlot<=other.toSlot && other.fromSlot<=c.toSlot && c.weeks.any {it in other.weeks} && CourseImport.key(c)!=CourseImport.key(other)}}
            if(conflicts)Text("部分上课时间有重叠，仍可导入，请核对预览。",color=MaterialTheme.colorScheme.secondary)
            p.courses.forEach {c->PaperCard {Text(c.title,fontWeight=FontWeight.SemiBold);Text("周${weekdayNames[c.weekday-1]} · 第 ${c.fromSlot}–${c.toSlot} 节 · ${c.weeks.joinToString(",")} 周",style=MaterialTheme.typography.bodySmall);Text(listOf(c.teacher,c.room).filter {it.isNotBlank()}.joinToString(" · "),style=MaterialTheme.typography.bodySmall)}}
            ToggleRow("替换该学期全部课程","关闭时追加；开启后原课程进入回收站",replace,{replace=it})
            Button(onClick={if(replace)confirm=true else commit()},Modifier.fillMaxWidth(),enabled=!busy && p.errors.isEmpty() && p.courses.isNotEmpty()){Text(if(replace)"确认替换导入" else "确认追加导入")}
            if(p.errors.isNotEmpty())Text("请修正所有错误后重新选择文件，未写入任何课程。",style=MaterialTheme.typography.bodySmall)
        }
        if(busy)CircularProgressIndicator();ErrorText(error)
    }
    if(confirm)ConfirmDialog("替换这个学期的课程？","原课程及单次调整将进入 30 天回收站，再写入此次课程；其他学期不受影响。",{confirm=false},{confirm=false;commit()},"替换导入")
    if(web)SchoolImport(vm,term,{web=false}) {html->web=false;error=null;parse(html,true)}
}
@Composable fun TimetableShare(vm:AppViewModel,data:AppData,term:Term,week:Int,onClose:()->Unit) {
    var teachers by remember {mutableStateOf(false)};var file by remember {mutableStateOf<File?>(null)};var busy by remember {mutableStateOf(false)};var error by remember {mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope();val context=LocalContext.current
    LaunchedEffect(teachers){busy=true;error=null;try {file=withContext(Dispatchers.IO){WeekTable.render(context,data,term,week,teachers)}}catch(e:Exception){error=e.message}finally{busy=false}}
    FormDialog("分享第 $week 周课表",onClose) {
        ToggleRow("显示教师姓名","默认仅显示课程与教室",teachers,{teachers=it})
        Text("完整导出周一至周日和全部 ${term.slots.size} 节，不含个人头像、昵称及签名。",style=MaterialTheme.typography.bodySmall)
        if(busy)CircularProgressIndicator() else file?.let {image->
            LocalImage(image,Modifier.fillMaxWidth().height((140+term.slots.size*40).dp),description="完整课表预览",scale=androidx.compose.ui.layout.ContentScale.Fit,sample=1)
            Button(onClick={runCatching{WeekTable.share(context,image)}.onFailure{error=it.message}},Modifier.fillMaxWidth()){Text("分享 PNG 图片")}
            OutlinedButton(onClick={scope.launch {try {withContext(Dispatchers.IO){WeekTable.save(context,image)};vm.message("完整课表已保存到相册")}catch(e:Exception){error=e.message}}},Modifier.fillMaxWidth()){Text("保存到相册")}
        };ErrorText(error)
    }
}

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package cn.suixinji.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as SystemSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.suixinji.app.LockSession
import cn.suixinji.app.MainActivity
import cn.suixinji.app.data.*
import cn.suixinji.app.domain.Rules
import java.time.LocalDate
import kotlinx.coroutines.launch

sealed interface Overlay {
    data class EditEvent(val event:Event,val instance:AgendaItem?=null):Overlay
    data class EditDiary(val diary:Diary):Overlay
    data class EditHabit(val habit:Habit):Overlay
    data class HabitInfo(val id:String):Overlay
    data class EditCourse(val course:Course,val instance:AgendaItem?=null):Overlay
    data class EditTerm(val term:Term):Overlay
    data class Item(val item:AgendaItem):Overlay
    data object Terms:Overlay
    data object Table:Overlay
    data object FocusOptions:Overlay
    data object Notifications:Overlay
    data object Backups:Overlay
    data object Recycle:Overlay
    data object HolidayInfo:Overlay
    data object About:Overlay
    data object Profile:Overlay
    data object Updates:Overlay
}

@Composable fun SuixinjiRoot(activity:MainActivity,vm:AppViewModel=viewModel()) {
    val data by vm.repo.state.collectAsStateWithLifecycle()
    val ready by vm.repo.ready.collectAsStateWithLifecycle()
    var lockEnabled by remember {mutableStateOf(vm.repo.device.getBoolean("app_lock",false))}
    val locked=lockEnabled && !LockSession.unlocked
    var authError by remember {mutableStateOf<String?>(null)}
    val dark=data.settings.theme=="dark" || data.settings.theme=="system" && isSystemInDarkTheme()
    SideEffect {WindowCompat.getInsetsController(activity.window,activity.window.decorView).apply {isAppearanceLightStatusBars=!dark;isAppearanceLightNavigationBars=!dark}}
    AppTheme(data.settings.theme) {
        if(locked) {
            Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().padding(32.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Lock,null,Modifier.size(56.dp),tint=MaterialTheme.colorScheme.primary); Spacer(Modifier.height(22.dp))
                    Text("随心记",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.SemiBold); Spacer(Modifier.height(10.dp)); Text("日常与心事，妥善收藏",color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(28.dp)); Button(onClick={activity.authenticate { _,message->authError=message }}) {Icon(Icons.Rounded.Fingerprint,null);Spacer(Modifier.width(8.dp));Text("验证并打开")}; ErrorText(authError)
                }
            }
            LaunchedEffect(Unit) {activity.authenticate {_,message->authError=message}}
        } else if(!ready) Surface(Modifier.fillMaxSize()) {Box(contentAlignment=Alignment.Center){CircularProgressIndicator()}}
        else {
            val pager=rememberPagerState(pageCount={5}); val scope=rememberCoroutineScope(); val navigate:(Int)->Unit={scope.launch {pager.animateScrollToPage(it)}}; val nav=pager.currentPage; var recordTab by remember {mutableIntStateOf(0)}
            var selectedDate by remember {mutableStateOf(LocalDate.now())}; var focusEvent by remember {mutableStateOf<Event?>(null)}
            val overlays=remember {mutableStateListOf<Overlay>()}; val snackbar=remember {SnackbarHostState()}
            var delete by remember {mutableStateOf<Pair<String,String>?>(null)}
            val request by activity.requests.collectAsStateWithLifecycle()
            val push:(Overlay)->Unit={overlays.add(it)}
            val close:()->Unit={if(overlays.isNotEmpty()) overlays.removeAt(overlays.lastIndex)}
            val notificationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted->vm.message(if(granted) "通知已开启" else "记录功能照常，通知未开启")}
            LaunchedEffect(Unit) {vm.messageFlow.collect {snackbar.showSnackbar(it)}}
            LaunchedEffect(request) {
                request?.let { r ->
                    overlays.clear()
                    when(r.kind) {
                        "timer"->navigate(3)
                        "habit"->{navigate(2);recordTab=1;if(data.habits.any {it.id==r.owner}) push(Overlay.HabitInfo(r.owner))}
                        else->{navigate(1);selectedDate=runCatching {LocalDate.parse(r.date)}.getOrDefault(LocalDate.now());Rules.agenda(data,selectedDate).find {it.ownerId==r.owner}?.let {push(Overlay.Item(it))}}
                    }
                    activity.requests.value=null
                }
            }
            // A permission prompt appears only after the user creates an item that requests reminders.
            val hasReminder=data.events.any {it.reminder>=0} || data.courses.any {it.reminder>=0} || data.habits.any {it.reminderTime!=null} || data.timer!=null
            LaunchedEffect(hasReminder) {
                if(hasReminder && Build.VERSION.SDK_INT>=33 && !vm.repo.device.getBoolean("asked_notifications",false)) {
                    vm.repo.device.edit().putBoolean("asked_notifications",true).apply();notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            Scaffold(containerColor=MaterialTheme.colorScheme.background,snackbarHost={SnackbarHost(snackbar)},bottomBar={
                NavigationBar(containerColor=MaterialTheme.colorScheme.surface,tonalElevation=0.dp) {
                    val icons=listOf(Icons.Rounded.WbSunny,Icons.Rounded.CalendarMonth,Icons.Rounded.MenuBook,Icons.Rounded.HourglassTop,Icons.Rounded.PersonOutline)
                    listOf("今天","日历","记录","专注","我的").forEachIndexed { i,name->NavigationBarItem(modifier=Modifier.testTag("nav-$i"),selected=nav==i,onClick={navigate(i)},icon={Icon(icons[i],name)},label={Text(name)},colors=NavigationBarItemDefaults.colors(indicatorColor=MaterialTheme.colorScheme.primaryContainer)) }
                }
            }) { padding ->
                Box(Modifier.padding(padding).fillMaxSize(),contentAlignment=Alignment.TopCenter) {
                    Box(Modifier.fillMaxSize().widthIn(max=680.dp)) {
                        HorizontalPager(state=pager,modifier=Modifier.fillMaxSize().testTag("main-pages"),userScrollEnabled=overlays.isEmpty()) { page ->
                        when(page) {
                            0->TodayScreen(vm,data,vm.app.holidays,{push(Overlay.EditEvent(Event()))},{push(Overlay.EditDiary(Diary()))},{navigate(2);recordTab=1},{push(Overlay.Item(it))},{focusEvent=it;navigate(3)},{push(Overlay.Table)},{push(Overlay.HabitInfo(it.id))})
                            1->CalendarScreen(vm,data,vm.app.holidays,selectedDate,{selectedDate=it},{push(Overlay.EditEvent(Event(date=selectedDate.toString())))},{push(Overlay.Item(it))},{push(Overlay.EditDiary(it))},{push(Overlay.HabitInfo(it.id))},{push(Overlay.Table)})
                            2->RecordsScreen(vm,data,recordTab,{recordTab=it},{push(Overlay.EditDiary(Diary()))},{push(Overlay.EditDiary(it))},{push(Overlay.EditHabit(Habit()))},{push(Overlay.HabitInfo(it.id))},{id,title->delete=id to title})
                            3->FocusScreen(vm,data,{push(Overlay.FocusOptions)},{id,title->delete=id to title},focusEvent)
                            4->SettingsScreen(vm,data,lockEnabled,{enabled->
                                if(enabled) activity.authenticate {success,message->if(success) {vm.repo.device.edit().putBoolean("app_lock",true).apply();lockEnabled=true;activity.updateRecents();vm.repo.reminders.clearDelivered();vm.run {vm.repo.reminders.reschedule(vm.repo.snapshotData())}} else message?.let(vm::message)}
                                else activity.authenticate {success,message->if(success){vm.repo.device.edit().putBoolean("app_lock",false).apply();lockEnabled=false;activity.updateRecents()}else message?.let(vm::message)}
                            },{LockSession.unlocked=false},push)
                        }
                    }
                }
            }
            }
            when(val overlay=overlays.lastOrNull()) {
                is Overlay.EditEvent->EventEditor(vm,data,overlay.event,close,overlay.instance)
                is Overlay.EditDiary->DiaryEditor(vm,overlay.diary,close)
                is Overlay.EditHabit->HabitEditor(vm,overlay.habit,close)
                is Overlay.HabitInfo->data.habits.find {it.id==overlay.id}?.let {h->HabitDetail(vm,data,h,close,{push(Overlay.EditHabit(h))})}
                is Overlay.EditTerm->TermEditor(vm,data,overlay.term,close)
                is Overlay.EditCourse->CourseEditor(vm,data,overlay.course,close,overlay.instance)
                is Overlay.Item->{
                    val item=Rules.agenda(data,LocalDate.parse(overlay.item.date)).find {it.id==overlay.item.id} ?: overlay.item
                    AgendaDetails(vm,data,item,close,{whole->if(item.kind=="event") {val e=if(whole) data.events.find {it.id==item.ownerId}!! else item.event!!.copy(date=item.date);push(Overlay.EditEvent(e,if(whole)null else item))}else {val c=if(whole)data.courses.find{it.id==item.ownerId}!! else item.course!!;push(Overlay.EditCourse(c,if(whole)null else item))}},{focusEvent=item.event;overlays.clear();navigate(3)},{push(Overlay.EditCourse(it.copy(id=newId())))})
                }
                Overlay.Terms->TermManager(vm,data,close,{push(Overlay.EditTerm(it))})
                Overlay.Table->Timetable(vm,data,close,{term->
                    push(Overlay.EditCourse(Course(termId=term.id,weeks=(1..term.weeks).toList(),toSlot=minOf(2,term.slots.size))))
                },{push(Overlay.Item(it))},{push(Overlay.Terms)})
                Overlay.FocusOptions->FocusSettings(vm,data.settings,close)
                Overlay.Notifications->NotificationSettings(activity,vm,data,close,{if(Build.VERSION.SDK_INT>=33)notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)else activity.startActivity(Intent(SystemSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(SystemSettings.EXTRA_APP_PACKAGE,activity.packageName))})
                Overlay.Backups->BackupDialog(vm,data,close)
                Overlay.Recycle->RecycleDialog(vm,data,close)
                Overlay.HolidayInfo->HolidayDialog(vm,close)
                Overlay.About->AboutDialog(vm,close)
                Overlay.Profile->ProfileEditor(vm,data.settings,close)
                Overlay.Updates->UpdateDialog(vm,close)
                null->Unit
            }
            val updateState by vm.app.updates.state.collectAsStateWithLifecycle()
            if(updateState.automaticPrompt && overlays.isEmpty()) AlertDialog(onDismissRequest={vm.app.updates.dismiss()},title={Text("发现新版本 ${updateState.release?.tag}")},text={Text("可查看更新说明后选择下载，现有数据将保留。")},confirmButton={TextButton(onClick={vm.app.updates.dismiss();push(Overlay.Updates)}){Text("查看更新")}},dismissButton={TextButton(onClick={vm.app.updates.dismiss()}){Text("稍后")}})
            delete?.let {(id,title)->ConfirmDialog("移入回收站？","$title 将保留 30 天，期间可以恢复。",{delete=null},{vm.run("已移入回收站"){vm.repo.trash(id)};delete=null},"移入回收站")}
        }
    }
}

@Composable fun AgendaDetails(vm:AppViewModel,data:AppData,item:AgendaItem,onClose:()->Unit,onEdit:(Boolean)->Unit,onFocus:()->Unit,onCopy:(Course)->Unit) {
    var deleting by remember {mutableStateOf(false)}; var cancelled by remember {mutableStateOf(false)}
    val event=data.events.find {it.id==item.ownerId}
    val repeated=item.kind=="course" || event?.repeat!=Repeat.NONE
    FormDialog(item.title,onClose) {
        Text("${item.date} · ${item.start ?: "全天"}${item.end?.let {"–$it"}.orEmpty()}",style=MaterialTheme.typography.titleMedium)
        Text(item.subtitle,color=MaterialTheme.colorScheme.secondary)
        item.event?.note?.takeIf {it.isNotBlank()}?.let {Text(it)}
        if(item.kind=="event") {
            ToggleRow("已完成",checked=item.complete,onChange={vm.run {vm.repo.toggleComplete(item)}})
            Button(onClick=onFocus,Modifier.fillMaxWidth()) {Icon(Icons.Rounded.PlayArrow,null);Text("为这件事专注")}
        }
        if(repeated) {
            OutlinedButton(onClick={onEdit(false)},Modifier.fillMaxWidth()) {Text("仅修改这一次")}
            OutlinedButton(onClick={onEdit(true)},Modifier.fillMaxWidth()) {Text("编辑整个系列")}
            TextButton(onClick={cancelled=true}) {Text("取消这一次",color=MaterialTheme.colorScheme.error)}
        } else OutlinedButton(onClick={onEdit(true)},Modifier.fillMaxWidth()) {Text("编辑日程")}
        item.course?.let {course->OutlinedButton(onClick={onCopy(course)},Modifier.fillMaxWidth()){Icon(Icons.Rounded.ContentCopy,null,Modifier.size(18.dp));Text(" 复制这门课程")}}
        TextButton(onClick={deleting=true}) {Text(if(repeated) "删除整个系列" else "删除日程",color=MaterialTheme.colorScheme.error)}
        val exceptions=data.occurrences.filter {it.ownerId==item.ownerId && (it.cancelled || it.replacement!=null)}
        if(exceptions.isNotEmpty()) {
            SectionTitle("这个系列的临时调整","可撤销取消或调课，恢复原安排")
            exceptions.sortedByDescending{it.date}.forEach {ex->
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("${ex.date} · ${if(ex.cancelled) "已取消" else if(ex.movedDate!=null) "移至 ${ex.movedDate}" else "已修改"}",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
                    TextButton(onClick={vm.run("已恢复原安排"){vm.repo.resetException(ex.id)}}){Text("恢复")}
                }
            }
        }
    }
    if(deleting) ConfirmDialog("移入回收站？","${if(repeated) "整个系列及相关例外" else "这条日程"}将保留 30 天。",{deleting=false},{vm.run("已移入回收站"){vm.repo.trash(item.ownerId)};deleting=false;onClose()})
    if(cancelled) ConfirmDialog("取消这一次？","保留其他日期的安排。之后可打开同系列的安排，在“临时调整”中恢复。",{cancelled=false},{vm.run("本次安排已取消"){if(item.kind=="event")vm.repo.eventException(item,cancel=true)else vm.repo.courseException(item,cancel=true)};cancelled=false;onClose()},"取消本次安排")
}

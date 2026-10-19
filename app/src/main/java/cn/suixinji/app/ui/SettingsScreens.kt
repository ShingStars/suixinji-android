package cn.suixinji.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as SystemSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cn.suixinji.app.MainActivity
import cn.suixinji.app.data.*
import kotlinx.coroutines.*
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun SettingsScreen(vm:AppViewModel,data:AppData,lockEnabled:Boolean,onLockChange:(Boolean)->Unit,onLock:()->Unit,push:(Overlay)->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        PageTitle("照顾日常，也照顾自己","我的随心记")
        PaperCard {
            Row(verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Rounded.AutoStories,null,Modifier.size(35.dp),tint=MaterialTheme.colorScheme.primary); Column(Modifier.padding(start=15.dp)) { Text("生活的记录者",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold); Text("${data.diaries.size} 篇日记 · ${data.habits.size} 个习惯",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) } }
        }
        PaperCard {
            Text("外观",style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold)
            Choices(listOf("跟随系统","浅色","深色"),listOf("system","light","dark").indexOf(data.settings.theme)) {i->vm.run {vm.repo.saveSettings(data.settings.copy(theme=listOf("system","light","dark")[i]))}}
        }
        PaperCard {
            MenuRow(Icons.Rounded.School,"学期管理","开学日期、教学周与节次时间") {push(Overlay.Terms)}
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            MenuRow(Icons.Rounded.NotificationsNone,"提醒设置","权限状态与各模块开关") {push(Overlay.Notifications)}
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            MenuRow(Icons.Rounded.CalendarMonth,"节假日数据","离线可用，最多每七天检查更新") {push(Overlay.HolidayInfo)}
        }
        PaperCard {
            ToggleRow("应用锁","每次启动验证；后台返回不重复验证",lockEnabled,onLockChange)
            if(lockEnabled) { Text("允许截图和录屏；Android 10–12 的最近任务预览可能显示内容。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant); TextButton(onClick=onLock) {Icon(Icons.Rounded.Lock,null,Modifier.size(18.dp));Text(" 立即锁定")} }
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            MenuRow(Icons.Rounded.Backup,"备份与恢复","密码加密，换机时完整恢复") {push(Overlay.Backups)}
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            MenuRow(Icons.Rounded.DeleteOutline,"回收站","${data.trashed.size} 项 · 保留 30 天") {push(Overlay.Recycle)}
        }
        PaperCard {MenuRow(Icons.Rounded.Info,"关于随心记","1.0.0 · 数据留在你的设备里") {push(Overlay.About)}}
        Text("不必把每一天都填满。",Modifier.fillMaxWidth().padding(vertical=16.dp),textAlign=androidx.compose.ui.text.style.TextAlign.Center,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable fun NotificationSettings(activity:MainActivity,vm:AppViewModel,data:AppData,onClose:()->Unit,onPermission:()->Unit) {
    var tick by remember {mutableIntStateOf(0)}
    LaunchedEffect(Unit) {while(true){delay(1000);tick++}}
    val notifications=remember(tick){vm.repo.reminders.notificationsAllowed()};val exact=remember(tick){vm.repo.reminders.exactAllowed()}
    FormDialog("提醒设置",onClose) {
        PaperCard {
            Text(if(notifications) "通知已开启" else "提醒不可用：通知未开启",style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary)
            TextButton(onClick={activity.startActivity(Intent(SystemSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(SystemSettings.EXTRA_APP_PACKAGE,activity.packageName))}) {Text("打开系统通知设置")}
            if(!notifications) OutlinedButton(onClick=onPermission) {Text("申请通知权限")}
            HorizontalDivider()
            Text(if(exact) "准时提醒权限已开启" else "可能延迟：准时提醒权限未开启",Modifier.padding(top=12.dp),style=MaterialTheme.typography.titleSmall)
            if(Build.VERSION.SDK_INT>=31 && !exact) TextButton(onClick={runCatching {activity.startActivity(Intent(SystemSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:${activity.packageName}")))}.onFailure{vm.message("请在系统设置中允许闹钟与提醒")}}) {Text("开启准时提醒")}
        }
        fun save(s:Settings) {vm.run {vm.repo.saveSettings(s)}}
        ToggleRow("日程提醒",checked=data.settings.eventNotifications,onChange={save(data.settings.copy(eventNotifications=it))})
        ToggleRow("课程提醒",checked=data.settings.courseNotifications,onChange={save(data.settings.copy(courseNotifications=it))})
        ToggleRow("习惯提醒",checked=data.settings.habitNotifications,onChange={save(data.settings.copy(habitNotifications=it))})
        ToggleRow("番茄钟通知与结束提醒",checked=data.settings.focusNotifications,onChange={save(data.settings.copy(focusNotifications=it))})
        Text("声音和振动还受系统通知渠道、勿扰模式与省电策略影响。关闭所有权限仍能保存记录和使用计时器。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text("系统强制停止应用后，请重新打开应用，以恢复提醒调度。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable fun BackupDialog(vm:AppViewModel,data:AppData,onClose:()->Unit) {
    var password by remember {mutableStateOf("")};var confirmation by remember {mutableStateOf("")};var error by remember {mutableStateOf<String?>(null)}
    var busy by remember {mutableStateOf(false)};var restoreConfirm by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    fun action(success:String,block:suspend ()->Unit) {busy=true;scope.launch {try {withContext(Dispatchers.IO){block()};vm.message(success);password="";confirmation=""}catch(e:Exception){error=if(e is javax.crypto.BadPaddingException) "密码错误或文件已损坏，现有数据未改变" else e.message?.take(160) ?: "操作失败"}finally{busy=false}}}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {uri->if(uri!=null){val chars=password.toCharArray();action("完整加密备份已导出"){vm.backup.export(uri,chars)}}}
    val restore=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->if(uri!=null){val chars=password.toCharArray();action("恢复成功，恢复前副本已保留"){vm.backup.restore(uri,chars)}}}
    val safety=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {uri->if(uri!=null)action("恢复前副本已导出"){vm.backup.exportRecovery(uri)}}
    FormDialog("备份与恢复",{if(!busy)onClose()}) {
        Text("给记录留一份安心的副本",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
        Text("备份包含所有记录、日记图片、普通设置和未过期的回收站内容。应用锁与系统凭据不会迁移。",style=MaterialTheme.typography.bodyMedium)
        OutlinedTextField(password,{password=it},Modifier.fillMaxWidth(),label={Text("备份密码（至少 8 位）")},singleLine=true,visualTransformation=PasswordVisualTransformation(),enabled=!busy)
        OutlinedTextField(confirmation,{confirmation=it},Modifier.fillMaxWidth(),label={Text("导出时再次输入密码")},singleLine=true,visualTransformation=PasswordVisualTransformation(),enabled=!busy)
        Text("密码无法找回，请妥善保存。恢复已有备份时只需填写第一项密码。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.secondary)
        Button(onClick={error=when{password.length<8->"密码至少需要 8 位";password!=confirmation->"两次密码不一致";else->null};if(error==null)export.launch("随心记_${today()}.sxj")},Modifier.fillMaxWidth(),enabled=!busy) {Icon(Icons.Rounded.FileUpload,null);Spacer(Modifier.width(8.dp));Text("导出完整加密备份")}
        OutlinedButton(onClick={error=when{password.length<8->"请填写备份密码";data.timer!=null->"请先结束当前计时，再恢复数据";else->null};if(error==null)restoreConfirm=true},Modifier.fillMaxWidth(),enabled=!busy) {Icon(Icons.Rounded.FileDownload,null);Spacer(Modifier.width(8.dp));Text("从备份完整恢复")}
        if(vm.backup.recovery.exists()) TextButton(onClick={safety.launch("随心记_恢复前副本_${today()}.sxj")},enabled=!busy) {Text("导出最近一次恢复前副本")}
        Text("恢复会完整替换现有数据，不合并。恢复前副本使用本次恢复所填的密码加密；新备份校验失败时不会替换现有记录。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(busy) Row(verticalAlignment=Alignment.CenterVertically) {CircularProgressIndicator(Modifier.size(22.dp));Spacer(Modifier.width(12.dp));Text("正在处理，请稍候…")}
        ErrorText(error)
    }
    if(restoreConfirm) ConfirmDialog("完整替换当前数据？","将先校验备份并保存当前数据的安全副本。请确认选择的是你要恢复的备份。",{restoreConfirm=false},{restoreConfirm=false;restore.launch(arrayOf("*/*"))},"选择备份并恢复")
}

@Composable fun RecycleDialog(vm:AppViewModel,data:AppData,onClose:()->Unit) {
    var removing by remember {mutableStateOf<RecordEntity?>(null)};var clear by remember {mutableStateOf(false)}
    FormDialog("回收站",onClose) {
        Text("删除后保留 30 天。恢复习惯或学期时，会同时恢复一并删除的子记录。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(data.trashed.isEmpty()) EmptyState(Icons.Rounded.DeleteOutline,"回收站是空的","误删的记录可以在这里找回。")
        else TextButton(onClick={clear=true}) {Text("清空回收站",color=MaterialTheme.colorScheme.error)}
        data.trashed.sortedByDescending{it.deletedAt}.forEach {row->
            val title=runCatching {when(row.kind){"event"->codec.decodeFromString<Event>(row.payload).title;"diary"->codec.decodeFromString<Diary>(row.payload).title.ifBlank{"未命名日记"};"habit"->codec.decodeFromString<Habit>(row.payload).title;"term"->codec.decodeFromString<Term>(row.payload).title;"course"->codec.decodeFromString<Course>(row.payload).title;else->codec.decodeFromString<FocusSession>(row.payload).title}}.getOrDefault("记录")
            val days=(30-((System.currentTimeMillis()-row.deletedAt!!)/86400000)).coerceAtLeast(0)
            PaperCard {Text(title,fontWeight=FontWeight.SemiBold);Text("约 $days 天后永久删除",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Row {TextButton(onClick={vm.run("记录已恢复"){vm.repo.restore(row.id)}}){Text("恢复")};TextButton(onClick={removing=row}){Text("永久删除",color=MaterialTheme.colorScheme.error)}}}
        }
    }
    removing?.let {row->ConfirmDialog("永久删除？","此操作无法撤销，只能从之前导出的备份中找回。",{removing=null},{vm.run("已永久删除"){vm.repo.permanentlyDelete(row.id)};removing=null},"永久删除")}
    if(clear) ConfirmDialog("清空回收站？","所有已删除内容将永久清除。",{clear=false},{val ids=data.trashed.map{it.id};vm.run("回收站已清空"){ids.forEach{vm.repo.permanentlyDelete(it)}};clear=false},"永久清空")
}

@Composable fun HolidayDialog(vm:AppViewModel,onClose:()->Unit) {
    val h=vm.app.holidays;val status by h.status.collectAsState();val revision by h.revision.collectAsState();val context=LocalContext.current
    FormDialog("节假日数据",onClose) {
        Text("法定假期与调休",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
        Text("数据依据国务院公布的年度安排，由 NateScarlet/holiday-cn 项目整理。第三方数据源不是官方 API。",style=MaterialTheme.typography.bodyMedium)
        val last=remember(revision){h.lastUpdated()}
        Text(if(last==0L) "当前使用安装包内置数据" else "最后成功更新："+Instant.ofEpochMilli(last).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),style=MaterialTheme.typography.bodySmall)
        Text(status,color=MaterialTheme.colorScheme.primary)
        Button(onClick={vm.run {vm.message(h.refresh())}},enabled=!status.startsWith("正在")) {Icon(Icons.Rounded.Refresh,null);Text(" 立即更新")}
        Text("仅此功能联网，不上传日记、日程、打卡或其他个人内容。每七天最多自动检查一次；更新失败继续使用本地缓存。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        for(year in LocalDate.now().year-1..LocalDate.now().year+1) {
            Text("$year 年：${if(h.available(year)) "已有安排" else "调休数据暂未获取"}",style=MaterialTheme.typography.titleSmall)
            h.sources(year).forEach {url->TextButton(onClick={runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}}){Text("查看国务院公告")}}
        }
        TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/NateScarlet/holiday-cn")))}) {Text("查看数据源与许可")}
    }
}
@Composable fun AboutDialog(vm:AppViewModel,onClose:()->Unit) {
    FormDialog("关于随心记",onClose) {
        Text("随心记 1.0.0",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold)
        Text("日常与心事，妥善收藏。")
        Text("隐私说明",fontWeight=FontWeight.SemiBold)
        Text("无需注册，无广告，无行为分析。个人记录保存在应用私有目录，系统自动备份和迁移已关闭。卸载或清除应用数据会移除本地记录，请先导出加密备份。应用锁控制访问，不替代手机的系统安全保护。")
        Text("数据与时间",fontWeight=FontWeight.SemiBold)
        Text("日历以周一为每周开始。课程按教学周运行，不自动随法定调休变动。时区变化后日程和课程保持本地钟点；历史日记、打卡和专注统计保留原记录日期。")
        Text("开源致谢",fontWeight=FontWeight.SemiBold)
        Text("Kotlin、Android Jetpack Compose、Material 3、Room、AndroidX Biometric、kotlinx.coroutines、kotlinx.serialization（Apache 2.0）；NateScarlet/holiday-cn（MIT）。")
        val license=remember {runCatching{vm.app.assets.open("holidays/LICENSE.txt").bufferedReader().use{it.readText()}}.getOrDefault("MIT License · Copyright (c) 2019 NateScarlet")}
        Text(license,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

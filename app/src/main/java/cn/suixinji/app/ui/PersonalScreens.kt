@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package cn.suixinji.app.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.net.http.SslError
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import cn.suixinji.app.BuildConfig
import cn.suixinji.app.data.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.File

fun String.limitCharacters(max:Int):String=if(codePointCount(0,length)>max)substring(0,offsetByCodePoints(0,max))else this
@Composable fun ProfileAvatar(vm:AppViewModel,name:String?,modifier:Modifier=Modifier) {
    Box(modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),contentAlignment=Alignment.Center){if(name!=null)LocalImage(File(vm.repo.attachments,name),Modifier.fillMaxSize(),description="个人头像")else Icon(Icons.Rounded.PersonOutline,"默认头像",Modifier.fillMaxSize().padding(12.dp),tint=MaterialTheme.colorScheme.primary)}
}
@Composable fun ProfileEditor(vm:AppViewModel,settings:Settings,onClose:()->Unit) {
    var nickname by remember {mutableStateOf(settings.nickname)};var signature by remember {mutableStateOf(settings.signature)};var avatar by remember {mutableStateOf(settings.avatar)}
    var bitmap by remember {mutableStateOf<Bitmap?>(null)};var zoom by remember {mutableFloatStateOf(1f)};var x by remember {mutableFloatStateOf(.5f)};var y by remember {mutableFloatStateOf(.5f)}
    var busy by remember {mutableStateOf(false)};var error by remember {mutableStateOf<String?>(null)};val scope=rememberCoroutineScope();val context=LocalContext.current
    DisposableEffect(Unit){onDispose {bitmap?.recycle()}}
    fun crop():Bitmap {val b=bitmap!!;val size=(minOf(b.width,b.height)/zoom).toInt().coerceAtLeast(1);return Bitmap.createBitmap(b,((b.width-size)*x).toInt(),((b.height-size)*y).toInt(),size,size)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()){uri->if(uri!=null){busy=true;scope.launch {try {val b=withContext(Dispatchers.IO){val options=BitmapFactory.Options().apply {inJustDecodeBounds=true};context.contentResolver.openInputStream(uri).use {BitmapFactory.decodeStream(it,null,options)};require(options.outWidth>0 && options.outHeight>0){"无法读取图片"};var sample=1;while(options.outWidth/sample>2400 || options.outHeight/sample>2400)sample*=2;context.contentResolver.openInputStream(uri).use {BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply {inSampleSize=sample})} ?: error("图片格式不支持")};bitmap?.recycle();bitmap=b;zoom=1f;x=.5f;y=.5f}catch(e:Exception){error=e.message}finally{busy=false}}}}
    FormDialog("编辑个人资料",{if(!busy)onClose()},onSave={busy=true;scope.launch {try {withContext(Dispatchers.IO){var name=avatar;bitmap?.let {val cropped=crop();try {name="${newId()}.jpg";File(vm.repo.attachments,name!!).outputStream().use {check(cropped.compress(Bitmap.CompressFormat.JPEG,90,it))}}finally {if(cropped!==bitmap)cropped.recycle()}};vm.repo.saveProfile(nickname,signature,name)};vm.message("个人资料已保存");onClose()}catch(e:Exception){error=e.message}finally{busy=false}}},enabled=!busy) {
        if(bitmap==null)ProfileAvatar(vm,avatar,Modifier.size(110.dp)) else {
            val b=bitmap!!;val size=(minOf(b.width,b.height)/zoom).toInt().coerceAtLeast(1)
            Canvas(Modifier.size(220.dp).clip(CircleShape)){drawImage(b.asImageBitmap(),srcOffset=IntOffset(((b.width-size)*x).toInt(),((b.height-size)*y).toInt()),srcSize=IntSize(size,size),dstSize=IntSize(this.size.width.toInt(),this.size.height.toInt()))}
            Text("裁剪头像",style=MaterialTheme.typography.titleSmall)
            Text("缩放");Slider(zoom,{zoom=it},valueRange=1f..3f)
            Text("水平位置");Slider(x,{x=it},valueRange=0f..1f)
            Text("垂直位置");Slider(y,{y=it},valueRange=0f..1f)
        }
        OutlinedButton(onClick={picker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},enabled=!busy){Text("从相册选择头像")}
        TextButton(onClick={bitmap?.recycle();bitmap=null;avatar=null},enabled=!busy){Text("恢复默认头像")}
        Field("昵称（最多10字）",nickname,{nickname=it.limitCharacters(10)})
        Text("${nickname.codePointCount(0,nickname.length)} / 10",style=MaterialTheme.typography.labelSmall)
        Field("个性签名（最多30字）",signature,{signature=it.limitCharacters(30)},multiline=true)
        Text("${signature.codePointCount(0,signature.length)} / 30 · 仅保存在本机，随加密备份迁移",style=MaterialTheme.typography.labelSmall)
        if(busy)CircularProgressIndicator();ErrorText(error)
    }
}

@Composable fun UpdateDialog(vm:AppViewModel,onClose:()->Unit) {
    val updates=vm.app.updates;val state by updates.state.collectAsState();val data by vm.repo.state.collectAsState();var error by remember {mutableStateOf<String?>(null)}
    FormDialog("检查更新",onClose) {
        Text("当前版本 ${BuildConfig.VERSION_NAME}",style=MaterialTheme.typography.titleMedium)
        ToggleRow("自动检查正式版","启动时每天最多检查一次；不会自动下载",data.settings.autoCheckUpdates,{enabled->vm.run {vm.repo.saveSettings(vm.repo.snapshotData().settings.copy(autoCheckUpdates=enabled))}})
        Text(state.message.ifBlank {"从 GitHub 公开仓库查询最新正式版本"})
        OutlinedButton(onClick={vm.run {updates.check()}},enabled=!state.checking && !state.downloading){Text("立即检查")}
        if(state.checking)CircularProgressIndicator()
        state.release?.let {release->
            SectionTitle("新版本 ${release.tag}")
            Text(release.body.ifBlank {"发布者未填写更新说明"},style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={runCatching {vm.app.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(release.page)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}.onFailure{error=it.message}}){Text("打开 GitHub 发布页")}
            if(state.downloading){LinearProgressIndicator(progress={state.progress/100f},modifier=Modifier.fillMaxWidth());Text("下载 ${state.progress}%")}
            else if(state.apk!=null)Button(onClick={runCatching{updates.install()}.onFailure{error=it.message}},Modifier.fillMaxWidth()){Text("安装更新")}
            else Button(onClick={vm.run {updates.download()}},Modifier.fillMaxWidth()){Text("下载更新（${"%.1f".format(release.size/1048576.0)} MB）")}
            TextButton(onClick={updates.dismiss(true);onClose()}){Text("忽略此版本")}
        }
        Text("下载后校验 SHA-256、包名、版本及签名。安装需系统确认；网络失败不影响本地功能。",style=MaterialTheme.typography.bodySmall)
        ErrorText(error)
    }
}

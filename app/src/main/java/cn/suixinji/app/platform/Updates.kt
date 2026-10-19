package cn.suixinji.app.platform

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import cn.suixinji.app.BuildConfig
import cn.suixinji.app.data.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

data class AppRelease(val tag:String,val body:String,val page:String,val apk:String,val digest:String,val size:Long)
data class UpdateState(val release:AppRelease?=null,val checking:Boolean=false,val downloading:Boolean=false,val progress:Int=0,val message:String="",val apk:File?=null,val automaticPrompt:Boolean=false)
object ReleaseRules {
    fun version(tag:String):List<Int>? = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)$").matchEntire(tag)?.groupValues?.drop(1)?.map {it.toIntOrNull() ?: return null}
    fun newer(tag:String,current:String):Boolean {val a=version(tag) ?: return false;val b=version(current) ?: return false;for(i in 0..2){if(a[i]!=b[i])return a[i]>b[i]};return false}
    fun trusted(url:String):Boolean=runCatching {val uri=URI(url);uri.scheme=="https" && uri.userInfo==null && (uri.host in setOf("api.github.com","github.com","release-assets.githubusercontent.com","objects.githubusercontent.com") || uri.host.endsWith(".githubusercontent.com"))}.getOrDefault(false)
    fun digest(value:String)=value.removePrefix("sha256:").lowercase().takeIf {it.matches(Regex("[a-f0-9]{64}"))}
}
class Updates(private val context:Context,private val repo:Repository) {
    val state=MutableStateFlow(UpdateState())
    private val checkMutex=Mutex();private val downloadMutex=Mutex()
    private val prefs=repo.device
    private val latest="https://api.github.com/repos/ShingStars/suixinji-android/releases/latest"
    private fun open(url:String):HttpURLConnection {
        var next=url
        repeat(6) {
            require(ReleaseRules.trusted(next)){"更新地址不受信任"}
            val c=URI(next).toURL().openConnection() as HttpURLConnection
            c.connectTimeout=15000;c.readTimeout=30000;c.instanceFollowRedirects=false
            c.setRequestProperty("Accept","application/vnd.github+json");c.setRequestProperty("User-Agent","Suixinji-Android/${BuildConfig.VERSION_NAME}")
            val status=c.responseCode
            if(status in 300..399){val location=c.getHeaderField("Location") ?: error("更新下载跳转地址缺失");val resolved=URI(next).resolve(location).toString();c.disconnect();next=resolved}
            else {if(status!=200){c.disconnect();error(if(status==403 || status==429) "GitHub 暂时限制请求，请稍后重试" else "更新服务暂不可用（$status）")};return c}
        };error("更新地址跳转过多")
    }
    suspend fun check(automatic:Boolean=false)=withContext(Dispatchers.IO) {checkMutex.withLock {
        if(automatic && (!repo.snapshotData().settings.autoCheckUpdates || System.currentTimeMillis()-prefs.getLong("update_attempt",0)<86400000L))return@withLock
        if(state.value.downloading)return@withLock
        prefs.edit().putLong("update_attempt",System.currentTimeMillis()).apply()
        state.value=state.value.copy(checking=true,message="正在检查正式版本…",automaticPrompt=false)
        try {
            val connection=open(latest)
            val raw=try {connection.inputStream.use {val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(true){val n=it.read(buffer);if(n<0)break;require(out.size()+n<=1024*1024){"更新响应过大"};out.write(buffer,0,n)};out.toString("UTF-8")}}finally {connection.disconnect()}
            val json=Json.parseToJsonElement(raw).jsonObject
            require(json["draft"]?.jsonPrimitive?.booleanOrNull==false && json["prerelease"]?.jsonPrimitive?.booleanOrNull==false){"没有可用正式版本"}
            val tag=json["tag_name"]!!.jsonPrimitive.content
            if(!ReleaseRules.newer(tag,BuildConfig.VERSION_NAME)){state.value=UpdateState(message="当前已是最新正式版本（${BuildConfig.VERSION_NAME}）");return@withLock}
            val asset=json["assets"]!!.jsonArray.map {it.jsonObject}.singleOrNull {it["name"]?.jsonPrimitive?.content=="Suixinji-${tag.removePrefix("v")}.apk"} ?: error("新版尚未提供匹配的安装包")
            val digest=ReleaseRules.digest(asset["digest"]?.jsonPrimitive?.content.orEmpty()) ?: error("新版缺少 SHA-256 校验信息，请前往发布页查看")
            val release=AppRelease(tag,json["body"]?.jsonPrimitive?.content.orEmpty().take(30000),"https://github.com/ShingStars/suixinji-android/releases/tag/$tag",asset["browser_download_url"]!!.jsonPrimitive.content,digest,asset["size"]!!.jsonPrimitive.long)
            require(ReleaseRules.trusted(release.apk) && release.size in 1..150L*1024*1024){"新版安装包信息异常"}
            state.value=UpdateState(release=release,message="发现新版本 $tag",automaticPrompt=automatic && prefs.getString("ignored_version","")!=tag)
        } catch(e:Exception) {state.value=UpdateState(message="检查失败：${e.message?.take(120) ?: "请检查网络"}")}
    }}
    fun dismiss(ignore:Boolean=false){if(ignore)state.value.release?.let {prefs.edit().putString("ignored_version",it.tag).apply()};state.value=state.value.copy(automaticPrompt=false)}
    suspend fun download()=withContext(Dispatchers.IO) {downloadMutex.withLock {
        val release=state.value.release ?: return@withLock
        val folder=File(context.cacheDir,"updates").apply {mkdirs()};val part=File(folder,"update.part");val apk=File(folder,"suixinji-update.apk")
        state.value=state.value.copy(downloading=true,progress=0,message="正在下载…",apk=null,automaticPrompt=false)
        try {
            val connection=open(release.apk);val digest=MessageDigest.getInstance("SHA-256");var count=0L
            try {connection.inputStream.use {input->part.outputStream().use {out->val buffer=ByteArray(32768);while(true){val n=input.read(buffer);if(n<0)break;count+=n;require(count<=release.size){"安装包大小异常"};out.write(buffer,0,n);digest.update(buffer,0,n);state.value=state.value.copy(progress=(count*100/release.size).toInt())}}}}finally {connection.disconnect()}
            require(count==release.size && digest.digest().joinToString(""){"%02x".format(it)}==release.digest){"安装包校验失败，请重新下载"}
            verifyApk(part)
            if(apk.exists())check(apk.delete());check(part.renameTo(apk))
            state.value=state.value.copy(downloading=false,apk=apk,message="下载及签名校验通过，点击安装更新")
        } catch(e:Exception){part.delete();state.value=state.value.copy(downloading=false,message="下载失败：${e.message?.take(120)}")}
    }}
    @Suppress("DEPRECATION") fun verifyApk(file:File) {
        val pm=context.packageManager
        val installed=pm.getPackageInfo(context.packageName,PackageManager.GET_SIGNING_CERTIFICATES)
        val archive=pm.getPackageArchiveInfo(file.path,PackageManager.GET_SIGNING_CERTIFICATES) ?: error("无法读取安装包")
        require(archive.packageName==context.packageName && archive.longVersionCode>installed.longVersionCode){"安装包名称或版本不匹配"}
        require((archive.applicationInfo?.minSdkVersion ?: Int.MAX_VALUE)<=android.os.Build.VERSION.SDK_INT){"新版不支持当前 Android 版本"}
        val a=installed.signingInfo?.apkContentsSigners?.map {it.toByteArray().toList()}?.toSet()
        val b=archive.signingInfo?.apkContentsSigners?.map {it.toByteArray().toList()}?.toSet()
        require(!a.isNullOrEmpty() && a==b){"安装包签名与当前应用不同，不能覆盖更新"}
    }
    fun install() {
        val apk=state.value.apk ?: error("请先下载安装包")
        if(!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            state.value=state.value.copy(message="请允许随心记安装应用，返回后再次点击安装更新");return
        }
        verifyApk(apk)
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",apk)
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
}

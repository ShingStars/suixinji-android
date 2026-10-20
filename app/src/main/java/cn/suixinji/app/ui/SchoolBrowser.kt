package cn.suixinji.app.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.net.http.SslError
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cn.suixinji.app.data.Term
import kotlinx.serialization.json.*

@SuppressLint("SetJavaScriptEnabled")
@Composable fun SchoolImport(vm:AppViewModel,term:Term,onClose:()->Unit,onRead:(String)->Unit) {
    val context=LocalContext.current
    var browser by remember {mutableStateOf<WebView?>(null)}
    var address by remember {mutableStateOf("http://xk.suda.edu.cn/")}
    var status by remember {mutableStateOf("输入学校教务网址，登录后选择学期并打开完整课表，再点击读取。支持旧/新正方、强智及课程清单式页面；请核对导入预览。")}
    var pending by remember {mutableStateOf<String?>(null)}
    var allowSudaHttp by remember {mutableStateOf(false)}
    var reading by remember {mutableStateOf(false)}
    var choices by remember {mutableStateOf<JsonArray?>(null)}
    fun valid(raw:String):Uri?=runCatching {Uri.parse(raw.trim()).takeIf {it.scheme in listOf("https","http") && !it.host.isNullOrBlank() && it.userInfo==null}}.getOrNull()
    fun open(raw:String) {
        val u=valid(raw)
        when {
            u==null->status="请输入完整的 http:// 或 https:// 教务网址"
            u.scheme=="https"->browser?.loadUrl(u.toString())
            u.host=="xk.suda.edu.cn" && allowSudaHttp->browser?.loadUrl(u.toString())
            else->pending=u.toString()
        }
    }
    DisposableEffect(Unit){onDispose {browser?.apply {stopLoading();clearHistory();clearCache(true);destroy()};CookieManager.getInstance().removeAllCookies(null);WebStorage.getInstance().deleteAllData()}}
    BackHandler {if(browser?.canGoBack()==true)browser?.goBack()else onClose()}
    FullPage("教务网页导入",onClose){padding->
        Column(Modifier.padding(padding).fillMaxSize()){
            OutlinedTextField(address,{address=it},label={Text("学校教务网址")},singleLine=true,modifier=Modifier.fillMaxWidth().padding(horizontal=12.dp))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){
                TextButton(onClick={open(address)}){Text("打开")}
                TextButton(onClick={browser?.let {if(it.canGoBack())it.goBack()}}){Text("后退")}
                TextButton(onClick={browser?.reload()}){Text("刷新")}
                Button(onClick={
                    val view=browser ?: return@Button
                    if(valid(view.url.orEmpty())==null){status="请先打开学校教务页面";return@Button}
                    reading=true
                    view.evaluateJavascript("""
                        (function(){
                          var out=[], seen=[];
                          function scan(w,depth){
                            if(depth>8 || out.length>=30)return;
                            try{
                              var d=w.document;
                              if(d.querySelector('table'))out.push({title:d.title||'课表页面',url:w.location.href,html:d.documentElement.outerHTML});
                              for(var i=0;i<w.frames.length;i++)scan(w.frames[i],depth+1);
                              var fs=d.querySelectorAll('iframe,frame');
                              for(var j=0;j<fs.length;j++){
                                try{var child=fs[j].contentDocument;if(child)continue;}catch(e){}
                                var url=fs[j].src;
                                if(url && /^https?:\/\//i.test(url) && seen.indexOf(url)<0){
                                  seen.push(url);out.push({title:'打开框架页面',url:url,html:''});
                                }
                              }
                            }catch(e){}
                          }
                          scan(window,0);
                          var result=JSON.stringify(out);
                          return result.length>2000000?'TOO_LARGE':result;
                        })()
                    """.trimIndent()){result->
                        reading=false
                        runCatching {
                            val encoded=Json.parseToJsonElement(result).jsonPrimitive.content
                            require(encoded!="TOO_LARGE"){"页面内容过大，请保存实际课表 HTML 导入"}
                            val pages=Json.parseToJsonElement(encoded).jsonArray
                            if(pages.isEmpty())status="当前页面没有表格。请进入实际课表；动态页面可尝试打印课表或导出 HTML / CSV。"
                            else choices=pages
                        }.onFailure {status=it.message ?: "读取失败，请重新打开课表或使用文件导入"}
                    }
                },enabled=!reading){Text(if(reading)"读取中"else "读取课表")}
            }
            Text(status,Modifier.padding(horizontal=12.dp,vertical=6.dp),style=MaterialTheme.typography.bodySmall)
            AndroidView(factory={ctx->WebView(ctx).apply {
                browser=this
                settings.javaScriptEnabled=true;settings.domStorageEnabled=true
                settings.allowFileAccess=false;settings.allowContentAccess=false
                settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW;settings.saveFormData=false
                CookieManager.getInstance().setAcceptThirdPartyCookies(this,false)
                webViewClient=object:WebViewClient(){
                    override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean {
                        val u=valid(request.url.toString())
                        if(u==null){status="此链接不能在教务浏览器打开";return true}
                        if(u.scheme=="http" && !(u.host=="xk.suda.edu.cn" && allowSudaHttp)){pending=u.toString();return true}
                        return false
                    }
                    override fun shouldInterceptRequest(view:WebView,request:WebResourceRequest):WebResourceResponse? {
                        val u=request.url
                        if(u.scheme=="http" && !(u.host=="xk.suda.edu.cn" && allowSudaHttp))
                            return WebResourceResponse("text/plain","UTF-8",403,"HTTP not approved",emptyMap(),java.io.ByteArrayInputStream(ByteArray(0)))
                        return null
                    }
                    override fun onPageFinished(view:WebView,url:String){address=url;status="请进入完整学期课表后读取；若是门户框架，读取后可选择实际课表页面。"}
                    override fun onReceivedSslError(view:WebView,handler:SslErrorHandler,error:SslError){handler.cancel();status="学校网站证书验证失败。请联系学校或改用已导出的 HTML / CSV。"}
                    override fun onReceivedError(view:WebView,request:WebResourceRequest,error:WebResourceError){if(request.isForMainFrame)status="网页暂不可达，请检查网址、校园网络或学校 VPN，再重试。"}
                }
            }},modifier=Modifier.weight(1f).fillMaxWidth().clipToBounds())
        }
    }
    pending?.let {url->
        val suda=Uri.parse(url).host=="xk.suda.edu.cn"
        AlertDialog(onDismissRequest={pending=null},title={Text("此教务页面使用 HTTP")},text={Text(if(suda)"苏大入口未加密，登录信息可能被网络中间方读取。请仅在可信校园网络使用；是否继续打开？" else "此网址未加密，当前仅对苏大旧教务提供应用内兼容。可在系统浏览器打开并保存实际课表 HTML 后导入。")},confirmButton={TextButton(onClick={
            pending=null
            if(suda){allowSudaHttp=true;open(url)}else runCatching {context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}.onFailure {status="没有可用的外部浏览器"}
        }){Text(if(suda)"继续打开"else "外部浏览器")}},dismissButton={TextButton(onClick={pending=null}){Text("取消")}})
    }
    choices?.let {pages->
        AlertDialog(onDismissRequest={choices=null},title={Text("选择课表所在页面")},text={
            Column(Modifier.heightIn(max=350.dp).verticalScroll(rememberScrollState())){
                pages.forEachIndexed {i,item->val p=item.jsonObject;val html=p["html"]!!.jsonPrimitive.content;val url=p["url"]!!.jsonPrimitive.content
                    TextButton(onClick={choices=null;if(html.isBlank())open(url)else onRead(html)}){
                        Text("${i+1}. ${p["title"]!!.jsonPrimitive.content.take(50)}\n${Uri.parse(url).host.orEmpty()}${if(html.isBlank())" · 先打开再读取" else ""}")
                    }
                }
            }
        },confirmButton={TextButton(onClick={choices=null}){Text("取消")}})
    }
}

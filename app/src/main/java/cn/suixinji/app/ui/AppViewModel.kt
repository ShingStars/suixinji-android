package cn.suixinji.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.suixinji.app.SuixinjiApplication
import cn.suixinji.app.data.*
import cn.suixinji.app.platform.Backup
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

class AppViewModel(application:Application):AndroidViewModel(application) {
    val app=application as SuixinjiApplication
    val repo=app.repository
    val backup=Backup(app,repo)
    val messages=Channel<String>(Channel.BUFFERED)
    val messageFlow=messages.receiveAsFlow()
    init { viewModelScope.launch { while(isActive) { delay(1000); repo.reconcileTimer() } } }
    fun run(success:String?=null,block:suspend ()->Unit) { viewModelScope.launch(Dispatchers.IO) {
        try { block(); success?.let { messages.send(it) } } catch(e:Exception) {
            if(e is CancellationException) throw e
            messages.send(if(e is javax.crypto.AEADBadTagException || e is javax.crypto.BadPaddingException) "密码错误或备份已损坏，现有数据未更改" else e.message?.take(150) ?: "操作未完成，请重试")
        }
    } }
    fun message(text:String) { messages.trySend(text) }
}

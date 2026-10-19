package cn.suixinji.app

import android.app.Application
import cn.suixinji.app.data.Repository
import cn.suixinji.app.platform.Holidays
import kotlinx.coroutines.*

class SuixinjiApplication : Application() {
    val appScope = CoroutineScope(SupervisorJob()+Dispatchers.IO)
    lateinit var repository: Repository
    lateinit var holidays: Holidays
    override fun onCreate() {
        super.onCreate()
        repository = Repository(this,appScope)
        holidays = Holidays(this)
        appScope.launch { holidays.refreshIfNeeded() }
    }
}

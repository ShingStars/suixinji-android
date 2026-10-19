package cn.suixinji.app

import android.app.Application
import cn.suixinji.app.data.Repository
import cn.suixinji.app.platform.Holidays
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class SuixinjiApplication : Application() {
    val appScope = CoroutineScope(SupervisorJob()+Dispatchers.IO)
    lateinit var repository: Repository
    lateinit var holidays: Holidays
    lateinit var updates: cn.suixinji.app.platform.Updates
    override fun onCreate() {
        super.onCreate()
        repository = Repository(this,appScope)
        holidays = Holidays(this)
        updates = cn.suixinji.app.platform.Updates(this,repository)
        appScope.launch { holidays.refreshIfNeeded() }
        appScope.launch { repository.ready.first {it}; updates.check(automatic=true) }
    }
}

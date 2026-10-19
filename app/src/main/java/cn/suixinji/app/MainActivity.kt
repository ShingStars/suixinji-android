package cn.suixinji.app

import android.os.Build
import android.os.Bundle
import android.content.Intent
import android.app.KeyguardManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import cn.suixinji.app.ui.SuixinjiRoot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object LockSession { var unlocked by mutableStateOf(false) }
data class OpenRequest(val kind:String,val owner:String="",val date:String="",val nonce:Long=System.nanoTime())

class MainActivity: FragmentActivity() {
    val requests=MutableStateFlow<OpenRequest?>(null)
    private val app get()=application as SuixinjiApplication
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        updateRecents()
        consumeIntent(intent)
        setContent { SuixinjiRoot(this) }
    }
    override fun onNewIntent(intent:Intent) { super.onNewIntent(intent); setIntent(intent); consumeIntent(intent) }
    private fun consumeIntent(intent:Intent?) { intent?.getStringExtra("destination")?.let { requests.value=OpenRequest(it,intent.getStringExtra("owner").orEmpty(),intent.getStringExtra("date").orEmpty()) } }
    override fun onResume() {
        super.onResume()
        app.appScope.launch { app.repository.ready.first {it}; app.repository.reconcileTimer(); app.repository.reminders.reschedule(app.repository.snapshotData()) }
    }
    fun updateRecents() { if(Build.VERSION.SDK_INT>=33) setRecentsScreenshotEnabled(!app.repository.device.getBoolean("app_lock",false)) }
    fun canAuthenticate() = BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)==BiometricManager.BIOMETRIC_SUCCESS
    fun authenticate(onResult:(Boolean,String?)->Unit) {
        if(!getSystemService(KeyguardManager::class.java).isDeviceSecure) { onResult(false,"请先在手机设置中启用锁屏密码"); return }
        val callback=object:BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result:BiometricPrompt.AuthenticationResult) { LockSession.unlocked=true; onResult(true,null) }
            override fun onAuthenticationError(code:Int,message:CharSequence) { onResult(false,if(code==BiometricPrompt.ERROR_USER_CANCELED || code==BiometricPrompt.ERROR_CANCELED || code==BiometricPrompt.ERROR_NEGATIVE_BUTTON) null else message.toString()) }
        }
        BiometricPrompt(this,ContextCompat.getMainExecutor(this),callback).authenticate(BiometricPrompt.PromptInfo.Builder()
            .setTitle("解锁随心记").setSubtitle("使用系统生物识别或设备凭据")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL).build())
    }
}

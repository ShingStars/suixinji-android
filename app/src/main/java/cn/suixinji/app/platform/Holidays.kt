package cn.suixinji.app.platform

import android.content.Context
import android.icu.util.ChineseCalendar
import cn.suixinji.app.data.codec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId

@Serializable data class HolidayDay(val name:String,val date:String,val isOffDay:Boolean)
@Serializable data class HolidayYear(val year:Int,val papers:List<String> = emptyList(),val days:List<HolidayDay> = emptyList())
data class DayLabel(val lunar:String,val festival:String,val offDay:Boolean?=null,val holidayName:String="")
class Holidays(private val context:Context) {
    private val dir=File(context.noBackupFilesDir,"holidays").apply { mkdirs() }
    private val prefs=context.getSharedPreferences("holiday_cache",Context.MODE_PRIVATE)
    private val mutex=Mutex()
    private val years=java.util.concurrent.ConcurrentHashMap<Int,HolidayYear>()
    val revision=MutableStateFlow(0)
    val status=MutableStateFlow("离线日历已就绪")
    init {
        context.assets.list("holidays").orEmpty().filter { it.endsWith(".json") }.forEach { name ->
            runCatching { codec.decodeFromString<HolidayYear>(context.assets.open("holidays/$name").bufferedReader().readText()) }.getOrNull()?.let { years[it.year]=it }
        }
        dir.listFiles()?.filter { it.extension=="json" }?.forEach { file -> runCatching { val y=codec.decodeFromString<HolidayYear>(file.readText()); validate(y,y.year); years[y.year]=y } }
    }
    fun available(year:Int)=years[year]?.days?.isNotEmpty()==true
    fun sources(year:Int)=years[year]?.papers.orEmpty()
    fun lastUpdated()=prefs.getLong("success",0)
    suspend fun refreshIfNeeded() { if(System.currentTimeMillis()-prefs.getLong("attempt",0)>7L*86400000) refresh() }
    suspend fun refresh():String = withContext(Dispatchers.IO) {
        mutex.withLock {
            prefs.edit().putLong("attempt",System.currentTimeMillis()).apply()
            status.value="正在更新节假日…"
            var success=0; var empty=0
            val current=LocalDate.now().year
            for(year in listOf(current-1,current,current+1)) {
                runCatching {
                    val conn=URL("https://raw.githubusercontent.com/NateScarlet/holiday-cn/master/$year.json").openConnection() as HttpURLConnection
                    conn.connectTimeout=8000; conn.readTimeout=8000; conn.instanceFollowRedirects=false
                    val content=try { require(conn.responseCode==200); conn.inputStream.use { input ->
                        val out=java.io.ByteArrayOutputStream(); val buffer=ByteArray(4096)
                        while(true) { val n=input.read(buffer);if(n<0)break;require(out.size()+n<=262144);out.write(buffer,0,n) }
                        out.toString("UTF-8")
                    } } finally { conn.disconnect() }
                    val y=codec.decodeFromString<HolidayYear>(content)
                    if(y.days.isEmpty() || y.papers.isEmpty()) { empty++; return@runCatching }
                    validate(y,year)
                    val temp=File(dir,"$year.tmp"); temp.writeText(content); check(temp.renameTo(File(dir,"$year.json")))
                    years[year]=y; success++
                }
            }
            if(success>0) { prefs.edit().putLong("success",System.currentTimeMillis()).apply(); revision.value++ }
            status.value=if(success>0) "已更新 $success 个年度${if(empty>0) "，部分年度调休数据暂未获取" else ""}" else "更新未成功，继续使用本地数据"
            status.value
        }
    }
    private fun validate(y:HolidayYear,expected:Int) {
        require(y.year==expected && y.days.isNotEmpty() && y.days.size<200 && y.papers.isNotEmpty())
        require(y.papers.all { runCatching { val u=java.net.URI(it); (u.scheme=="https" || u.scheme=="http") && (u.host=="www.gov.cn" || u.host?.endsWith(".gov.cn")==true) }.getOrDefault(false) })
        require(y.days.map { it.date }.distinct().size==y.days.size)
        y.days.forEach { val d=LocalDate.parse(it.date); require(d.year in expected-1..expected+1 && it.name.isNotBlank()) }
    }
    fun label(date:LocalDate):DayLabel {
        val cal=ChineseCalendar().apply { timeInMillis=date.atTime(12,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
        val month=cal.get(ChineseCalendar.MONTH)+1; val day=cal.get(ChineseCalendar.DAY_OF_MONTH)
        val leap=cal.get(ChineseCalendar.IS_LEAP_MONTH)==1
        val months=listOf("正","二","三","四","五","六","七","八","九","十","冬","腊")
        val numbers=listOf("一","二","三","四","五","六","七","八","九","十")
        val lunarDay=when { day<=10 -> "初"+numbers[day-1]; day<20 -> "十"+numbers[day-11]; day==20 -> "二十"; day<30 -> "廿"+numbers[day-21]; else -> "三十" }
        val lunar=if(day==1) (if(leap) "闰" else "")+months[month-1]+"月" else lunarDay
        val solar=mapOf("01-01" to "元旦","05-01" to "劳动节","10-01" to "国庆节")[date.toString().takeLast(5)].orEmpty()
        var festival=if(leap) "" else mapOf("1-1" to "春节","1-15" to "元宵","5-5" to "端午","7-7" to "七夕","8-15" to "中秋","9-9" to "重阳","12-8" to "腊八")["$month-$day"].orEmpty()
        if(month==12 && day>=29) {
            cal.add(ChineseCalendar.DAY_OF_MONTH,1)
            if(cal.get(ChineseCalendar.MONTH)==0 && cal.get(ChineseCalendar.DAY_OF_MONTH)==1) festival="除夕"
        }
        val official=listOfNotNull(years[date.year],years[date.year+1]).flatMap { it.days }.lastOrNull { it.date==date.toString() }
        return DayLabel(lunar,festival.ifBlank { solar }.ifBlank { official?.name.orEmpty() },official?.isOffDay,official?.name.orEmpty())
    }
}

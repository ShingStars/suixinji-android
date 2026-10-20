package cn.suixinji.app.domain

import cn.suixinji.app.data.*
import org.jsoup.Jsoup
import java.nio.charset.Charset

data class CoursePreview(val courses:List<Course>,val errors:List<String>,val notes:List<String> = emptyList())

object CourseImport {
    val headers=listOf("课程名称","星期","开始节数","结束节数","老师","地点","周数")
    val template="\uFEFF"+headers.joinToString(",")+"\r\n示例课程,1,1,2,示例教师,示例教室,1-16\r\n"
    fun key(c:Course)=listOf(c.termId,c.title.trim(),c.weekday,c.fromSlot,c.toSlot,c.teacher.trim(),c.room.trim(),c.weeks.distinct().sorted()).joinToString("\u001f")
    fun decode(bytes:ByteArray):String {
        require(bytes.size<=2*1024*1024) {"文件超过 2 MB，请仅选择课表文件"}
        val utf8=bytes.toString(Charsets.UTF_8)
        return if('\uFFFD' in utf8) bytes.toString(Charset.forName("GB18030")) else utf8
    }
    fun weeks(raw:String):List<Int> {
        val text=raw.trim().replace("第","").replace("周","").replace("（","(").replace("）",")").replace("，",",").replace("、",",").replace("；",",").replace(";",",").replace("至","-").replace("~","-").replace("—","-").replace("～","-").replace(" ","")
        val result=mutableSetOf<Int>()
        text.split(',').filter {it.isNotBlank()}.forEach {part->
            val m=Regex("^(\\d+)(?:-(\\d+))?(?:[({]?(单|双)[)}]?)?$").matchEntire(part) ?: error("无法识别周数：$raw")
            val start=m.groupValues[1].toInt();val end=m.groupValues[2].toIntOrNull() ?: start
            require(start in 1..60 && end in start..60) {"周数应在 1–60 之间，且区间由小到大"}
            result+=(start..end).filter {when(m.groupValues[3]){"单"->it%2==1;"双"->it%2==0;else->true}}
        }
        require(result.isNotEmpty()) {"教学周不能为空"};return result.sorted()
    }
    internal fun validate(c:Course,t:Term) {
        require(c.title.isNotBlank() && c.title.length<=150) {"课程名称为空或超过 150 字"}
        require(c.weekday in 1..7) {"星期需为 1–7"}
        require(c.fromSlot>=1 && c.toSlot>=c.fromSlot && c.toSlot<=t.slots.size) {"课程节次超出学期设置（共 ${t.slots.size} 节），请先调整学期"}
        require(c.weeks.isNotEmpty() && c.weeks.all {it in 1..t.weeks}) {"课程周数超出学期设置（共 ${t.weeks} 周）"}
    }
    // RFC 4180-style quoting, including commas and newlines inside quoted fields.
    fun csvRows(text:String):List<List<String>> {
        val rows=mutableListOf<List<String>>();val row=mutableListOf<String>();val cell=StringBuilder();var quoted=false;var closed=false;var i=0
        fun endCell(){row+=cell.toString();cell.setLength(0);closed=false}
        fun endRow(){endCell();if(row.any {it.isNotBlank()})rows+=row.toList();row.clear()}
        while(i<text.length){val ch=text[i]
            when {
                quoted && ch=='"' && i+1<text.length && text[i+1]=='"'->{cell.append('"');i++}
                ch=='"' && quoted->{quoted=false;closed=true}
                ch=='"' && cell.isEmpty() && !closed->quoted=true
                quoted->cell.append(ch)
                ch==','->endCell()
                ch=='\r' || ch=='\n'->{endRow();if(ch=='\r' && i+1<text.length && text[i+1]=='\n')i++}
                closed && !ch.isWhitespace()->error("CSV 引号结束后含非法字符")
                ch=='"'->error("CSV 引号格式错误")
                else->cell.append(ch)
            };i++
        }
        require(!quoted){"CSV 引号未闭合"};if(cell.isNotEmpty() || row.isNotEmpty())endRow();require(rows.size<=2001){"最多导入 2000 条课程"};return rows
    }
    fun csv(text:String,term:Term):CoursePreview {
        val rows=csvRows(text.removePrefix("\uFEFF"));require(rows.isNotEmpty()){ "CSV 文件为空" }
        val head=rows.first().map {it.trim()};require(head==headers){"表头需依次为：${headers.joinToString("、")}"}
        val errors=mutableListOf<String>();val courses=mutableListOf<Course>()
        rows.drop(1).forEachIndexed {i,r->runCatching {
            require(r.size==7){"应有 7 列"}
            val day=when(val d=r[1].trim().removePrefix("星期").removePrefix("周")){"一"->1;"二"->2;"三"->3;"四"->4;"五"->5;"六"->6;"日","天"->7;else->d.toInt()}
            Course(termId=term.id,title=r[0].trim(),weekday=day,fromSlot=r[2].trim().toInt(),toSlot=r[3].trim().toInt(),teacher=r[4].trim(),room=r[5].trim(),weeks=weeks(r[6]),color=i%5).also {validate(it,term);courses+=it}
        }.onFailure {errors+="第 ${i+2} 行：${it.message ?: "格式错误"}"}}
        return preview(courses,errors)
    }
    fun html(text:String,term:Term):CoursePreview {
        require(text.toByteArray(Charsets.UTF_8).size<=2*1024*1024) {"课表内容超过 2 MB"}
        MultiSchoolImport.parse(text,term)?.let {return it}
        val doc=Jsoup.parse(text);doc.outputSettings().prettyPrint(false)
        val tables=doc.select("table[id=Table1]")
        val table=tables.firstOrNull() ?: doc.select("table").firstOrNull { it.select("tr").first()?.text()?.let {header->listOf("星期一","星期二","星期三","星期四","星期五","星期六").all {day->day in header} && ("星期日" in header || "星期天" in header)}==true }
        if(table==null)return CoursePreview(emptyList(),listOf(if(doc.select("iframe,frame").isNotEmpty()) "这是教务外层页面，请选择实际课表框架；苏大离线保存文件通常在配套文件夹的 content_xs.html" else "未识别课表结构。当前支持旧/新正方、带周次节次标记的强智及课程清单式 URP；可使用 CSV 模板导入。"))
        val courses=mutableListOf<Course>();val errors=mutableListOf<String>();var cells=0
        val schedule=Regex("周([一二三四五六日天])第([0-9,，、\\-]+)节\\s*\\{([^}]+)}")
        table.select("td").forEach {td->
            val html=td.html().replace(Regex("(?i)<br\\s*/?>"),"\n")
            val lines=Jsoup.parse(html).wholeText().replace('\u00a0',' ').lines().map {it.trim()}
            val locations=lines.indices.filter {schedule.containsMatchIn(lines[it])}
            if(locations.isEmpty()) {
                if(lines.any {it.contains("周") && it.contains("节") && it.contains("{")}) errors+="课程格 ${++cells}：上课时间格式未识别"
                return@forEach
            }
            cells++
            var previous=-1
            locations.forEach {index->runCatching {
                val m=schedule.find(lines[index]) ?: error("未识别上课时间")
                val name=lines.take(index).drop(previous+1).firstOrNull {it.isNotBlank()} ?: lines.first()
                val teacher=lines.getOrNull(index+1).orEmpty();val room=lines.getOrNull(index+2).orEmpty()
                val periods=m.groupValues[2].replace('，',',').replace('、',',').split(',').flatMap {s->val p=s.split('-');if(p.size==2)(p[0].toInt()..p[1].toInt()).toList()else listOf(s.toInt())}.distinct().sorted()
                require(periods.isNotEmpty() && periods.all {it in 1..20}) {"节次异常"}
                val groups=mutableListOf<MutableList<Int>>()
                periods.forEach {p->if(groups.isEmpty() || groups.last().last()+1!=p)groups.add(mutableListOf(p))else groups.last().add(p)}
                groups.forEach {g->Course(termId=term.id,title=name,weekday="一二三四五六日".indexOf(m.groupValues[1].replace("天","日"))+1,fromSlot=g.first(),toSlot=g.last(),teacher=teacher,room=room,weeks=weeks(m.groupValues[3]),color=cells%5).also {validate(it,term);courses+=it}}
                previous=index+2
            }.onFailure {errors+="课程格 $cells：${it.message ?: "格式错误"}"}}
        }
        if(courses.isEmpty() && errors.isEmpty())errors+="已找到表格，但没有识别到课程；请确认已选择学期并打开实际课表，不能导入空结果。"
        return preview(courses,errors).let {it.copy(notes=it.notes+listOf("识别类型：旧正方。开学日期及节次钟点沿用目标学期，请核对。","学校可能修改页面结构；识别结果需核对后保存。"))}
    }
    private fun preview(courses:List<Course>,errors:List<String>):CoursePreview {
        val unique=courses.distinctBy(::key)
        return CoursePreview(unique,errors,if(unique.size<courses.size)listOf("文件内 ${courses.size-unique.size} 条完全重复安排已合并")else emptyList())
    }
}

package cn.suixinji.app.domain

import cn.suixinji.app.data.Course
import cn.suixinji.app.data.Term
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** Independent parsers for identifiable timetable markup; never invent missing weeks/periods. */
object MultiSchoolImport {
    private fun normalized(s:String)=s.replace('（','(').replace('）',')').replace('，',',').replace('～','-').replace('—','-').replace("至","-")
    private fun day(s:String):Int {
        val v=s.trim().removePrefix("星期").removePrefix("周")
        return v.toIntOrNull() ?: ("一二三四五六日".indexOf(v.replace("天","日"))+1).also {require(v.length==1 && it in 1..7){"星期未识别"}}
    }
    private fun periods(s:String):List<Int> {
        val v=normalized(s).replace("第","").replace("节","").replace(" ","").replace("、",",")
        val result=v.split(',').flatMap {
            val m=Regex("^(\\d{1,2})(?:-(\\d{1,2}))?$").matchEntire(it) ?: error("节次未识别：$s")
            val a=m.groupValues[1].toInt();val b=m.groupValues[2].toIntOrNull() ?: a
            require(a in 1..30 && b in a..30){"节次范围错误"}
            (a..b).toList()
        }.distinct().sorted()
        require(result.isNotEmpty()){"节次为空"};return result
    }
    private fun expandedRows(table:Element):List<List<Element?>> {
        val spans=mutableMapOf<Int,Pair<Element,Int>>()
        return table.select("tr").filter {it.parents().firstOrNull {p->p.tagName()=="table"}===table}.map {tr->
            val row=mutableMapOf<Int,Element>()
            spans.toMap().forEach {(col,p)->row[col]=p.first;if(p.second<=1)spans.remove(col)else spans[col]=p.first to p.second-1}
            var column=0
            tr.children().filter {it.tagName() in listOf("td","th")}.forEach {cell->
                while(row.containsKey(column))column++
                val width=(cell.attr("colspan").toIntOrNull() ?: 1);val height=(cell.attr("rowspan").toIntOrNull() ?: 1)
                require(width in 1..40 && height in 1..200){"表格合并范围异常"}
                repeat(width){row[column]=cell;if(height>1)spans[column]=cell to height-1;column++}
            }
            List((row.keys.maxOrNull() ?: -1)+1){row[it]}
        }
    }
    fun parse(text:String,term:Term):CoursePreview? {
        val doc=Jsoup.parse(text);doc.outputSettings().prettyPrint(false)
        val courses=mutableListOf<Course>();val errors=mutableListOf<String>();var type=""
        fun add(name:String,teacher:String,room:String,day:Int,periods:List<Int>,weeks:String) {
            val groups=mutableListOf<MutableList<Int>>()
            periods.forEach {p->if(groups.isEmpty() || groups.last().last()+1!=p)groups.add(mutableListOf(p))else groups.last().add(p)}
            val parsedWeeks=CourseImport.weeks(normalized(weeks).replace("(周)","").replace("()","").trim())
            groups.forEach {g->
                val c=Course(termId=term.id,title=name.trim(),teacher=teacher.trim(),room=room.trim(),weekday=day,fromSlot=g.first(),toSlot=g.last(),weeks=parsedWeeks,color=courses.size%5)
                CourseImport.validate(c,term);require(courses.size<2000){"最多导入 2000 条课程"};courses+=c
            }
        }
        fun attempt(label:String,block:()->Unit){runCatching(block).onFailure {errors+="$label：${it.message ?: "结构无法识别"}"}}
        val newZF=doc.select("table").firstOrNull {it.select(".title").isNotEmpty() && it.select("[title=节/周], [title=周/节]").isNotEmpty()}
        val qz=doc.select("table").firstOrNull {t->t.select(".kbcontent").any {it.parents().firstOrNull {p->p.tagName()=="table"}===t}}
        val list=doc.select("table").firstOrNull {t->t.select("tr").any {r->r.parents().firstOrNull {it.tagName()=="table"}===t && run {val h=r.children().map {it.text().trim()};h.any {it=="课程名" || it=="课程名称"} && "星期" in h && "周次" in h && "节次" in h}}}
        when {
            newZF!=null->{
                type="新正方"
                newZF.select(".title").forEachIndexed {i,title->attempt("新正方课程 ${i+1}"){
                    var block=title.parent() ?: error("课程容器缺失")
                    while(block.tagName()!="td" && block.select("[title=节/周], [title=周/节]").isEmpty())block=block.parent() ?: error("时间字段缺失")
                    require(block.select(".title").size==1){"多课程容器不明确，请使用文件或 CSV 导入"}
                    val cell=title.parents().firstOrNull {it.tagName()=="td"} ?: error("星期列缺失")
                    val weekday=Regex("^([1-7])(?:[-_].*)?$").matchEntire(cell.id())?.groupValues?.get(1)?.toInt() ?: error("星期列 ID 未识别")
                    fun field(vararg names:String):String {
                        val e=block.select("[title]").firstOrNull {it.attr("title") in names} ?: return ""
                        return (if(e.tagName()=="p")e else e.parents().firstOrNull {it.tagName()=="p"} ?: e).text().trim()
                    }
                    val time=normalized(field("节/周","周/节"))
                    val m=Regex("\\(?([0-9,\\-]+)节\\)?").find(time) ?: error("缺少明确节次")
                    val weeks=time.removeRange(m.range).trim()
                    add(title.text(),field("教师","老师"),field("上课地点","教室"),weekday,periods(m.groupValues[1]),weeks)
                }}
            }
            qz!=null->{
                type="强智"
                val rows=expandedRows(qz)
                val header=rows.firstOrNull {r->r.count {c->c?.text()?.trim()?.matches(Regex("(星期|周)[一二三四五六日天]"))==true}>=7}
                require(header!=null){"强智课表缺少完整星期表头，无法确定星期"}
                val days=header.mapIndexedNotNull {index,e->runCatching {day(e?.text().orEmpty())}.getOrNull()?.let {index to it}}.toMap()
                val seen=mutableSetOf<Element>()
                rows.forEach {r->r.forEachIndexed {col,cell->if(cell!=null && seen.add(cell))cell.select(".kbcontent").forEach {content->
                    if(content.text().isNotBlank())content.html().split(Regex("(?:-{3,}|<hr\\b[^>]*>)",RegexOption.IGNORE_CASE)).filter {Jsoup.parse(it).text().isNotBlank()}.forEach {part->attempt("强智课程"){
                        val b=Jsoup.parseBodyFragment(part)
                        val name=Jsoup.parse(part.substringBefore("<font")).text().trim()
                        val time=normalized(b.select("[title=周次(节次)]").text())
                        val m=Regex("\\[([0-9,\\-]+)]节").find(time) ?: error("缺少明确节次，请导出包含节次的课表")
                        val weeks=time.substringBefore("(周)").takeIf {it!=time} ?: time.removeRange(m.range)
                        add(name,b.select("[title=老师]").text(),b.select("[title=教室]").text(),days[col] ?: error("无法确定星期"),periods(m.groupValues[1]),weeks)
                    }}
                }}}
            }
            list!=null->{
                type="课程清单（URP 等）"
                val rows=expandedRows(list);val hi=rows.indexOfFirst {r->r.any {it?.text()?.trim()=="星期"} && r.any {it?.text()?.trim()=="周次"}}
                val head=rows[hi].map {it?.text()?.trim().orEmpty()}
                fun index(vararg keys:String)=head.indexOfFirst {it in keys}
                rows.drop(hi+1).forEachIndexed {i,r->if(r.any {it?.text()?.isNotBlank()==true})attempt("课程清单第 ${i+1} 行"){
                    fun value(vararg keys:String)=r.getOrNull(index(*keys))?.text()?.trim().orEmpty()
                    val start=periods(value("节次"));val count=value("节数").toIntOrNull()
                    val slots=if(count!=null){require(start.size==1 && count in 1..30){"节数错误"};(start.single() until start.single()+count).toList()}else start
                    add(value("课程名","课程名称"),value("教师","老师"),listOf(value("教学楼"),value("教室","地点")).filter {it.isNotBlank()}.joinToString(" "),day(value("星期")),slots,value("周次"))
                }}
            }
            else->return null
        }
        if(courses.isEmpty() && errors.isEmpty())errors+="未读取到课程，请确认选择了有课的学期"
        val unique=courses.distinctBy(CourseImport::key)
        return CoursePreview(unique,errors,listOf("识别类型：$type；请核对星期、周次和节次。","开学日期和节次钟点沿用目标学期。学校变更页面后可能需要重新适配。")+
            if(unique.size<courses.size)listOf("已合并 ${courses.size-unique.size} 条完全重复安排")else emptyList())
    }
}

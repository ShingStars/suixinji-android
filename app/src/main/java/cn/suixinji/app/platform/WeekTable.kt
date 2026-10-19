package cn.suixinji.app.platform

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import cn.suixinji.app.data.*
import cn.suixinji.app.domain.Rules
import java.io.File
import java.time.LocalDate

data class PlacedCourse(val item:AgendaItem,val lane:Int,val lanes:Int)
object WeekTable {
    fun items(data:AppData,term:Term,date:LocalDate):List<PlacedCourse> {
        val items=Rules.agenda(data,date).filter {it.kind=="course" && it.course?.termId==term.id}.sortedBy {it.course!!.fromSlot}
        val ends=mutableListOf<Int>()
        val placed=items.map {item->val c=item.course!!;var lane=ends.indexOfFirst {it<c.fromSlot};if(lane<0){lane=ends.size;ends+=c.toSlot}else ends[lane]=c.toSlot;item to lane}
        return placed.map {(item,lane)->PlacedCourse(item,lane,maxOf(1,ends.size))}
    }
    fun render(context:Context,data:AppData,term:Term,week:Int,teachers:Boolean):File {
        require(week in 1..term.weeks)
        val row=144;val top=230;val left=100;val col=180;val width=left+7*col+24;val height=top+row*term.slots.size+65
        val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);val canvas=Canvas(bitmap);canvas.drawColor(Color.rgb(247,245,239))
        val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        fun text(value:String,x:Float,y:Float,size:Float=24f,bold:Boolean=false){paint.color=Color.rgb(39,54,44);paint.textSize=size;paint.typeface=if(bold)Typeface.DEFAULT_BOLD else Typeface.DEFAULT;canvas.drawText(value,x,y,paint)}
        val monday=Rules.monday(LocalDate.parse(term.startDate)).plusWeeks((week-1).toLong())
        text(term.title,28f,52f,34f,true);text("第 $week 周 · $monday — ${monday.plusDays(6)}",28f,94f)
        text("课程按教学周安排 · 随心记",28f,130f,20f)
        term.slots.forEachIndexed {i,t->val y=top+i*row;text("${i+1}",30f,y+40f,26f,true);text(t.start,14f,y+75f,18f);text(t.end,14f,y+103f,18f)}
        val colors=intArrayOf(0xFFE0E8DA.toInt(),0xFFEDE4D6.toInt(),0xFFDDE7EE.toInt(),0xFFEEDDE2.toInt(),0xFFEAE7CF.toInt())
        for(day in 0..6){val x=left+day*col;val date=monday.plusDays(day.toLong());text("周${"一二三四五六日"[day]}",x+50f,180f,25f,true);text("${date.monthValue}/${date.dayOfMonth}",x+55f,213f,20f)
            paint.color=Color.rgb(235,238,229);canvas.drawRoundRect(x+3f,top.toFloat(),x+col-3f,(top+row*term.slots.size).toFloat(),14f,14f,paint)
            items(data,term,date).forEach {placed->val c=placed.item.course!!;val laneWidth=col.toFloat()/placed.lanes;val bx=x+placed.lane*laneWidth+4;val by=top+(c.fromSlot-1)*row+3f;val bw=laneWidth-8;val bh=(c.toSlot-c.fromSlot+1)*row-6f
                paint.color=colors[Math.floorMod(c.color,colors.size)];canvas.drawRoundRect(bx,by,bx+bw,by+bh,12f,12f,paint)
                val content=listOf(c.title,c.room,if(teachers)c.teacher else "").filter {it.isNotBlank()}.joinToString("\n")
                val tp=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.rgb(39,54,44);textSize=22f}
                val layout=StaticLayout.Builder.obtain(content,0,content.length,tp,(bw-14).toInt().coerceAtLeast(12)).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(2f,1f).build()
                canvas.save();canvas.clipRect(bx,by,bx+bw,by+bh);canvas.translate(bx+7,by+10);layout.draw(canvas);canvas.restore()
            }
        }
        text("${term.slots.size} 节 / 天",28f,height-23f,18f)
        val folder=File(context.cacheDir,"shares").apply {mkdirs()};folder.listFiles()?.filter {System.currentTimeMillis()-it.lastModified()>86400000}?.forEach {it.delete()}
        val file=File(folder,"timetable-${newId()}.png")
        try {file.outputStream().use {check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}}finally {bitmap.recycle()};return file
    }
    fun share(context:Context,file:File){val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",file);context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),"分享课程表"))}
    fun save(context:Context,file:File) {
        val resolver=context.contentResolver;val values=ContentValues().apply {put(MediaStore.Images.Media.DISPLAY_NAME,"随心记课表-${System.currentTimeMillis()}.png");put(MediaStore.Images.Media.MIME_TYPE,"image/png");put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Suixinji");put(MediaStore.Images.Media.IS_PENDING,1)}
        val uri=resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values) ?: error("无法创建相册图片")
        try {resolver.openOutputStream(uri).use {out->requireNotNull(out);file.inputStream().use {it.copyTo(out)}};resolver.update(uri,ContentValues().apply{put(MediaStore.Images.Media.IS_PENDING,0)},null,null)}catch(e:Exception){resolver.delete(uri,null,null);throw e}
    }
}

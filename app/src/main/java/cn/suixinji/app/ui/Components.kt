@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package cn.suixinji.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

val Sage=Color(0xFF647D67)
val Ink=Color(0xFF27362C)
val accents=listOf(Color(0xFF6A836C),Color(0xFF9C7D63),Color(0xFF748DA5),Color(0xFFAB7D87),Color(0xFF999565))
val weekdayNames=listOf("一","二","三","四","五","六","日")
fun humanDate(date:LocalDate)=date.format(DateTimeFormatter.ofPattern("M月d日"))+" · 周"+weekdayNames[date.dayOfWeek.value-1]
fun secondsLabel(seconds:Int)="%02d:%02d".format(seconds/60,seconds%60)
fun compactNumber(value:Double)=if(value==value.toLong().toDouble()) value.toLong().toString() else "%.1f".format(java.util.Locale.ROOT,value)

@Composable fun AppTheme(theme:String,content:@Composable ()->Unit) {
    val dark=theme=="dark" || theme=="system" && isSystemInDarkTheme()
    val colors=if(dark) darkColorScheme(primary=Color(0xFFAEC7AA),onPrimary=Color(0xFF1F3523),primaryContainer=Color(0xFF344A37),onPrimaryContainer=Color(0xFFDFE9D8),secondary=Color(0xFFD5B99A),secondaryContainer=Color(0xFF4E4235),onSecondaryContainer=Color(0xFFF0DDC5),background=Color(0xFF171D19),surface=Color(0xFF202722),surfaceVariant=Color(0xFF303A32),onSurface=Color(0xFFE4E8DF),onBackground=Color(0xFFE4E8DF),onSurfaceVariant=Color(0xFFBFCABD),outlineVariant=Color(0xFF414C42))
    else lightColorScheme(primary=Sage,onPrimary=Color.White,primaryContainer=Color(0xFFE0E8DA),onPrimaryContainer=Ink,secondary=Color(0xFF9C7D63),secondaryContainer=Color(0xFFEDE4D6),onSecondaryContainer=Ink,background=Color(0xFFF7F5EF),surface=Color(0xFFFFFDF8),surfaceVariant=Color(0xFFECEEE5),onSurface=Ink,onBackground=Ink,onSurfaceVariant=Color(0xFF626C61),outlineVariant=Color(0xFFE2E4D9))
    MaterialTheme(colorScheme=colors,shapes=Shapes(small=RoundedCornerShape(12.dp),medium=RoundedCornerShape(18.dp),large=RoundedCornerShape(24.dp)),content=content)
}
@Composable fun PageTitle(eyebrow:String,title:String,action:@Composable RowScope.()->Unit={}) {
    Row(Modifier.fillMaxWidth().padding(top=14.dp,bottom=16.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(eyebrow,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.secondary); Spacer(Modifier.height(5.dp)); Text(title,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.SemiBold) }
        action()
    }
}
@Composable fun SectionTitle(title:String,subtitle:String?=null,action:@Composable ()->Unit={}) {
    Row(Modifier.fillMaxWidth().padding(top=10.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold); subtitle?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) } }; action()
    }
}
@Composable fun PaperCard(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    Surface(modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface,tonalElevation=0.dp,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant.copy(alpha=.6f))) { Column(Modifier.padding(16.dp),content=content) }
}
@Composable fun EmptyState(icon:ImageVector,title:String,body:String,action:String?=null,onAction:()->Unit={}) {
    Column(Modifier.fillMaxWidth().padding(vertical=26.dp,horizontal=12.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Surface(shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.primaryContainer) { Icon(icon,null,Modifier.padding(18.dp).size(30.dp),tint=MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(14.dp)); Text(title,style=MaterialTheme.typography.titleMedium); Spacer(Modifier.height(6.dp))
        Text(body,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        action?.let { TextButton(onClick=onAction) { Text(it) } }
    }
}
@Composable fun Choices(options:List<String>,selected:Int,onSelect:(Int)->Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { index,label -> FilterChip(selected=index==selected,onClick={onSelect(index)},label={Text(label)}) }
    }
}
@Composable fun FormDialog(title:String,onClose:()->Unit,onSave:(()->Unit)?=null,saveLabel:String="保存",enabled:Boolean=true,content:@Composable ColumnScope.()->Unit) {
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth().padding(horizontal=16.dp).widthIn(max=560.dp).fillMaxHeight(.91f).imePadding(),shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.background) {
            Column {
                Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick=onClose) { Icon(Icons.Rounded.Close,"关闭") }
                    Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                    onSave?.let { FilledTonalButton(onClick=it,enabled=enabled) { Text(saveLabel) } }
                }
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
            }
        }
    }
}
@Composable fun Field(label:String,value:String,onChange:(String)->Unit,multiline:Boolean=false,modifier:Modifier=Modifier) {
    OutlinedTextField(value=value,onValueChange=onChange,label={Text(label)},modifier=modifier.fillMaxWidth().testTag(label),singleLine=!multiline,minLines=if(multiline) 4 else 1,shape=RoundedCornerShape(14.dp))
}
@Composable fun DateField(label:String,date:String,onChange:(String)->Unit) {
    val context=LocalContext.current; val parsed=runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now())
    OutlinedButton(onClick={ DatePickerDialog(context,{_,y,m,d->onChange(LocalDate.of(y,m+1,d).toString())},parsed.year,parsed.monthValue-1,parsed.dayOfMonth).show() },modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),contentPadding=PaddingValues(16.dp)) {
        Icon(Icons.Rounded.CalendarMonth,null,Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)); Text(label,Modifier.weight(1f)); Text(date)
    }
}
@Composable fun TimeField(label:String,time:String,onChange:(String)->Unit,modifier:Modifier=Modifier) {
    val context=LocalContext.current; val parsed=runCatching { LocalTime.parse(time) }.getOrDefault(LocalTime.of(9,0))
    OutlinedButton(onClick={ TimePickerDialog(context,{_,h,m->onChange("%02d:%02d".format(h,m))},parsed.hour,parsed.minute,true).show() },modifier=modifier,shape=RoundedCornerShape(14.dp)) { Text("$label  $time") }
}
@Composable fun ToggleRow(title:String,subtitle:String?=null,checked:Boolean,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end=12.dp)) { Text(title,style=MaterialTheme.typography.bodyLarge); subtitle?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) } }
        Switch(checked=checked,onCheckedChange=onChange)
    }
}
@Composable fun ColorPicker(selected:Int,onSelect:(Int)->Unit) {
    Row(horizontalArrangement=Arrangement.spacedBy(14.dp),verticalAlignment=Alignment.CenterVertically) {
        Text("标记颜色",Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
        accents.forEachIndexed { i,c -> Box(Modifier.size(30.dp).background(c,RoundedCornerShape(12.dp)).clickable { onSelect(i) },contentAlignment=Alignment.Center) { if(i==selected) Icon(Icons.Rounded.Check,null,Modifier.size(20.dp),tint=Color.White) } }
    }
}
@Composable fun WeekdayPicker(selected:List<Int>,onChange:(List<Int>)->Unit) {
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { weekdayNames.forEachIndexed { i,n -> FilterChip(selected=i+1 in selected,onClick={onChange(if(i+1 in selected) selected-(i+1) else (selected+(i+1)).sorted())},label={Text("周$n")}) } }
}
@Composable fun ErrorText(text:String?) { text?.let { Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall) } }
@Composable fun ConfirmDialog(title:String,body:String,onDismiss:()->Unit,onConfirm:()->Unit,confirm:String="确认") {
    AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={Text(body)},confirmButton={TextButton(onClick=onConfirm){Text(confirm)}},dismissButton={TextButton(onClick=onDismiss){Text("取消")}})
}
@Composable fun MenuRow(icon:ImageVector,title:String,subtitle:String?=null,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=13.dp),verticalAlignment=Alignment.CenterVertically) {
        Icon(icon,null,Modifier.size(22.dp),tint=MaterialTheme.colorScheme.primary); Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) { Text(title,style=MaterialTheme.typography.bodyLarge); subtitle?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis) } }
        Icon(Icons.AutoMirrored.Rounded.ArrowBack,null,Modifier.size(16.dp).graphicsLayer(rotationZ=180f),tint=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

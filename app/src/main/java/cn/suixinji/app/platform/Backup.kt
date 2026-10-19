package cn.suixinji.app.platform

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import androidx.room.withTransaction
import cn.suixinji.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.io.*
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.util.zip.*

object BackupCrypto {
    private const val ITERATIONS = 600_000
    private fun key(password: CharArray,salt:ByteArray,iterations:Int):SecretKeySpec {
        val spec=PBEKeySpec(password,salt,iterations,256)
        return try { SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded,"AES") } finally { spec.clearPassword() }
    }
    fun encrypt(input:InputStream,output:OutputStream,password:CharArray) {
        require(password.size>=8) { "备份密码至少 8 位" }
        val salt=ByteArray(16).also { SecureRandom().nextBytes(it) }; val nonce=ByteArray(12).also { SecureRandom().nextBytes(it) }
        val header=ByteBuffer.allocate(40).put(byteArrayOf(83,88,74,49)).putInt(1).putInt(ITERATIONS).put(salt).put(nonce).array()
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,key(password,salt,ITERATIONS),GCMParameterSpec(128,nonce)); cipher.updateAAD(header)
        output.write(header)
        val buffer=ByteArray(32768)
        while(true) { val n=input.read(buffer); if(n<0) break; cipher.update(buffer,0,n)?.let(output::write) }
        output.write(cipher.doFinal())
    }
    fun decrypt(input:InputStream,output:OutputStream,password:CharArray) {
        val header=ByteArray(40); DataInputStream(input).readFully(header)
        val b=ByteBuffer.wrap(header); val magic=ByteArray(4).also(b::get)
        require(magic.contentEquals(byteArrayOf(83,88,74,49)) && b.int==1) { "不支持的备份格式或版本" }
        val iterations=b.int; require(iterations==ITERATIONS) { "不支持的密钥派生参数" }
        val salt=ByteArray(16).also(b::get); val nonce=ByteArray(12).also(b::get)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(password,salt,iterations),GCMParameterSpec(128,nonce)); cipher.updateAAD(header)
        val buffer=ByteArray(32768); var total=0L
        while(true) { val n=input.read(buffer); if(n<0) break; total+=n; require(total<=1_073_741_824L) { "备份文件超过 1 GB 限制" }; cipher.update(buffer,0,n)?.let(output::write) }
        output.write(cipher.doFinal())
    }
}

class Backup(private val context:Context,private val repo:Repository) {
    val recovery = File(context.noBackupFilesDir,"before-restore.sxj")
    private val marker = File(context.noBackupFilesDir,"restore-journal.json")
    private val oldImages = File(context.noBackupFilesDir,"images-before-restore")
    suspend fun recoverJournal() = withContext(Dispatchers.IO) {
        if(!marker.exists() && !File(marker.path+".bak").exists()) return@withContext
        val snapshot=codec.decodeFromString<Snapshot>(String(AtomicFile(marker).readFully(),Charsets.UTF_8))
        repo.db.withTransaction { repo.dao.clear(); repo.dao.putAll(snapshot.rows.map { RecordEntity(it.id,it.kind,it.payload,it.deletedAt) }) }
        if(oldImages.exists()) {
            deleteLocalTree(repo.attachments)
            check(oldImages.renameTo(repo.attachments))
        }
        marker.delete()
    }
    private fun deleteLocalTree(file:File) {
        require(file.canonicalPath.startsWith(context.noBackupFilesDir.canonicalPath+File.separator) || file.canonicalPath.startsWith(context.cacheDir.canonicalPath+File.separator))
        file.deleteRecursively()
    }
    private suspend fun makeZip(file:File) {
        val rows=repo.dao.all().filter { it.kind!="timer" }
        val data=AppData(rows.map { it.copy(deletedAt=null) })
        val names=(data.diaries.flatMap { it.images }+listOfNotNull(data.settings.avatar)).toSet()
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(codec.encodeToString(Snapshot(rows=rows.map { it.stored() })).toByteArray()); zip.closeEntry()
            names.forEach { name ->
                require(name.matches(Regex("[a-f0-9-]{36}\\.jpg"))) { "附件名称异常" }
                val image=File(repo.attachments,name); require(image.isFile) { "缺少图片附件，请检查后重试" }
                zip.putNextEntry(ZipEntry("images/$name")); image.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            }
        }
    }
    suspend fun export(uri:Uri,password:CharArray) = withContext(Dispatchers.IO) {
        repo.mutation.withLock {
            val zip=File.createTempFile("backup-",".zip",context.cacheDir)
            try { makeZip(zip); context.contentResolver.openOutputStream(uri,"wt").use { output -> requireNotNull(output); zip.inputStream().use { BackupCrypto.encrypt(it,output,password) } } }
            finally { zip.delete(); password.fill('\u0000') }
        }
    }
    suspend fun restore(uri:Uri,password:CharArray) = withContext(Dispatchers.IO) {
        repo.mutation.withLock {
            val zip=File.createTempFile("restore-",".zip",context.cacheDir)
            val stage=File(context.noBackupFilesDir,"restore-stage-${newId()}").apply { mkdirs() }
            try {
                context.contentResolver.openInputStream(uri).use { input -> requireNotNull(input); zip.outputStream().use { BackupCrypto.decrypt(input,it,password) } }
                var manifest:String?=null; var total=0L; val entries=mutableSetOf<String>()
                ZipInputStream(zip.inputStream().buffered()).use { zin ->
                    while(true) {
                        val entry=zin.nextEntry ?: break
                        require(entries.add(entry.name)) { "备份有重复条目" }
                        if(entry.name=="manifest.json") {
                            val out=ByteArrayOutputStream(); total+=copyLimited(zin,out,16*1024*1024); manifest=out.toString("UTF-8")
                        } else {
                            require(entry.name.matches(Regex("images/[a-f0-9-]{36}\\.jpg"))) { "备份含非法附件路径" }
                            File(stage,entry.name.substringAfter('/')).outputStream().use { total+=copyLimited(zin,it,32*1024*1024) }
                        }
                        require(total<=1_073_741_824L) { "解压后的备份过大" }
                        zin.closeEntry()
                    }
                }
                val snap=codec.decodeFromString<Snapshot>(requireNotNull(manifest) { "备份缺少清单" })
                validate(snap,stage)
                val before=File.createTempFile("safety-",".zip",context.cacheDir)
                try { makeZip(before); recovery.outputStream().use { output -> before.inputStream().use { BackupCrypto.encrypt(it,output,password) } } } finally { before.delete() }
                val old=Snapshot(rows=repo.dao.all().map { it.stored() })
                val journal=AtomicFile(marker)
                val journalStream=journal.startWrite()
                try {journalStream.write(codec.encodeToString(old).toByteArray(Charsets.UTF_8));journal.finishWrite(journalStream)} catch(e:Exception){journal.failWrite(journalStream);throw e}
                if(oldImages.exists()) deleteLocalTree(oldImages)
                try {
                    repo.db.withTransaction {
                        repo.dao.clear(); repo.dao.putAll(snap.rows.filter { it.kind!="timer" }.map { RecordEntity(it.id,it.kind,it.payload,it.deletedAt) })
                        check(repo.attachments.renameTo(oldImages)) { "无法保存原附件" }
                        check(stage.renameTo(repo.attachments)) { "无法恢复图片" }
                    }
                    check(marker.delete()) { "无法完成恢复提交" }
                    deleteLocalTree(oldImages)
                } catch(e:Exception) { recoverJournal(); throw e }
                repo.reminders.reschedule(repo.snapshotData())
            } finally { zip.delete(); if(stage.exists()) deleteLocalTree(stage); password.fill('\u0000') }
        }
    }
    private fun copyLimited(input:InputStream,output:OutputStream,limit:Long):Long {
        val b=ByteArray(32768); var total=0L
        while(true) { val n=input.read(b); if(n<0) return total; total+=n; require(total<=limit) { "备份条目过大" }; output.write(b,0,n) }
    }
    private fun validate(snapshot:Snapshot,stage:File) {
        require(snapshot.formatVersion==1) { "不支持的备份版本" }
        require(snapshot.rows.size<=200000 && snapshot.rows.map { it.id }.distinct().size==snapshot.rows.size) { "记录数量或 ID 异常" }
        snapshot.rows.forEach { r ->
            require(r.id.length in 1..200)
            when(r.kind) {
                "event" -> { val x=codec.decodeFromString<Event>(r.payload); require(x.id==r.id && x.title.isNotBlank()); LocalDateCheck.check(x.date); require(validateClock(x.start)&&validateClock(x.end)); x.repeatUntil?.let(LocalDateCheck::check) }
                "diary" -> { val x=codec.decodeFromString<Diary>(r.payload); require(x.id==r.id); LocalDateCheck.check(x.date); x.images.forEach { require(it.matches(Regex("[a-f0-9-]{36}\\.jpg")) && File(stage,it).isFile) { "图片附件不完整" } } }
                "habit" -> { val x=codec.decodeFromString<Habit>(r.payload); require(x.id==r.id && x.target>0 && x.target.isFinite() && x.weeklyTarget in 1..7); LocalDateCheck.check(x.createdDate) }
                "habitlog" -> { val x=codec.decodeFromString<HabitLog>(r.payload); require(x.id==r.id && x.value>0 && x.value.isFinite()); LocalDateCheck.check(x.date) }
                "term" -> { val x=codec.decodeFromString<Term>(r.payload); require(x.id==r.id && x.weeks in 1..60 && x.slots.isNotEmpty()); LocalDateCheck.check(x.startDate); require(x.slots.all { validateClock(it.start)&&validateClock(it.end)&&it.start<it.end }) }
                "course" -> { val x=codec.decodeFromString<Course>(r.payload); require(x.id==r.id && x.weekday in 1..7 && x.fromSlot>0 && x.toSlot>=x.fromSlot) }
                "occurrence" -> { val x=codec.decodeFromString<Occurrence>(r.payload); require(x.id==r.id); LocalDateCheck.check(x.date); if(x.kind=="event") { x.replacement?.let { codec.decodeFromString<Event>(it) }; x.snapshot?.let { codec.decodeFromString<Event>(it) } } else x.replacement?.let { codec.decodeFromString<Course>(it) } }
                "focus" -> { val x=codec.decodeFromString<FocusSession>(r.payload); require(x.id==r.id && x.seconds>=0); LocalDateCheck.check(x.date) }
                "settings" -> { val x=codec.decodeFromString<Settings>(r.payload); require(r.id=="settings" && x.focusMinutes in 1..240 && x.shortMinutes in 1..240 && x.longMinutes in 1..240 && x.rounds in 1..12); require(x.nickname.codePointCount(0,x.nickname.length)<=10 && x.signature.codePointCount(0,x.signature.length)<=30); x.avatar?.let { require(it.matches(Regex("[a-f0-9-]{36}\\.jpg")) && File(stage,it).isFile) {"头像附件不完整"} } }
                else -> error("备份包含不支持的记录类型")
            }
        }
        val data=AppData(snapshot.rows.map { RecordEntity(it.id,it.kind,it.payload) })
        require(data.courses.all { c -> data.terms.any { it.id==c.termId && c.toSlot<=it.slots.size && c.weeks.all { w -> w in 1..it.weeks } } }) { "课程与学期数据不一致" }
        require(data.logs.all { log -> data.habits.any { it.id==log.habitId } }) { "打卡记录缺少习惯" }
    }
    suspend fun exportRecovery(uri:Uri) = withContext(Dispatchers.IO) {
        require(recovery.isFile) { "暂无恢复前副本" }
        context.contentResolver.openOutputStream(uri,"wt").use { out -> requireNotNull(out); recovery.inputStream().use { it.copyTo(out) } }
    }
}
private object LocalDateCheck { fun check(s:String) { java.time.LocalDate.parse(s) } }

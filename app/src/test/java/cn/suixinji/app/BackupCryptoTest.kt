package cn.suixinji.app

import cn.suixinji.app.platform.BackupCrypto
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class BackupCryptoTest {
    private val password="正确的密码-1234".toCharArray()
    private fun encrypt(bytes:ByteArray)=ByteArrayOutputStream().also {BackupCrypto.encrypt(ByteArrayInputStream(bytes),it,password)}.toByteArray()
    private fun decrypt(bytes:ByteArray,p:CharArray=password)=ByteArrayOutputStream().also {BackupCrypto.decrypt(ByteArrayInputStream(bytes),it,p)}.toByteArray()
    @Test fun encryptedBackupRoundTripsUnicodeAndBinary() {val b="日记 图片 日期".toByteArray()+ByteArray(4096){it.toByte()};assertArrayEquals(b,decrypt(encrypt(b)))}
    @Test fun saltsMakeBackupsDifferent() {assertFalse(encrypt(byteArrayOf(1,2,3)).contentEquals(encrypt(byteArrayOf(1,2,3))))}
    @Test fun wrongPasswordIsRejected() {assertThrows(Exception::class.java){decrypt(encrypt(byteArrayOf(1)),"另一个错误密码".toCharArray())}}
    @Test fun damagedAndTruncatedFilesAreRejected() {val b=encrypt(ByteArray(200){it.toByte()});val modified=b.clone();modified[modified.lastIndex-2]=(modified[modified.lastIndex-2].toInt() xor 1).toByte();assertThrows(Exception::class.java){decrypt(modified)};assertThrows(Exception::class.java){decrypt(b.copyOf(b.size-8))}}
    @Test fun unsupportedHeaderIsRejected() {val b=encrypt(byteArrayOf(1));b[0]=0;assertThrows(IllegalArgumentException::class.java){decrypt(b)}}
}

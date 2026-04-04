package yangfentuozi.batteryrecorder.server.notification.stream

import yangfentuozi.batteryrecorder.shared.data.LineRecord
import java.util.zip.CRC32

object LineRecordStreamProtocol {
    // 4 bytes 标志位
    const val MAGIC: Int = 0x4C524543 // "LREC"
    const val FLAG_DATA = 1 // "LREC"
    const val FLAG_STOP = 2 // "LREC"

    // 最大数据长度
    const val MAX_PAYLOAD_SIZE = 4 * 1024 // 4KB

    fun toPayload(record: LineRecord): ByteArray {
        return record.toString().toByteArray()
    }

    fun fromPayload(payload: ByteArray): LineRecord {
        return LineRecord.fromString(String(payload)) ?: throw IllegalArgumentException("非法数据格式")
    }

    fun crc32(bytes: ByteArray): Int {
        val crc = CRC32()
        crc.update(bytes)
        return crc.value.toInt()
    }

    class StopException: Exception()
}
package yangfentuozi.batteryrecorder.server.notification.stream

import yangfentuozi.batteryrecorder.shared.data.LineRecord
import java.io.Closeable
import java.io.DataOutputStream
import java.io.IOException
import java.io.OutputStream

class LineRecordWriter(
    outputStream: OutputStream
): Closeable {
    private val out = DataOutputStream(outputStream)

    fun write(record: LineRecord) {
        val payload = LineRecordStreamProtocol.toPayload(record)
        if (payload.size > LineRecordStreamProtocol.MAX_PAYLOAD_SIZE) {
            throw IOException("Payload 过大: ${payload.size}")
        }

        val crc = LineRecordStreamProtocol.crc32(payload)

        // 标志位
        out.writeInt(LineRecordStreamProtocol.MAGIC)
        // flag
        out.writeInt(LineRecordStreamProtocol.FLAG_DATA)
        // 数据长度
        out.writeInt(payload.size)
        // 数据体
        out.write(payload)
        // 效验值
        out.writeInt(crc)

        // 刷新一下
        out.flush()
    }

    fun writeClose() {
        // 标志位
        out.writeInt(LineRecordStreamProtocol.MAGIC)
        // flag
        out.writeInt(LineRecordStreamProtocol.FLAG_STOP)
    }

    override fun close() {
        out.close()
    }
}
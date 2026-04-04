package yangfentuozi.batteryrecorder.server.notification.stream

import yangfentuozi.batteryrecorder.shared.data.LineRecord
import java.io.Closeable
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream

class LineRecordReader(
    inputStream: InputStream
): Closeable {
    private val input = DataInputStream(inputStream)

    /**
     * 读取下一条记录：
     * - 成功：返回 LineRecord
     * - 正常 EOF（还没开始读新帧就到流末尾）：返回 null
     * - 帧读到一半 EOF / 数据损坏 / 协议错误：抛 IOException
     */
    fun readNext(): LineRecord? {
        // 标志位
        val magic = try {
            input.readInt()
        } catch (_: EOFException) {
            // 流正常结束：连新帧的 magic 都没读到
            return null
        }

        if (magic != LineRecordStreamProtocol.MAGIC) {
            throw IOException(
                "错误标志位: 0x${magic.toUInt().toString(16)}, " +
                        "期望: 0x${LineRecordStreamProtocol.MAGIC.toUInt().toString(16)}"
            )
        }
        try {
            // flag
            when(val flag = input.readInt()) {
                LineRecordStreamProtocol.FLAG_DATA -> {}
                LineRecordStreamProtocol.FLAG_STOP -> throw LineRecordStreamProtocol.StopException()
                else -> throw IOException("无效 flag: $flag")
            }

            // 数据长度
            val length = input.readInt()
            if (length < 0 || length > LineRecordStreamProtocol.MAX_PAYLOAD_SIZE) {
                throw IOException("无效 payload 长度: $length")
            }

            // 数据体
            val payload = ByteArray(length)
            input.readFully(payload)

            // 效验
            val expectedCrc = input.readInt()
            val actualCrc = LineRecordStreamProtocol.crc32(payload)
            if (expectedCrc != actualCrc) {
                throw IOException(
                    "CRC 不匹配: 期望=$expectedCrc 实际=$actualCrc"
                )
            }

            return LineRecordStreamProtocol.fromPayload(payload)
        } catch (e: EOFException) {
            throw IOException("非预期的 EOF", e)
        }
    }

    override fun close() {
        input.close()
    }
}
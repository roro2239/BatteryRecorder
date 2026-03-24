package yangfentuozi.batteryrecorder.server.writer

import android.os.Handler
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus.Charging
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus.Discharging
import yangfentuozi.batteryrecorder.shared.data.LineRecord
import yangfentuozi.batteryrecorder.shared.data.RecordsFile
import yangfentuozi.batteryrecorder.shared.util.Handlers
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import yangfentuozi.batteryrecorder.shared.writer.AdvancedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

class PowerRecordWriter(
    powerDir: File,
    private val fixFileOwner: ((File) -> Unit)
) {
    /**
     * 单条采样写入结果。
     *
     * accepted 为 true 表示该样本已经进入当前记录文件；
     * changedRecordsFile 不为空表示当前记录文件在本次写入后发生切换。
     */
    data class WriteResult(
        val accepted: Boolean,
        val changedRecordsFile: RecordsFile? = null
    )

    private val chargeDir = File(powerDir, "charge")
    private val dischargeDir = File(powerDir, "discharge")

    @Volatile
    var lastStatus: BatteryStatus? = null
        private set

    val chargeDataWriter = ChargeDataWriter(chargeDir)
    val dischargeDataWriter = DischargeDataWriter(dischargeDir)

    @Volatile
    var batchSize = 200

    @Volatile
    var flushIntervalMs = 30 * 1000L

    @Volatile
    var maxSegmentDurationMs = 24 * 60 * 60 * 1000L

    init {
        fun makeSureExists(file: File) {
            if (!file.exists()) {
                if (!file.mkdirs()) {
                    LoggerX.e<PowerRecordWriter>("makeSureExists: 创建功率数据目录失败, dir=${file.absolutePath}")
                    throw IOException("makeSureExists: 创建功率数据文件夹: ${file.absolutePath} 失败")
                }
                LoggerX.d<PowerRecordWriter>("makeSureExists: 创建功率数据目录, dir=${file.absolutePath}")
            } else if (!file.isDirectory()) {
                LoggerX.e<PowerRecordWriter>("makeSureExists: 功率数据路径不是目录, path=${file.absolutePath}")
                throw IOException("makeSureExists: 功率数据文件夹: ${file.absolutePath} 不是一个文件夹")
            }
            fixFileOwner(file)
        }
        makeSureExists(powerDir)
        makeSureExists(chargeDir)
        makeSureExists(dischargeDir)
    }

    fun write(record: LineRecord): WriteResult {
        if (lastStatus != record.status) {
            LoggerX.d<PowerRecordWriter>("write: 电池状态切换, $lastStatus -> ${record.status}")
        }
        val result = when (record.status) {
            Charging -> {
                chargeDataWriter.write(record, lastStatus != Charging)
            }

            Discharging -> {
                dischargeDataWriter.write(record, lastStatus != Discharging)
            }

            else -> WriteResult(accepted = false)
        }
        lastStatus = record.status
        return result
    }

    fun close() {
        chargeDataWriter.closeCurrentSegment()
        dischargeDataWriter.closeCurrentSegment()
    }

    fun flushBuffer() {
        chargeDataWriter.flushBuffer()
        dischargeDataWriter.flushBuffer()
    }

    inner class ChargeDataWriter(dir: File) : BaseDelayedRecordWriter(dir) {
        override fun needStartNewSegment(justChangedStatus: Boolean, nowTime: Long): Boolean {
            // case1 记录超过最大分段时间（0 表示不按时间分段）
            return (maxSegmentDurationMs > 0 && nowTime - startTime > maxSegmentDurationMs) ||
                    // case2 允许短时间内续接之前记录
                    (justChangedStatus && nowTime - lastTime > 30 * 1000)
        }

        override fun needDeleteSegment(nowTime: Long): Boolean {
            return nowTime - startTime < 1 * 60 * 1000 // 1min
        }
    }

    inner class DischargeDataWriter(dir: File) : BaseDelayedRecordWriter(dir) {
        override fun needStartNewSegment(justChangedStatus: Boolean, nowTime: Long): Boolean {
            // case1 记录超过最大分段时间（0 表示不按时间分段）
            return (maxSegmentDurationMs > 0 && nowTime - startTime > maxSegmentDurationMs) || justChangedStatus
                    // case2 允许短时间内续接之前记录 (暂时禁用)
                    // (justChangedStatus && nowTime - lastTime > 10 * 60 * 1000)
        }

        override fun needDeleteSegment(nowTime: Long): Boolean {
            return nowTime - startTime < 10 * 60 * 1000 // 10min
        }
    }

    abstract inner class BaseDelayedRecordWriter(val dir: File) {
        @Volatile
        var segmentFile: File? = null
            private set
        private var writer: AdvancedWriter? = null

        protected var startTime: Long = 0L
        protected var lastTime: Long = 0L
        protected var lastChangedStatusTime = 0L

        protected val handler: Handler = Handlers.getHandler("RecorderWritingThread")

        fun write(
            record: LineRecord,
            justChangedStatus: Boolean
        ): WriteResult {

            // 选择性丢弃一些干扰数据
            if (justChangedStatus) lastChangedStatusTime = record.timestamp
            if (record.timestamp - lastChangedStatusTime < 2 * 1000L) {
                if ((record.power > 0) != (record.status == Discharging)) {
                    if (justChangedStatus) {
                        closeCurrentSegment()
                    }
                    LoggerX.v<BaseDelayedRecordWriter>("write: 跳过状态切换瞬时干扰数据, dir=${dir.name}")
                    return WriteResult(accepted = false)
                }
            }

            val startedNewSegment = startNewSegmentIfNeed(justChangedStatus)
            lastTime = record.timestamp

            writer!!.appendLine(record)

            if (startedNewSegment) {
                LoggerX.d<BaseDelayedRecordWriter>("write: 新分段已创建, 立即落盘, file=${segmentFile?.name}")
                writer!!.flushNow()
                val currentFile = segmentFile
                if (currentFile != null) {
                    LoggerX.d<BaseDelayedRecordWriter>(
                        "write: 当前记录文件已切换, reason=新分段已就绪 file=${currentFile.name}"
                    )
                }
                return WriteResult(
                    accepted = true,
                    changedRecordsFile = currentFile?.let(RecordsFile::fromFile)
                )
            }

            writer!!.onEnqueued()
            if (justChangedStatus) {
                writer!!.flushNow()
                val currentFile = segmentFile
                if (currentFile != null) {
                    LoggerX.d<BaseDelayedRecordWriter>(
                        "write: 当前记录文件已切换, reason=状态切换后续写当前分段 file=${currentFile.name}"
                    )
                }
                return WriteResult(
                    accepted = true,
                    changedRecordsFile = currentFile?.let(RecordsFile::fromFile)
                )
            }
            return WriteResult(accepted = true)
        }

        private fun startNewSegmentIfNeed(
            justChangedStatus: Boolean
        ): Boolean {
            val nowTime = System.currentTimeMillis()
            if (needStartNewSegment(justChangedStatus, nowTime) ||
                // case 还没记录过
                writer == null
            ) {
                // 关闭之前的记录，打开新的
                closeCurrentSegment()
                startTime = nowTime
                val fileName = "$nowTime.txt"
                val file = File(dir, fileName)
                segmentFile = file
                LoggerX.d<BaseDelayedRecordWriter>(
                    "startNewSegmentIfNeed: 创建新分段, dir=${dir.name} file=${file.name} justChangedStatus=$justChangedStatus"
                )

                val openOutputStream: (() -> OutputStream) = {
                    if (!file.exists() && !file.createNewFile()) {
                        throw IOException("@openOutputStream: 创建分段文件: ${file.absolutePath} 失败")
                    }
                    fixFileOwner(file)
                    FileOutputStream(file, true)
                }
                writer = AdvancedWriter(
                    handler = handler,
                    batchSize = { batchSize },
                    flushIntervalMs = { flushIntervalMs },
                    outputStream = openOutputStream(),
                    retryTimes = 3,
                    retryIntervalMs = 1000,
                    reopenOutputStream = openOutputStream
                )
                return true
            }
            return false
        }

        abstract fun needStartNewSegment(
            justChangedStatus: Boolean,
            nowTime: Long = System.currentTimeMillis()
        ): Boolean

        abstract fun needDeleteSegment(
            nowTime: Long = System.currentTimeMillis()
        ): Boolean

        fun flushBuffer() {
            writer?.flushNow()
        }

        fun closeCurrentSegment() {
            flushBuffer()
            if (writer != null) {
                try {
                    writer!!.close()
                } catch (e: IOException) {
                    LoggerX.e<BaseDelayedRecordWriter>("closeCurrentSegment: 关闭分段文件失败", tr = e)
                }
                writer = null
                if (needDeleteSegment(System.currentTimeMillis())) {
                    LoggerX.v<BaseDelayedRecordWriter>("closeCurrentSegment: 删除短分段, file=${segmentFile?.name}")
                    segmentFile!!.delete()
                }
                segmentFile = null
            }
        }

        fun getCurrFile(
            justChangedStatus: Boolean
        ): File? {
            if (needStartNewSegment(justChangedStatus)) closeCurrentSegment()
            return segmentFile
        }
    }
}

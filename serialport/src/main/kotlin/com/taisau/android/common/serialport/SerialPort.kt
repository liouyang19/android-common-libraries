package com.taisau.android.common.serialport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * 串口操作入口。
 *
 * 使用示例：
 * ```kotlin
 * val serialPort = SerialPort.builder("/dev/ttyS0", BaudRate.B115200)
 *     .dataBits(DataBits.EIGHT)
 *     .parity(Parity.NONE)
 *     .stopBits(StopBits.ONE)
 *     .build()
 *
 * lifecycleScope.launch {
 *     if (serialPort.open()) {
 *         serialPort.receive().collect { bytes ->
 *             // 处理收到的串口数据
 *         }
 *     }
 * }
 *
 * lifecycleScope.launch {
 *     serialPort.send(byteArrayOf(0x01, 0x02))
 * }
 * ```
 */
interface SerialPort {

    /** 当前串口是否已打开。 */
    val isOpen: Boolean

    /**
     * 打开串口设备。
     *
     * 内部会尝试 `chmod 666` 获取读写权限，并在 [Dispatchers.IO] 上执行 JNI 打开操作。
     *
     * @return 打开成功返回 `true`，否则返回 `false`
     */
    suspend fun open(): Boolean

    /**
     * 发送数据到串口。
     *
     * 挂起直到数据写入完成；在 [Dispatchers.IO] 上执行。
     *
     * @param data 待发送的字节数组
     * @return 实际写入的字节数
     * @throws IllegalStateException 串口未打开时调用
     * @throws IOException 写入过程中发生 IO 错误
     */
    @Throws(IOException::class)
    suspend fun send(data: ByteArray): Int

    /**
     * 接收串口数据流。
     *
     * 返回热流 [SharedFlow]，多个订阅者共享同一个读取循环。收到的数据以 [ByteArray] 形式发射，
     * 每次发射的字节数由内部读取缓冲区大小决定。
     *
     * @return 持续发射收到数据的 Flow
     */
    fun receive(): Flow<ByteArray>

    /** 关闭串口并释放协程资源。可重复调用，不会抛出异常。 */
    fun close()

    /**
     * 串口构造器。
     */
    interface Builder {
        /**
         * 数据位。
         *
         * @param dataBits 5、6、7、8，默认 [DataBits.EIGHT]
         */
        fun dataBits(@DataBits dataBits: Int): Builder

        /**
         * 校验位。
         *
         * @param parity 0：无校验（默认）；1：奇校验；2：偶校验
         */
        fun parity(@Parity parity: Int): Builder

        /**
         * 停止位。
         *
         * @param stopBits 1（默认）或 2
         */
        fun stopBits(@StopBits stopBits: Int): Builder

        /**
         * 打开标志，透传给 `open()` 系统调用。
         *
         * @param flags 例如 [OpenFlags.O_NONBLOCK] 等，默认 0
         */
        fun flags(@OpenFlags flags: Int): Builder

        /**
         * su 可执行文件路径。
         *
         * @param suPath 默认 `/system/bin/su`
         */
        fun suPath(suPath: String): Builder

        /** 构建 [SerialPort] 实例。 */
        fun build(): SerialPort
    }

    companion object {
        internal const val DEFAULT_SU_PATH = "/system/bin/su"

        /**
         * 创建串口构造器。
         *
         * @param port 串口设备文件
         * @param baudrate 波特率，例如 [BaudRate.B115200]
         */
        @JvmStatic
        fun builder(port: File, @BaudRate baudrate: Int): Builder = SerialPortBuilder(port, baudrate)

        /**
         * 创建串口构造器。
         *
         * @param portPath 串口设备绝对路径
         * @param baudrate 波特率，例如 [BaudRate.B115200]
         */
        @JvmStatic
        fun builder(portPath: String, @BaudRate baudrate: Int): Builder = SerialPortBuilder(File(portPath), baudrate)
    }
}

private class SerialPortBuilder(
    private val port: File,
    @param:BaudRate private val baudrate: Int
) : SerialPort.Builder {

    @field:DataBits
    private var dataBits: Int = DataBits.EIGHT
    @field:Parity
    private var parity: Int = Parity.NONE
    @field:StopBits
    private var stopBits: Int = StopBits.ONE
    @field:OpenFlags
    private var flags: Int = 0
    private var suPath: String = SerialPort.DEFAULT_SU_PATH

    override fun dataBits(@DataBits dataBits: Int): SerialPort.Builder = apply {
        this.dataBits = dataBits
    }

    override fun parity(@Parity parity: Int): SerialPort.Builder = apply {
        this.parity = parity
    }

    override fun stopBits(@StopBits stopBits: Int): SerialPort.Builder = apply {
        this.stopBits = stopBits
    }

    override fun flags(@OpenFlags flags: Int): SerialPort.Builder = apply {
        this.flags = flags
    }

    override fun suPath(suPath: String): SerialPort.Builder = apply {
        this.suPath = suPath
    }

    override fun build(): SerialPort = SerialPortImpl(
        port = port,
        baudrate = baudrate,
        dataBits = dataBits,
        parity = parity,
        stopBits = stopBits,
        flags = flags,
        suPath = suPath
    )
}

private class SerialPortImpl(
    private val port: File,
    @param:BaudRate private val baudrate: Int,
    @param:DataBits private val dataBits: Int,
    @param:Parity private val parity: Int,
    @param:StopBits private val stopBits: Int,
    @param:OpenFlags private val flags: Int,
    private val suPath: String
) : SerialPort {

    companion object {
        init {
            System.loadLibrary("serial_port")
        }

        private const val READ_BUFFER_SIZE = 256
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var fd: FileDescriptor? = null
    private var inputStream: FileInputStream? = null
    private var outputStream: FileOutputStream? = null

    private val _receive = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )

    private var readerJob: Job? = null

    @Volatile
    private var closed = false

    override val isOpen: Boolean
        get() = fd != null && !closed

    override suspend fun open(): Boolean = withContext(Dispatchers.IO) {
        if (closed || fd != null) {
            return@withContext isOpen
        }

        try {
            if (!port.canRead() || !port.canWrite()) {
                chmod666()
            }
        } catch (e: SecurityException) {
            return@withContext false
        } catch (e: IOException) {
            return@withContext false
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            return@withContext false
        }

        val descriptor = openNative(
            absolutePath = port.absolutePath,
            baudrate = baudrate,
            dataBits = dataBits,
            parity = parity,
            stopBits = stopBits,
            flags = flags
        ) ?: return@withContext false

        fd = descriptor
        inputStream = FileInputStream(descriptor)
        outputStream = FileOutputStream(descriptor)
        startReader()
        true
    }

    private fun chmod666() {
        val process = Runtime.getRuntime().exec(suPath)
        process.outputStream.use { output ->
            output.write("chmod 666 ${port.absolutePath}\n".toByteArray())
            output.write("exit\n".toByteArray())
        }
        if (process.waitFor() != 0 || !port.canRead() || !port.canWrite()) {
            throw SecurityException("无法获取串口读写权限: ${port.absolutePath}")
        }
    }

    private fun startReader() {
        readerJob = scope.launch {
            val stream = inputStream ?: return@launch
            val buffer = ByteArray(READ_BUFFER_SIZE)
            while (isActive) {
                val count = try {
                    stream.read(buffer)
                } catch (e: IOException) {
                    if (!isActive || closed) break
                    throw e
                }
                if (count < 0) break
                if (count > 0) {
                    _receive.emit(buffer.copyOf(count))
                }
            }
        }
    }

    override suspend fun send(data: ByteArray): Int = withContext(Dispatchers.IO) {
        check(isOpen) { "串口未打开，请先调用 open()" }
        val stream = outputStream ?: throw IllegalStateException("输出流未初始化")
        stream.write(data)
        stream.flush()
        data.size
    }

    override fun receive(): Flow<ByteArray> = _receive.asSharedFlow()

    override fun close() {
        if (closed) return
        closed = true

        // 刷出待发送数据，避免数据滞留缓冲区
        try {
            outputStream?.flush()
        } catch (_: IOException) {
            // ignore
        }

        // 关闭输入流，让阻塞在 read() 上的读取协程立即抛出 IOException 退出
        try {
            inputStream?.close()
        } catch (_: IOException) {
            // ignore
        }

        // 取消读取协程及整个作用域
        readerJob?.cancel()
        scope.cancel()

        if (fd != null) {
            closeNative()
            fd = null
        }
        inputStream = null
        outputStream = null
        readerJob = null
    }

    private external fun openNative(
        absolutePath: String,
        @BaudRate baudrate: Int,
        @DataBits dataBits: Int,
        @Parity parity: Int,
        @StopBits stopBits: Int,
        @OpenFlags flags: Int
    ): FileDescriptor?

    private external fun closeNative()
}

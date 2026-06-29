package com.taisau.android.common.serialport

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds

/**
 * 串口高级操作器，封装两种常见通信模式：
 *
 * 1. **轮询模式** —— 以固定周期循环发送同一指令，响应仍通过 [SerialPort.receive] 收取。
 * 2. **请求-响应模式** —— 发送一条指令后挂起，等待返回数据再继续下一条指令。
 *
 * 所有发送操作内部通过 [Mutex] 串行化，避免多协程同时写串口。
 *
 * @param serialPort 已创建的串口实例
 * @param scope 用于启动轮询协程的作用域，建议使用生命周期作用域如 `lifecycleScope`
 */
class SerialPortOperator(
    private val serialPort: SerialPort,
    private val scope: CoroutineScope
) {

    private val sendMutex = Mutex()
    private var pollingJob: Job? = null

    /** 当前是否正在轮询。 */
    val isPolling: Boolean
        get() = pollingJob?.isActive == true

    /**
     * 启动轮询：每隔 [intervalMillis] 发送一次 [command]。
     *
     * 用户需单独收集 [SerialPort.receive] 来获取串口返回数据。
     * 调用 [stopPolling] 或取消 [scope] 即可停止。
     *
     * @param command 要循环发送的指令
     * @param intervalMillis 发送间隔，单位毫秒
     * @throws IllegalStateException 串口未打开时调用
     */
    fun startPolling(command: ByteArray, intervalMillis: Long) {
        require(intervalMillis > 0) { "轮询间隔必须大于 0" }
        stopPolling()

        pollingJob = scope.launch {
            while (isActive) {
                try {
                    sendMutex.withLock {
                        serialPort.send(command)
                    }
                    delay(intervalMillis.milliseconds)
                } catch (_: CancellationException) {
                    break
                }
            }
        }
    }

    /** 停止轮询。可重复调用，不会抛出异常。 */
    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    /**
     * 执行单条指令并等待响应。
     *
     * 发送 [command] 后挂起，直到收到满足 [endCondition] 的数据或超时。
     * 默认 [endCondition] 为收到首包数据即返回，适合一问一答的协议。
     *
     * @param command 待发送指令
     * @param timeoutMillis 等待响应的超时时间，单位毫秒，默认 1000ms
     * @param endCondition 判断响应是否完整的条件，参数为当前累计收到的字节数组，返回 true 则结束等待
     * @return 响应字节数组
     * @throws IllegalStateException 串口未打开时调用
     * @throws kotlinx.coroutines.TimeoutCancellationException 等待响应超时
     */
    suspend fun execute(
        command: ByteArray,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MS,
        endCondition: (ByteArray) -> Boolean = { true }
    ): ByteArray = withTimeout(timeoutMillis.milliseconds) {
        sendMutex.withLock {
            val sent = serialPort.send(command)
            check(sent == command.size) { "串口写入不完整：期望 ${command.size} 字节，实际 $sent 字节" }
            serialPort.receive()
                .runningFold(ByteArray(0)) { acc, chunk -> acc + chunk }
                .first { endCondition(it) }
        }
    }

    /**
     * 顺序执行多条指令，每条指令等待返回后再执行下一条。
     *
     * @param commands 指令列表
     * @param timeoutMillis 单条指令等待响应的超时时间
     * @param endCondition 判断单条响应是否完整的条件
     * @return 每条指令对应的响应列表，顺序与 [commands] 一致
     */
    suspend fun executeSequential(
        commands: List<ByteArray>,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MS,
        endCondition: (ByteArray) -> Boolean = { true }
    ): List<ByteArray> {
        val responses = mutableListOf<ByteArray>()
        for (command in commands) {
            responses.add(execute(command, timeoutMillis, endCondition))
        }
        return responses
    }

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 1000L
    }
}

/**
 * 为 [SerialPort] 创建 [SerialPortOperator]。
 *
 * @param scope 用于启动轮询协程的作用域
 */
fun SerialPort.operator(scope: CoroutineScope): SerialPortOperator {
    return SerialPortOperator(this, scope)
}

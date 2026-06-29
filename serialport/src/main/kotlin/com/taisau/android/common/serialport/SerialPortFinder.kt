package com.taisau.android.common.serialport

import java.io.File
import java.io.IOException

/**
 * 串口设备查询器。
 *
 * 通过解析 `/proc/tty/drivers` 获取系统中注册的串口驱动，并在 `/dev` 下枚举对应设备节点。
 * 典型使用场景：在打开串口前让用户选择可用串口。
 */
class SerialPortFinder {

    /**
     * 查询所有可用串口设备信息。
     *
     * @return 按设备路径排序的 [SerialDeviceInfo] 列表；无权限或解析失败时返回空列表
     */
    fun findAllDevices(): List<SerialDeviceInfo> {
        val drivers = try {
            loadDrivers()
        } catch (_: IOException) {
            return emptyList()
        }

        if (drivers.isEmpty()) {
            return emptyList()
        }

        val devDir = File(DEV_PATH)
        if (!devDir.exists() || !devDir.isDirectory || !devDir.canRead()) {
            return emptyList()
        }

        val result = mutableListOf<SerialDeviceInfo>()
        val files = devDir.listFiles() ?: return emptyList()

        for (driver in drivers) {
            val rootName = File(driver.deviceRoot).name
            if (rootName.isEmpty()) continue

            for (file in files) {
                if (file.name.startsWith(rootName) && file.canRead()) {
                    result.add(
                        SerialDeviceInfo(
                            path = file.absolutePath,
                            name = file.name,
                            driver = driver.driverName
                        )
                    )
                }
            }
        }

        return result.distinctBy { it.path }.sortedBy { it.path }
    }

    /**
     * 只返回串口设备路径数组。
     *
     * @return 例如 `[/dev/ttyS0, /dev/ttyS1, /dev/ttyUSB0]`；无设备时返回空数组
     */
    fun findAllDevicePaths(): Array<String> {
        return findAllDevices().map { it.path }.toTypedArray()
    }

    /**
     * 只返回串口设备名称数组。
     *
     * @return 例如 `[ttyS0, ttyS1, ttyUSB0]`；无设备时返回空数组
     */
    fun findAllDeviceNames(): Array<String> {
        return findAllDevices().map { it.name }.toTypedArray()
    }

    /**
     * 按驱动名称筛选串口设备。
     *
     * @param driverName 驱动名，例如 `serial`、`usbserial`
     * @return 该驱动下的设备列表
     */
    fun findDevicesByDriver(driverName: String): List<SerialDeviceInfo> {
        return findAllDevices().filter { it.driver == driverName }
    }

    @Throws(IOException::class)
    private fun loadDrivers(): List<SerialDriver> {
        val driversFile = File(DRIVERS_PATH)
        if (!driversFile.exists() || !driversFile.canRead()) {
            return emptyList()
        }

        return driversFile.readLines().mapNotNull { line ->
            parseDriver(line)
        }
    }

    private fun parseDriver(line: String): SerialDriver? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null

        val parts = WHITESPACE_REGEX.split(trimmed)
        if (parts.size < 4) return null

        // /proc/tty/drivers 格式：driver_name device_root major minor_range [type]
        // 最后一列为 "serial" 时认为是一个串口驱动
        val type = parts.lastOrNull()
        if (type != SERIAL_TYPE) {
            return null
        }

        val driverName = parts[0]
        val deviceRoot = parts[1]
        if (!deviceRoot.startsWith(DEV_PATH)) {
            return null
        }

        return SerialDriver(driverName, deviceRoot)
    }

    private data class SerialDriver(
        val driverName: String,
        val deviceRoot: String
    )

    companion object {
        private const val DRIVERS_PATH = "/proc/tty/drivers"
        private const val DEV_PATH = "/dev"
        private const val SERIAL_TYPE = "serial"
        private val WHITESPACE_REGEX = "\\s+".toRegex()
    }
}

/**
 * 串口设备信息。
 *
 * @property path 设备完整路径，例如 `/dev/ttyS0`
 * @property name 设备文件名，例如 `ttyS0`
 * @property driver 驱动名称，例如 `serial`、`usbserial`
 */
data class SerialDeviceInfo(
    val path: String,
    val name: String,
    val driver: String
)

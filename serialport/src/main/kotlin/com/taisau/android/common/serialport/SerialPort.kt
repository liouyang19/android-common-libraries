package com.taisau.android.common.serialport

import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class SerialPort private constructor(
	private val builder: Builder){
	
	companion object {
		private const val DEFAULT_SU_PATH = "/system/bin/su"
		
		init {
			System.loadLibrary("serial_port")
		}
		
	}
	
	/**文件描述符**/
	private var mFd: FileDescriptor? = null
	private var mFileInputStream: FileInputStream? = null
	private var mFileOutputStream: FileOutputStream? = null
	
	fun tryOpen():Boolean{
		val device = builder.port
		val suPath = builder.suPath
		val baudrate = builder.baudrate
		val dataBits = builder.data_bits
		
		try {
			if (!device.canRead() || !device.canWrite()){
				val process = Runtime.getRuntime().exec(suPath)
				val cmd = "chmod 666 ${device.absolutePath} \n exit \n"
				process.outputStream.write(cmd.toByteArray())
				if (process.waitFor() != 0 || !device.canRead() || !device.canWrite()) {
					throw SecurityException()
				}
			}
		}catch (e: IOException){
			return false
		}
		mFd = open(device.absolutePath,baudrate,dataBits,0,0,0)
		return mFd != null
	}
	
	fun getFileInputStream(): FileInputStream{
		return mFileInputStream ?:  mFd.let { descriptor ->
			FileInputStream(descriptor).also {
				mFileInputStream = it
			} }
	}
	
	fun getFileOutputStream(): FileOutputStream{
		return mFileOutputStream ?: mFd.let { descriptor ->
			FileOutputStream(descriptor).also {
				mFileOutputStream = it
			}
		}
	}
	
	fun tryClose(){
		if (mFd != null){
			close()
			mFd = null
		}
		mFileInputStream?.close()
	}
	
	
	private external fun open(
		absolutePath: String,
		baudrate: Int,
		dataBits: Int,
		parity: Int,
		stopBits: Int,
		flags: Int
	): FileDescriptor?
	
	private external fun close()
	
	
	class Builder (
		internal val port: File,
		internal val baudrate: Int){
		
		internal var data_bits: Int = 0
		internal var parity: Int = 0
		internal var suPath = DEFAULT_SU_PATH
		
		/*构造方法*/
		constructor(portPath:String, baudrate: Int):this(File(portPath),baudrate)
		
		
		/**
		 *
		 */
		fun dataBits(dataBits: Int) = apply {
			this.data_bits = dataBits
		}
		
		/**
		 * 校验位
		 * @param parity 0:无校验位(NONE，默认)；1:奇校验位(ODD);2:偶校验位(EVEN)
		 */
		fun parity(parity: Int) = apply {
			this.parity = parity
		}
		
		fun suPath(suPath: String){
			this.suPath = suPath
		}
		
		fun build():SerialPort{
			return SerialPort(this)
		}
	}
}

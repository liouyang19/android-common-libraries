@file:Suppress("DEPRECATION")

package com.taisau.android.common.camera.core

/**
 * 相机会话封装，用于 [ICamera.createCaptureSession] 的回调类型安全返回。
 *
 * Camera1 和 Camera2 使用不同的底层会话类型，通过此密封类统一。
 */
sealed class CameraSession {
    /** Camera1 会话，内部为 [android.hardware.Camera] */
    data class Camera1Session(val camera: android.hardware.Camera) : CameraSession()

    /** Camera2 会话，内部为 [android.hardware.camera2.CameraCaptureSession] */
    data class Camera2Session(val session: android.hardware.camera2.CameraCaptureSession) : CameraSession()
}

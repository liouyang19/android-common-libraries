package com.taisau.android.common.camera.core

import android.hardware.camera2.CameraCharacteristics

class CameraSelector private constructor(
    internal val cameraId: String?,
    internal val lensFacing: Int?
) {
    companion object {
        @JvmField
        val DEFAULT_BACK_CAMERA = CameraSelector(
            cameraId = null,
            lensFacing = CameraCharacteristics.LENS_FACING_BACK
        )

        @JvmField
        val DEFAULT_FRONT_CAMERA = CameraSelector(
            cameraId = null,
            lensFacing = CameraCharacteristics.LENS_FACING_FRONT
        )
    }

    class Builder {
        private var cameraId: String? = null
        private var lensFacing: Int? = null

        fun requireLensFacing(lensFacing: Int): Builder = apply {
            this.lensFacing = lensFacing
        }

        fun cameraId(cameraId: String): Builder = apply {
            this.cameraId = cameraId
        }

        fun build(): CameraSelector = CameraSelector(
            cameraId = cameraId,
            lensFacing = lensFacing
        )
    }

    override fun toString(): String = buildString {
        append("CameraSelector[")
        if (cameraId != null) append("cameraId=$cameraId, ")
        if (lensFacing != null) {
            append("lensFacing=")
            append(
                when (lensFacing) {
                    CameraCharacteristics.LENS_FACING_BACK -> "BACK"
                    CameraCharacteristics.LENS_FACING_FRONT -> "FRONT"
                    else -> lensFacing.toString()
                }
            )
        }
        append("]")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CameraSelector) return false
        return cameraId == other.cameraId && lensFacing == other.lensFacing
    }

    override fun hashCode(): Int {
        var result = cameraId?.hashCode() ?: 0
        result = 31 * result + (lensFacing ?: 0)
        return result
    }
}

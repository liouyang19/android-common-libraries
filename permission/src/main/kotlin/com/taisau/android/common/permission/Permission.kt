package com.taisau.android.common.permission

import android.Manifest
import android.os.Build

sealed class Permission(val rawPermissions: List<String>) {
    object Camera : Permission(listOf(Manifest.permission.CAMERA))
    object Notifications : Permission(
        if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS)
        else emptyList()
    )
    object LocationFine : Permission(listOf(Manifest.permission.ACCESS_FINE_LOCATION))
    object LocationCoarse : Permission(listOf(Manifest.permission.ACCESS_COARSE_LOCATION))
    object Location : Permission(
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    )
    object RecordAudio : Permission(listOf(Manifest.permission.RECORD_AUDIO))

    object MediaImages : Permission(listOf(Manifest.permission.READ_MEDIA_IMAGES))
    object MediaVideo : Permission(listOf(Manifest.permission.READ_MEDIA_VIDEO))
    object MediaAudio : Permission(listOf(Manifest.permission.READ_MEDIA_AUDIO))
    object Storage : Permission(
        if (Build.VERSION.SDK_INT >= 33) {
            listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
            )
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    )
    object WriteExternalStorage : Permission(listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE))
}

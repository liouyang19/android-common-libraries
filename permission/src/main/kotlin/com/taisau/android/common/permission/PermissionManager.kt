package com.taisau.android.common.permission

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.shouldShowRationale

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun rememberPermission(permission: Permission): PermissionHandle {
    if (permission.rawPermissions.isEmpty()) {
        return remember { EmptyPermissionHandle }
    }
    val state = rememberMultiplePermissionsState(permission.rawPermissions)
    return remember(state) { MultiplePermissionHandle(state) }
}

interface PermissionHandle {
    val isGranted: Boolean
    val shouldShowRationale: Boolean
    fun launchRequest()
}

private object EmptyPermissionHandle : PermissionHandle {
    override val isGranted = true
    override val shouldShowRationale = false
    override fun launchRequest() = Unit
}

@OptIn(ExperimentalPermissionsApi::class)
private class MultiplePermissionHandle(
    private val state: MultiplePermissionsState,
) : PermissionHandle {
    override val isGranted: Boolean get() = state.allPermissionsGranted
    override val shouldShowRationale: Boolean
        get() = state.permissions.any { it.status.shouldShowRationale }
    override fun launchRequest() = state.launchMultiplePermissionRequest()
}

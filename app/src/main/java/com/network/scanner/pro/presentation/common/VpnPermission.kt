package com.network.scanner.pro.presentation.common

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Android requires the user to approve a VPN once (system dialog).
 * Usage: `val withVpn = rememberVpnPermissionRequest(); withVpn { doWork() }`.
 * [doWork] runs immediately when the permission already exists, otherwise
 * after the user accepts the system dialog.
 */
@Composable
fun rememberVpnPermissionRequest(): (onGranted: () -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) pending?.invoke()
        pending = null
    }

    return remember(launcher) {
        { onGranted: () -> Unit ->
            val intent = VpnService.prepare(context)
            if (intent == null) {
                onGranted()
            } else {
                pending = onGranted
                launcher.launch(intent)
            }
        }
    }
}

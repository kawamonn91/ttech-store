package com.ttech.driverecord.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/** 記録に必要な権限・設定の状況 */
data class PermState(
    /** 位置情報(アプリの使用中) */
    val location: Boolean,
    /** 「常に許可」。画面を閉じていても、Android Auto につながったら自動で記録するのに必要 */
    val background: Boolean,
    val notifications: Boolean,
    /** 端末の位置情報(GPS)がオンか */
    val gpsOn: Boolean,
) {
    val canRecord: Boolean get() = location && gpsOn
    val canAutoRecord: Boolean get() = location && background && gpsOn
}

fun readPermState(context: Context): PermState {
    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return PermState(
        location = granted(Manifest.permission.ACCESS_FINE_LOCATION),
        background = Build.VERSION.SDK_INT < 29 || granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
        notifications = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS),
        gpsOn = runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false),
    )
}

/** 権限の状況。設定画面から戻ってきたとき(画面が再表示されたとき)にも読み直す */
@Composable
fun rememberPermState(): State<PermState> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(readPermState(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { state.value = readPermState(context) }
    return state
}

/** 権限を頼む操作。位置情報 → 通知 → 「常に許可」の順に、足りないものを頼む */
class PermissionActions(
    val requestLocation: () -> Unit,
    val requestBackground: () -> Unit,
    val openAppSettings: () -> Unit,
    val openLocationSettings: () -> Unit,
)

@Composable
fun rememberPermissionActions(onResult: (PermState) -> Unit): PermissionActions {
    val context = LocalContext.current
    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onResult(readPermState(context)) }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        onResult(readPermState(context))
    }
    return PermissionActions(
        requestLocation = {
            val perms = buildList {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }
            locationLauncher.launch(perms.toTypedArray())
        },
        requestBackground = {
            if (Build.VERSION.SDK_INT >= 29) backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        },
        openAppSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
        openLocationSettings = {
            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        },
    )
}

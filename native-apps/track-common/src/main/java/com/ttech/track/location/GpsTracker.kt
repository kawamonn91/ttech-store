package com.ttech.track.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.ttech.track.domain.TrackPoint

/** 測位に使えている衛星の状況(GPSの受信状態の表示用) */
data class GpsStatus(val satellitesUsed: Int = 0, val satellitesVisible: Int = 0)

/** 端末が返した位置を、加工せずに記録用の点にする */
fun Location.toTrackPoint(): TrackPoint {
    val sats = extras?.getInt("satellites", -1)?.takeIf { it >= 0 }
    return TrackPoint(
        timeMs = time,
        lat = latitude,
        lon = longitude,
        altitude = if (hasAltitude()) altitude else null,
        speed = if (hasSpeed()) speed.toDouble() else null,
        bearing = if (hasBearing()) bearing.toDouble() else null,
        hAcc = if (hasAccuracy()) accuracy.toDouble() else null,
        vAcc = if (hasVerticalAccuracy()) verticalAccuracyMeters.toDouble() else null,
        sAcc = if (hasSpeedAccuracy()) speedAccuracyMetersPerSecond.toDouble() else null,
        satellites = sats,
    )
}

/**
 * GPS(衛星測位)を直接使って、1秒ごとに位置を受け取る。
 * 電池を節約するために粗く測る仕組みを通さず、衛星の生の測位をそのまま使うので、ルートが細かく正確に残る。
 * 呼ぶ前に、位置情報の権限があることを確かめておくこと。
 */
class GpsTracker(private val context: Context, private val intervalMs: Long = 1000L) {
    interface Listener {
        fun onPoint(point: TrackPoint)
        fun onStatus(status: GpsStatus) {}
    }

    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var locationListener: LocationListener? = null
    private var gnssCallback: GnssStatus.Callback? = null

    val isGpsEnabled: Boolean get() = runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)

    /** 開始できたら true。GPSが端末で無効・権限が無いときは false */
    @SuppressLint("MissingPermission")
    fun start(listener: Listener, looper: Looper = Looper.getMainLooper()): Boolean {
        stop()
        if (!isGpsEnabled) return false
        val l = LocationListener { location -> listener.onPoint(location.toTrackPoint()) }
        return try {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, intervalMs, 0f, l, looper)
            locationListener = l
            registerGnss(listener, looper)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerGnss(listener: Listener, looper: Looper) {
        val callback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                var used = 0
                for (i in 0 until status.satelliteCount) if (status.usedInFix(i)) used++
                listener.onStatus(GpsStatus(used, status.satelliteCount))
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                manager.registerGnssStatusCallback(ContextCompat.getMainExecutor(context), callback)
            } else {
                @Suppress("DEPRECATION")
                manager.registerGnssStatusCallback(callback, Handler(looper))
            }
            gnssCallback = callback
        } catch (_: SecurityException) {
            // 衛星の数が分からないだけで、記録はできる
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        locationListener?.let { runCatching { manager.removeUpdates(it) } }
        locationListener = null
        gnssCallback?.let { runCatching { manager.unregisterGnssStatusCallback(it) } }
        gnssCallback = null
    }
}

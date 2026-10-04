package com.ttech.bikenavi.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import com.ttech.track.domain.LatLon
import com.ttech.track.domain.TrackPoint
import com.ttech.track.location.GpsStatus
import com.ttech.track.location.GpsTracker
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** ルートの出発地にする、いまの場所を調べる(案内を始める前だけ使う) */
object CurrentLocation {
    /** これより新しい測位だけ、そのまま使う。古い位置を出発地にしないため、それ以外は新しく測る */
    private const val FRESH_MS = 30_000L

    /** 直近の測位(古くてもよい。地名検索で近くを優先するためだけに使う)。権限が無ければ null */
    @SuppressLint("MissingPermission")
    fun lastKnown(context: Context): LatLon? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val last = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        return last?.let { LatLon(it.latitude, it.longitude) }
    }

    /**
     * ごく最近(30秒以内)の測位があればそれを、無ければGPSの最初の1点を [timeoutMs] まで待つ。
     * それでも取れなければ、古い測位でもあればそれを返す。位置情報の権限があることを確かめてから呼ぶこと。
     */
    @SuppressLint("MissingPermission")
    suspend fun get(context: Context, timeoutMs: Long = 15_000L): LatLon? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val last: Location? = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < FRESH_MS) return LatLon(last.latitude, last.longitude)

        val tracker = GpsTracker(context, intervalMs = 1000L)
        val fix = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<TrackPoint?> { cont ->
                val started = tracker.start(object : GpsTracker.Listener {
                    override fun onPoint(point: TrackPoint) {
                        if (cont.isActive) cont.resume(point)
                    }

                    override fun onStatus(status: GpsStatus) {}
                })
                if (!started && cont.isActive) cont.resume(null)
                cont.invokeOnCancellation { tracker.stop() }
            }
        }
        tracker.stop()
        return fix?.let { LatLon(it.lat, it.lon) } ?: last?.let { LatLon(it.latitude, it.longitude) }
    }
}

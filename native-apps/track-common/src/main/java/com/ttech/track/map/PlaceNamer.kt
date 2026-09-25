package com.ttech.track.map

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.ttech.track.domain.PlaceLabel
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 座標から、住所の名前を調べる(端末の逆ジオコーディング)。
 * 端末や通信の状況で取れないことがあるので、取れなければ null(画面には座標を出す)。
 */
class PlaceNamer(context: Context) {
    private val geocoder: Geocoder? = if (Geocoder.isPresent()) Geocoder(context.applicationContext, Locale.JAPAN) else null

    suspend fun name(lat: Double, lon: Double): String? {
        val g = geocoder ?: return null
        return withTimeoutOrNull(8_000) {
            runCatching {
                val addresses: List<Address>? = if (Build.VERSION.SDK_INT >= 33) {
                    suspendCancellableCoroutine { cont ->
                        g.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses)
                            override fun onError(errorMessage: String?) = cont.resume(null)
                        })
                    }
                } else {
                    withContext(Dispatchers.IO) {
                        @Suppress("DEPRECATION")
                        g.getFromLocation(lat, lon, 1)
                    }
                }
                addresses?.firstOrNull()?.let { PlaceLabel.shorten(it.getAddressLine(0)) }
            }.getOrNull()
        }
    }
}

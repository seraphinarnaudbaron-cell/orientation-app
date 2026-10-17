package org.orientationjp.app

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.min

/** Altitudes du terrain via le service altimétrique de l'IGN (RGE ALTI, précision métrique en France). */
object ElevationApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private const val BASE = "https://data.geopf.fr/altimetrie/1.0/calcul/alti/rest/elevation.json"
    private const val BATCH = 100

    /** Altitude de chaque point (même ordre), ou null si le service est indisponible. Appel bloquant. */
    fun fetch(points: List<GeoPt>): List<Double>? {
        try {
            val out = ArrayList<Double>(points.size)
            var i = 0
            while (i < points.size) {
                val batch = points.subList(i, min(i + BATCH, points.size))
                val lon = batch.joinToString("|") { String.format(Locale.US, "%.6f", it.lon) }
                val lat = batch.joinToString("|") { String.format(Locale.US, "%.6f", it.lat) }
                val url = BASE.toHttpUrl().newBuilder()
                    .addQueryParameter("lon", lon)
                    .addQueryParameter("lat", lat)
                    .addQueryParameter("resource", "ign_rge_alti_wld")
                    .addQueryParameter("zonly", "true")
                    .build()
                val request = Request.Builder().url(url).header("User-Agent", Config.USER_AGENT).get().build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return null
                    val body = resp.body?.string() ?: return null
                    val arr = JSONObject(body).optJSONArray("elevations") ?: return null
                    if (arr.length() != batch.size) return null
                    for (k in 0 until arr.length()) {
                        val v = arr.get(k)
                        val z = when (v) {
                            is Number -> v.toDouble()
                            is JSONObject -> v.optDouble("z", Double.NaN)
                            else -> Double.NaN
                        }
                        // -99999 = pas de donnée
                        out.add(if (z.isNaN() || z <= -500.0) Double.NaN else z)
                    }
                }
                i += BATCH
            }
            return fillGaps(out)
        } catch (e: Exception) {
            return null
        }
    }

    /** Comble quelques trous isolés par interpolation ; trop de trous : on renonce (null). */
    private fun fillGaps(z: ArrayList<Double>): List<Double>? {
        val missing = z.count { it.isNaN() }
        if (missing == 0) return z
        if (missing > z.size / 5 || missing == z.size) return null
        val res = ArrayList(z)
        for (i in res.indices) {
            if (!res[i].isNaN()) continue
            var lo = i - 1
            while (lo >= 0 && res[lo].isNaN()) lo--
            var hi = i + 1
            while (hi < res.size && z[hi].isNaN()) hi++
            res[i] = when {
                lo >= 0 && hi < res.size -> res[lo] + (z[hi] - res[lo]) * (i - lo).toDouble() / (hi - lo).toDouble()
                lo >= 0 -> res[lo]
                hi < res.size -> z[hi]
                else -> return null
            }
        }
        return res
    }
}

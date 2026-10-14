package org.orientationjp.app

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class ApiException(message: String) : Exception(message)

/** Client minimal pour l'API REST / RPC de Supabase. Appels bloquants : à lancer sur Dispatchers.IO. */
object SupabaseApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private fun Request.Builder.withAuth(): Request.Builder = this
        .header("apikey", Config.SUPABASE_ANON_KEY)
        .header("Authorization", "Bearer " + Config.SUPABASE_ANON_KEY)
        .header("User-Agent", Config.USER_AGENT)

    private fun execute(request: Request): String {
        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val msg = try {
                    JSONObject(body).optString("message", body)
                } catch (e: Exception) {
                    body
                }
                throw ApiException(msg.take(200))
            }
            return body
        }
    }

    private fun rpc(name: String, payload: JSONObject): String {
        val request = Request.Builder()
            .url(Config.SUPABASE_URL + "/rest/v1/rpc/" + name)
            .withAuth()
            .post(payload.toString().toRequestBody(jsonType))
            .build()
        return execute(request)
    }

    private fun select(table: String, params: Map<String, String>): JSONArray {
        val builder = (Config.SUPABASE_URL + "/rest/v1/" + table).toHttpUrl().newBuilder()
        for ((k, v) in params) builder.addQueryParameter(k, v)
        val request = Request.Builder().url(builder.build()).withAuth().get().build()
        return JSONArray(execute(request))
    }

    private fun JSONObject.str(key: String): String = if (isNull(key)) "" else getString(key)

    // ------------------------------------------------------------------ lecture
    fun getBeacons(): List<Beacon> {
        val arr = select("beacons", mapOf("select" to "*", "order" to "id.asc"))
        val out = ArrayList<Beacon>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                Beacon(
                    id = o.getLong("id"),
                    name = o.str("name"),
                    lat = o.getDouble("latitude"),
                    lon = o.getDouble("longitude"),
                    pattern = o.str("pattern"),
                    description = o.str("description"),
                )
            )
        }
        return out
    }

    fun getRunners(): List<Runner> {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        val since = fmt.format(Date(System.currentTimeMillis() - Config.POSITION_MAX_AGE_S * 1000L))
        val arr = select(
            "runner_positions",
            mapOf("select" to "username,latitude,longitude", "updated_at" to "gte.$since"),
        )
        val out = ArrayList<Runner>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(Runner(o.str("username"), o.getDouble("latitude"), o.getDouble("longitude")))
        }
        return out
    }

    // ------------------------------------------------------------------ écriture (RPC)
    fun pushPosition(username: String, lat: Double, lon: Double) {
        rpc(
            "upsert_position",
            JSONObject().put("p_username", username).put("p_lat", lat).put("p_lon", lon),
        )
    }

    fun clearPosition(username: String) {
        rpc("clear_position", JSONObject().put("p_username", username))
    }

    fun logEvent(username: String, event: String) {
        rpc("log_event", JSONObject().put("p_username", username).put("p_event", event))
    }

    // ------------------------------------------------------------------ admin
    fun adminCheck(password: String): Boolean =
        rpc("admin_check", JSONObject().put("p_password", password)).trim() == "true"

    fun adminAddBeacon(password: String, name: String, lat: Double, lon: Double, pattern: String, description: String) {
        rpc(
            "admin_add_beacon",
            JSONObject()
                .put("p_password", password)
                .put("p_name", name)
                .put("p_lat", lat)
                .put("p_lon", lon)
                .put("p_pattern", pattern)
                .put("p_description", description),
        )
    }

    fun adminDeleteBeacon(password: String, id: Long) {
        rpc("admin_delete_beacon", JSONObject().put("p_password", password).put("p_id", id))
    }

    fun adminStats(password: String): AdminStats {
        val o = JSONObject(rpc("admin_stats", JSONObject().put("p_password", password)))
        return AdminStats(
            beacons = o.optInt("beacons"),
            activeNow = o.optInt("active_now"),
            launches = o.optInt("launches"),
            distinctUsers = o.optInt("distinct_users"),
            validations = o.optInt("validations"),
        )
    }
}

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
    /** JSON brut (gardé en cache pour le mode hors ligne). */
    fun fetchBeaconsJson(): String =
        select("beacons", mapOf("select" to "*", "order" to "id.asc")).toString()

    fun parseBeacons(json: String): List<Beacon> {
        val arr = JSONArray(json)
        val out = ArrayList<Beacon>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = o.getLong("id")
            out.add(
                Beacon(
                    id = id,
                    name = o.str("name"),
                    lat = o.getDouble("latitude"),
                    lon = o.getDouble("longitude"),
                    pattern = o.str("pattern"),
                    description = o.str("description"),
                    number = if (o.isNull("number")) id else o.getLong("number"),
                )
            )
        }
        return out
    }

    fun fetchTrailsJson(): String =
        select("trails", mapOf("select" to "id,name,points,kind", "order" to "id.asc")).toString()

    fun parseTrails(json: String): List<Trail> {
        val arr = JSONArray(json)
        val out = ArrayList<Trail>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val raw = o.getJSONArray("points")
            val pts = ArrayList<GeoPt>(raw.length())
            for (k in 0 until raw.length()) {
                val p = raw.getJSONArray(k)
                val ele = if (p.length() >= 3 && !p.isNull(2)) p.getDouble(2) else Double.NaN
                pts.add(GeoPt(p.getDouble(0), p.getDouble(1), ele))
            }
            val kind = o.str("kind").ifEmpty { "path" }
            out.add(Trail(o.getLong("id"), o.str("name"), pts, kind))
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

    fun adminAddBeacon(password: String, name: String, lat: Double, lon: Double, pattern: String, description: String, number: Long?) {
        val body = JSONObject()
            .put("p_password", password)
            .put("p_name", name)
            .put("p_lat", lat)
            .put("p_lon", lon)
            .put("p_pattern", pattern)
            .put("p_description", description)
        if (number != null) body.put("p_number", number)
        rpc("admin_add_beacon", body)
    }

    fun adminUpdateBeacon(password: String, id: Long, name: String, lat: Double, lon: Double, pattern: String, description: String, number: Long) {
        rpc(
            "admin_update_beacon",
            JSONObject()
                .put("p_password", password)
                .put("p_id", id)
                .put("p_name", name)
                .put("p_lat", lat)
                .put("p_lon", lon)
                .put("p_pattern", pattern)
                .put("p_description", description)
                .put("p_number", number),
        )
    }

    fun adminDeleteBeacon(password: String, id: Long) {
        rpc("admin_delete_beacon", JSONObject().put("p_password", password).put("p_id", id))
    }

    fun adminAddTrail(password: String, name: String, points: List<GeoPt>, kind: String) {
        val arr = JSONArray()
        for (p in points) {
            val item = JSONArray().put(Math.round(p.lat * 1e6) / 1e6).put(Math.round(p.lon * 1e6) / 1e6)
            if (!p.ele.isNaN()) item.put(Math.round(p.ele * 10.0) / 10.0)
            arr.put(item)
        }
        rpc(
            "admin_add_trail",
            JSONObject().put("p_password", password).put("p_name", name).put("p_points", arr).put("p_kind", kind),
        )
    }

    fun adminDeleteTrail(password: String, id: Long) {
        rpc("admin_delete_trail", JSONObject().put("p_password", password).put("p_id", id))
    }

    fun logBug(username: String, version: String, device: String, tag: String, message: String, stack: String) {
        rpc(
            "log_bug",
            JSONObject()
                .put("p_username", username)
                .put("p_version", version)
                .put("p_device", device)
                .put("p_tag", tag)
                .put("p_message", message)
                .put("p_stack", stack),
        )
    }

    fun adminBugs(password: String): List<AdminBug> {
        val arr = JSONArray(rpc("admin_bugs", JSONObject().put("p_password", password)))
        val out = ArrayList<AdminBug>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                AdminBug(
                    id = o.getLong("id"),
                    time = o.str("created_at").take(16).replace('T', ' '),
                    user = o.str("username"),
                    version = o.str("app_version"),
                    device = o.str("device"),
                    tag = o.str("tag"),
                    message = o.str("message"),
                )
            )
        }
        return out
    }

    fun adminClearBugs(password: String) {
        rpc("admin_clear_bugs", JSONObject().put("p_password", password))
    }

    fun adminStats(password: String): AdminStats {
        val o = JSONObject(rpc("admin_stats", JSONObject().put("p_password", password)))
        return AdminStats(
            beacons = o.optInt("beacons"),
            trails = o.optInt("trails"),
            activeNow = o.optInt("active_now"),
            launches = o.optInt("launches"),
            distinctUsers = o.optInt("distinct_users"),
        )
    }
}

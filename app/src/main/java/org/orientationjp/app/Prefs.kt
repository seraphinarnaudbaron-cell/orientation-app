package org.orientationjp.app

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("orientation", Context.MODE_PRIVATE)

    var name: String
        get() = (sp.getString("name", "") ?: "").take(Config.MAX_NAME)
        set(value) {
            sp.edit().putString("name", value).apply()
        }

    var privateMode: Boolean
        get() = sp.getBoolean("private", false)
        set(value) {
            sp.edit().putBoolean("private", value).apply()
        }

    var basemap: String
        get() = sp.getString("basemap", "osm") ?: "osm"
        set(value) {
            sp.edit().putString("basemap", value).apply()
        }

    var showOthers: Boolean
        get() = sp.getBoolean("showOthers", false)
        set(value) {
            sp.edit().putBoolean("showOthers", value).apply()
        }
}

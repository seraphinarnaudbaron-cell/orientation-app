package org.orientationjp.app

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("orientation", Context.MODE_PRIVATE)

    var name: String
        get() = sp.getString("name", "") ?: ""
        set(value) {
            sp.edit().putString("name", value).apply()
        }

    var privateMode: Boolean
        get() = sp.getBoolean("private", false)
        set(value) {
            sp.edit().putBoolean("private", value).apply()
        }
}

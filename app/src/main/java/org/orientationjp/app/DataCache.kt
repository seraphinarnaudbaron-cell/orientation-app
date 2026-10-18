package org.orientationjp.app

import android.content.Context
import java.io.File

/** Copie locale des balises et chemins (JSON brut du serveur) pour travailler sans réseau. */
class DataCache(context: Context) {
    private val dir = context.filesDir

    private fun write(name: String, text: String) {
        try {
            val tmp = File(dir, "$name.tmp")
            tmp.writeText(text)
            tmp.renameTo(File(dir, name))
        } catch (e: Exception) {
        }
    }

    private fun read(name: String): String? {
        return try {
            val f = File(dir, name)
            if (f.exists()) f.readText() else null
        } catch (e: Exception) {
            null
        }
    }

    fun saveBeacons(json: String) = write("beacons.json", json)
    fun saveTrails(json: String) = write("trails.json", json)
    fun beaconsJson(): String? = read("beacons.json")
    fun trailsJson(): String? = read("trails.json")
}

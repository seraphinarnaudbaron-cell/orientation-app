package org.orientationjp.app

import android.content.Context
import java.io.File
import java.util.Locale

/**
 * Sauvegarde sur le téléphone de la trace en cours d'enregistrement (un point par ligne) :
 * si l'app plante ou si le téléphone s'éteint pendant la marche, la trace est reprise au prochain lancement.
 */
class RecordingStore(context: Context) {
    private val file = File(context.filesDir, "recording.txt")

    fun begin(kind: String) {
        try {
            file.writeText(kind + "\n")
        } catch (e: Exception) {
        }
    }

    fun append(p: GeoPt) {
        try {
            file.appendText(String.format(Locale.US, "%.6f,%.6f\n", p.lat, p.lon))
        } catch (e: Exception) {
        }
    }

    fun clear() {
        try {
            file.delete()
        } catch (e: Exception) {
        }
    }

    /** (type, points) de la trace sauvegardée, ou null s'il n'y en a pas. */
    fun load(): Pair<String, List<GeoPt>>? {
        try {
            if (!file.exists()) return null
            val lines = file.readLines()
            if (lines.isEmpty()) return null
            val kind = if (lines[0].trim() == "barrier") "barrier" else "path"
            val pts = ArrayList<GeoPt>()
            for (i in 1 until lines.size) {
                val parts = lines[i].split(",")
                if (parts.size != 2) continue
                val la = parts[0].toDoubleOrNull()
                val lo = parts[1].toDoubleOrNull()
                if (la != null && lo != null) pts.add(GeoPt(la, lo))
            }
            return if (pts.isEmpty()) null else Pair(kind, pts)
        } catch (e: Exception) {
            return null
        }
    }
}

package org.orientationjp.app

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Journal de bugs automatique : les plantages (et quelques erreurs inattendues) sont écrits sur le téléphone,
 * puis envoyés au serveur dès qu'il y a du réseau. Contenu envoyé : type d'erreur, trace technique, pseudo,
 * version de l'app, modèle du téléphone et version d'Android. Jamais la position.
 */
object BugReporter {
    private const val MAX_PENDING = 20
    private var appContext: Context? = null
    private var dir: File? = null
    private val seen = HashSet<String>()

    fun install(context: Context) {
        val app = context.applicationContext
        appContext = app
        dir = File(app.filesDir, "bugs").also { it.mkdirs() }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                save("PLANTAGE", error)
            } catch (e: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Erreur inattendue, sans plantage (la même erreur n'est enregistrée qu'une fois par lancement). */
    fun report(tag: String, error: Throwable) {
        try {
            save(tag, error)
        } catch (e: Throwable) {
        }
    }

    private fun save(tag: String, error: Throwable) {
        val d = dir ?: return
        val message = (error.javaClass.name + ": " + (error.message ?: "")).take(300)
        val signature = tag + "|" + message
        synchronized(seen) {
            if (!seen.add(signature)) return
        }
        if ((d.listFiles()?.size ?: 0) >= MAX_PENDING) return
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        val json = JSONObject()
            .put("tag", tag)
            .put("message", message)
            .put("stack", sw.toString().take(4000))
        File(d, "bug_" + System.currentTimeMillis() + "_" + (0..999).random() + ".json").writeText(json.toString())
    }

    /** Envoie les rapports en attente (appel bloquant : hors thread principal). Réessaie plus tard si pas de réseau. */
    fun sendPending(username: String) {
        val d = dir ?: return
        val ctx = appContext ?: return
        val files = d.listFiles()?.sortedBy { it.name }?.take(5) ?: return
        if (files.isEmpty()) return
        val version = appVersion(ctx)
        val device = (Build.MANUFACTURER + " " + Build.MODEL + " / Android " + Build.VERSION.RELEASE).take(80)
        for (f in files) {
            try {
                val o = JSONObject(f.readText())
                SupabaseApi.logBug(username, version, device, o.optString("tag"), o.optString("message"), o.optString("stack"))
                f.delete()
            } catch (e: ApiException) {
                // trop de rapports : on réessaiera plus tard ; autre refus du serveur : on abandonne ce rapport
                if (e.message?.contains("Trop") == true) return
                f.delete()
            } catch (e: java.io.IOException) {
                return // pas de réseau : plus tard
            } catch (e: Exception) {
                f.delete() // fichier illisible
            }
        }
    }
}

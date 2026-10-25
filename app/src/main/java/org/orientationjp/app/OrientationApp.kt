package org.orientationjp.app

import android.app.Application

class OrientationApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // enregistre les plantages pour les envoyer au serveur au prochain lancement
        BugReporter.install(this)
    }
}

package org.orientationjp.app

object Config {
    const val SUPABASE_URL = "https://ceypqwcxflnqzcggyqor.supabase.co"

    // Clé ANON : publique par conception. La sécurité repose sur le RLS et les fonctions SQL
    // de supabase_setup.sql (le mot de passe admin n'est jamais dans l'application).
    const val SUPABASE_ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImNleXBxd2N4ZmxucXpjZ2d5cW9yIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjM2NjcxMjUsImV4cCI6MjA3OTI0MzEyNX0.slMB-xuck2dajI85Ha-nPXnnaitWpFUmy4sahXMH-cA"

    const val UPDATE_INTERVAL_S = 20L
    const val POSITION_MAX_AGE_S = 120L

    // Pont de l'Amour, Villard-de-Lans
    const val DEFAULT_LAT = 45.0617
    const val DEFAULT_LON = 5.56722
    const val DEFAULT_ZOOM = 16.0

    // Distance (m) à partir de laquelle on considère être arrivé à la balise
    const val BEACON_REACH_M = 25.0

    // ASCII uniquement : OkHttp refuse les accents dans les en-têtes HTTP
    const val USER_AGENT = "OrientationJP/2.0 (College Jean Prevost, Villard-de-Lans)"
}

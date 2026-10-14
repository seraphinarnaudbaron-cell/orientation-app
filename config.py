"""Configuration de l'application Orientation-J.P.

La clé ANON de Supabase est conçue pour être publique : la sécurité repose sur
les politiques RLS définies dans supabase_setup.sql.
Le mot de passe admin n'est PLUS stocké dans l'application (il serait lisible
dans l'APK) : il est vérifié côté serveur par les fonctions SQL admin_*.
"""

SUPABASE_URL = "https://ceypqwcxflnqzcggyqor.supabase.co"
SUPABASE_ANON_KEY = (
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImNleXBxd2N4ZmxucXpjZ2d5cW9yIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjM2NjcxMjUsImV4cCI6MjA3OTI0MzEyNX0.slMB-xuck2dajI85Ha-nPXnnaitWpFUmy4sahXMH-cA"
)

# Intervalle d'envoi / rafraîchissement des positions (secondes)
LOCATION_UPDATE_INTERVAL = 20

# Une position plus vieille que ça n'est plus affichée (secondes)
POSITION_MAX_AGE = 120

# Départ : Pont de l'Amour, Villard-de-Lans
DEFAULT_LATITUDE = 45.0617
DEFAULT_LONGITUDE = 5.56722
DEFAULT_ZOOM = 16

# Distance (m) à laquelle une balise est considérée comme "atteinte"
BEACON_REACH_METERS = 25

USER_AGENT = "OrientationJP/1.0 (Collège Jean Prévost, Villard-de-Lans)"

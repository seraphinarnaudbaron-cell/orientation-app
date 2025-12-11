# Configuration de l'application Orientation-J.P.
# NE PAS COMMITER CE FICHIER DANS UN REPO PUBLIC

import hashlib

# Configuration Supabase
SUPABASE_URL = "https://ceypqwcxflnqzcggyqor.supabase.co"
SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImNleXBxd2N4ZmxucXpjZ2d5cW9yIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjM2NjcxMjUsImV4cCI6MjA3OTI0MzEyNX0.slMB-xuck2dajI85Ha-nPXnnaitWpFUmy4sahXMH-cA"

# Intervalle de mise à jour de la position (en secondes)
LOCATION_UPDATE_INTERVAL = 20

# Coordonnées de démarrage - Pont de l'Amour, Villard-de-Lans
DEFAULT_LATITUDE = 45.0617
DEFAULT_LONGITUDE = 5.56722
DEFAULT_ZOOM = 16

# Mot de passe admin (hashé en SHA-256)
# Mot de passe original : adminator_38250
ADMIN_PASSWORD_HASH = hashlib.sha256("adminator_38250".encode()).hexdigest()

def verify_admin_password(password):
    """Vérifie si le mot de passe admin est correct"""
    return hashlib.sha256(password.encode()).hexdigest() == ADMIN_PASSWORD_HASH

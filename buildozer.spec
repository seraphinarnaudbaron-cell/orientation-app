[app]

# Nom de l'application
title = Orientation-J.P.

# Nom du package (doit être unique)
package.name = orientationjp
package.domain = org.orientationjp

# Fichier source principal
source.dir = .
source.include_exts = py,png,jpg,kv,atlas,json

# Version de l'application
version = 1.0.0

# Dépendances Python (simplifié - pas besoin de kivy_garden.mapview pour le moment)
requirements = python3,kivy==2.3.0,requests,plyer,certifi

# Presplash et icône (optionnel, décommenter si tu as les fichiers)
#icon.filename = %(source.dir)s/icon.png
#presplash.filename = %(source.dir)s/presplash.png

# Orientation supportée
orientation = portrait

# Permissions Android
android.permissions = INTERNET,ACCESS_FINE_LOCATION,ACCESS_COARSE_LOCATION

# Fonctionnalités Android
android.features = android.hardware.location.gps

# API Android cible
android.api = 31
android.minapi = 21

# Architecture cible
android.archs = arm64-v8a,armeabi-v7a

# Accepter les licences SDK automatiquement
android.accept_sdk_license = True

# Mode debug
android.debug = True

# Log level pour debugging
log_level = 2
warn_on_root = 1

[buildozer]

# Répertoire de build
log_level = 2
warn_on_root = 1

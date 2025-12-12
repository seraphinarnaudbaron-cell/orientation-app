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

# Dépendances Python
requirements = python3,kivy==2.3.0,requests,plyer,certifi

# Presplash et icône (optionnel, décommenter si tu as les fichiers)
#icon.filename = %(source.dir)s/icon.png
#presplash.filename = %(source.dir)s/presplash.png

# Orientation supportée
orientation = portrait

# Services Android
services = 

# Permissions Android
android.permissions = INTERNET,ACCESS_FINE_LOCATION,ACCESS_COARSE_LOCATION,WRITE_EXTERNAL_STORAGE,READ_EXTERNAL_STORAGE

# Fonctionnalités Android
android.features = android.hardware.location.gps

# API Android cible
android.api = 31
android.minapi = 21
android.ndk = 25b

# Architecture cible
android.archs = arm64-v8a,armeabi-v7a

# Bootstrap python-for-android
p4a.bootstrap = sdl2

# Modules Python à inclure
android.add_src = 

# Accepter les licences SDK automatiquement
android.accept_sdk_license = True

# Mode debug ou release
android.release = False

# Nom du package APK
android.ouya.category = GAME
android.ouya.icon.filename = %(source.dir)s/icon.png

# Manifest Android (optionnel)
android.manifest.intent_filters = 

# Métadonnées
android.meta_data = 

# Librairies Android additionnelles
android.add_libs_armeabi_v7a = 
android.add_libs_arm64_v8a = 

# Gradle
android.gradle_dependencies = 

# Activer AndroidX
android.enable_androidx = True

# Activer les logs
log_level = 2

# Ne pas copier les librairies
android.copy_libs = 1

# Whitelist
android.whitelist = lib-dynload/termios.so

# Presplash color
#presplash.color = #008000

[buildozer]

# Répertoire de build
log_level = 2
warn_on_root = 1

# Chemins
#android.sdk_path = 
#android.ndk_path =
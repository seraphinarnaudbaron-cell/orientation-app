[app]

title = Orientation J.P.
package.name = orientationjp
package.domain = org.orientationjp

source.dir = .
source.include_exts = py,png,jpg,kv,atlas,json
source.exclude_dirs = .git,.github,.buildozer,bin,__pycache__,tests
source.exclude_patterns = *.sh,*.sql,Dockerfile,README.md,.gitignore

version = 1.1.0

# kivy_garden.mapview : carte OSM ; requests + dépendances pour l'API Supabase ;
# plyer + pyjnius : GPS Android
requirements = python3,kivy==2.3.0,kivy_garden.mapview,requests,urllib3,idna,charset-normalizer,certifi,plyer,pyjnius,android

icon.filename = %(source.dir)s/assets/icon.png
presplash.filename = %(source.dir)s/assets/presplash.png
android.presplash_color = #1B4332

orientation = portrait
fullscreen = 0

android.permissions = INTERNET,ACCESS_FINE_LOCATION,ACCESS_COARSE_LOCATION

android.api = 33
android.minapi = 21
android.ndk_api = 21
android.archs = arm64-v8a,armeabi-v7a
android.accept_sdk_license = True

# Évite l'extraction de l'APK à chaque lancement
android.allow_backup = False

[buildozer]
log_level = 2
warn_on_root = 1


[app]
title = OrientationApp
package.name = orientationapp
package.domain = org.example
source.dir = .
version = 1.0.0
source.include_exts = py,kv,png,jpg
requirements = python3==3.10, kivy==2.3.0, kivymd, kivy_garden.mapview, pyjnius, requests
orientation = portrait
fullscreen = 0
android.api = 33
android.minapi = 21
android.ndk = 25b

[buildozer]
log_level = 2

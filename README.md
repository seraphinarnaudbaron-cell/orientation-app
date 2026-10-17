# Orientation J.P.

Application Android de course d'orientation — **Collège Jean Prévost, Villard-de-Lans**.
Kotlin natif, Jetpack Compose, carte OpenStreetMap (osmdroid), backend Supabase.

> L'ancienne version Python/Kivy est conservée sur la branche `legacy-kivy`.

## Fonctionnalités

- Carte OSM interactive (pincer pour zoomer), départ au Pont de l'Amour
- Position des autres coureurs en direct (mode privé pour se cacher)
- Balises : un clic sur une balise affiche son motif de poinçon (grille 3×3) à reproduire sur la carte papier, ou lance « Y aller »
- « Y aller » : itinéraire qui suit les chemins enregistrés par l'admin (sinon ligne droite, distance et cap)
- Recherche et liste des balises ; zoom jusqu'au niveau 22 et cercle de précision du GPS
- **Parcours** : tu donnes les numéros des balises, tu poses l'arrivée sur la carte (point rouge), l'app trouve l'ordre le plus rapide (calcul exact jusqu'à 15 balises) en suivant les chemins
- Panel admin : statistiques, ajout de balise au centre de la carte, suppression
- **Obstacles** : l'admin trace une rivière, une clôture... aucun chemin n'est relié à travers (sauf un chemin enregistré qui la traverse, ex. un pont)
- **Dénivelé réel** : les altitudes viennent de l'IGN (RGE ALTI) ; l'itinéraire est le plus *rapide* (formule de Tobler), pas le plus court en 2D ; deux chemins proches mais à des niveaux différents (falaise) ne sont pas reliés
- **Mode créateur de chemin** : l'admin marche, le GPS enregistre sa trace. Les traces qui passent à moins de 5 m l'une de l'autre sont reliées (nœuds) ; l'app calcule le plus court chemin (Dijkstra) sur ce réseau et le trace au coureur.

## Mise en route

### 1. Base de données (Supabase)
Exécuter `supabase_setup.sql` dans **SQL Editor** (une fois, en remplaçant `CHANGE_MOI` par le mot de passe admin).
Le mot de passe admin est vérifié côté serveur : il n'est ni dans le code ni dans l'APK.
Si le projet est en pause (offre gratuite), le restaurer depuis le dashboard.

### 2. Compiler l'APK
- **GitHub Actions** : chaque push sur `main` compile l'APK (≈ 5-8 min) → onglet *Actions* → artifact `orientation-jp-debug`.
- **Android Studio** : ouvrir le dossier, *Run*.
- **Ligne de commande** : `./gradlew assembleDebug` → `app/build/outputs/apk/debug/`.

### 3. Installer
Copier l'APK sur le téléphone et l'ouvrir (autoriser les sources inconnues).

## Structure

```
app/src/main/java/org/orientationjp/app/
  MainActivity.kt         point d'entrée, permissions, navigation
  AppViewModel.kt         état et logique (session, GPS, réseau, balises, admin)
  SupabaseApi.kt          client REST / RPC Supabase
  LocationTracker.kt      GPS (LocationManager)
  MapScreen.kt            carte, marqueurs, navigation
  BeaconSheet.kt          fiche balise + liste
  SettingsAdminScreens.kt menu et panel admin
  LoginScreen.kt, Components.kt, Theme.kt, Models.kt, Geo.kt, Prefs.kt, Config.kt
supabase_setup.sql        tables, RLS et fonctions serveur
```

## Dépannage
- **« Serveur injoignable »** : Supabase en pause ou téléphone hors ligne.
- **« Base non initialisée »** : `supabase_setup.sql` n'a pas été exécuté.
- **Carte grise** : pas de connexion.
- **Pas de position** : autoriser la localisation, tester en extérieur.

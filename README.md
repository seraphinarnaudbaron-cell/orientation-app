# Orientation J.P.

Application mobile de course d'orientation — **Collège Jean Prévost, Villard-de-Lans**.
Carte OpenStreetMap, positions en direct, balises à poinçons 3×3, navigation vers une balise, mode privé et panel admin.

## Fonctionnalités

- Carte OSM interactive (pincer pour zoomer), départ au Pont de l'Amour
- Position des autres coureurs en temps réel (mode privé pour se cacher)
- Balises : on reproduit le motif de poinçons vu sur la balise (grille 3×3) ; la validation exige d'être à moins de 25 m (si le GPS est actif)
- « Y aller » : ligne + distance + cap jusqu'à la balise
- Recherche et liste des balises
- Panel admin : statistiques, ajout de balise au centre de la carte, suppression

## Mise en route

### 1. Base de données (Supabase)

1. Si le projet est en pause (offre gratuite, 7 jours d'inactivité), le **restaurer** depuis le dashboard Supabase.
2. Ouvrir `supabase_setup.sql`, remplacer `CHANGE_MOI` par ton mot de passe admin, puis l'exécuter dans **SQL Editor**.

Le mot de passe admin n'est **plus dans l'application** : il est vérifié côté serveur (fonctions `admin_*`), donc personne ne peut le lire dans l'APK ni dans GitHub.

### 2. Tester sur PC

```bash
pip install -r requirements.txt
python main.py
```

Sans GPS, la position par défaut (Pont de l'Amour) est utilisée.

### 3. Compiler l'APK

- **GitHub Actions** : chaque push sur `main` lance le workflow *Build Android APK* (20-30 min) ; l'APK est dans les *artifacts*.
- **En local** : `buildozer android debug` → `bin/*.apk`.

## Structure

```
main.py              application Kivy (écrans, GPS, carte, logique)
orientation.kv       interface (thème vert / orange)
api.py               client Supabase (REST + RPC) en threads
config.py            URL / clé publique Supabase, constantes
supabase_setup.sql   tables, RLS et fonctions serveur
assets/              icônes, marqueurs, champs
buildozer.spec       configuration Android
.github/workflows/   compilation automatique
```

## Dépannage

- **« Serveur injoignable »** : le projet Supabase est probablement en pause, ou le téléphone est hors ligne.
- **« Base non initialisée »** : `supabase_setup.sql` n'a pas été exécuté.
- **Carte grise** : pas de connexion (les tuiles sont téléchargées en HTTPS depuis openstreetmap.org).
- **GPS** : autoriser la localisation dans les réglages Android, tester en extérieur.

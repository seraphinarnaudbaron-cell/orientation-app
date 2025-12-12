# Orientation-J.P.

Application mobile de course d'orientation avec carte OpenStreetMap, localisation en temps réel et gestion de balises.

**Collège Jean Prévost - Villard-de-Lans**

## 📱 Fonctionnalités

- 🗺️ Carte OpenStreetMap interactive (pinch-to-zoom)
- 📍 Localisation en temps réel des utilisateurs
- 🎯 Balises avec poinçons 3×3 interactifs
- 🧭 Navigation vers les balises
- 🔒 Mode privé pour désactiver le partage de position
- 👨‍💼 Panel admin protégé par mot de passe
- 📊 Statistiques d'utilisation

## 🎯 Zone de démarrage

L'application démarre automatiquement centrée sur :
- **Lieu** : Le Pont de l'Amour, Villard-de-Lans
- **Coordonnées** : 45.0617° N, 5.5672° E
- **Zoom** : 16 (niveau détail optimal)

## 🚀 Installation

### Prérequis
- Python 3.10+
- Android SDK (pour la compilation)
- Buildozer

### Configuration rapide

1. **Cloner le projet**
```bash
git clone https://github.com/seraphinarnaudbaron-cell/orientation-app.git
cd orientation-app
```

2. **Installer les dépendances**
```bash
pip install -r requirements.txt
```

3. **Configurer Supabase**
- Exécuter le script `supabase_setup.sql` dans l'éditeur SQL Supabase
- La clé ANON est déjà configurée dans `config.py`

## 📦 Compilation Android

### Option 1 : GitHub Actions (automatique)

Le workflow GitHub Actions compile automatiquement l'APK à chaque push sur `main`.

1. Push le code sur GitHub
2. Aller dans l'onglet "Actions"
3. Attendre la fin de la compilation (~20-30 min)
4. Télécharger l'APK dans les artifacts

### Option 2 : Build local

```bash
buildozer android debug
```

L'APK sera dans `bin/orientationjp-1.0.0-arm64-v8a-debug.apk`

## 📲 Distribution

### Créer un QR code

1. Upload l'APK sur GitHub Releases ou un serveur web
2. Générer un QR code avec l'URL de téléchargement
3. Les utilisateurs scannent le QR pour télécharger

### Installation sur Android

1. Activer "Sources inconnues" dans Paramètres → Sécurité
2. Scanner le QR code ou télécharger l'APK
3. Installer l'application
4. Autoriser la localisation GPS

## 🔧 Configuration

### Fichiers importants

- `config.py` : Configuration Supabase et coordonnées de démarrage
- `main.py` : Application Kivy principale
- `buildozer.spec` : Configuration de build Android
- `supabase_setup.sql` : Script de création des tables

### Base de données Supabase

**Tables créées** :
- `positions` : Positions des utilisateurs en temps réel
- `beacons` : Balises de course d'orientation
- `paths` : Chemins enregistrés par l'admin
- `app_stats` : Statistiques d'utilisation

## 🎮 Utilisation

### Pour les utilisateurs

1. Lancer l'application
2. Entrer son nom d'utilisateur
3. Autoriser l'accès à la localisation
4. La carte s'ouvre sur le Pont de l'Amour
5. Cliquer sur les balises pour voir les poinçons
6. Utiliser la recherche pour trouver une balise spécifique

### Pour l'admin

1. Paramètres → Admin Panel
2. Entrer le mot de passe admin
3. Accéder à la gestion des balises, chemins et statistiques

## 🔐 Sécurité

- Mot de passe admin hashé en SHA-256
- Clés Supabase non exposées publiquement
- Row Level Security (RLS) activé sur toutes les tables
- Mode privé pour désactiver le partage de position

## 🐛 Dépannage

### GPS ne fonctionne pas
- Vérifier les permissions dans Paramètres Android
- Tester dehors (le GPS peut ne pas fonctionner en intérieur)

### Pas d'autres utilisateurs visibles
- Vérifier que d'autres utilisateurs sont connectés
- Vérifier dans Supabase que la table `positions` contient des données

### Build échoue
- Vérifier que toutes les dépendances sont installées
- Consulter les logs GitHub Actions pour plus de détails

## 📊 Structure du projet

```
orientation-app/
├── main.py                 # Application Kivy principale
├── config.py              # Configuration (Supabase, coordonnées)
├── buildozer.spec         # Configuration de build Android
├── requirements.txt       # Dépendances Python
├── supabase_setup.sql     # Script SQL pour créer les tables
├── README.md             # Ce fichier
├── .gitignore            # Fichiers à ignorer
└── .github/
    └── workflows/
        └── android-build.yml  # CI/CD automatique
```

## 📝 Ajouter des balises

### Via SQL (Supabase)

```sql
INSERT INTO beacons (latitude, longitude, pattern, description) 
VALUES (45.0620, 5.5675, '101010101', 'Description de la balise');
```

Le pattern est une chaîne de 9 caractères ('0' ou '1') représentant une grille 3×3.

### Via l'application (TODO)

Fonctionnalité en cours de développement dans le panel admin.

## 🆘 Support

Pour toute question ou problème :
1. Consulter la section "Dépannage" ci-dessus
2. Vérifier les logs GitHub Actions
3. Vérifier la connexion Supabase

## 📄 Licence

Projet éducatif - Collège Jean Prévost

---

**Version** : 1.0.0  
**Date** : Décembre 2024


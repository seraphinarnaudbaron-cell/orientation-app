#!/bin/bash

# ============================================
# COMMANDES GIT POUR ORIENTATION-J.P.
# Exécuter depuis /home/seraphin/Documents/orientation_claude/
# ============================================

cd /home/seraphin/Documents/orientation_claude

echo "🔧 Configuration Git..."

# Configurer ton nom et email (si pas déjà fait)
git config --global user.name "seraphinarnaudbaron-cell"
git config --global user.email "ton-email@example.com"  # Remplace par ton email

# Initialiser Git si nécessaire
if [ ! -d .git ]; then
    echo "📦 Initialisation du dépôt Git..."
    git init
fi

# Ajouter le remote GitHub (supprimer l'ancien si existe)
echo "🔗 Configuration du remote GitHub..."
git remote remove origin 2>/dev/null
git remote add origin https://github.com/seraphinarnaudbaron-cell/orientation-app.git

# Ajouter tous les fichiers
echo "➕ Ajout des fichiers..."
git add .

# Commit
echo "💾 Création du commit..."
git commit -m "Configuration complète Orientation-J.P. avec CI/CD"

# Créer/basculer sur la branche main
echo "🌿 Basculement sur la branche main..."
git branch -M main

# Pousser sur GitHub
echo "📤 Push vers GitHub..."
echo "⚠️  Tu devras peut-être t'authentifier avec ton token GitHub"
git push -u origin main --force

echo ""
echo "✅ TERMINÉ !"
echo ""
echo "📋 Prochaines étapes :"
echo "1. Va sur https://github.com/seraphinarnaudbaron-cell/orientation-app"
echo "2. Clique sur l'onglet 'Actions'"
echo "3. Le workflow 'Build Android APK' va se lancer automatiquement"
echo "4. Attends ~20-30 minutes"
echo "5. Télécharge l'APK dans les artifacts"
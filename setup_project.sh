#!/bin/bash

# Script de configuration du projet Orientation-J.P.
# Exécuter depuis /home/seraphin/Documents/orientation_claude/

PROJECT_DIR="/home/seraphin/Documents/orientation_claude"

echo "📦 Configuration du projet Orientation-J.P."
echo "📁 Répertoire : $PROJECT_DIR"

# Créer la structure si nécessaire
mkdir -p "$PROJECT_DIR/.github/workflows"

echo "✅ Tous les fichiers ont été créés manuellement dans les artifacts."
echo "📋 Copie les fichiers suivants depuis les artifacts :"
echo "   - config.py"
echo "   - main.py"
echo "   - buildozer.spec"
echo "   - requirements.txt"
echo "   - supabase_setup.sql"
echo "   - README.md"
echo "   - GUIDE_COMPLET.md"
echo "   - .gitignore"
echo "   - Dockerfile"
echo "   - .github/workflows/android-build.yml"

echo ""
echo "🔄 Initialisation Git..."

cd "$PROJECT_DIR"

# Initialiser Git si pas déjà fait
if [ ! -d .git ]; then
    git init
    echo "✅ Git initialisé"
fi

# Ajouter le remote GitHub
git remote remove origin 2>/dev/null
git remote add origin https://github.com/seraphinarnaudbaron-cell/orientation-app.git

echo "✅ Remote GitHub configuré"
echo ""
echo "📤 Pour pousser sur GitHub, exécute :"
echo "   git add ."
echo "   git commit -m 'Configuration complète Orientation-J.P.'"
echo "   git branch -M main"
echo "   git push -u origin main"
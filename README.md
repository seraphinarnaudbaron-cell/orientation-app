Orientation – C. Jean Prévost

Instructions :
1) REMPLACE dans src/config.py la valeur <REMPLACEZ_PAR_VOTRE_ANON_KEY> par ta ANON KEY Supabase.
   (Supabase → Project → Settings → API → Project API keys → anon public key)

2) Dans Supabase -> SQL Editor : exécute le fichier supabase_setup.sql pour créer la table positions.

3) Tester localement (Windows PowerShell) :
    python -m venv venv
    .\\venv\\Scripts\\Activate.ps1
    pip install --upgrade pip setuptools wheel
    pip install kivy==2.1.0 kivy_garden.mapview requests

    python src\\main.py

4) Pour builder l'APK : mieux via GitHub Actions (workflow fourni) ou WSL + buildozer (Ubuntu). Windows natif + buildozer n'est pas recommandé.

5) Hébergement APK : pousse sur GitHub, crée une Release et uploade l'APK. Génère un QR pointant vers le lien de la release pour que les utilisateurs téléchargent.

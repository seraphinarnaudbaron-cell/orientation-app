# admin.py : mot de passe admin embarqué (ne pas mettre en clair sur GitHub en prod)
ADMIN_PASSWORD = "adminator_38250"

def check_password(pw: str) -> bool:
    return pw == ADMIN_PASSWORD

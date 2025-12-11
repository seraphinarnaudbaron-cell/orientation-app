"""
Application Orientation-J.P.
Application de course d'orientation avec carte OpenStreetMap
Collège Jean Prévost - Villard-de-Lans
"""

from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.floatlayout import FloatLayout
from kivy.uix.button import Button
from kivy.uix.label import Label
from kivy.uix.textinput import TextInput
from kivy.uix.popup import Popup
from kivy.uix.togglebutton import ToggleButton
from kivy.uix.gridlayout import GridLayout
from kivy.clock import Clock
from kivy.graphics import Color, Ellipse, Line
from kivy_garden.mapview import MapView, MapMarker, MapMarkerPopup
from kivy.core.window import Window
import requests
import json
from datetime import datetime
import uuid
import config

try:
    from plyer import gps
    GPS_AVAILABLE = True
except ImportError:
    GPS_AVAILABLE = False
    print("GPS non disponible (plyer non installé)")


class BeaconMarker(MapMarker):
    """Marqueur personnalisé pour une balise"""
    def __init__(self, beacon_data, **kwargs):
        super().__init__(**kwargs)
        self.beacon_data = beacon_data
        self.lat = beacon_data['latitude']
        self.lon = beacon_data['longitude']
        self.selected = False
        
    def on_release(self):
        """Quand on clique sur la balise"""
        app = App.get_running_app()
        app.show_beacon_menu(self)


class UserMarker(MapMarker):
    """Marqueur pour un utilisateur"""
    def __init__(self, user_data, **kwargs):
        super().__init__(**kwargs)
        self.user_data = user_data
        self.lat = user_data['latitude']
        self.lon = user_data['longitude']


class OrientationApp(App):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.user_id = str(uuid.uuid4())
        self.username = None
        self.current_position = None
        self.private_mode = False
        self.user_markers = {}
        self.beacon_markers = {}
        self.selected_beacons = []
        self.gps_started = False
        
    def build(self):
        """Construction de l'interface"""
        self.title = "Orientation-J.P."
        self.layout = FloatLayout()
        
        # Carte centrée sur le Pont de l'Amour
        self.mapview = MapView(
            zoom=config.DEFAULT_ZOOM,
            lat=config.DEFAULT_LATITUDE,
            lon=config.DEFAULT_LONGITUDE
        )
        self.layout.add_widget(self.mapview)
        
        # Barre supérieure
        self.top_bar = BoxLayout(
            orientation='horizontal',
            size_hint=(1, 0.08),
            pos_hint={'top': 1}
        )
        
        # Bouton paramètres
        settings_btn = Button(
            text='Paramètres',
            size_hint=(0.25, 1),
            background_color=(0.2, 0.6, 0.2, 1)
        )
        settings_btn.bind(on_press=self.show_settings)
        self.top_bar.add_widget(settings_btn)
        
        # Barre de recherche
        self.search_input = TextInput(
            hint_text='n° de balise',
            size_hint=(0.45, 1),
            multiline=False
        )
        self.search_input.bind(on_text_validate=self.search_beacon)
        self.top_bar.add_widget(self.search_input)
        
        # Bouton mode privé
        self.private_btn = ToggleButton(
            text='Privé',
            size_hint=(0.3, 1),
            background_color=(0.5, 0.5, 0.5, 1)
        )
        self.private_btn.bind(on_press=self.toggle_private_mode)
        self.top_bar.add_widget(self.private_btn)
        
        self.layout.add_widget(self.top_bar)
        
        # Label de statut (en bas)
        self.status_label = Label(
            text='Initialisation...',
            size_hint=(1, 0.05),
            pos_hint={'bottom': 1, 'x': 0}
        )
        self.layout.add_widget(self.status_label)
        
        # Demander le nom d'utilisateur au premier lancement
        Clock.schedule_once(lambda dt: self.ask_username(), 0.5)
        
        # Démarrer la géolocalisation
        if GPS_AVAILABLE:
            try:
                gps.configure(on_location=self.on_gps_location, on_status=self.on_gps_status)
                gps.start(minTime=1000, minDistance=0)
                self.gps_started = True
                self.status_label.text = 'GPS démarré'
            except Exception as e:
                print(f"Erreur GPS: {e}")
                self.status_label.text = 'GPS non disponible'
        else:
            self.status_label.text = 'GPS non disponible'
            # Position de test pour le développement
            self.current_position = {
                'latitude': config.DEFAULT_LATITUDE,
                'longitude': config.DEFAULT_LONGITUDE
            }
        
        # Mise à jour périodique
        Clock.schedule_interval(self.update_positions, config.LOCATION_UPDATE_INTERVAL)
        Clock.schedule_interval(self.fetch_users, 5)
        Clock.schedule_interval(self.fetch_beacons, 10)
        
        # Charger les balises
        Clock.schedule_once(lambda dt: self.fetch_beacons(0), 1)
        
        return self.layout
    
    def on_gps_status(self, stype, status):
        """Callback du statut GPS"""
        self.status_label.text = f'GPS: {status}'
    
    def ask_username(self):
        """Demande le nom d'utilisateur au démarrage"""
        content = BoxLayout(orientation='vertical', padding=10, spacing=10)
        
        label = Label(text='Entrez votre nom d\'utilisateur:')
        content.add_widget(label)
        
        username_input = TextInput(multiline=False)
        content.add_widget(username_input)
        
        def save_username(instance):
            self.username = username_input.text or f"User_{self.user_id[:8]}"
            self.status_label.text = f'Bienvenue {self.username}'
            popup.dismiss()
        
        save_btn = Button(text='Valider', size_hint=(1, 0.3))
        save_btn.bind(on_press=save_username)
        content.add_widget(save_btn)
        
        popup = Popup(
            title='Bienvenue sur Orientation-J.P.',
            content=content,
            size_hint=(0.8, 0.4),
            auto_dismiss=False
        )
        popup.open()
    
    def on_gps_location(self, **kwargs):
        """Callback quand la position GPS change"""
        self.current_position = {
            'latitude': kwargs.get('lat'),
            'longitude': kwargs.get('lon')
        }
        self.status_label.text = f'Position: {kwargs.get("lat"):.5f}, {kwargs.get("lon"):.5f}'
    
    def update_positions(self, dt):
        """Envoie la position au serveur"""
        if self.private_mode or not self.current_position or not self.username:
            return
        
        data = {
            'user_id': self.user_id,
            'username': self.username,
            'latitude': self.current_position['latitude'],
            'longitude': self.current_position['longitude'],
            'updated_at': datetime.now().isoformat()
        }
        
        try:
            headers = {
                'apikey': config.SUPABASE_ANON_KEY,
                'Authorization': f'Bearer {config.SUPABASE_ANON_KEY}',
                'Content-Type': 'application/json',
                'Prefer': 'resolution=merge-duplicates'
            }
            
            url = f"{config.SUPABASE_URL}/rest/v1/positions"
            response = requests.post(url, json=data, headers=headers)
            
            if response.status_code in [200, 201]:
                print(f"Position mise à jour: {response.status_code}")
            else:
                print(f"Erreur position: {response.status_code} - {response.text}")
        except Exception as e:
            print(f"Erreur mise à jour position: {e}")
    
    def fetch_users(self, dt):
        """Récupère les positions des autres utilisateurs"""
        try:
            headers = {
                'apikey': config.SUPABASE_ANON_KEY,
                'Authorization': f'Bearer {config.SUPABASE_ANON_KEY}'
            }
            
            url = f"{config.SUPABASE_URL}/rest/v1/positions?select=*"
            response = requests.get(url, headers=headers, timeout=5)
            
            if response.status_code == 200:
                users = response.json()
                self.update_user_markers(users)
            else:
                print(f"Erreur fetch users: {response.status_code}")
        except Exception as e:
            print(f"Erreur récupération users: {e}")
    
    def update_user_markers(self, users):
        """Met à jour les marqueurs utilisateurs sur la carte"""
        # Supprimer les anciens marqueurs
        for marker in list(self.user_markers.values()):
            self.mapview.remove_marker(marker)
        self.user_markers.clear()
        
        # Ajouter les nouveaux
        for user in users:
            if user['user_id'] != self.user_id:  # Ne pas afficher soi-même
                marker = UserMarker(user)
                self.mapview.add_marker(marker)
                self.user_markers[user['user_id']] = marker
    
    def fetch_beacons(self, dt):
        """Charge les balises depuis Supabase"""
        try:
            headers = {
                'apikey': config.SUPABASE_ANON_KEY,
                'Authorization': f'Bearer {config.SUPABASE_ANON_KEY}'
            }
            
            url = f"{config.SUPABASE_URL}/rest/v1/beacons?select=*"
            response = requests.get(url, headers=headers, timeout=5)
            
            if response.status_code == 200:
                beacons = response.json()
                self.update_beacon_markers(beacons)
            else:
                print(f"Erreur fetch beacons: {response.status_code}")
        except Exception as e:
            print(f"Erreur récupération balises: {e}")
    
    def update_beacon_markers(self, beacons):
        """Met à jour les marqueurs de balises sur la carte"""
        # Supprimer les anciens marqueurs
        for marker in list(self.beacon_markers.values()):
            self.mapview.remove_marker(marker)
        self.beacon_markers.clear()
        
        # Ajouter les nouveaux
        for beacon in beacons:
            marker = BeaconMarker(beacon)
            self.mapview.add_marker(marker)
            self.beacon_markers[beacon['id']] = marker
    
    def show_beacon_menu(self, marker):
        """Affiche le menu contextuel d'une balise"""
        marker.selected = not marker.selected
        
        content = BoxLayout(orientation='vertical', padding=10, spacing=10)
        
        # Numéro de la balise
        title_label = Label(
            text=f"Balise n°{marker.beacon_data['id']}",
            size_hint=(1, 0.15),
            bold=True
        )
        content.add_widget(title_label)
        
        # Description
        if marker.beacon_data.get('description'):
            desc_label = Label(
                text=marker.beacon_data['description'],
                size_hint=(1, 0.15)
            )
            content.add_widget(desc_label)
        
        # Grille 3x3 du poinçon
        pattern_label = Label(text='Poinçon:', size_hint=(1, 0.1))
        content.add_widget(pattern_label)
        
        pattern_grid = GridLayout(cols=3, size_hint=(1, 0.4), spacing=5)
        pattern = marker.beacon_data.get('pattern', '000000000')
        
        for i, cell in enumerate(pattern):
            btn = ToggleButton(
                text='',
                state='down' if cell == '1' else 'normal',
                disabled=True,  # Non éditable pour l'utilisateur
                background_color=(0.2, 0.6, 0.2, 1) if cell == '1' else (0.8, 0.8, 0.8, 1)
            )
            pattern_grid.add_widget(btn)
        
        content.add_widget(pattern_grid)
        
        # Bouton S'y rendre
        navigate_btn = Button(
            text='S\'y rendre',
            size_hint=(1, 0.2),
            background_color=(0.2, 0.6, 0.2, 1)
        )
        navigate_btn.bind(on_press=lambda x: self.navigate_to(marker, popup))
        content.add_widget(navigate_btn)
        
        popup = Popup(
            title='Balise',
            content=content,
            size_hint=(0.8, 0.7)
        )
        popup.open()
    
    def navigate_to(self, marker, popup):
        """Navigation vers une balise"""
        self.mapview.center_on(marker.lat, marker.lon)
        self.status_label.text = f'Navigation vers balise {marker.beacon_data["id"]}'
        popup.dismiss()
    
    def search_beacon(self, instance):
        """Recherche une balise par numéro"""
        try:
            beacon_id = int(self.search_input.text)
            if beacon_id in self.beacon_markers:
                marker = self.beacon_markers[beacon_id]
                self.mapview.center_on(marker.lat, marker.lon)
                self.show_beacon_menu(marker)
            else:
                self.status_label.text = f'Balise {beacon_id} introuvable'
        except ValueError:
            self.status_label.text = 'Entrez un numéro valide'
    
    def toggle_private_mode(self, instance):
        """Active/désactive le mode privé"""
        self.private_mode = instance.state == 'down'
        if self.private_mode:
            instance.background_color = (0.8, 0.2, 0.2, 1)
            self.status_label.text = 'Mode privé activé'
        else:
            instance.background_color = (0.5, 0.5, 0.5, 1)
            self.status_label.text = 'Mode privé désactivé'
    
    def show_settings(self, instance):
        """Affiche les paramètres"""
        content = BoxLayout(orientation='vertical', padding=10, spacing=10)
        
        # Changer nom d'utilisateur
        username_label = Label(text=f'Nom: {self.username}', size_hint=(1, 0.2))
        content.add_widget(username_label)
        
        username_input = TextInput(text=self.username, multiline=False, size_hint=(1, 0.2))
        content.add_widget(username_input)
        
        def save_settings(instance):
            self.username = username_input.text
            self.status_label.text = f'Nom changé: {self.username}'
            popup.dismiss()
        
        save_btn = Button(text='Sauvegarder', size_hint=(1, 0.2))
        save_btn.bind(on_press=save_settings)
        content.add_widget(save_btn)
        
        # Infos GPS
        gps_info = Label(
            text=f'GPS: {"Actif" if self.gps_started else "Inactif"}',
            size_hint=(1, 0.2)
        )
        content.add_widget(gps_info)
        
        # Bouton Admin Panel
        admin_btn = Button(
            text='Admin Panel',
            size_hint=(1, 0.2),
            background_color=(0.6, 0.3, 0.3, 1)
        )
        admin_btn.bind(on_press=self.show_admin_login)
        content.add_widget(admin_btn)
        
        popup = Popup(
            title='Paramètres',
            content=content,
            size_hint=(0.8, 0.6)
        )
        popup.open()
    
    def show_admin_login(self, instance):
        """Affiche la popup de connexion admin"""
        content = BoxLayout(orientation='vertical', padding=10, spacing=10)
        
        label = Label(text='Mot de passe admin:', size_hint=(1, 0.3))
        content.add_widget(label)
        
        password_input = TextInput(password=True, multiline=False, size_hint=(1, 0.3))
        content.add_widget(password_input)
        
        error_label = Label(text='', color=(1, 0, 0, 1), size_hint=(1, 0.2))
        content.add_widget(error_label)
        
        def check_password(instance):
            if config.verify_admin_password(password_input.text):
                popup.dismiss()
                self.show_admin_panel()
            else:
                error_label.text = 'Mot de passe incorrect'
        
        login_btn = Button(text='Connexion', size_hint=(1, 0.2))
        login_btn.bind(on_press=check_password)
        content.add_widget(login_btn)
        
        popup = Popup(
            title='Connexion Admin',
            content=content,
            size_hint=(0.8, 0.5)
        )
        popup.open()
    
    def show_admin_panel(self):
        """Affiche le panel admin"""
        content = BoxLayout(orientation='vertical', padding=10, spacing=10)
        
        title = Label(text='Panel Admin', size_hint=(1, 0.15), bold=True)
        content.add_widget(title)
        
        # Boutons admin
        manage_beacons = Button(
            text='Gérer les balises',
            size_hint=(1, 0.2),
            background_color=(0.2, 0.6, 0.2, 1)
        )
        manage_beacons.bind(on_press=lambda x: self.status_label.setText('Gestion balises - TODO'))
        content.add_widget(manage_beacons)
        
        manage_paths = Button(
            text='Gérer les chemins',
            size_hint=(1, 0.2),
            background_color=(0.2, 0.6, 0.2, 1)
        )
        manage_paths.bind(on_press=lambda x: self.status_label.setText('Gestion chemins - TODO'))
        content.add_widget(manage_paths)
        
        stats = Button(
            text='Statistiques',
            size_hint=(1, 0.2),
            background_color=(0.2, 0.6, 0.2, 1)
        )
        stats.bind(on_press=lambda x: self.show_stats())
        content.add_widget(stats)
        
        close_btn = Button(text='Fermer', size_hint=(1, 0.2))
        close_btn.bind(on_press=lambda x: popup.dismiss())
        content.add_widget(close_btn)
        
        popup = Popup(
            title='Administration',
            content=content,
            size_hint=(0.9, 0.7)
        )
        popup.open()
    
    def show_stats(self):
        """Affiche les statistiques"""
        try:
            headers = {
                'apikey': config.SUPABASE_ANON_KEY,
                'Authorization': f'Bearer {config.SUPABASE_ANON_KEY}'
            }
            
            # Compter les utilisateurs actifs
            url = f"{config.SUPABASE_URL}/rest/v1/positions?select=user_id"
            response = requests.get(url, headers=headers, timeout=5)
            
            if response.status_code == 200:
                users = response.json()
                nb_users = len(users)
                
                # Compter les balises
                url = f"{config.SUPABASE_URL}/rest/v1/beacons?select=id"
                response = requests.get(url, headers=headers, timeout=5)
                nb_beacons = len(response.json()) if response.status_code == 200 else 0
                
                msg = f'Utilisateurs actifs: {nb_users}\nBalises: {nb_beacons}'
            else:
                msg = 'Erreur récupération stats'
        except Exception as e:
            msg = f'Erreur: {e}'
        
        content = BoxLayout(orientation='vertical', padding=10)
        label = Label(text=msg)
        content.add_widget(label)
        
        popup = Popup(title='Statistiques', content=content, size_hint=(0.7, 0.4))
        popup.open()
    
    def on_stop(self):
        """Arrêt de l'application"""
        if GPS_AVAILABLE and self.gps_started:
            gps.stop()


if __name__ == '__main__':
    OrientationApp().run()


# main.py
# Orientation App (version with admin on-site, graph routing, TSP)
# ADMIN PASSWORD set per user request.

ADMIN_PASSWORD = "Lacacahuette38"  # <-- set by user

import json
import math
import os
from heapq import heappush, heappop
from copy import deepcopy

from kivy.app import App
from kivy.clock import Clock
from kivy.lang import Builder
from kivy.metrics import dp
from kivy.properties import ObjectProperty, NumericProperty, StringProperty, ListProperty
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.popup import Popup
from kivy.uix.button import Button
from kivy.uix.label import Label
from kivy.uix.image import Image
from kivy.uix.textinput import TextInput
from plyer import gps

from kivy_garden.mapview import MapView, MapMarkerPopup, MapLayer

KV = '''
<FloatButton@Button>:
    size_hint: None, None
    size: dp(52), dp(52)
    background_normal: ''
    background_color: 0.06,0.45,0.2,1  # green-ish
    color: 1,1,1,1
    font_size: '18sp'
    halign: 'center'
    valign: 'middle'

<RootWidget>:
    orientation: 'vertical'
    mapview: mapview
    BoxLayout:
        height: dp(56)
        size_hint_y: None
        orientation: 'horizontal'
        padding: dp(4)
        spacing: dp(4)
        canvas.before:
            Color:
                rgba: 0.12,0.35,0.18,1  # header green
            Rectangle:
                pos: self.pos
                size: self.size
        Label:
            text: 'Orientation — Lycée'
            size_hint_x: 0.7
            color: 1,1,1,1
            bold: True
        Button:
            text: 'Planifier'
            size_hint_x: None
            width: dp(90)
            on_release: root.open_plan_popup()
        Button:
            text: 'Centrer'
            size_hint_x: None
            width: dp(90)
            on_release: root.center_on_user()

    MapView:
        id: mapview
        lat: 45.06194
        lon: 5.56745
        zoom: 15

    BoxLayout:
        size_hint_y: None
        height: dp(56)
        padding: dp(6)
        spacing: dp(6)
        Label:
            id: status_lbl
            text: 'Statut GPS: inactif'
            size_hint_x: 0.7
        Button:
            text: 'Start GPS'
            size_hint_x: None
            width: dp(120)
            on_release: root.start_gps()
        Button:
            text: 'Stop GPS'
            size_hint_x: None
            width: dp(120)
            on_release: root.stop_gps()
'''

def haversine(lat1, lon1, lat2, lon2):
    R = 6371000
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlambda = math.radians(lon2 - lon1)
    a = math.sin(dphi/2)**2 + math.cos(phi1)*math.cos(phi2)*math.sin(dlambda/2)**2
    return 2*R*math.asin(math.sqrt(a))

def build_graph_from_beacons(beacons):
    nodes = {}
    edges = {}
    for b in beacons:
        key = f"b:{b['id']}"
        nodes[key] = (b['lat'], b['lon'])
        edges[key] = {}
    p_idx = 0
    for b in beacons:
        passage = b.get('passage') or []
        last_key = f"b:{b['id']}"
        for pt in passage:
            pkey = f"p:{p_idx}"
            nodes[pkey] = (pt[0], pt[1])
            edges.setdefault(pkey, {})
            dist = haversine(nodes[last_key][0], nodes[last_key][1], pt[0], pt[1])
            edges[last_key][pkey] = dist
            edges[pkey][last_key] = dist
            last_key = pkey
            p_idx += 1
    return nodes, edges

def dijkstra(edges, start_key, goal_key):
    pq = []
    heappush(pq, (0, start_key, [start_key]))
    visited = set()
    while pq:
        cost, node, path = heappop(pq)
        if node == goal_key:
            return cost, path
        if node in visited:
            continue
        visited.add(node)
        for nb, w in edges.get(node, {}).items():
            if nb in visited:
                continue
            heappush(pq, (cost + w, nb, path + [nb]))
    return float('inf'), []

def nearest_neighbor_tour(points):
    n = len(points)
    if n == 0:
        return []
    unvisited = set(range(n))
    tour = [0]
    unvisited.remove(0)
    while unvisited:
        last = tour[-1]
        next_idx = min(unvisited, key=lambda i: haversine(points[last][0], points[last][1], points[i][0], points[i][1]))
        tour.append(next_idx)
        unvisited.remove(next_idx)
    return tour

def tour_length(order, points):
    L = 0.0
    for i in range(len(order)-1):
        a = points[order[i]]
        b = points[order[i+1]]
        L += haversine(a[0], a[1], b[0], b[1])
    return L

def two_opt(order, points, improvement_threshold=0.0001):
    best = order[:]
    improved = True
    while improved:
        improved = False
        best_len = tour_length(best, points)
        for i in range(1, len(best)-2):
            for j in range(i+1, len(best)):
                if j - i == 1: continue
                new_order = best[:i] + best[i:j][::-1] + best[j:]
                new_len = tour_length(new_order, points)
                if new_len + improvement_threshold < best_len:
                    best = new_order
                    improved = True
                    break
            if improved:
                break
    return best

class BeaconMarker(MapMarkerPopup):
    beacon_id = StringProperty('')
    beacon_data = ObjectProperty(None)

    def on_release(self):
        content = BoxLayout(orientation='vertical', spacing=10, padding=10)
        lbl = Label(text=f"Balise #{self.beacon_id}\n{self.beacon_data.get('name','')}", size_hint_y=None, height=dp(50))
        content.add_widget(lbl)
        btn_go = Button(text="S'y rendre", size_hint_y=None, height=dp(40))
        btn_go.bind(on_release=lambda *a: self.parent.parent.start_navigation(self.beacon_data))
        content.add_widget(btn_go)
        btn_punch = Button(text="Voir le poinçon", size_hint_y=None, height=dp(40))
        btn_punch.bind(on_release=lambda *a: self.parent.parent.show_punch(self.beacon_data))
        content.add_widget(btn_punch)
        popup = Popup(title=f"Balise {self.beacon_id}", content=content, size_hint=(0.8,0.5))
        popup.open()

class PathLayer(MapLayer):
    def __init__(self, points=None, **kwargs):
        super().__init__(**kwargs)
        self.points = points or []

    def reposition(self):
        self.canvas.clear()
        if not self.points:
            return
        from kivy.graphics import Color, Line
        mapview = self.parent
        with self.canvas:
            Color(0, 0.2, 0, 0.9)
            coords = []
            for (lat, lon) in self.points:
                x, y = mapview.get_window_xy_from(lat, lon, mapview.zoom)
                coords.extend([x, y])
            if len(coords) >= 4:
                Line(points=coords, width=2)

class RootWidget(BoxLayout):
    mapview = ObjectProperty(None)
    user_lat = NumericProperty(None)
    user_lon = NumericProperty(None)
    beacons = ListProperty([])
    current_path_layer = ObjectProperty(None)

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        Clock.schedule_once(self.post_init, 0.5)

    def post_init(self, dt):
        self.load_beacons()
        self.add_beacon_markers()

    def load_beacons(self):
        p = os.path.join(os.path.dirname(__file__), 'beacons.json')
        if not os.path.exists(p):
            self.beacons = []
            return
        with open(p, 'r', encoding='utf8') as f:
            self.beacons = json.load(f)

    def save_beacons(self):
        p = os.path.join(os.path.dirname(__file__), 'beacons.json')
        with open(p, 'w', encoding='utf8') as f:
            json.dump(self.beacons, f, ensure_ascii=False, indent=2)

    def add_beacon_markers(self):
        for w in list(self.mapview.children):
            if isinstance(w, BeaconMarker):
                self.mapview.remove_widget(w)
        for b in self.beacons:
            marker = BeaconMarker(lat=b['lat'], lon=b['lon'])
            marker.beacon_id = str(b['id'])
            marker.beacon_data = b
            self.mapview.add_widget(marker)

    def open_admin(self):
        content = BoxLayout(orientation='vertical', spacing=10, padding=10)
        content.add_widget(Label(text='Mot de passe admin:'))
        pw = TextInput(password=True, multiline=False)
        content.add_widget(pw)
        btns = BoxLayout(size_hint_y=None, height=dp(44))
        ok = Button(text='OK')
        cancel = Button(text='Annuler')
        btns.add_widget(ok); btns.add_widget(cancel)
        content.add_widget(btns)
        popup = Popup(title='Admin', content=content, size_hint=(0.8,0.4))
        ok.bind(on_release=lambda *a: self._check_admin(pw.text, popup))
        cancel.bind(on_release=lambda *a: popup.dismiss())
        popup.open()

    def _check_admin(self, text, popup):
        if text == ADMIN_PASSWORD:
            popup.dismiss()
            self.open_admin_panel()
        else:
            popup.content.add_widget(Label(text='Mot de passe incorrect', size_hint_y=None, height=dp(30)))

    def open_admin_panel(self):
        content = BoxLayout(orientation='vertical', spacing=10, padding=10)
        content.add_widget(Label(text='Mode Admin — Enregistrer position sur place', size_hint_y=None, height=dp(40)))
        btn_record = Button(text='Enregistrer position actuelle comme balise')
        btn_record.bind(on_release=lambda *a: self.record_current_position())
        content.add_widget(btn_record)
        btn_reload = Button(text='Recharger balises')
        btn_reload.bind(on_release=lambda *a: (self.load_beacons(), self.add_beacon_markers()))
        content.add_widget(btn_reload)
        btn_export = Button(text='Exporter beacons.json (chemin local)')
        btn_export.bind(on_release=lambda *a: self.export_beacons())
        content.add_widget(btn_export)
        close = Button(text='Fermer', size_hint_y=None, height=dp(44))
        content.add_widget(close)
        pop = Popup(title='Admin', content=content, size_hint=(0.9,0.5))
        close.bind(on_release=lambda *a: pop.dismiss())
        pop.open()

    def export_beacons(self):
        Popup(title='Export', content=Label(text='beacons.json enregistré dans le dossier app.'), size_hint=(0.6,0.3)).open()

    def record_current_position(self):
        if not (self.user_lat and self.user_lon):
            Popup(title='Erreur', content=Label(text='Position GPS inconnue. Démarrer le GPS et attendre.'), size_hint=(0.7,0.3)).open()
            return
        content = BoxLayout(orientation='vertical', spacing=8, padding=8)
        ti_name = TextInput(text=f'Balise {len(self.beacons)+1}', multiline=False)
        content.add_widget(Label(text='Nom de la balise:'))
        content.add_widget(ti_name)
        ti_img = TextInput(text=f'punch_{len(self.beacons)+1:03d}.png', multiline=False)
        content.add_widget(Label(text='Nom fichier image (mettre dans assets/):'))
        content.add_widget(ti_img)
        btns = BoxLayout(size_hint_y=None, height=dp(44))
        ok = Button(text='Enregistrer')
        cancel = Button(text='Annuler')
        btns.add_widget(ok); btns.add_widget(cancel)
        content.add_widget(btns)
        pop = Popup(title='Nouvelle balise', content=content, size_hint=(0.9,0.5))
        ok.bind(on_release=lambda *a: self._save_new_beacon(ti_name.text, ti_img.text, pop))
        cancel.bind(on_release=lambda *a: pop.dismiss())
        pop.open()

    def _save_new_beacon(self, name, img, popup):
        new_id = 1
        if self.beacons:
            new_id = max(b['id'] for b in self.beacons) + 1
        b = {
            'id': new_id,
            'name': name,
            'lat': float(self.user_lat),
            'lon': float(self.user_lon),
            'punch_img': img,
            'passage': [[float(self.user_lat), float(self.user_lon)]]
        }
        self.beacons.append(b)
        self.save_beacons()
        self.add_beacon_markers()
        popup.dismiss()
        Popup(title='OK', content=Label(text=f'Balise {new_id} enregistrée'), size_hint=(0.6,0.3)).open()

    def start_gps(self):
        try:
            gps.configure(on_location=self.on_gps_location, on_status=self.on_gps_status)
            gps.start(minTime=1000, minDistance=0)
            self.ids.status_lbl.text = 'Statut GPS: démarré'
        except Exception as e:
            self.ids.status_lbl.text = f'Erreur GPS: {e}'

    def stop_gps(self):
        try:
            gps.stop()
            self.ids.status_lbl.text = 'Statut GPS: arrêté'
        except Exception as e:
            self.ids.status_lbl.text = f'Erreur stop: {e}'

    def on_gps_location(self, **kwargs):
        lat = kwargs.get('lat') or kwargs.get('latitude')
        lon = kwargs.get('lon') or kwargs.get('longitude')
        if lat is None or lon is None:
            return
        try:
            lat = float(lat); lon = float(lon)
        except:
            return
        self.user_lat = lat; self.user_lon = lon
        self.mapview.center_on(lat, lon)
        self.ids.status_lbl.text = f'GPS: {lat:.5f}, {lon:.5f}'
        if hasattr(self, 'nav_target') and self.nav_target:
            d = haversine(self.user_lat, self.user_lon, self.nav_target['lat'], self.nav_target['lon'])
            self.ids.status_lbl.text = f'Dist: {d:.1f} m'

    def on_gps_status(self, stype, status):
        self.ids.status_lbl.text = f'GPS statut: {stype} {status}'

    def center_on_user(self):
        if self.user_lat and self.user_lon:
            self.mapview.center_on(self.user_lat, self.user_lon)

    def show_punch(self, beacon):
        img_path = beacon.get('punch_img')
        if not img_path:
            Popup(title='Poinçon', content=Label(text='Pas d\'image disponible'), size_hint=(0.6,0.4)).open()
            return
        p = os.path.join(os.path.dirname(__file__), 'assets', img_path)
        if not os.path.exists(p):
            Popup(title='Poinçon', content=Label(text=f'Fichier manquant: {img_path}'), size_hint=(0.6,0.4)).open()
            return
        content = BoxLayout(orientation='vertical')
        content.add_widget(Image(source=p))
        close = Button(text='Fermer', size_hint_y=None, height=dp(40))
        content.add_widget(close)
        popup = Popup(title=f"Poinçon balise {{beacon.get('id')}}", content=content, size_hint=(0.8,0.8))
        close.bind(on_release=lambda *a: popup.dismiss())
        popup.open()

    def start_navigation(self, beacon):
        self.nav_target = beacon
        self.ids.status_lbl.text = f'Navigation vers balise {{beacon.get("id")}}'
        if beacon.get('passage'):
            self.draw_passage(beacon.get('passage'))
        else:
            nodes, edges = build_graph_from_beacons(self.beacons)
            if not self.user_lat or not self.user_lon:
                Popup(title='Info', content=Label(text='Démarrer GPS pour calculer un itinéraire depuis votre position.'), size_hint=(0.6,0.4)).open()
                return
            nodes_temp = deepcopy(nodes)
            edges_temp = deepcopy(edges)
            user_key = 'user:0'
            nodes_temp[user_key] = (self.user_lat, self.user_lon)
            edges_temp[user_key] = {}
            distances = [(k, haversine(self.user_lat, self.user_lon, v[0], v[1])) for k, v in nodes_temp.items() if not k.startswith('user:')]
            distances.sort(key=lambda x: x[1])
            for k, d in distances[:3]:
                edges_temp[user_key][k] = d
                edges_temp.setdefault(k, {})[user_key] = d
            target_key = f"b:{beacon['id']}"
            cost, path = dijkstra(edges_temp, user_key, target_key)
            if path:
                coords = [nodes_temp[k] for k in path]
                self.draw_passage(coords)

    def draw_passage(self, passage_points):
        if self.current_path_layer:
            try:
                self.mapview.remove_layer(self.current_path_layer)
            except:
                pass
        if not passage_points:
            return
        layer = PathLayer(points=[(p[0], p[1]) for p in passage_points])
        self.current_path_layer = layer
        self.mapview.add_layer(layer)
        if passage_points:
            self.mapview.center_on(passage_points[0][0], passage_points[0][1])

    def open_plan_popup(self):
        content = BoxLayout(orientation='vertical', spacing=8, padding=8)
        content.add_widget(Label(text='Entrer IDs de balises séparés par des virgules (ex: 1,3,5)'))
        ti = TextInput(multiline=False)
        content.add_widget(ti)
        btns = BoxLayout(size_hint_y=None, height=dp(44))
        ok = Button(text='Planifier')
        cancel = Button(text='Annuler')
        btns.add_widget(ok); btns.add_widget(cancel)
        content.add_widget(btns)
        pop = Popup(title='Planification multi-balises', content=content, size_hint=(0.9,0.4))
        ok.bind(on_release=lambda *a: (self._plan_from_text(ti.text), pop.dismiss()))
        cancel.bind(on_release=lambda *a: pop.dismiss())
        pop.open()

    def _plan_from_text(self, text):
        try:
            ids = [int(x.strip()) for x in text.split(',') if x.strip()]
        except:
            Popup(title='Erreur', content=Label(text='IDs invalides'), size_hint=(0.6,0.3)).open()
            return
        self.plan_multi(ids)

    def plan_multi(self, ids_list):
        selected = []
        for i in ids_list:
            for b in self.beacons:
                if b['id'] == i:
                    selected.append(b)
                    break
        if not selected:
            Popup(title='Erreur', content=Label(text='Aucune balise trouvée pour les IDs donnés'), size_hint=(0.6,0.3)).open()
            return
        points = [(b['lat'], b['lon']) for b in selected]
        order = nearest_neighbor_tour(points)
        if len(order) > 2:
            order = two_opt(order, points)
        ordered_points = [points[i] for i in order]
        self.draw_passage(ordered_points)
        self.ids.status_lbl.text = f'Trajet {len(ordered_points)} balises planifié'

class OrientationApp(App):
    def build(self):
        Builder.load_string(KV)
        return RootWidget()

if __name__ == '__main__':
    OrientationApp().run()

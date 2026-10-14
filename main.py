"""Orientation J.P. — application de course d'orientation (Kivy + Supabase).

Collège Jean Prévost, Villard-de-Lans.
"""
import math
import os

from kivy.animation import Animation
from kivy.app import App
from kivy.clock import Clock, mainthread
from kivy.core.window import Window
from kivy.graphics import Color, Line
from kivy.metrics import dp
from kivy.properties import (BooleanProperty, DictProperty, ListProperty,
                             NumericProperty, StringProperty)
from kivy.resources import resource_add_path, resource_find
from kivy.storage.jsonstore import JsonStore
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.button import Button
from kivy.uix.floatlayout import FloatLayout
from kivy.uix.label import Label
from kivy.uix.modalview import ModalView
from kivy.uix.screenmanager import FadeTransition, Screen, ScreenManager
from kivy.uix.togglebutton import ToggleButton
from kivy.utils import platform

import api
import config

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
resource_add_path(BASE_DIR)

# Doit être importé APRÈS resource_add_path ; patch du User-Agent exigé par OSM
from kivy_garden.mapview import MapMarker, MapSource, MapView  # noqa: E402
from kivy_garden.mapview import downloader as _mv_downloader  # noqa: E402
from kivy_garden.mapview.downloader import Downloader  # noqa: E402

_mv_downloader.USER_AGENT = config.USER_AGENT

VERSION = "1.1.0"


# ------------------------------------------------------------------ géo
def haversine(lat1, lon1, lat2, lon2):
    r = 6371000.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    a = (math.sin((p2 - p1) / 2) ** 2
         + math.cos(p1) * math.cos(p2) * math.sin(math.radians(lon2 - lon1) / 2) ** 2)
    return 2 * r * math.asin(math.sqrt(a))


def bearing(lat1, lon1, lat2, lon2):
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dl = math.radians(lon2 - lon1)
    y = math.sin(dl) * math.cos(p2)
    x = math.cos(p1) * math.sin(p2) - math.sin(p1) * math.cos(p2) * math.cos(dl)
    return (math.degrees(math.atan2(y, x)) + 360) % 360


def compass(deg):
    return ["N", "NE", "E", "SE", "S", "SO", "O", "NO"][int((deg + 22.5) // 45) % 8]


def fmt_dist(m):
    return f"{int(round(m))} m" if m < 1000 else f"{m / 1000:.1f} km"


# ------------------------------------------------------------------ widgets
class Toast(Label):
    def show(self, text, duration=2.6):
        Animation.cancel_all(self)
        self.text = text
        self.opacity = 0
        (Animation(opacity=1, d=0.15) + Animation(opacity=1, d=duration)
         + Animation(opacity=0, d=0.3)).start(self)


class PunchCell(ToggleButton):
    """Une case de la grille de poinçons 3x3."""


class BeaconMarker(MapMarker):
    def __init__(self, beacon, **kw):
        self.beacon = beacon
        super().__init__(**kw)
        self.size = (dp(40), dp(40))
        self.anchor_y = 0.12


class SimpleMarker(MapMarker):
    def __init__(self, size_dp=26, **kw):
        super().__init__(**kw)
        self.size = (dp(size_dp), dp(size_dp))
        self.anchor_y = 0.5


class LoginScreen(Screen):
    prefill_name = StringProperty("")
    prefill_private = BooleanProperty(False)


class SettingsScreen(Screen):
    pass


class MapScreen(Screen):
    status_text = StringProperty("Démarrage…")
    target_text = StringProperty("")

    def on_kv_post(self, base_widget):
        mv = self.ids.mapview
        with mv.canvas.after:
            Color(0.97, 0.5, 0.0, 0.9)
            self._line = Line(points=[], width=dp(2.2), dash_length=dp(8), dash_offset=dp(6))
        Clock.schedule_interval(self._update_line, 1 / 12)

    def _update_line(self, dt):
        app = App.get_running_app()
        b = app.target_beacon
        if not b or not app.has_fix:
            if self._line.points:
                self._line.points = []
            return
        mv = self.ids.mapview
        x1, y1 = mv.get_window_xy_from(app.my_lat, app.my_lon, mv.zoom)
        x2, y2 = mv.get_window_xy_from(b["latitude"], b["longitude"], mv.zoom)
        self._line.points = [x1, y1, x2, y2]


class BeaconSheet(ModalView):
    title_text = StringProperty("")
    subtitle = StringProperty("")
    description = StringProperty("")
    feedback = StringProperty("")
    feedback_ok = BooleanProperty(False)
    done = BooleanProperty(False)

    def __init__(self, beacon, **kw):
        super().__init__(**kw)
        self.beacon = beacon
        self.cells = []
        for _ in range(9):
            cell = PunchCell()
            self.ids.grid.add_widget(cell)
            self.cells.append(cell)
        app = App.get_running_app()
        self.title_text = beacon.get("name") or f"Balise {beacon['id']}"
        self.description = beacon.get("description") or "Pas de description."
        self.done = beacon["id"] in app.validated
        self.refresh_subtitle()
        if self.done:
            self.feedback, self.feedback_ok = "Déjà validée", True

    def refresh_subtitle(self):
        d = App.get_running_app().distance_to(self.beacon)
        self.subtitle = f"À {fmt_dist(d)}" if d is not None else "Position inconnue"

    def validate(self):
        app = App.get_running_app()
        b = self.beacon
        d = app.distance_to(b)
        if app.gps_real and d is not None and d > config.BEACON_REACH_METERS:
            self.feedback, self.feedback_ok = (
                f"Trop loin ({fmt_dist(d)}), max {config.BEACON_REACH_METERS} m", False)
            return
        state = "".join("1" if c.state == "down" else "0" for c in self.cells)
        if state == b["pattern"]:
            app.mark_validated(b)
            self.done = True
            self.feedback, self.feedback_ok = "Balise validée !", True
            Clock.schedule_once(lambda dt: self.dismiss(), 1.3)
        else:
            self.feedback, self.feedback_ok = "Ce n'est pas le bon motif, regarde bien.", False

    def go_there(self):
        App.get_running_app().set_target(self.beacon)
        self.dismiss()


class BeaconListSheet(ModalView):
    def __init__(self, **kw):
        super().__init__(**kw)
        app = App.get_running_app()
        rows = self.ids.rows
        if not app.beacons:
            lbl = Label(text="Aucune balise pour l'instant.", color=(0.4, 0.4, 0.4, 1),
                        size_hint_y=None, height=dp(40))
            rows.add_widget(lbl)
        for b in app.beacons:
            d = app.distance_to(b)
            mark = "  (validée)" if b["id"] in app.validated else ""
            txt = (b.get("name") or f"Balise {b['id']}") + mark
            if d is not None:
                txt += f"   {fmt_dist(d)}"
            btn = Factory_GhostButton(text=txt)
            btn.bind(on_release=lambda inst, beacon=b: self._pick(beacon))
            rows.add_widget(btn)

    def _pick(self, beacon):
        self.dismiss()
        App.get_running_app().focus_beacon(beacon)


def Factory_GhostButton(**kw):
    from kivy.factory import Factory
    return Factory.GhostButton(**kw)


class AdminScreen(Screen):
    unlocked = BooleanProperty(False)
    stats_text = StringProperty("")

    def on_kv_post(self, base_widget):
        self.password = ""
        self.cells = []
        for _ in range(9):
            c = PunchCell()
            self.ids.new_grid.add_widget(c)
            self.cells.append(c)

    def on_pre_enter(self, *args):
        self.lock()

    def leave(self):
        self.lock()
        App.get_running_app().go("settings")

    def lock(self):
        self.unlocked = False
        self.password = ""
        self.ids.pw_input.text = ""

    def unlock(self, pw):
        app = App.get_running_app()
        if not pw:
            app.show_toast("Entre le mot de passe")
            return

        def ok(result):
            if result:
                self.password = pw
                self.unlocked = True
                self.refresh()
            else:
                app.show_toast("Mot de passe incorrect")

        api.run_async(lambda: api.admin_check(pw), ok, app.on_api_error)

    def refresh(self):
        app = App.get_running_app()

        def done(s):
            self.stats_text = (
                f"Balises : {s.get('beacons', 0)}\n"
                f"Coureurs en ligne (2 min) : {s.get('active_now', 0)}\n"
                f"Lancements de l'app : {s.get('launches', 0)}\n"
                f"Coureurs différents : {s.get('distinct_users', 0)}\n"
                f"Balises validées : {s.get('validations', 0)}")

        api.run_async(lambda: api.admin_stats(self.password), done, app.on_api_error)
        app.load_beacons(callback=self.fill_rows)

    def fill_rows(self):
        app = App.get_running_app()
        rows = self.ids.admin_rows
        rows.clear_widgets()
        for b in app.beacons:
            row = BoxLayout(size_hint_y=None, height=dp(44), spacing=dp(8))
            name = b.get("name") or f"Balise {b['id']}"
            row.add_widget(Label(text=f"{name}  [{b['pattern']}]", color=(0.12, 0.16, 0.2, 1),
                                 halign="left", valign="middle", font_size="14sp",
                                 text_size=(dp(200), None)))
            btn = Factory_GhostButton(text="Suppr.", size_hint=(None, None), size=(dp(80), dp(40)))
            btn.bind(on_release=lambda inst, beacon=b: self.delete_beacon(beacon))
            row.add_widget(btn)
            rows.add_widget(row)

    def add_beacon(self):
        app = App.get_running_app()
        pattern = "".join("1" if c.state == "down" else "0" for c in self.cells)
        if "1" not in pattern:
            app.show_toast("Coche au moins un poinçon")
            return
        lat, lon = app.map_center()
        name = self.ids.new_name.text.strip()
        desc = self.ids.new_desc.text.strip()

        def done(_):
            app.show_toast("Balise ajoutée")
            self.ids.new_name.text = ""
            self.ids.new_desc.text = ""
            for c in self.cells:
                c.state = "normal"
            self.refresh()

        api.run_async(
            lambda: api.admin_add_beacon(self.password, name, lat, lon, pattern, desc),
            done, app.on_api_error)

    def delete_beacon(self, beacon):
        app = App.get_running_app()

        def done(_):
            app.show_toast("Balise supprimée")
            self.refresh()

        api.run_async(lambda: api.admin_delete_beacon(self.password, beacon["id"]),
                      done, app.on_api_error)


# ------------------------------------------------------------------ application
class OrientationApp(App):
    title = "Orientation J.P."
    username = StringProperty("")
    private_mode = BooleanProperty(False)
    default_lat = NumericProperty(config.DEFAULT_LATITUDE)
    default_lon = NumericProperty(config.DEFAULT_LONGITUDE)
    default_zoom = NumericProperty(config.DEFAULT_ZOOM)
    version_text = StringProperty(VERSION)

    def build(self):
        Window.clearcolor = (0.96, 0.97, 0.95, 1)
        Window.softinput_mode = "below_target"
        # orientation.kv est chargé automatiquement par Kivy (nom de la classe OrientationApp)
        # cache des tuiles dans le dossier de données de l'app (inscriptible sur Android)
        Downloader.instance(cache_dir=os.path.join(self.user_data_dir, "tiles"))
        self.store = JsonStore(os.path.join(self.user_data_dir, "settings.json"))

        self.beacons = []
        self.validated = set(self.store.get("validated")["ids"]) if self.store.exists("validated") else set()
        self.target_beacon = None
        self.my_lat = config.DEFAULT_LATITUDE
        self.my_lon = config.DEFAULT_LONGITUDE
        self.has_fix = False
        self.gps_real = False
        self.gps_state = "GPS en attente…"
        self.online = None
        self.others_count = 0
        self.beacon_markers = {}
        self.user_markers = {}
        self.me_marker = None
        self._session_started = False
        self._centered_once = False
        self._gps_started = False

        root = FloatLayout()
        self.sm = ScreenManager(transition=FadeTransition(duration=0.15))
        self.login_screen = LoginScreen()
        self.map_screen = MapScreen()
        self.settings_screen = SettingsScreen()
        self.admin_screen = AdminScreen()
        # Tuiles OSM en HTTPS : Android (API 28+) bloque le HTTP en clair, la carte restait vide
        self.map_screen.ids.mapview.map_source = MapSource(
            url="https://tile.openstreetmap.org/{z}/{x}/{y}.png",
            cache_key="osm-https", min_zoom=0, max_zoom=19, tile_size=256,
            image_ext="png", attribution="© OpenStreetMap contributors")
        for s in (self.login_screen, self.map_screen, self.settings_screen, self.admin_screen):
            self.sm.add_widget(s)
        root.add_widget(self.sm)
        self.toast = Toast()
        root.add_widget(self.toast)
        Window.bind(on_keyboard=self._on_keyboard)
        return root

    def on_start(self):
        if self.store.exists("user"):
            u = self.store.get("user")
            self.login_screen.prefill_name = u.get("name", "")
            self.login_screen.prefill_private = bool(u.get("private", False))
            if u.get("name"):
                self.start_session(u["name"], bool(u.get("private", False)))

    def on_stop(self):
        try:
            from plyer import gps
            gps.stop()
        except Exception:
            pass

    def on_pause(self):
        return True

    def on_resume(self):
        if self._session_started:
            self.tick()

    # ---------------------------------------------------------- navigation
    def go(self, screen):
        self.sm.current = screen

    def _on_keyboard(self, window, key, *args):
        if key != 27:  # touche "retour" Android / Échap
            return False
        cur = self.sm.current
        if cur == "admin":
            self.admin_screen.leave()
            return True
        if cur == "settings":
            self.go("map")
            return True
        if cur == "map" and platform == "android":
            try:
                from jnius import autoclass
                autoclass("org.kivy.android.PythonActivity").mActivity.moveTaskToBack(True)
                return True
            except Exception:
                return False
        return False

    def show_toast(self, text):
        self.toast.show(text)

    def on_api_error(self, exc):
        was_offline = self.online is False
        self.online = False
        self.update_status()
        if isinstance(exc, api.ApiError):
            msg = str(exc)
            if "schema cache" in msg or "Could not find" in msg:
                msg = "Base non initialisée : exécute supabase_setup.sql dans Supabase"
            self.show_toast(msg[:90])
        elif not was_offline:  # un seul message tant qu'on reste hors ligne
            self.show_toast("Serveur injoignable (connexion coupée ou base en pause)")

    # ---------------------------------------------------------- session
    def start_session(self, name, private):
        name = (name or "").strip()
        if not 1 <= len(name) <= 30:
            self.show_toast("Entre un nom (30 caractères max)")
            return
        self.username = name
        self.private_mode = private
        self.store.put("user", name=name, private=private)
        self.go("map")
        if self._session_started:
            return
        self._session_started = True
        self.start_gps()
        self.load_beacons()
        Clock.schedule_interval(self.tick, config.LOCATION_UPDATE_INTERVAL)
        Clock.schedule_interval(lambda dt: self.load_beacons(), 60)
        Clock.schedule_interval(self._refresh_nav, 1)
        api.run_async(lambda: api.log_event(name, "launch"))
        self.update_status()

    def logout(self):
        if self.username:
            n = self.username
            api.run_async(lambda: api.clear_position(n))
        self.store.delete("user") if self.store.exists("user") else None
        self.login_screen.prefill_name = ""
        self.username = ""
        self.clear_target()
        self.go("login")

    def set_private(self, private):
        self.private_mode = private
        self.store.put("user", name=self.username, private=private)
        if private:
            n = self.username
            api.run_async(lambda: api.clear_position(n))
            self.show_toast("Mode privé : ta position n'est plus partagée")
        else:
            self.show_toast("Position partagée")
            self.tick()
        self.update_status()

    # ---------------------------------------------------------- GPS
    def start_gps(self):
        if self._gps_started:
            return
        self._gps_started = True
        if platform == "android":
            from android.permissions import Permission, request_permissions
            request_permissions(
                [Permission.ACCESS_FINE_LOCATION, Permission.ACCESS_COARSE_LOCATION],
                self._on_permissions)
        else:
            # Poste de bureau : pas de GPS, on utilise la position par défaut
            self.gps_state = "Mode bureau (position par défaut)"
            self._on_location(lat=config.DEFAULT_LATITUDE, lon=config.DEFAULT_LONGITUDE, real=False)

    def _on_permissions(self, permissions, grants):
        if grants and any(grants):
            self._begin_gps()
        else:
            self.gps_state = "Localisation refusée (Réglages Android)"
            self.update_status()

    def _begin_gps(self):
        try:
            from plyer import gps
            gps.configure(on_location=self._gps_callback, on_status=self._gps_status)
            gps.start(minTime=2000, minDistance=2)
            self.gps_state = "GPS en attente d'un signal…"
        except Exception as exc:
            self.gps_state = "GPS indisponible"
            self.show_toast(f"GPS : {exc}")
        self.update_status()

    @mainthread
    def _gps_callback(self, **kwargs):
        lat, lon = kwargs.get("lat"), kwargs.get("lon")
        if lat is None or lon is None:
            return
        self._on_location(lat=float(lat), lon=float(lon), real=True)

    @mainthread
    def _gps_status(self, stype, status):
        if stype == "provider-disabled":
            self.gps_state = "GPS désactivé : active la localisation"
            self.update_status()

    def _on_location(self, lat, lon, real):
        first = not self.has_fix
        self.my_lat, self.my_lon = lat, lon
        self.has_fix = True
        self.gps_real = real
        if real:
            self.gps_state = "GPS actif"
        self._update_me_marker()
        if not self._centered_once:
            self._centered_once = True
            if real:
                self.map_screen.ids.mapview.center_on(lat, lon)
        if first:
            self.tick()
        self.update_status()

    def distance_to(self, beacon):
        if not self.has_fix:
            return None
        return haversine(self.my_lat, self.my_lon, beacon["latitude"], beacon["longitude"])

    # ---------------------------------------------------------- réseau
    def tick(self, dt=None):
        if not self._session_started:
            return
        api.run_async(api.get_positions, self._on_positions, self.on_api_error)
        if self.has_fix and not self.private_mode and self.username:
            n, la, lo = self.username, self.my_lat, self.my_lon
            api.run_async(lambda: api.push_position(n, la, lo), None, self.on_api_error)

    def _on_positions(self, rows):
        self.online = True
        mv = self.map_screen.ids.mapview
        seen = set()
        for r in rows or []:
            name = r.get("username")
            if not name or name == self.username:
                continue
            seen.add(name)
            m = self.user_markers.get(name)
            if m is None:
                m = SimpleMarker(size_dp=26, lat=r["latitude"], lon=r["longitude"],
                                 source=resource_find("assets/user.png"))
                mv.add_marker(m)
                self.user_markers[name] = m
            else:
                m.lat, m.lon = r["latitude"], r["longitude"]
        for name in list(self.user_markers):
            if name not in seen:
                mv.remove_marker(self.user_markers.pop(name))
        self.others_count = len(seen)
        self._reposition_markers()
        self.update_status()

    def load_beacons(self, callback=None):
        def done(rows):
            self.online = True
            rows = rows or []
            if rows != self.beacons:
                self.beacons = rows
                self._sync_beacon_markers()
            self.update_status()
            if callback:
                callback()

        api.run_async(api.get_beacons, done, self.on_api_error)

    # ---------------------------------------------------------- marqueurs
    def _reposition_markers(self):
        layer = getattr(self.map_screen.ids.mapview, "_default_marker_layer", None)
        if layer:
            layer.reposition()

    def _sync_beacon_markers(self):
        mv = self.map_screen.ids.mapview
        for m in self.beacon_markers.values():
            mv.remove_marker(m)
        self.beacon_markers.clear()
        for b in self.beacons:
            icon = "assets/beacon_done.png" if b["id"] in self.validated else "assets/beacon.png"
            m = BeaconMarker(b, lat=b["latitude"], lon=b["longitude"], source=resource_find(icon))
            m.bind(on_release=lambda inst: self.open_beacon(inst.beacon))
            mv.add_marker(m)
            self.beacon_markers[b["id"]] = m
        # ma position toujours au-dessus
        if self.me_marker is not None:
            mv.remove_marker(self.me_marker)
            mv.add_marker(self.me_marker)
        if self.target_beacon and self.target_beacon["id"] not in self.beacon_markers:
            self.clear_target()

    def _update_me_marker(self):
        mv = self.map_screen.ids.mapview
        if self.me_marker is None:
            self.me_marker = SimpleMarker(size_dp=34, lat=self.my_lat, lon=self.my_lon,
                                          source=resource_find("assets/me.png"))
            mv.add_marker(self.me_marker)
        else:
            self.me_marker.lat, self.me_marker.lon = self.my_lat, self.my_lon
        self._reposition_markers()

    # ---------------------------------------------------------- actions carte
    def map_center(self):
        mv = self.map_screen.ids.mapview
        return mv.lat, mv.lon

    def recenter(self):
        mv = self.map_screen.ids.mapview
        mv.center_on(self.my_lat, self.my_lon)
        if not self.has_fix:
            self.show_toast("Position pas encore disponible")

    def open_beacon(self, beacon):
        BeaconSheet(beacon).open()

    def open_beacon_list(self):
        BeaconListSheet().open()

    def focus_beacon(self, beacon):
        mv = self.map_screen.ids.mapview
        mv.zoom = max(mv.zoom, 17)
        mv.center_on(beacon["latitude"], beacon["longitude"])
        self.open_beacon(beacon)

    def search_beacon(self, query):
        q = (query or "").strip().lower()
        if not q:
            return
        for b in self.beacons:
            hay = f"{b.get('name', '')} {b.get('description', '')} {b['id']}".lower()
            if q in hay:
                self.focus_beacon(b)
                return
        self.show_toast("Aucune balise trouvée")

    def mark_validated(self, beacon):
        self.validated.add(beacon["id"])
        self.store.put("validated", ids=sorted(self.validated))
        self._sync_beacon_markers()
        if self.target_beacon and self.target_beacon["id"] == beacon["id"]:
            self.clear_target()
        n = self.username
        api.run_async(lambda: api.log_event(n, "beacon_validated"))

    # ---------------------------------------------------------- navigation vers balise
    def set_target(self, beacon):
        self.target_beacon = beacon
        self._refresh_nav()
        self.show_toast("Direction : " + (beacon.get("name") or "balise"))

    def clear_target(self):
        self.target_beacon = None
        self.map_screen.target_text = ""

    def _refresh_nav(self, dt=None):
        b = self.target_beacon
        if not b or not self.has_fix:
            if not b:
                self.map_screen.target_text = ""
            return
        d = self.distance_to(b)
        name = b.get("name") or "Balise"
        if d <= config.BEACON_REACH_METERS:
            self.map_screen.target_text = f"{name} : tu y es ! Poinçonne"
        else:
            brg = bearing(self.my_lat, self.my_lon, b["latitude"], b["longitude"])
            self.map_screen.target_text = f"{name} · {fmt_dist(d)} · cap {int(brg)}° {compass(brg)}"

    # ---------------------------------------------------------- statut
    def update_status(self):
        parts = [self.gps_state]
        if self.private_mode:
            parts.append("mode privé")
        elif self.online is False:
            parts.append("hors ligne")
        elif self.online:
            n = self.others_count
            parts.append(f"{n} autre{'s' if n > 1 else ''} en ligne")
        if self.validated:
            parts.append(f"{len(self.validated)}/{len(self.beacons) or len(self.validated)} validées")
        self.map_screen.status_text = " · ".join(parts)


if __name__ == "__main__":
    OrientationApp().run()

# main.py - prototype minimal (Kivy)
from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.clock import Clock
from kivy_garden.mapview import MapView, MapMarkerPopup
import requests, threading, time, uuid
from config import SUPABASE_URL, SUPABASE_ANON_KEY, LOCATION_UPDATE_INTERVAL

HEADERS = {
    "apikey": SUPABASE_ANON_KEY,
    "Authorization": f"Bearer {SUPABASE_ANON_KEY}",
    "Content-Type": "application/json"
}

USER_ID = str(uuid.uuid4())
USERNAME = "Utilisateur_" + USER_ID[:6]

class OrientationRoot(BoxLayout):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.map = MapView(zoom=14, lat=48.8566, lon=2.3522)
        self.add_widget(self.map)
        self.markers = {}
        Clock.schedule_interval(lambda dt: threading.Thread(target=self.sync_positions).start(), LOCATION_UPDATE_INTERVAL)

    def add_or_update_marker(self, user_id, lat, lon, username):
        if user_id == USER_ID:
            return
        if user_id in self.markers:
            m = self.markers[user_id]
            m.lat = lat; m.lon = lon
        else:
            m = MapMarkerPopup(lat=lat, lon=lon)
            self.map.add_widget(m)
            self.markers[user_id] = m

    def sync_positions(self):
        lat, lon = self.map.lat, self.map.lon
        try:
            url = f\"{SUPABASE_URL}/rest/v1/positions\"
            payload = {
                "user_id": USER_ID,
                "username": USERNAME,
                "latitude": lat,
                "longitude": lon,
                "updated_at": time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())
            }
            # Note: supabase configuration CORS & RLS doivent permettre ces actions
            requests.post(url + "?on_conflict=user_id", headers=HEADERS, json=payload, timeout=10)
            r = requests.get(url + "?select=*", headers=HEADERS, timeout=10)
            if r.status_code == 200:
                positions = r.json()
                for p in positions:
                    uid = p.get("user_id")
                    lat = p.get("latitude")
                    lon = p.get("longitude")
                    uname = p.get("username", "")
                    App.get_running_app().root.add_or_update_marker(uid, lat, lon, uname)
        except Exception as e:
            print("Sync error:", e)

class OrientationApp(App):
    def build(self):
        return OrientationRoot()

if __name__ == '__main__':
    OrientationApp().run()


from kivy.core.window import Window
from kivymd.app import MDApp
from kivymd.uix.screenmanager import MDScreenManager
from kivy_garden.mapview import MapView, MapMarker
from kivy.clock import Clock
from kivy.uix.boxlayout import BoxLayout
from kivy.lang import Builder
import base64, requests, json, threading

from config import SUPABASE_URL, SUPABASE_ANON_KEY, ADMIN_PASS_OBF


class MapScreen(BoxLayout):
    pass

class AdminScreen(BoxLayout):
    pass

class Root(MDScreenManager):
    pass


class OrientationApp(MDApp):
    def build(self):
        self.theme_style = "Dark"
        return Builder.load_file("ui.kv")

    def decode_admin(self):
        return base64.b64decode(ADMIN_PASS_OBF).decode()

    def login_admin(self, pwd):
        if pwd == self.decode_admin():
            self.root.current = "admin"
            return True
        return False

    def load_balises(self):
        url = SUPABASE_URL + "/rest/v1/balises?select=*"
        r = requests.get(url, headers={"apikey": SUPABASE_ANON_KEY, "Authorization": "Bearer "+SUPABASE_ANON_KEY})
        if r.status_code == 200:
            data = r.json()
            self.update_markers(data)

    def update_markers(self, balises):
        mapview = self.root.get_screen("map").ids.map_id
        for b in balises:
            m = MapMarker(lat=b["lat"], lon=b["lon"])
            mapview.add_widget(m)

    def add_balise(self, lat, lon, name):
        url = SUPABASE_URL + "/rest/v1/balises"
        payload={"lat": lat, "lon": lon, "name": name}
        requests.post(url, json=payload, headers={"apikey": SUPABASE_ANON_KEY, "Authorization": "Bearer "+SUPABASE_ANON_KEY})

    def delete_balise(self, bid):
        url = SUPABASE_URL + "/rest/v1/balises?id=eq."+str(bid)
        requests.delete(url, headers={"apikey": SUPABASE_ANON_KEY, "Authorization": "Bearer "+SUPABASE_ANON_KEY})


if __name__ == "__main__":
    OrientationApp().run()

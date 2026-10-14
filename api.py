"""Client minimal pour l'API REST / RPC de Supabase (sans SDK, juste requests)."""
import threading
from datetime import datetime, timedelta, timezone

import requests
from kivy.clock import Clock

import config

_HEADERS = {
    "apikey": config.SUPABASE_ANON_KEY,
    "Authorization": f"Bearer {config.SUPABASE_ANON_KEY}",
    "Content-Type": "application/json",
    "User-Agent": config.USER_AGENT,
}
_TIMEOUT = 10


class ApiError(Exception):
    pass


def _check(resp):
    if resp.status_code >= 400:
        try:
            msg = resp.json().get("message") or resp.text
        except Exception:
            msg = resp.text
        raise ApiError(msg[:200])
    if not resp.content:
        return None
    return resp.json()


def rpc(name, payload=None):
    r = requests.post(
        f"{config.SUPABASE_URL}/rest/v1/rpc/{name}",
        json=payload or {}, headers=_HEADERS, timeout=_TIMEOUT,
    )
    return _check(r)


def select(table, params=None):
    r = requests.get(
        f"{config.SUPABASE_URL}/rest/v1/{table}",
        params=params or {}, headers=_HEADERS, timeout=_TIMEOUT,
    )
    return _check(r)


# --- Appels métier -------------------------------------------------------
def get_beacons():
    return select("beacons", {"select": "*", "order": "id.asc"})


def get_positions():
    since = datetime.now(timezone.utc) - timedelta(seconds=config.POSITION_MAX_AGE)
    return select("runner_positions", {
        "select": "username,latitude,longitude",
        "updated_at": "gte." + since.strftime("%Y-%m-%dT%H:%M:%SZ"),
    })


def push_position(username, lat, lon):
    return rpc("upsert_position", {"p_username": username, "p_lat": lat, "p_lon": lon})


def clear_position(username):
    return rpc("clear_position", {"p_username": username})


def log_event(username, event):
    return rpc("log_event", {"p_username": username, "p_event": event})


def admin_check(password):
    return bool(rpc("admin_check", {"p_password": password}))


def admin_add_beacon(password, name, lat, lon, pattern, description):
    return rpc("admin_add_beacon", {
        "p_password": password, "p_name": name, "p_lat": lat, "p_lon": lon,
        "p_pattern": pattern, "p_description": description,
    })


def admin_delete_beacon(password, beacon_id):
    return rpc("admin_delete_beacon", {"p_password": password, "p_id": beacon_id})


def admin_stats(password):
    return rpc("admin_stats", {"p_password": password})


# --- Exécution en arrière-plan ------------------------------------------
def run_async(func, on_done=None, on_error=None):
    """Exécute func() dans un thread, puis rappelle sur le thread UI."""
    def worker():
        try:
            result = func()
        except Exception as exc:  # réseau, API, JSON...
            if on_error:
                # 'exc' est supprimée à la sortie du except : on la capture par défaut d'argument
                Clock.schedule_once(lambda dt, e=exc: on_error(e), 0)
            return
        if on_done:
            Clock.schedule_once(lambda dt, r=result: on_done(r), 0)

    threading.Thread(target=worker, daemon=True).start()

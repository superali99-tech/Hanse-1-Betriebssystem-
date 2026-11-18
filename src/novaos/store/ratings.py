"""App ratings and reviews from the Open Desktop Ratings Service (ODRS).

ODRS (https://odrs.gnome.org) is the public review service also used by
GNOME Software and KDE Discover. Nova Store only *reads* from it:

* ``/ratings`` – aggregated star counts for all apps (cached for 24 h)
* ``/fetch``   – the most helpful reviews for one app

No personal data is sent. The ``user_hash`` required by the fetch API is a
random value generated once per user account (not derived from machine-id or
user name). Fetching can be switched off in the store settings.
"""

from __future__ import annotations

import json
import os
import secrets
import time
import urllib.request
from dataclasses import dataclass

from novaos import __version__

API = "https://odrs.gnome.org/1.0/reviews/api"
CACHE_DIR = os.path.join(os.environ.get("XDG_CACHE_HOME", os.path.expanduser("~/.cache")), "nova-store")
MAX_AGE = 24 * 3600
USER_AGENT = f"NovaStore/{__version__}"


@dataclass
class Rating:
    stars: list[int]   # index 1..5 = number of votes with that many stars
    total: int

    @property
    def average(self) -> float:
        votes = sum(self.stars[1:6])
        if not votes:
            return 0.0
        return sum(i * self.stars[i] for i in range(1, 6)) / votes


@dataclass
class Review:
    summary: str
    description: str
    rating: float      # 0..5
    user: str
    date: int
    version: str
    distro: str


def _cache_path(name: str) -> str:
    os.makedirs(CACHE_DIR, exist_ok=True)
    return os.path.join(CACHE_DIR, name)


def _user_hash() -> str:
    path = _cache_path("odrs-user-hash")
    try:
        with open(path, encoding="ascii") as fh:
            value = fh.read().strip()
            if len(value) == 40:
                return value
    except OSError:
        pass
    value = secrets.token_hex(20)
    with open(path, "w", encoding="ascii") as fh:
        fh.write(value)
    return value


def _request(url: str, payload: dict | None = None, timeout: int = 20) -> bytes:
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(url, data=data, headers={"User-Agent": USER_AGENT})
    if data is not None:
        req.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return resp.read()


class RatingsService:
    def __init__(self) -> None:
        self._ratings: dict[str, Rating] = {}

    def load(self, allow_network: bool = True) -> int:
        """Load aggregated ratings (cache first, network if stale)."""
        path = _cache_path("ratings.json")
        raw = None
        fresh = os.path.exists(path) and time.time() - os.path.getmtime(path) < MAX_AGE
        if allow_network and not fresh:
            try:
                raw = _request(f"{API}/ratings")
                with open(path + ".tmp", "wb") as fh:
                    fh.write(raw)
                os.replace(path + ".tmp", path)
            except OSError:
                raw = None
        if raw is None and os.path.exists(path):
            with open(path, "rb") as fh:
                raw = fh.read()
        if not raw:
            return 0
        data = json.loads(raw)
        self._ratings = {}
        for app_id, r in data.items():
            stars = [int(r.get(f"star{i}", 0)) for i in range(6)]
            self._ratings[app_id] = Rating(stars, int(r.get("total", 0)))
        return len(self._ratings)

    def get(self, app_id: str) -> Rating | None:
        return self._ratings.get(app_id) or self._ratings.get(app_id + ".desktop")

    def popular(self, ids, limit: int = 12) -> list[str]:
        """App ids from ``ids`` sorted by number of ratings (real popularity)."""
        scored = [(self.get(i).total if self.get(i) else 0, i) for i in ids]
        scored = [s for s in scored if s[0] > 0]
        scored.sort(reverse=True)
        return [i for _, i in scored[:limit]]

    @staticmethod
    def fetch_reviews(app_id: str, locale: str, version: str = "", limit: int = 10) -> list[Review]:
        payload = {
            "app_id": app_id,
            "user_hash": _user_hash(),
            "locale": locale or "en_US",
            "distro": "NovaOS",
            "version": version or "unknown",
            "limit": limit,
        }
        raw = _request(f"{API}/fetch", payload)
        out = []
        for r in json.loads(raw):
            out.append(
                Review(
                    summary=r.get("summary", ""),
                    description=r.get("description", ""),
                    rating=int(r.get("rating", 0)) / 20.0,
                    user=r.get("user_display") or "",
                    date=int(r.get("date_created", 0)),
                    version=r.get("version", ""),
                    distro=r.get("distro", ""),
                )
            )
        return out

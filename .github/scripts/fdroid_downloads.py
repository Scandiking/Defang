"""Count all-time F-Droid APK downloads for Defang from F-Droid's public metrics.

Source: https://fdroid.gitlab.io/metrics/<server>/<week>.json (weekly per mirror).
Recomputes the full total from scratch every run, so it is idempotent.
Writes a shields.io endpoint JSON plus a stats JSON with per-version/per-week detail.
"""

import json
import sys
import urllib.request
from collections import Counter

BASE = "https://fdroid.gitlab.io/metrics"
SERVERS = [
    "http01.fdroid.net",
    "http02.fdroid.net",
    "http03.fdroid.net",
    "http04.fdroid.net",
    "originserver.f-droid.org",
]
PACKAGE = "com.defang.launcher"
FIRST_WEEK = "2026-07-01"  # Defang entered F-Droid in July 2026; earlier weeks have no hits.
PREFIX = f"/repo/{PACKAGE}_"


def get(url: str):
    req = urllib.request.Request(url, headers={"User-Agent": "defang-badge"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.load(r)


def main(badge_path: str, stats_path: str, previous_path: str | None) -> None:
    total = 0
    by_version: Counter[str] = Counter()
    by_week: Counter[str] = Counter()
    latest_week = ""

    for server in SERVERS:
        for name in sorted(get(f"{BASE}/{server}/index.json")):
            week = name.removesuffix(".json")
            if week < FIRST_WEEK:
                continue
            latest_week = max(latest_week, week)
            for path, value in get(f"{BASE}/{server}/{name}").get("paths", {}).items():
                if path.startswith(PREFIX) and path.endswith(".apk"):
                    hits = value["hits"] if isinstance(value, dict) else value
                    total += hits
                    by_version[path[len(PREFIX):-4]] += hits
                    by_week[week] += hits

    # An all-time count can't shrink. If it does, a mirror's data went missing:
    # fail and keep the last published number instead of publishing a wrong one.
    if previous_path:
        try:
            with open(previous_path, encoding="utf-8") as f:
                previous = json.load(f)["total_downloads"]
        except (OSError, KeyError, ValueError):
            previous = 0
        if total < previous:
            sys.exit(f"New total {total} < previous {previous}; refusing to publish.")

    with open(badge_path, "w", encoding="utf-8") as f:
        json.dump(
            {
                "schemaVersion": 1,
                "label": "F-Droid downloads (all time)",
                "message": str(total),
                "color": "blue",
                "namedLogo": "fdroid",
            },
            f,
            indent=2,
        )
    with open(stats_path, "w", encoding="utf-8") as f:
        json.dump(
            {
                "package_id": PACKAGE,
                "total_downloads": total,
                "data_through_week": latest_week,
                "by_version_code": dict(sorted(by_version.items(), key=lambda kv: int(kv[0]))),
                "by_week": dict(sorted(by_week.items())),
            },
            f,
            indent=2,
        )
    print(f"{PACKAGE}: {total} downloads (data through week {latest_week})")


if __name__ == "__main__":
    main(*sys.argv[1:3], sys.argv[3] if len(sys.argv) > 3 else None)

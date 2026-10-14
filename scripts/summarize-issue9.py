"""Independently verify and summarize preserved Issue9 CSV windows (stdlib only)."""
import argparse
import csv
import json
import math
from pathlib import Path


def read_rows(path):
    with path.open(encoding="utf-8-sig", newline="") as stream:
        return list(csv.DictReader(stream))


def stats(values):
    values = sorted(values)
    if not values:
        return {"count": 0}
    return {"count": len(values), "medianMs": values[len(values) // 2] / 1e6,
            "p95Ms": values[math.ceil(len(values) * .95) - 1] / 1e6,
            "maxMs": values[-1] / 1e6}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--detailed", type=Path, required=True)
    parser.add_argument("--normal", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    results = {}
    for kind, root in (("detailed", args.detailed), ("normal", args.normal)):
        assert json.loads((root / "result.json").read_text(encoding="utf-8-sig"))["passed"], root
        windows = sorted(root.glob("round-*-load-*"))
        assert len(windows) == 9, (root, len(windows))
        rows = []
        for window in windows:
            parts = window.name.split("-")
            round_number, load = int(parts[1]), int(parts[3])
            ticks = read_rows(window / "ticks.csv")
            assert len(ticks) == 600, window
            tick_stats = stats([int(row["world_tick_ns"]) for row in ticks])
            row = {"round": round_number, "load": load, "tick": tick_stats}
            if kind == "detailed":
                calls = read_rows(window / "sampling.csv")
                assert len(calls) == load * 30, window
                for call in calls:
                    assert int(call["fluid_queries"]) == 245 and int(call["water_scans"]) == 1
                    assert int(call["water_found"]) == 0
                summary = json.loads((window / "summary.json").read_text(encoding="utf-8-sig"))
                assert summary["tick"] == tick_stats, window
                row.update(calls=len(calls), fluidQueries=sum(int(call["fluid_queries"]) for call in calls),
                           humidity=stats([int(call["humidity_ns"]) for call in calls]),
                           waterScan=stats([int(call["water_scan_ns"]) for call in calls]),
                           totalHumidityMs=sum(int(call["humidity_ns"]) for call in calls) / 1e6)
            else:
                frames = read_rows(window / "normal-render-intervals.csv")
                frame_stats = stats([int(frame["frame_interval_ns"]) for frame in frames])
                summary = json.loads((window / "normal-summary.json").read_text(encoding="utf-8-sig"))
                assert summary["renderInterval"] == frame_stats and summary["tick"] == tick_stats, window
                row["renderInterval"] = frame_stats
            rows.append(row)
        results[kind] = {"environment": json.loads((root / "environment.json").read_text(encoding="utf-8-sig")), "windows": rows}
    args.output.write_text(json.dumps(results, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for kind, result in results.items():
        print(kind)
        for row in result["windows"]:
            print(json.dumps(row))


if __name__ == "__main__":
    main()

"""Verify the portable Issue #9 evidence bundle using Python's standard library."""
import argparse
import csv
import hashlib
import json
import math
from pathlib import Path


def require(condition, message):
    if not condition:
        raise ValueError(message)


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def read_csv(path):
    with path.open(encoding="utf-8-sig", newline="") as stream:
        return list(csv.DictReader(stream))


def stats(values):
    values = sorted(values)
    if not values:
        return {"count": 0}
    return {"count": len(values), "medianMs": values[len(values) // 2] / 1e6,
            "p95Ms": values[math.ceil(len(values) * .95) - 1] / 1e6,
            "maxMs": values[-1] / 1e6}


def verify(root):
    manifest = read_json(root / "manifest.json")
    declared = set()
    for entry in manifest["files"]:
        name = entry["path"]
        path = root / name
        require(path.resolve().is_relative_to(root.resolve()), f"Unsafe path: {name}")
        require(name not in declared, f"Duplicate manifest path: {name}")
        declared.add(name)
        data = path.read_bytes()
        require(len(data) == entry["bytes"], f"Size mismatch: {name}")
        require(hashlib.sha256(data).hexdigest() == entry["sha256"], f"Hash mismatch: {name}")
    actual = {p.relative_to(root).as_posix() for p in root.rglob("*") if p.is_file()}
    require(actual == declared | {"manifest.json"}, "Manifest does not cover exactly the bundle")
    expected = read_json(root / "reference-summary.json")
    corrected = read_json(root / "recomputed-utf8.json")
    discrepancy = read_json(root / "metadata-discrepancy.json")
    metadata_differences = []
    totals = {}
    for kind, run in (("normal", "normal-formal-4"), ("detailed", "detailed-formal-2")):
        run_root = root / "formal" / run
        result = read_json(run_root / "result.json")
        require(result.get("passed") is True, f"Capture failed: {run}")
        require(result.get("formalTickSamples") == 5400, f"Wrong root tick count: {run}")
        require(not (run_root / "failure.json").exists(), f"Failure marker: {run}")
        environment_path = run_root / "environment.json"
        environment = read_json(environment_path)
        require(environment == corrected[kind]["environment"], f"Corrected environment mismatch: {run}")
        if environment != expected[kind]["environment"]:
            require(kind == "detailed", f"Unexpected environment mismatch: {run}")
            differences = [key for key in environment if environment[key] != expected[kind]["environment"].get(key)]
            require(set(environment) == set(expected[kind]["environment"])
                    and sorted(differences) == ["jvmArgs", "template"], "Unexpected metadata fields differ")
            require(json.loads(environment_path.read_bytes().decode("cp936")) == expected[kind]["environment"],
                    "Recorded CP936 interpretation does not explain entire shared environment")
            observed = {"kind": kind, "fields": sorted(differences),
                        "rawUtf8EnvironmentSha256": hashlib.sha256(environment_path.read_bytes()).hexdigest(),
                        "cp936InterpretationEqualsSharedEnvironment": True}
            require(observed == discrepancy, "Metadata discrepancy record mismatch")
            metadata_differences.append(observed)
        windows = sorted(run_root.glob("round-*-load-*"))
        require({p.name for p in windows} == {f"round-{r}-load-{n}" for r in (1, 2, 3)
                                            for n in (0, 64, 128)}, f"Wrong windows: {run}")
        recomputed = []
        total_ticks = total_frames = total_calls = total_queries = 0
        for window in windows:
            parts = window.name.split("-")
            round_number, load = int(parts[1]), int(parts[3])
            ticks = read_csv(window / "ticks.csv")
            require(len(ticks) == 600, f"Wrong tick samples: {window.name}")
            require([int(t["sample"]) for t in ticks] == list(range(600)),
                    f"Discontinuous tick samples: {window.name}")
            row = {"round": round_number, "load": load,
                   "tick": stats([int(t["world_tick_ns"]) for t in ticks])}
            total_ticks += len(ticks)
            if kind == "normal":
                frames = read_csv(window / "normal-render-intervals.csv")
                frame_stats = stats([int(f["frame_interval_ns"]) for f in frames])
                require(bool(frames), f"Missing frame samples: {window.name}")
                exported = read_json(window / "normal-summary.json")
                require(exported["tick"] == row["tick"] and exported["renderInterval"] == frame_stats,
                        f"Normal export mismatch: {window.name}")
                row["renderInterval"] = frame_stats
                total_frames += len(frames)
            else:
                calls = read_csv(window / "sampling.csv")
                require(len(calls) == load * 30, f"Wrong call count: {window.name}")
                require(all(int(c["fluid_queries"]) == 245 and int(c["water_scans"]) == 1
                            and int(c["water_found"]) == 0 for c in calls),
                        f"Unexpected scan semantics: {window.name}")
                exported = read_json(window / "summary.json")
                require(exported.get("complete") is True and exported.get("overflow") is False,
                        f"Incomplete detailed window: {window.name}")
                row.update(calls=len(calls), fluidQueries=sum(int(c["fluid_queries"]) for c in calls),
                           humidity=stats([int(c["humidity_ns"]) for c in calls]),
                           waterScan=stats([int(c["water_scan_ns"]) for c in calls]),
                           totalHumidityMs=sum(int(c["humidity_ns"]) for c in calls) / 1e6)
                require(exported["tick"] == row["tick"], f"Detailed tick mismatch: {window.name}")
                for key, raw_key in (("humidityCalls", "humidity_ns"), ("waterScans", "water_scans"),
                                     ("fluidQueries", "fluid_queries"), ("humidityNs", "humidity_ns"),
                                     ("waterScanNs", "water_scan_ns"), ("fluidQueryNs", "fluid_query_ns")):
                    value = len(calls) if key == "humidityCalls" else sum(int(c[raw_key]) for c in calls)
                    require(exported["sampling"][key] == value, f"Detailed totals mismatch: {window.name}/{key}")
                if calls:
                    require(exported["detailedDistributions"]["humidity"] == row["humidity"]
                            and exported["detailedDistributions"]["waterScan"] == row["waterScan"],
                            f"Detailed distribution mismatch: {window.name}")
                total_calls += len(calls)
                total_queries += row["fluidQueries"]
            recomputed.append(row)
        require(recomputed == expected[kind]["windows"], f"Shared summary mismatch: {run}")
        require(recomputed == corrected[kind]["windows"], f"UTF-8 recomputation mismatch: {run}")
        totals[kind] = {"windows": len(windows), "tickSamples": total_ticks,
                        "renderIntervals": total_frames, "humidityCalls": total_calls,
                        "fluidQueries": total_queries}
    require(len(metadata_differences) == 1, "Expected recorded metadata discrepancy not found")
    return {"evidenceIntegrityPassed": True, "sharedNumericSummaryMatches": True,
            "sharedEnvironmentExactMatch": False, "metadataDifferences": metadata_differences,
            "manifestFiles": len(declared), "totals": totals,
            "referenceCommit": manifest["referenceCommit"],
            "scope": "Offline evidence verification; no new game run or historical bug fix claim"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bundle", type=Path)
    args = parser.parse_args()
    print(json.dumps(verify(args.bundle), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

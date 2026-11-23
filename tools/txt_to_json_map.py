#!/usr/bin/env python3
"""
Compiles the .txt maps of Greed Island into the .json maps the server loads.

The server reads a level as static/map_test_beta_<n>.json and only falls back to the .txt when
there is no .json (WorldService.loadMap). A .txt map has no episode config and is parsed by a
slower, stricter path, so every level is meant to have a .json next to its .txt. This script
produces them. It needs nothing but Python 3.8+ and does not start the server.

Usage (from the repo root):

    python3 tools/txt_to_json_map.py                    # every map_test_beta_*.txt in static/ that has no .json yet
    python3 tools/txt_to_json_map.py 11 12 20           # only map_test_beta_11/12/20
    python3 tools/txt_to_json_map.py path/to/map.txt    # one explicit file
    python3 tools/txt_to_json_map.py --force            # also rewrite .json files that already exist
    python3 tools/txt_to_json_map.py --check            # convert nothing, report what would happen
    python3 tools/txt_to_json_map.py --verify           # re-derive every existing .json from its .txt and
                                                        # report the ones that differ (hand edits show up here)

Config written into the .json (override with the flags below):

    --gate-price 50  --merchant-budget 50  --refill-rounds 4  --max-rounds 500

barbarianEnabled / villagerEnabled are not flags: they are true exactly when the map has a B / V,
which is what the existing levels do.

The .txt format
---------------
First line "<height> <width>", then <height> lines of <width> symbols:

    -  water      _  pasture    $  mountain    +  woods     >  elevation     |  gate
    P  the player   M  merchant   V  villager   B  barbarian

P, M, V and B stand on a space of their own that the file does not draw; like the Java loader the
space under them is the terrain symbol to their left (the one before them on the line, or on the
line above when the line starts with one). '<' is an elevation that has already been looted - it is
what GET /map/full/matrix prints - and is accepted as an elevation.
"""

import argparse
import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
STATIC = REPO / "src" / "main" / "resources" / "static"

# symbol -> className, exactly the names SpaceAdapter reads back
TERRAIN = {
    "-": "Water",
    "_": "Pasture",
    "$": "Mountain",
    "+": "Woods",
    ">": "Elevation",
    "<": "Elevation",
    "|": "Gate",
}
CHARACTERS = {"M": "Merchant", "V": "Villager", "B": "Barbarian"}
DEFAULT_TERRAIN = "_"  # under an NPC with no terrain before it at all


class MapError(Exception):
    pass


def parse_txt(path, config, warnings=None):
    """Reads one .txt map and returns the dict that gets written as JSON. Things that are legal but
    almost certainly not meant are appended to `warnings` when a list is given."""
    lines = path.read_text(encoding="utf-8").splitlines()
    if not lines:
        raise MapError("file is empty")

    header = lines[0].split()
    if len(header) != 2 or not all(h.isdigit() for h in header):
        raise MapError(f"line 1: expected '<height> <width>', got {lines[0]!r}")
    height, width = int(header[0]), int(header[1])

    rows = lines[1:]
    # trailing blank lines are harmless, missing or ragged rows are not
    while rows and not rows[-1].strip():
        rows.pop()
    if len(rows) != height:
        raise MapError(f"header says {height} rows, file has {len(rows)}")

    spaces = []
    characters = []
    players = []
    gate_count = 0
    prev = DEFAULT_TERRAIN

    for x, row in enumerate(rows):
        if len(row) != width:
            raise MapError(f"line {x + 2}: header says width {width}, row has {len(row)} symbols")
        out = []
        for y, sym in enumerate(row):
            if sym == "P" or sym in CHARACTERS:
                if sym == "P":
                    players.append((x, y))
                else:
                    characters.append((x, y, sym))
                terrain = prev
                if prev == "-" and warnings is not None:
                    warnings.append(f"line {x + 2}, column {y + 1}: {sym} stands on water, because the symbol "
                                    f"before it is '-' (an NPC takes the terrain to its left)")
            elif sym in TERRAIN:
                terrain = sym
                prev = sym
            else:
                raise MapError(f"line {x + 2}, column {y + 1}: unknown symbol {sym!r}")

            cell = {"className": TERRAIN[terrain], "x": x, "y": y}
            if terrain == "|":
                gate_count += 1
                cell["price"] = config["gatePrice"]
            out.append(cell)
        spaces.append(out)

    if len(players) != 1:
        raise MapError(f"a map needs exactly one P, found {len(players)}")
    if gate_count == 0:
        raise MapError("a map needs a gate '|', found none")

    npcs = []
    for x, y, sym in characters:  # row-major, the order the Java loader adds them in
        npc = {"className": CHARACTERS[sym], "x": x, "y": y}
        if sym == "M":
            npc["fullAmountOfGold"] = config["merchantBudget"]
        npcs.append(npc)

    present = {sym for _, _, sym in characters}
    px, py = players[0]
    # key order is the one Gson writes, so a regenerated file diffs cleanly against an old one
    return {
        "width": width,
        "height": height,
        "spaces": spaces,
        "characters": npcs,
        "thePlayer": {"className": "Player", "x": px, "y": py},
        "running": True,
        "config": {
            "gatePrice": config["gatePrice"],
            "merchantBudget": config["merchantBudget"],
            "merchantRefillRounds": config["merchantRefillRounds"],
            "barbarianEnabled": "B" in present,
            "villagerEnabled": "V" in present,
            "maxRounds": config["maxRounds"],
        },
    }


def dump(data):
    return json.dumps(data, separators=(",", ":"))


def resolve_targets(args):
    """The .txt files the command line asks for."""
    if not args.maps:
        return sorted(STATIC.glob("map_test_beta_*.txt"), key=natural_key)

    out = []
    for m in args.maps:
        if m.isdigit():
            out.append(STATIC / f"map_test_beta_{m}.txt")
        else:
            out.append(Path(m))
    return out


def natural_key(p):
    return [int(t) if t.isdigit() else t for t in re.split(r"(\d+)", p.name)]


def main():
    ap = argparse.ArgumentParser(description="Compile .txt maps into the .json maps the server loads.")
    ap.add_argument("maps", nargs="*", help="map numbers (11 -> map_test_beta_11.txt) or .txt paths; default: every map_test_beta_*.txt in static/")
    ap.add_argument("--force", action="store_true", help="overwrite .json files that already exist")
    ap.add_argument("--check", action="store_true", help="only report what would be written")
    ap.add_argument("--verify", action="store_true", help="compare existing .json files with what their .txt gives")
    ap.add_argument("--gate-price", type=int, default=50)
    ap.add_argument("--merchant-budget", type=int, default=50)
    ap.add_argument("--refill-rounds", type=int, default=4)
    ap.add_argument("--max-rounds", type=int, default=500)
    args = ap.parse_args()

    config = {
        "gatePrice": args.gate_price,
        "merchantBudget": args.merchant_budget,
        "merchantRefillRounds": args.refill_rounds,
        "maxRounds": args.max_rounds,
    }

    written = skipped = failed = differing = 0
    for txt in resolve_targets(args):
        target = txt.with_suffix(".json")
        if not txt.exists():
            print(f"FAIL  {txt.name}: no such file")
            failed += 1
            continue

        warnings = []
        try:
            data = parse_txt(txt, config, warnings)
        except MapError as e:
            print(f"FAIL  {txt.name}: {e}")
            failed += 1
            continue
        for w in warnings:
            print(f"warn  {txt.name}: {w}")

        if args.verify:
            if not target.exists():
                continue
            if json.loads(target.read_text(encoding="utf-8")) == data:
                print(f"same  {target.name}")
            else:
                print(f"DIFF  {target.name} differs from {txt.name}")
                differing += 1
            continue

        if target.exists() and not args.force:
            print(f"skip  {target.name} (exists, --force to overwrite)")
            skipped += 1
            continue

        if args.check:
            print(f"would write {target.name}")
        else:
            target.write_text(dump(data), encoding="utf-8")
            print(f"wrote {target.name}")
        written += 1

    if args.verify:
        print(f"\n{differing} differing, {failed} failed")
        return 1 if (differing or failed) else 0

    print(f"\n{written} {'would be ' if args.check else ''}written, {skipped} skipped, {failed} failed")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())

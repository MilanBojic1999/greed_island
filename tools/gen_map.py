#!/usr/bin/env python3
"""
Generates a new Greed Island map - a .txt and the .json the server loads - from a handful of knobs.

    python3 tools/gen_map.py                              # 15x30 map, random seed, next free number
    python3 tools/gen_map.py --seed 7 --height 20 --width 40 --lakes 3 --mountains 2 --barbarians 3
    python3 tools/gen_map.py --number 101 --villagers 0   # call it map 101, no villager on it
    python3 tools/gen_map.py --validate maps/map_test_beta_101.json   # check any map (.txt or .json)

The same seed and the same options always give the same map, so a seed is enough to share one.

Where the map goes
------------------
Into the folder `maps/` at the repo root (--out-dir changes it) as map_test_beta_<number>.txt and
.json. The server reads that folder before the levels bundled in its jar, so the map is played with

    PUT /map/restart?map_number=<number>&game_id=env_0

The server looks in the folder `maps` next to where it is started; --greed.maps.dir=<path> (or the
environment variable GREED_MAPS_DIR) points it somewhere else. Numbers that a bundled level already
uses replace that level for as long as the file is there, so by default the generator numbers new
maps from 100 up.

What a generated map is
-----------------------
A ring of water around an island of pasture with lakes (water), woods and mountains on it. A
mountain is a plateau that can only be entered through an elevation ramp (the game's rule: a step
onto or off a mountain needs an elevation at the other end). The player, the merchant and the
gate are put on cells that the player can walk to, so the map can always be finished; the map is
checked again after it has been compiled and nothing is written if that check fails. "Can be
finished" means the player can walk to the merchant and the gate - it says nothing about how much
loot the map holds or how hard it is to earn the gate's price.
"""

import argparse
import json
import random
import sys
from collections import deque
from pathlib import Path

import txt_to_json_map as converter  # lives next to this file; its folder is on sys.path

REPO = converter.REPO
DEFAULT_OUT_DIR = REPO / "maps"
FIRST_GENERATED_NUMBER = 100

WATER, PASTURE, MOUNTAIN, WOODS, ELEVATION, GATE = "-", "_", "$", "+", ">", "|"
NPC_SYMBOLS = "PMVB"
MAX_BLOB_ATTEMPTS = 40


class GenerationError(Exception):
    pass


# --------------------------------------------------------------------------- walking rules ---

def can_step(frm, to):
    """GameMap.canStep on terrain symbols: water is solid, and a step onto or off a mountain needs
    an elevation at the other end of it."""
    if to == WATER:
        return False
    from_mountain, to_mountain = frm == MOUNTAIN, to == MOUNTAIN
    if from_mountain == to_mountain:
        return True
    return (to if from_mountain else frm) == ELEVATION


def walk(terrain, start):
    """Distance in moves from `start` to every cell the player can walk to (4 directions)."""
    h, w = len(terrain), len(terrain[0])
    dist = {start: 0}
    queue = deque([start])
    while queue:
        x, y = queue.popleft()
        for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
            if 0 <= nx < h and 0 <= ny < w and (nx, ny) not in dist \
                    and can_step(terrain[x][y], terrain[nx][ny]):
                dist[(nx, ny)] = dist[(x, y)] + 1
                queue.append((nx, ny))
    return dist


# ------------------------------------------------------------------------------- validating ---

def check_compiled(data):
    """Problems of a compiled map (the dict a .json holds); an empty list means it is playable."""
    names = {"Water": WATER, "Pasture": PASTURE, "Mountain": MOUNTAIN,
             "Woods": WOODS, "Elevation": ELEVATION, "Gate": GATE}
    terrain = [[names[c["className"]] for c in row] for row in data["spaces"]]
    player = (data["thePlayer"]["x"], data["thePlayer"]["y"])
    reachable = walk(terrain, player)

    problems = []
    if terrain[player[0]][player[1]] == WATER:
        problems.append("the player starts in the water")
    merchants = [c for c in data["characters"] if c["className"] == "Merchant"]
    if len(merchants) != 1:
        problems.append(f"a map needs exactly one merchant, it has {len(merchants)}")
    for m in merchants:
        if (m["x"], m["y"]) not in reachable:
            problems.append(f"the player cannot walk to the merchant at ({m['x']}, {m['y']})")
    gates = [(x, y) for x, row in enumerate(terrain) for y, t in enumerate(row) if t == GATE]
    if not gates:
        problems.append("the map has no gate")
    elif not any(g in reachable for g in gates):
        problems.append("the player cannot walk to the gate")
    return problems


def load_for_validation(path):
    path = Path(path)
    if path.suffix == ".json":
        return json.loads(path.read_text(encoding="utf-8"))
    return converter.parse_txt(path, {"gatePrice": 50, "merchantBudget": 50,
                                      "merchantRefillRounds": 4, "maxRounds": 500})


# ------------------------------------------------------------------------------- generating ---

def interior_cells(h, w):
    return [(x, y) for x in range(1, h - 1) for y in range(1, w - 1)]


def grow_blob(rng, terrain, kind, size, allowed):
    """Grows a blob of `kind` from a random cell by adding random neighbours, on cells that are
    currently one of `allowed`. Stops early when it has nowhere left to grow."""
    h, w = len(terrain), len(terrain[0])
    starts = [c for c in interior_cells(h, w) if terrain[c[0]][c[1]] in allowed]
    if not starts:
        return
    cell = rng.choice(starts)
    blob = {cell}
    frontier = [cell]
    while frontier and len(blob) < size:
        x, y = rng.choice(frontier)
        options = [(nx, ny) for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1))
                   if 1 <= nx < h - 1 and 1 <= ny < w - 1 and (nx, ny) not in blob
                   and terrain[nx][ny] in allowed]
        if not options:
            frontier.remove((x, y))
            continue
        nxt = rng.choice(options)
        blob.add(nxt)
        frontier.append(nxt)
    for x, y in blob:
        terrain[x][y] = kind


def place_mountain(rng, terrain):
    """A rectangular plateau with an elevation ramp along one side. Gives up quietly when no spot
    on the island has room for both."""
    h, w = len(terrain), len(terrain[0])
    for _ in range(MAX_BLOB_ATTEMPTS):
        mh, mw = rng.randint(2, 5), rng.randint(2, 6)
        x0, y0 = rng.randint(1, h - 2), rng.randint(1, w - 2)
        plateau = [(x, y) for x in range(x0, x0 + mh) for y in range(y0, y0 + mw)]

        side = rng.choice("NSWE")
        if side == "N":
            ramp = [(x0 - 1, y) for y in range(y0, y0 + mw)]
        elif side == "S":
            ramp = [(x0 + mh, y) for y in range(y0, y0 + mw)]
        elif side == "W":
            ramp = [(x, y0 - 1) for x in range(x0, x0 + mh)]
        else:
            ramp = [(x, y0 + mw) for x in range(x0, x0 + mh)]
        # a ramp of two or three cells is enough, a whole side of elevation is a staircase
        keep = rng.randint(1, min(3, len(ramp)))
        start = rng.randint(0, len(ramp) - keep)
        ramp = ramp[start:start + keep]

        if all(1 <= x < h - 1 and 1 <= y < w - 1 and terrain[x][y] in (PASTURE, WOODS)
               for x, y in plateau + ramp):
            for x, y in plateau:
                terrain[x][y] = MOUNTAIN
            for x, y in ramp:
                terrain[x][y] = ELEVATION
            return True
    return False


def left_is_pasture(terrain, x, y):
    return y > 0 and terrain[x][y - 1] == PASTURE


def generate(args, rng):
    h, w = args.height, args.width
    terrain = [[WATER if x in (0, h - 1) or y in (0, w - 1) else PASTURE for y in range(w)]
               for x in range(h)]

    for _ in range(args.lakes):
        grow_blob(rng, terrain, WATER, rng.randint(4, max(5, (h * w) // 40)), {PASTURE})
    for _ in range(args.woods):
        grow_blob(rng, terrain, WOODS, rng.randint(4, max(5, (h * w) // 30)), {PASTURE})
    placed = sum(place_mountain(rng, terrain) for _ in range(args.mountains))
    if placed < args.mountains:
        print(f"note: only {placed} of {args.mountains} mountains fitted on the island")

    # An NPC takes the terrain symbol to its left when the file is read (see txt_to_json_map), so
    # every cell that holds one has to be pasture with pasture on its left: then it is drawn as the
    # letter and read back as the pasture it stands on.
    def spots():
        return [(x, y) for x, y in interior_cells(h, w)
                if terrain[x][y] == PASTURE and left_is_pasture(terrain, x, y)]

    candidates = spots()
    if not candidates:
        raise GenerationError("no pasture left to stand on - fewer lakes/woods/mountains, or a bigger map")
    player = rng.choice(candidates)

    dist = walk(terrain, player)
    walkable_pasture = [c for c in dist if terrain[c[0]][c[1]] == PASTURE and c != player]
    if not walkable_pasture:
        raise GenerationError("the player is boxed in - try another seed")

    # the gate is far from the player, not next to it: the far half of what can be walked to
    ordered = sorted(walkable_pasture, key=lambda c: dist[c])
    gate = rng.choice(ordered[len(ordered) // 2:])
    terrain[gate[0]][gate[1]] = GATE

    taken = {player, gate}
    # the gate changed what is "left of" the cell to its right, so look at the spots again
    free = [c for c in spots() if c in dist and c not in taken]

    def take(count, what):
        if len(free) < count:
            raise GenerationError(f"no room for {count} {what} - a bigger map or fewer NPCs")
        chosen = rng.sample(free, count)
        for c in chosen:
            free.remove(c)
            taken.add(c)
        return chosen

    npcs = {"P": [player], "M": take(1, "merchant"),
            "V": take(args.villagers, "villagers"), "B": take(args.barbarians, "barbarians")}
    return terrain, npcs


def render(terrain, npcs):
    grid = [row[:] for row in terrain]
    for letter, cells in npcs.items():
        for x, y in cells:
            grid[x][y] = letter
    return f"{len(grid)} {len(grid[0])}\n" + "\n".join("".join(r) for r in grid) + "\n"


def terrain_of_compiled(data):
    names = {"Water": WATER, "Pasture": PASTURE, "Mountain": MOUNTAIN,
             "Woods": WOODS, "Elevation": ELEVATION, "Gate": GATE}
    return [[names[c["className"]] for c in row] for row in data["spaces"]]


def next_free_number(out_dir):
    used = set()
    for folder in (out_dir, converter.STATIC):
        for f in folder.glob("map_test_beta_*.*") if folder.is_dir() else []:
            digits = f.stem.rsplit("_", 1)[-1]
            if digits.isdigit():
                used.add(int(digits))
    n = FIRST_GENERATED_NUMBER
    while n in used:
        n += 1
    return n


def main():
    ap = argparse.ArgumentParser(description="Generate a Greed Island map.")
    ap.add_argument("--validate", metavar="MAP", help="check a .txt or .json map and exit")
    ap.add_argument("--height", type=int, default=15)
    ap.add_argument("--width", type=int, default=30)
    ap.add_argument("--seed", type=int, help="same seed and options, same map (default: random, and printed)")
    ap.add_argument("--lakes", type=int, default=2)
    ap.add_argument("--woods", type=int, default=3)
    ap.add_argument("--mountains", type=int, default=1)
    ap.add_argument("--villagers", type=int, default=1)
    ap.add_argument("--barbarians", type=int, default=1)
    ap.add_argument("--number", type=int, help=f"map number (default: the first free one from {FIRST_GENERATED_NUMBER})")
    ap.add_argument("--out-dir", type=Path, default=DEFAULT_OUT_DIR)
    ap.add_argument("--force", action="store_true", help="overwrite the map of that number if it exists")
    ap.add_argument("--gate-price", type=int, default=50)
    ap.add_argument("--merchant-budget", type=int, default=50)
    ap.add_argument("--refill-rounds", type=int, default=4)
    ap.add_argument("--max-rounds", type=int, default=500)
    args = ap.parse_args()

    if args.validate:
        try:
            problems = check_compiled(load_for_validation(args.validate))
        except (converter.MapError, OSError, KeyError, ValueError) as e:
            print(f"FAIL  {args.validate}: {e}")
            return 1
        for p in problems:
            print(f"FAIL  {p}")
        if not problems:
            print("ok    the player can walk to the merchant and the gate")
        return 1 if problems else 0

    if args.height < 7 or args.width < 7:
        ap.error("--height and --width must be at least 7")
    if min(args.lakes, args.woods, args.mountains, args.villagers, args.barbarians) < 0:
        ap.error("counts cannot be negative")

    seed = args.seed if args.seed is not None else random.SystemRandom().randrange(1_000_000)
    number = args.number if args.number is not None else next_free_number(args.out_dir)
    txt_path = args.out_dir / f"map_test_beta_{number}.txt"
    json_path = txt_path.with_suffix(".json")
    if (txt_path.exists() or json_path.exists()) and not args.force:
        print(f"FAIL  map {number} already exists in {args.out_dir} (--force to overwrite, or pick another --number)")
        return 1

    rng = random.Random(seed)
    try:
        terrain, npcs = generate(args, rng)
        text = render(terrain, npcs)

        # compile exactly what will be written, and check the result - not the intention
        scratch = args.out_dir / f".gen_{number}.txt"
        args.out_dir.mkdir(parents=True, exist_ok=True)
        scratch.write_text(text, encoding="utf-8")
        try:
            data = converter.parse_txt(scratch, {
                "gatePrice": args.gate_price, "merchantBudget": args.merchant_budget,
                "merchantRefillRounds": args.refill_rounds, "maxRounds": args.max_rounds})
        finally:
            scratch.unlink(missing_ok=True)

        if terrain_of_compiled(data) != terrain:
            raise GenerationError("the compiled map has other terrain than the generated one (a bug in the generator)")
        problems = check_compiled(data)
        if problems:
            raise GenerationError("; ".join(problems))
    except (GenerationError, converter.MapError) as e:
        print(f"FAIL  seed {seed}: {e}")
        return 1

    txt_path.write_text(text, encoding="utf-8")
    json_path.write_text(converter.dump(data), encoding="utf-8")

    print(text)
    print(f"wrote {txt_path}\nwrote {json_path}")
    print(f"seed {seed}   play it with: PUT /map/restart?map_number={number}&game_id=env_0")
    return 0


if __name__ == "__main__":
    sys.exit(main())

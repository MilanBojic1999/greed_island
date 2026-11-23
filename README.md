# greed_island

RAF-Deep-RPG — a 2D grid RPG served over a REST API, used as the environment for the
Reinforcement Learning student project. The player walks an island, loots the fields, sells
what it finds to a merchant and pays the gate to leave. A villager hands out presents, a
barbarian steals.

The full documentation (in Serbian) is `src/main/resources/static/game_documentation.tex`,
and its rendered PDF sits next to it. It covers the materials, the fields and their loot
probabilities, the NPC behaviour, and the whole API.

## Running the server

Needs JDK 17. The server listens on `http://localhost:8080`.

```
./gradlew bootRun
```

## Playing one episode

An episode is one session. `PUT /map/restart` both creates a session and resets it, so a
training loop calls the same thing at the start and between two episodes:

```
PUT  /map/restart?map_number=1&seed=7&game_id=env_0
     -> { "gameId": "env_0", "seed": 7, "mapNumber": 1, "config": { ... } }

GET  /game/env_0/state
     -> the first observation, the one env.reset() would return

POST /game/env_0/step?action=UP
     -> { "matrix": ..., "inventory": ..., "gold": ..., "round": ...,
          "done": false, "won": false, "events": ["LOOTED Rice"], ... }
```

One `POST /game/{gameId}/step` is one full environment transition — the player acts, then
every NPC acts, then the state is returned. It is the direct analogue of `env.step()`: no
polling, no buffered actions, nothing moving between two calls.

Sessions named differently are independent, so eight parallel environments are eight
`game_id` values. Actions are `UP`, `DOWN`, `LEFT`, `RIGHT` and `WAIT`.

### The rest of the API

| Endpoint | What it does |
|---|---|
| `PUT /map/restart` | starts an episode; takes `map_number`, `seed`, `game_id` and the config overrides below |
| `POST /game/{gameId}/step?action=UP` | plays one round and returns the whole state |
| `GET /game/{gameId}/state` | the same observation without playing a round |
| `GET /game/sessions` | every session the server is keeping |
| `DELETE /game/{gameId}` | closes a session |
| `GET /player/view?radius=2` | a `(2r+1)²` crop around the player, for partial observability / CNNs |
| `GET /map/full/matrix` | the full map as a matrix of symbols |
| `GET /player/inventory`, `/inventory/gold`, `/inventory/value` | the player's bag and gold |
| `PUT /player/{up,down,left,right,wait}` | the legacy asynchronous mode, used by the frontend |

Every level setting can be overridden on `/map/restart`, which is what makes a curriculum
possible without touching the Java code: `gate_price`, `merchant_budget`,
`merchant_refill_rounds`, `barbarian_enabled`, `villager_enabled`, `max_rounds`. The same
fields can be written into a level's JSON as an optional `config` object.

`seed` makes an episode reproducible — every loot draw, NPC move, present and theft comes
from it. Omit it and one is drawn and returned anyway, so any episode can be replayed after
the fact.

## Making your own maps

Needs Python 3 and nothing else. From the repo root:

```
python3 tools/gen_map.py --seed 7 --height 20 --width 40 --lakes 3 --mountains 2 --barbarians 3
```

This writes `maps/map_test_beta_100.txt` and `.json` (the first free number from 100 up) and
prints the map. The same seed and options always give the same map. The options are `--height`,
`--width`, `--lakes`, `--woods`, `--mountains`, `--villagers`, `--barbarians`, `--number`,
`--gate-price`, `--merchant-budget`, `--refill-rounds` and `--max-rounds`; `--force` overwrites a
map of the same number.

The server reads the `maps/` folder next to where it is started before it reads its bundled
levels, so a new map needs no rebuild. Start an episode on it with its number:

```
PUT /map/restart?map_number=100&game_id=env_0
```

`--greed.maps.dir=<path>` (or the environment variable `GREED_MAPS_DIR`) points the server at a
different folder. A map in the folder with the same number as a bundled level replaces it while
the file is there, which is why generated maps start at 100.

A generated map can always be finished: the player, merchant and gate are placed on cells the
player can walk to. That says nothing about how much loot there is or how hard the gate's price
is to earn.

**Drawing a map by hand.** Write a `.txt` and compile it:

```
python3 tools/txt_to_json_map.py path/to/map.txt     # or: ... 13 14 for map_test_beta_13/14
```

The first line is `<height> <width>`, then one line per row: `-` water, `_` pasture, `$` mountain,
`+` woods, `>` elevation, `|` gate, and `P` player, `M` merchant, `V` villager, `B` barbarian.
A mountain can only be entered or left over an elevation. `P`, `M`, `V` and `B` take the terrain
of the symbol to their left, so a `P` straight after the border `-` stands in water. Without
arguments the script converts every `map_test_beta_*.txt` that has no `.json` yet, `--force`
rewrites existing ones and `--check` only reports. It rejects rows of the wrong length, unknown
symbols, and maps without exactly one `P` or without a gate.

`python3 tools/gen_map.py --validate <map.txt or map.json>` checks that the player can walk to the
merchant and the gate, for generated and hand drawn maps alike.

## Implementation details

Java 17, Spring Boot for the REST API, Gson for reading the level files. The six levels are
`src/main/resources/static/map_test_beta_{0..5}.json`.

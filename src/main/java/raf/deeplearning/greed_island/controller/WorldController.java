package raf.deeplearning.greed_island.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import raf.deeplearning.greed_island.Paths;
import raf.deeplearning.greed_island.dto.RestartResponse;
import raf.deeplearning.greed_island.model.EpisodeConfig;
import raf.deeplearning.greed_island.services.GameSession;
import raf.deeplearning.greed_island.services.WorldService;

@RestController
@CrossOrigin
@RequestMapping(Paths.MAP_PATH)
public class WorldController {


    private WorldService worldService;

    @Autowired
    public WorldController(WorldService worldService) {
        this.worldService = worldService;
    }

    @GetMapping("/full")
    public String getFullMap(@RequestParam(required = false, name = "game_id") String gameId) {
        return worldService.session(gameId).play(map -> map.getMapString());
    }

    @GetMapping("/full/matrix")
    public String[][] getFullMapMatrix(@RequestParam(required = false, name = "game_id") String gameId) {
        return worldService.session(gameId).play(map -> map.getMatrix());
    }

    @GetMapping("/isover")
    public boolean checkIfGameOver(@RequestParam(required = false, name = "game_id") String gameId) {
        return worldService.session(gameId).play(map -> map.isDone());
    }

    /**
     * Starts a new episode and answers with everything needed to write it down: the session it runs
     * in, the seed it runs on and the configuration it ended up with.
     * <p>
     * This is the one call that both creates and resets an environment. Without a "game_id" it is
     * the default session - the one the frontend shows - that starts over, exactly as before. With
     * one, it is that session: it is created if the server does not know it yet and played over from
     * the beginning if it does, so a training loop uses the same request for its first episode and
     * for every reset after it. Sessions named differently are independent and can be stepped at the
     * same time, which is what running several environments in parallel needs:
     * <pre>
     *   PUT /map/restart?map_number=1&amp;seed=100&amp;game_id=env_0
     *   PUT /map/restart?map_number=1&amp;seed=101&amp;game_id=env_1   (and so on)
     *   POST /game/env_0/step?action=UP                      (in parallel with env_1)
     * </pre>
     * Every setting of the level can be overridden here, which is what makes a curriculum possible
     * without touching the map files - the same level with the barbarian switched off, with a
     * cheaper gate or with a shorter round budget is one query parameter away. A parameter that is
     * not given keeps what the map JSON says, and a map that says nothing plays as it always did.
     *
     * @param mapNumber            the level, 0 to 5, 1 when not given
     * @param seed                 the seed of the episode, drawn at random when not given
     * @param gameId               the session to start the episode in, the default one when not given
     * @param gatePrice            the gold needed to open the gate
     * @param merchantBudget       the gold the merchant trades with
     * @param merchantRefillRounds rounds between two refills of that budget
     * @param barbarianEnabled     false takes the barbarians of the level off the map
     * @param villagerEnabled      false takes the villagers of the level off the map
     * @param maxRounds            the episode ends unwon after this many rounds, 0 for no limit
     */
    @PutMapping("/restart")
    public RestartResponse restartGame(@RequestParam(required = false, name = "map_number") Integer mapNumber,
                               @RequestParam(required = false, name = "seed") Long seed,
                               @RequestParam(required = false, name = "game_id") String gameId,
                               @RequestParam(required = false, name = "gate_price") Integer gatePrice,
                               @RequestParam(required = false, name = "merchant_budget") Integer merchantBudget,
                               @RequestParam(required = false, name = "merchant_refill_rounds") Integer merchantRefillRounds,
                               @RequestParam(required = false, name = "barbarian_enabled") Boolean barbarianEnabled,
                               @RequestParam(required = false, name = "villager_enabled") Boolean villagerEnabled,
                               @RequestParam(required = false, name = "max_rounds") Integer maxRounds) {
        if (mapNumber == null) {
            mapNumber = WorldService.FALLBACK_MAP_NUMBER;
        }

        EpisodeConfig overrides = new EpisodeConfig();
        overrides.setGatePrice(gatePrice);
        overrides.setMerchantBudget(merchantBudget);
        overrides.setMerchantRefillRounds(merchantRefillRounds);
        overrides.setBarbarianEnabled(barbarianEnabled);
        overrides.setVillagerEnabled(villagerEnabled);
        overrides.setMaxRounds(maxRounds);

        System.out.printf("SEED: %d ~~\n", seed);
        System.out.println("CONFIG OVERRIDES: " + overrides);

        GameSession session;
        try {
            session = worldService.restart(gameId, mapNumber, seed, overrides);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Could not start map " + mapNumber + ": " + e);
        }

        return new RestartResponse(session.getId(), session.getMap().getGameSeed(), mapNumber,
                session.getMap().describeConfig());
    }


    /**
     * Plays the default session over again, on a new seed. Kept for the frontend; a training run is
     * better served by /map/restart, which says which session to reset and on which seed.
     */
    @PostMapping("/reset")
    public void resetMap() {
        System.out.println("RESET MAP");
        try {
            worldService.rerunGame();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

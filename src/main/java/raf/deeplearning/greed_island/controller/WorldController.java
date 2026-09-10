package raf.deeplearning.greed_island.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import raf.deeplearning.greed_island.Paths;
import raf.deeplearning.greed_island.dto.RestartResponse;
import raf.deeplearning.greed_island.model.EpisodeConfig;
import raf.deeplearning.greed_island.model.GameMap;
import raf.deeplearning.greed_island.services.WorldService;

import java.io.File;

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
    public String getFullMap() {
        return GameMap.getInstance().getMapString();
    }

    @GetMapping("/full/matrix")
    public String[][] getFullMapMatrix() {
        return GameMap.getInstance().getMatrix();
    }

    @GetMapping("/isover")
    public boolean checkIfGameOver() {
        return GameMap.getInstance().isDone();
    }

    /**
     * Starts a new episode on one of the levels and answers with everything needed to write it
     * down: the seed it runs on and the configuration it ended up with.
     * <p>
     * Every setting of the level can be overridden here, which is what makes a curriculum possible
     * without touching the map files - the same level with the barbarian switched off, with a
     * cheaper gate or with a shorter round budget is one query parameter away. A parameter that is
     * not given keeps what the map JSON says, and a map that says nothing plays as it always did.
     *
     * @param mapNumber            the level, 0 to 5, 1 when not given
     * @param seed                 the seed of the episode, drawn at random when not given
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
                               @RequestParam(required = false, name = "gate_price") Integer gatePrice,
                               @RequestParam(required = false, name = "merchant_budget") Integer merchantBudget,
                               @RequestParam(required = false, name = "merchant_refill_rounds") Integer merchantRefillRounds,
                               @RequestParam(required = false, name = "barbarian_enabled") Boolean barbarianEnabled,
                               @RequestParam(required = false, name = "villager_enabled") Boolean villagerEnabled,
                               @RequestParam(required = false, name = "max_rounds") Integer maxRounds) {
        if (mapNumber == null) {
            mapNumber = 1;
        }

        EpisodeConfig overrides = new EpisodeConfig();
        overrides.setGatePrice(gatePrice);
        overrides.setMerchantBudget(merchantBudget);
        overrides.setMerchantRefillRounds(merchantRefillRounds);
        overrides.setBarbarianEnabled(barbarianEnabled);
        overrides.setVillagerEnabled(villagerEnabled);
        overrides.setMaxRounds(maxRounds);

        String map = "static"+ File.separator +"map_test_beta_"+mapNumber+".json";
        System.out.println(map);
        System.out.printf("SEED: %d ~~\n", seed);
        System.out.println("CONFIG OVERRIDES: " + overrides);
        try {
            // loadGameMap seeds the episode and starts the game, calling rerunGame() on top of it
            // would reseed with another seed and leave a second round loop running on the same map
            worldService.loadGameMap(map, seed, overrides);
        } catch (Exception e) {
            e.printStackTrace();
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Could not start map " + mapNumber + ": " + e);
        }

        GameMap gameMap = GameMap.getInstance();
        return new RestartResponse(gameMap.getGameSeed(), mapNumber, gameMap.describeConfig());
    }


    @PostMapping("/reset")
    public void resetMap() {
        System.out.println("RESET MAP");
        GameMap.getInstance().setRunning(false);
        worldService.rerunGame();

    }
}
